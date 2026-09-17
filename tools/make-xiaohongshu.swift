import AppKit

// Xiaohongshu carousel generator: 1080x1440 (3:4) slides.
// Reuses the shipped screenshots and the brand palette from AppColors.kt.

let W: CGFloat = 1080
let H: CGFloat = 1440

func col(_ hex: UInt32, _ a: CGFloat = 1) -> NSColor {
    NSColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255, alpha: a)
}

let navy     = col(0x172554)
let navyDeep = col(0x081638)
let lime     = col(0xD6FF00)
let pageBg   = col(0xF7F8FA)
let text2    = col(0x475569)
let faint    = col(0x64748B)
let hairline = col(0xE2E8F0)
let marker   = col(0xF97316)
let white    = col(0xFFFFFF)

/// Run it from anywhere: `swift tools/make-xiaohongshu.swift`
let repoRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()   // tools/
    .deletingLastPathComponent()   // repository root
    .path
let shotDir = repoRoot + "/design/screenshots/"
let outDir  = repoRoot + "/design/xiaohongshu/"
try! FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

let fBold = "PingFangSC-Semibold"
let fMed  = "PingFangSC-Medium"
let fReg  = "PingFangSC-Regular"

func font(_ name: String, _ size: CGFloat) -> NSFont {
    NSFont(name: name, size: size) ?? NSFont.systemFont(ofSize: size)
}

func attrs(_ name: String, _ size: CGFloat, _ color: NSColor, kern: CGFloat = 0) -> [NSAttributedString.Key: Any] {
    let p = NSMutableParagraphStyle()
    p.lineSpacing = 0
    return [.font: font(name, size), .foregroundColor: color, .kern: kern, .paragraphStyle: p]
}

/// Draws one line of text with its top edge at `top` (measured from the canvas top).
@discardableResult
func text(_ s: String, x: CGFloat, top: CGFloat, _ a: [NSAttributedString.Key: Any]) -> NSSize {
    let str = NSAttributedString(string: s, attributes: a)
    let size = str.size()
    str.draw(at: NSPoint(x: x, y: H - top - size.height))
    return size
}

func textWidth(_ s: String, _ a: [NSAttributedString.Key: Any]) -> CGFloat {
    NSAttributedString(string: s, attributes: a).size().width
}

func centeredText(_ s: String, top: CGFloat, _ a: [NSAttributedString.Key: Any]) {
    text(s, x: (W - textWidth(s, a)) / 2, top: top, a)
}

/// Shrinks the size until the string fits `maxWidth`.
func fit(_ s: String, name: String, start: CGFloat, maxWidth: CGFloat, color: NSColor) -> [NSAttributedString.Key: Any] {
    var size = start
    while size > 12 {
        let a = attrs(name, size, color)
        if textWidth(s, a) <= maxWidth { return a }
        size -= 2
    }
    return attrs(name, size, color)
}

/// A highlighter stroke: a rounded bar sitting behind a word, very slightly tilted
/// so it reads as drawn by hand rather than printed.
func highlighter(x: CGFloat, top: CGFloat, width: CGFloat, height: CGFloat, tilt: CGFloat = -1.1) {
    let rect = NSRect(x: x, y: H - top - height, width: width, height: height)
    NSGraphicsContext.saveGraphicsState()
    let t = NSAffineTransform()
    t.translateX(by: rect.midX, yBy: rect.midY)
    t.rotate(byDegrees: tilt)
    t.translateX(by: -rect.midX, yBy: -rect.midY)
    t.concat()
    lime.setFill()
    NSBezierPath(roundedRect: rect, xRadius: 8, yRadius: 8).fill()
    NSGraphicsContext.restoreGraphicsState()
}

/// A circle scribbled around something, the way a person annotates a screenshot.
/// `cx`/`cy` are measured from the top-left like every other coordinate here, and
/// the sweep runs slightly past a full turn with a wobbling radius, so the two ends
/// overlap instead of meeting cleanly.
func scribbleCircle(cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, color: NSColor, width: CGFloat) {
    let steps = 150
    let start = -0.42 * CGFloat.pi
    let sweep = 2 * CGFloat.pi * 1.045
    let path = NSBezierPath()
    for i in 0...steps {
        let a = start + sweep * CGFloat(i) / CGFloat(steps)
        let wobble = 1
            + 0.022 * sin(CGFloat(i) * 0.13)
            + 0.012 * cos(CGFloat(i) * 0.31)
        let p = NSPoint(x: cx + cos(a) * rx * wobble, y: H - (cy - sin(a) * ry * wobble))
        if i == 0 { path.move(to: p) } else { path.line(to: p) }
    }
    path.lineWidth = width
    path.lineCapStyle = .round
    path.lineJoinStyle = .round
    color.setStroke()
    path.stroke()
}

// MARK: - Canvas

func render(_ file: String, _ draw: () -> Void) {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(W), pixelsHigh: Int(H),
                               bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
                               colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    rep.size = NSSize(width: W, height: H)
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    guard let png = rep.representation(using: .png, properties: [:]) else { exit(1) }
    try! png.write(to: URL(fileURLWithPath: outDir + file))
    print("wrote", file, "\(Int(W))x\(Int(H))")
}

func darkBackground() {
    NSGradient(starting: navyDeep, ending: navy)!.draw(in: NSRect(x: 0, y: 0, width: W, height: H), angle: -90)
}

/// The slide backdrop: a photographed sheet of paper rather than flat colour.
/// Generated with Z-Image-Turbo (see `design/xiaohongshu/jobs-models.json`); the
/// flat colour stays as the fallback so the set can still be rebuilt offline.
let paperFile = repoRoot + "/design/xiaohongshu/generated/models/bg-z-image-turbo.png"

func lightBackground() {
    if let image = NSImage(contentsOfFile: paperFile),
       let rep = NSBitmapImageRep(data: image.tiffRepresentation!),
       let cg = rep.cgImage {
        NSGraphicsContext.current!.cgContext.draw(cg, in: NSRect(x: 0, y: 0, width: W, height: H))
        return
    }
    pageBg.setFill()
    NSRect(x: 0, y: 0, width: W, height: H).fill()
}

// MARK: - Screenshots

/// Drops the phone's status bar (top 92 px) and navigation bar (bottom 125 px).
func shot(_ name: String) -> CGImage {
    let img = NSImage(contentsOfFile: shotDir + name)!
    let bmp = NSBitmapImageRep(data: img.tiffRepresentation!)!
    return bmp.cgImage!.cropping(to: CGRect(x: 0, y: 92, width: 1080, height: 1703))!
}

func shotHeight(_ cg: CGImage, width: CGFloat) -> CGFloat {
    width * CGFloat(cg.height) / CGFloat(cg.width)
}

/// Draws a screenshot as a rounded card; the bottom may run past the canvas edge,
/// which reads as the screen continuing off-frame.
func drawShot(_ cg: CGImage, centerX: CGFloat, top: CGFloat, width: CGFloat, radius: CGFloat,
              border: NSColor = col(0xFFFFFF, 0.92), borderWidth: CGFloat = 7,
              shadowAlpha: CGFloat = 0.38, shadowBlur: CGFloat = 48) {
    let h = shotHeight(cg, width: width)
    let rect = NSRect(x: centerX - width / 2, y: H - top - h, width: width, height: h)
    let path = NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius)

    NSGraphicsContext.saveGraphicsState()
    let shadow = NSShadow()
    shadow.shadowColor = NSColor.black.withAlphaComponent(shadowAlpha)
    shadow.shadowBlurRadius = shadowBlur
    shadow.shadowOffset = NSSize(width: 0, height: -18)
    shadow.set()
    white.setFill()
    path.fill()
    NSGraphicsContext.restoreGraphicsState()

    NSGraphicsContext.saveGraphicsState()
    path.addClip()
    NSGraphicsContext.current!.cgContext.draw(cg, in: rect)
    NSGraphicsContext.restoreGraphicsState()

    border.setStroke()
    path.lineWidth = borderWidth
    path.stroke()
}

// MARK: - Cover

/// Plain and spoken, on white: no logo lockup and no tag row, so it reads as
/// someone showing a screen rather than a product banner. `annotate` adds the
/// hand-drawn circle around the call button the sub-line is talking about.
func cover(_ file: String, annotate: Bool) {
    // Geometry of the call button on the first card, derived from the crop
    // (card x 90-465, button y 773-943) scaled into the inset screenshot below.
    let shotTop: CGFloat = 592
    let shotWidth: CGFloat = 664
    let scale = shotWidth / 1080
    let shotLeft = (W - shotWidth) / 2
    let buttonCX = shotLeft + (90 + 465) / 2 * scale
    let buttonCY = shotTop + (773 + 943) / 2 * scale
    let buttonRX = (465 - 90) / 2 * scale
    let buttonRY = (943 - 773) / 2 * scale

    render(file) {
        white.setFill()
        NSRect(x: 0, y: 0, width: W, height: H).fill()

        text("给不会用手机的老人做的安卓拨号 App", x: 88, top: 96, attrs(fMed, 34, faint, kern: 0.5))

        let l1 = attrs(fBold, 88, navy, kern: 2)
        let l1Pre = "老人"
        let l1Key = "不用记号码"
        let l1Line = l1Pre + l1Key + "，"
        let l1H = NSAttributedString(string: l1Line, attributes: l1).size().height
        let l1KeyX = 88 + textWidth(l1Pre, l1)
        highlighter(x: l1KeyX - 8, top: 182 + l1H * 0.30, width: textWidth(l1Key, l1) + 16, height: l1H * 0.62)
        text(l1Line, x: 88, top: 182, l1)

        let l2 = attrs(fBold, 88, navy, kern: 2)
        let l2Top = 182 + l1H + 8
        let l2H = NSAttributedString(string: "看照片按一下就打过去", attributes: l2).size().height
        text("看照片按一下就打过去", x: 88, top: l2Top, l2)

        text("点照片和名字没反应，只有绿色按钮会拨号", x: 88, top: l2Top + l2H + 44, attrs(fReg, 42, text2))

        let cg = shot("01-home-zh.png")
        drawShot(cg, centerX: W / 2, top: shotTop, width: shotWidth, radius: 40,
                 border: hairline, borderWidth: 3, shadowAlpha: 0.16, shadowBlur: 40)

        if annotate {
            scribbleCircle(cx: buttonCX, cy: buttonCY, rx: buttonRX + 8, ry: buttonRY + 4,
                           color: marker, width: 8)
        }
    }
}

// One cover only. The shipped cover is the composite built by
// tools/make-xhs-cover.swift; this one stays as the offline fallback.
cover("01-cover.png", annotate: false)

// MARK: - Slides 2-6, one screenshot each

func contentSlide(_ file: String, headline: String, sub: String, source: String, width: CGFloat = 680) {
    render(file) {
        lightBackground()

        lime.setFill()
        NSBezierPath(roundedRect: NSRect(x: 96, y: H - 156, width: 12, height: 56),
                     xRadius: 6, yRadius: 6).fill()

        text(headline, x: 132, top: 96, attrs(fBold, 66, navy, kern: 1))
        text(sub, x: 132, top: 194, attrs(fReg, 40, text2))

        drawShot(shot(source), centerX: W / 2, top: 322, width: width, radius: 40)
    }
}

contentSlide("02-home.png",
             headline: "打开手机看到的就是这一屏",
             sub: "照片和名字点了没反应，只有绿色按钮会拨号",
             source: "01-home-zh.png")

contentSlide("03-settings.png",
             headline: "家属设置一次就够了",
             sub: "亲人、字号、语言、导入导出都在这一页",
             source: "05-family-settings-zh.png")

contentSlide("04-add-contact.png",
             headline: "添加一位亲人",
             sub: "选一张照片，填上称呼和电话号码",
             source: "04-editor-zh.png")

contentSlide("05-text-size.png",
             headline: "字号不够大，可以调大",
             sub: "一共四档，调大后文字和按钮一起变大",
             source: "08-text-size-zh.png")

contentSlide("06-transfer.png",
             headline: "换一台手机，亲人一起搬过去",
             sub: "导出一个文件，在新手机上导入就行",
             source: "07-transfer-zh.png")

// MARK: - Slide 7, download

/// The same plain white language as the cover: the app icon for recognition, the
/// two facts that decide whether someone can install it, and the address. No badge,
/// no logo lockup — a call-to-action card is where the ad voice creeps back in.
render("07-download.png") {
    white.setFill()
    NSRect(x: 0, y: 0, width: W, height: H).fill()

    let iconSize: CGFloat = 128
    NSImage(contentsOfFile: repoRoot + "/design/icon-source.png")!
        .draw(in: NSRect(x: 88, y: H - 100 - iconSize, width: iconSize, height: iconSize))

    let head = attrs(fBold, 88, navy, kern: 2)
    let headPre = "完全免费，"
    let headKey = "代码开源"
    let headTop: CGFloat = 288
    let headH = NSAttributedString(string: headPre + headKey, attributes: head).size().height
    let keyX = 88 + textWidth(headPre, head)
    highlighter(x: keyX - 8, top: headTop + headH * 0.30, width: textWidth(headKey, head) + 16, height: headH * 0.62)
    text(headPre + headKey, x: 88, top: headTop, head)

    text("MIT 协议 · 无账号 · 无广告 · 不采集数据", x: 88, top: headTop + headH + 36, attrs(fReg, 40, text2))

    func card(_ top: CGFloat, _ height: CGFloat) {
        let path = NSBezierPath(roundedRect: NSRect(x: 88, y: H - top - height, width: W - 176, height: height),
                                xRadius: 32, yRadius: 32)
        white.setFill(); path.fill()
        hairline.setStroke(); path.lineWidth = 2.5; path.stroke()
    }

    card(570, 308)
    text("下载地址", x: 136, top: 616, attrs(fMed, 34, faint, kern: 2))
    text("github.com/Changjingjiu/SilverPhone", x: 136, top: 678,
         fit("github.com/Changjingjiu/SilverPhone", name: fBold, start: 52,
             maxWidth: W - 176 - 96, color: navy))
    text("在 Releases 页面下载最新的 APK", x: 136, top: 782, attrs(fReg, 36, text2))

    card(918, 262)
    text("安卓 6.0 以上 · 安装包 2.6 MB", x: 136, top: 962, attrs(fMed, 46, navy, kern: 0.5))
    text("不需要谷歌服务，国内手机也能装", x: 136, top: 1050, attrs(fReg, 38, text2))

    text("GitHub 搜 SilverPhone 也能找到", x: 88, top: 1268, attrs(fReg, 34, faint))
}
print("done ->", outDir)
