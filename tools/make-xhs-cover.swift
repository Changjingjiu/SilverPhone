import AppKit

// Builds the cover: the model's photo of a wall and a table, with the real app
// screenshot standing on it as a phone, and the caption set in the app's own
// typeface.
//
// The screenshot is never handed to the image model: it redraws any interface
// it is asked to re-compose, and the characters come back wrong. It only makes
// the background, which has nothing to get wrong.
//
//   swift tools/make-xhs-cover.swift
//
// Needs: design/xiaohongshu/generated/08-background.png
// Out:   design/xiaohongshu/final/01-cover.png

let W: CGFloat = 1080
let H: CGFloat = 1440

let repoRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .path
let bgFile   = repoRoot + "/design/xiaohongshu/generated/models/cover-bg-z-image.png"
let shotFile = repoRoot + "/design/screenshots/01-home-zh.png"
let outDir   = repoRoot + "/design/xiaohongshu/final/"
try! FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

let ink = NSColor(srgbRed: 0x17 / 255.0, green: 0x25 / 255.0, blue: 0x54 / 255.0, alpha: 1)

/// The photo, scaled to cover the canvas and cropped evenly on the sides.
func backgroundImage() -> CGImage {
    let img = NSImage(contentsOfFile: bgFile)!
    let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
    return rep.cgImage!
}

/// The app screenshot without the phone's status and navigation bars.
func screenshot() -> CGImage {
    let img = NSImage(contentsOfFile: shotFile)!
    let rep = NSBitmapImageRep(data: img.tiffRepresentation!)!
    return rep.cgImage!.cropping(to: CGRect(x: 0, y: 92, width: 1080, height: 1703))!
}

let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W), pixelsHigh: Int(H),
                           bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                           colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
rep.size = NSSize(width: W, height: H)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)

// Background: fill the canvas, cropping the sides rather than letterboxing.
let cg = backgroundImage()
let scale = max(W / CGFloat(cg.width), H / CGFloat(cg.height))
let drawn = NSRect(x: (W - CGFloat(cg.width) * scale) / 2,
                   y: (H - CGFloat(cg.height) * scale) / 2,
                   width: CGFloat(cg.width) * scale,
                   height: CGFloat(cg.height) * scale)
NSGraphicsContext.current!.cgContext.draw(cg, in: drawn)

// The phone, standing on the table, straight and unwarped.
let phoneWidth: CGFloat = 566
let phoneHeight = phoneWidth * 1703 / 1080
let bottomInset: CGFloat = 196
let screen = NSRect(x: (W - phoneWidth) / 2, y: bottomInset, width: phoneWidth, height: phoneHeight)
let bezel: CGFloat = 13
let body = screen.insetBy(dx: -bezel, dy: -bezel)
let bodyPath = NSBezierPath(roundedRect: body, xRadius: 46, yRadius: 46)

NSGraphicsContext.saveGraphicsState()
let shadow = NSShadow()
shadow.shadowColor = NSColor.black.withAlphaComponent(0.34)
shadow.shadowBlurRadius = 46
shadow.shadowOffset = NSSize(width: 6, height: -16)
shadow.set()
NSColor(srgbRed: 0.09, green: 0.10, blue: 0.13, alpha: 1).setFill()
bodyPath.fill()
NSGraphicsContext.restoreGraphicsState()

NSGraphicsContext.saveGraphicsState()
NSBezierPath(roundedRect: screen, xRadius: 34, yRadius: 34).addClip()
NSGraphicsContext.current!.cgContext.draw(screenshot(), in: screen)
NSGraphicsContext.restoreGraphicsState()

// Caption, in the app's own ink colour, over the empty wall at the top. Same
// size as the captions on the other slides so the carousel reads as one set.
let size: CGFloat = 66
let font = NSFont(name: "PingFangSC-Semibold", size: size) ?? NSFont.systemFont(ofSize: size)
let paragraph = NSMutableParagraphStyle()
paragraph.lineSpacing = size * 0.6
let attrs: [NSAttributedString.Key: Any] = [
    .font: font, .foregroundColor: ink, .kern: 0.5, .paragraphStyle: paragraph,
]
var top: CGFloat = 92
for line in ["老人不用记号码，", "看照片按一下就打过去"] {
    let str = NSAttributedString(string: line, attributes: attrs)
    str.draw(at: NSPoint(x: 88, y: H - top - str.size().height))
    top += str.size().height + size * 0.6
}

NSGraphicsContext.restoreGraphicsState()
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: outDir + "01-cover.png"))
print("wrote 01-cover.png  \(Int(W))x\(Int(H))")
