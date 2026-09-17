import AppKit

// Draws the captions onto the model's pictures.
//
// The model is asked for an image with no text at all: it renders Chinese
// unreliably (wrong strokes, invented lines) and styles each caption
// differently, so the words are set here instead, in the app's own typeface,
// at one consistent size and position across the whole carousel.
//
//   swift tools/make-xhs-final.swift
//
// Input:  design/xiaohongshu/generated/*.png   (no text)
// Output: design/xiaohongshu/final/*.png       (ready to upload)

let repoRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()   // tools/
    .deletingLastPathComponent()   // repository root
    .path
let inDir  = repoRoot + "/design/xiaohongshu/generated/"
let outDir = repoRoot + "/design/xiaohongshu/final/"
try! FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

let ink = NSColor(srgbRed: 0x17 / 255.0, green: 0x25 / 255.0, blue: 0x54 / 255.0, alpha: 1)

struct Caption {
    let lines: [String]
    /// Where the first line's top edge sits, as a fraction of image height.
    let topFraction: CGFloat
    /// Left-aligned by default, like a note; the download card is centred.
    let centered: Bool
    /// Cap height as a fraction of image width, so it scales with the picture.
    let sizeFraction: CGFloat

    init(_ lines: [String], top: CGFloat = 0.062, centered: Bool = false, size: CGFloat = 0.049) {
        self.lines = lines
        self.topFraction = top
        self.centered = centered
        self.sizeFraction = size
    }
}

let captions: [String: Caption] = [
    "01-cover":       Caption(["老人不用记号码，", "看照片按一下就打过去"], top: 0.055, size: 0.050),
    "02-home":        Caption(["照片和名字点了没反应"]),
    "03-settings":    Caption(["家属设置一次就够了"]),
    "04-add-contact": Caption(["选一张照片，填上称呼和号码"]),
    "05-text-size":   Caption(["字号不够大，可以调大"]),
    "06-transfer":    Caption(["换一台手机，亲人一起搬过去"]),
    // The icon sits in the upper half of this one, so the text goes below it.
    "07-download":    Caption(["完全免费，代码开源",
                               "github.com/Changjingjiu/SilverPhone",
                               "安卓 6.0 以上 · 安装包 2.6 MB"],
                              top: 0.545, centered: true, size: 0.040),
]

func draw(_ name: String, _ caption: Caption) {
    let source = inDir + name + ".png"
    guard let image = NSImage(contentsOfFile: source),
          let sourceRep = NSBitmapImageRep(data: image.tiffRepresentation!) else {
        print("跳过（没有文件）:", name)
        return
    }
    let w = CGFloat(sourceRep.pixelsWide)
    let h = CGFloat(sourceRep.pixelsHigh)

    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(w), pixelsHigh: Int(h),
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    rep.size = NSSize(width: w, height: h)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    NSGraphicsContext.current!.cgContext.draw(sourceRep.cgImage!,
                                              in: NSRect(x: 0, y: 0, width: w, height: h))
    NSGraphicsContext.restoreGraphicsState()

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

    let size = (w * caption.sizeFraction).rounded()
    let font = NSFont(name: "PingFangSC-Semibold", size: size) ?? NSFont.systemFont(ofSize: size)
    let paragraph = NSMutableParagraphStyle()
    paragraph.lineSpacing = size * 0.62
    let attrs: [NSAttributedString.Key: Any] = [
        .font: font, .foregroundColor: ink, .kern: 0.5, .paragraphStyle: paragraph,
    ]
    let left = w * 0.088
    var top = h * caption.topFraction
    for line in caption.lines {
        let str = NSAttributedString(string: line, attributes: attrs)
        let lineWidth = str.size().width
        let x = caption.centered ? (w - lineWidth) / 2 : left
        str.draw(at: NSPoint(x: x, y: h - top - str.size().height))
        top += str.size().height + size * 0.62
    }

    NSGraphicsContext.restoreGraphicsState()

    let data = rep.representation(using: .png, properties: [:])!
    try! data.write(to: URL(fileURLWithPath: outDir + name + ".png"))
    print("wrote \(name).png  \(Int(w))x\(Int(h))  标题 \(Int(size))px")
}

for name in captions.keys.sorted() {
    draw(name, captions[name]!)
}
print("done ->", outDir)
