import AppKit

// Prepares the 3:4 canvases that go into the image API as reference images:
// the real screenshot placed on a white 1080x1440 sheet, with the caption area
// left empty at the top. No text is drawn here — the model adds it.
//
// Qwen-Image-Edit ignores `image_size`, so the output ratio follows the input;
// padding to 3:4 here is what keeps the result usable on Xiaohongshu.
//
//   swift tools/make-xhs-canvas.swift
//
// Output: design/xiaohongshu/canvas/*.png

let W: CGFloat = 1080
let H: CGFloat = 1440

let repoRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()   // tools/
    .deletingLastPathComponent()   // repository root
    .path
let shotDir = repoRoot + "/design/screenshots/"
let outDir  = repoRoot + "/design/xiaohongshu/canvas/"
try! FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

let sheet   = NSColor(srgbRed: 1, green: 1, blue: 1, alpha: 1)
let hairline = NSColor(srgbRed: 0.886, green: 0.910, blue: 0.941, alpha: 1)

/// Drops the phone's status bar (top 92 px) and navigation bar (bottom 125 px).
func shot(_ name: String) -> CGImage {
    let img = NSImage(contentsOfFile: shotDir + name)!
    let bmp = NSBitmapImageRep(data: img.tiffRepresentation!)!
    return bmp.cgImage!.cropping(to: CGRect(x: 0, y: 92, width: 1080, height: 1703))!
}

func save(_ file: String, _ draw: () -> Void) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W), pixelsHigh: Int(H),
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    rep.size = NSSize(width: W, height: H)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    sheet.setFill()
    NSRect(x: 0, y: 0, width: W, height: H).fill()
    draw()
    NSGraphicsContext.restoreGraphicsState()
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: outDir + file))
    print("wrote", file)
}

/// Places a screenshot on the sheet, whole and unwarped, with a hairline edge.
/// `top` leaves the space above for the caption the model will draw.
func placeShot(_ cg: CGImage, width: CGFloat, top: CGFloat) {
    let h = width * CGFloat(cg.height) / CGFloat(cg.width)
    let rect = NSRect(x: (W - width) / 2, y: H - top - h, width: width, height: h)
    let path = NSBezierPath(roundedRect: rect, xRadius: 36, yRadius: 36)
    NSGraphicsContext.saveGraphicsState()
    path.addClip()
    NSGraphicsContext.current!.cgContext.draw(cg, in: rect)
    NSGraphicsContext.restoreGraphicsState()
    hairline.setStroke()
    path.lineWidth = 3
    path.stroke()
}

// Slide 1: same screenshot as slide 2, but larger and framed with room above so
// the model can rebuild it into a photo with a hand and a wall. Kept as big as
// the caption allows: the more pixels the screen gets, the fewer wrong strokes
// the model invents in the interface text.
save("01-cover.png") {
    placeShot(shot("01-home-zh.png"), width: 860, top: 330)
}

// Slides 2-6: one screen each, same geometry so the set reads as a series.
let screens: [(String, String)] = [
    ("02-home.png", "01-home-zh.png"),
    ("03-settings.png", "05-family-settings-zh.png"),
    ("04-add-contact.png", "04-editor-zh.png"),
    ("05-text-size.png", "08-text-size-zh.png"),
    ("06-transfer.png", "07-transfer-zh.png"),
]
for (file, source) in screens {
    save(file) { placeShot(shot(source), width: 650, top: 400) }
}

// Slide 7: the app icon alone; there is no screenshot without a version number
// that would go stale.
save("07-download.png") {
    let iconSize: CGFloat = 150
    let icon = NSImage(contentsOfFile: repoRoot + "/design/icon-source.png")!
    icon.draw(in: NSRect(x: 88, y: H - 130 - iconSize, width: iconSize, height: iconSize))
}

print("done ->", outDir)
