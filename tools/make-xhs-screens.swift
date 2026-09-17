import AppKit

// Prepares the interface screenshots for the post: same crops the other tools use,
// with the phone's status bar and navigation bar taken off so a screenshot reads
// as an interface rather than a capture of someone's phone.
//
//   swift tools/make-xhs-screens.swift
//
// In:  design/screenshots/*.png        (1080x1920 device captures)
// Out: design/xiaohongshu/screens/*.png (1080x1703, ready to upload)

let repoRoot = URL(fileURLWithPath: #filePath)
    .deletingLastPathComponent()
    .deletingLastPathComponent()
    .path
let inDir  = repoRoot + "/design/screenshots/"
let outDir = repoRoot + "/design/xiaohongshu/screens/"
try! FileManager.default.createDirectory(atPath: outDir, withIntermediateDirectories: true)

// Status bar occupies the top 92 px, the navigation bar the bottom 125 px.
let crop = CGRect(x: 0, y: 92, width: 1080, height: 1703)

/// Upload order, and the file names the post refers to.
let screens: [(source: String, output: String)] = [
    ("01-home-zh.png",             "01-首页.png"),
    ("05-family-settings-zh.png",  "02-家属设置.png"),
    ("04-editor-zh.png",           "03-添加亲人.png"),
    ("08-text-size-zh.png",        "04-字体大小.png"),
    ("07-transfer-zh.png",         "05-导入导出.png"),
    ("03-manage-zh.png",           "06-管理亲人.png"),
    ("14-about-zh.png",            "07-关于.png"),
]

for (source, output) in screens {
    let path = inDir + source
    guard let image = NSImage(contentsOfFile: path),
          let rep = NSBitmapImageRep(data: image.tiffRepresentation!),
          let cropped = rep.cgImage!.cropping(to: crop) else {
        print("跳过（读不到）:", source)
        continue
    }
    let out = NSBitmapImageRep(cgImage: cropped)
    try! out.representation(using: .png, properties: [:])!
        .write(to: URL(fileURLWithPath: outDir + output))
    print("wrote \(output)  \(cropped.width)x\(cropped.height)")
}
print("done ->", outDir)
