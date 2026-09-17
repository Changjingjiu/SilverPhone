<div align="center">

[English](README.md) · **简体中文**

<img src="design/hero/hero-zh.png" width="100%" alt="SilverPhone：左边写着「点一下照片，电话就拨出去」，右边两台手机分别显示亲人首页和家属设置页">

# SilverPhone

**给老人用的 Android 拨号应用。** 家属添加照片、称呼和号码，老人点一下绿色按钮就能拨出去。

<p>
  <a href="https://github.com/Changjingjiu/SilverPhone/releases/latest"><img src="https://img.shields.io/github/v/release/Changjingjiu/SilverPhone?style=flat-square&amp;color=146C43" alt="最新版本"></a>
  <img src="https://img.shields.io/badge/Android-6.0%2B-146C43?style=flat-square" alt="Android 6.0 及以上">
  <a href="https://github.com/Changjingjiu/SilverPhone/actions/workflows/android.yml"><img src="https://github.com/Changjingjiu/SilverPhone/actions/workflows/android.yml/badge.svg" alt="构建与测试"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-146C43?style=flat-square" alt="MIT 协议"></a>
</p>

<p>
  <a href="https://github.com/Changjingjiu/SilverPhone/releases/latest"><strong>下载 Android 版</strong></a> ·
  <a href="#如何开始">如何开始</a> ·
  <a href="https://github.com/Changjingjiu/SilverPhone/releases">更新记录</a> ·
  <a href="https://github.com/Changjingjiu/SilverPhone/issues">反馈问题</a>
</p>

</div>

## 功能

- **只有绿色按钮会拨号。** 卡片上的照片和称呼都不响应点击，手搭在卡片上、或者滑动停在卡片上，都不会误拨出去。
- **首页上没有别的东西。** 没有计数、没有菜单、没有通话记录。
- **权限在用到的那一刻才申请**，同意之后这一通立刻拨出去。
- **四档字号**，作用于整个应用，保存前有真实卡片预览。
- **中英文界面**，区号可以设成任何国家。
- **一个文件把亲人搬到第二台手机**：称呼、号码、顺序、照片都在里面，接收方可以先预览再导入。
- **长按亲人卡片可以多选删除**，删之前会告诉你要删掉几位。
- **一台手机里其实是两套界面。** 拨号首页围绕老人来做；所有家属页面就是普通 Android 应用的样子：紧凑的标题栏、一行的列表项、没有占半屏的大按钮。
- **跟着屏幕大小走。** 手机上一个页面一个页面地开；平板和折叠屏上，菜单常驻在左边，选中的页面显示在右边（用的是 Material 3 的 `ListDetailPaneScaffold` 和窗口尺寸档位，不靠机型判断）。
- **每次按下都有回应**：控件会变深、缩小、回弹，并给一次轻微的触感。
- **整体只有一套设计**：六种文字角色、四种圆角、一套配色、一套间距、每个角色一种字重；手势也统一——点一行就是打开，长按才是多选，删除一定先确认，保存永远在同一个角落。
- **没有账号、没有广告、没有统计。** 应用里唯一联网的地方是手动点「检查更新」。

## 如何开始

从 [Releases](https://github.com/Changjingjiu/SilverPhone/releases/latest) 下载 APK，或者自己构建。

**从 v1.0.1 或更早版本升级的，要先卸载应用。** 那些包用的是 Android 调试密钥，Android 不允许
覆盖安装。卸载会清空应用里的亲人，所以请先在**家属设置 → 导入 / 导出**里导出，装好之后再导入
回来。之后的每个版本都用项目自己的发布密钥签名，可以正常互相覆盖升级。

```bash
# 需要 JDK 17 和 Android SDK 36
./gradlew assembleDebug
./gradlew installDebug
```

装好之后：

1. 打开应用，点右上角的**家属设置**。
2. **管理亲人 → 添加亲人**：选一张照片，填上老人看到的称呼和电话号码。每位亲人重复一次。
3. **拨号权限**：想提前打开就进「拨号权限与使用说明」。这一步可以不做的——没权限时按绿色按钮会当场申请。
4. 标准字号不够大，进**字体大小**。
5. 手机不在中国大陆，进**语言与拨打区号**。
6. 从亲人卡片上给自己打一次，确认能拨通；然后把图标固定到桌面顺手的位置。

## 预览

| 亲人首页 | 家属设置 | 添加亲人 |
|:---:|:---:|:---:|
| <img src="design/screenshots/01-home-zh.png" width="220"> | <img src="design/screenshots/17-family-settings-about-en.png" width="220"> | <img src="design/screenshots/04-editor-zh.png" width="220"> |
| 老人看到的全部内容。 | 家属用的：亲人、导入导出、字号、语言、权限。 | 照片、老人看到的称呼、电话号码。 |

| 语言与拨打区号 | 拨号权限 | 关于 |
|:---:|:---:|:---:|
| <img src="design/screenshots/10-language-and-code-zh.png" width="220"> | <img src="design/screenshots/13-calling-help-zh.png" width="220"> | <img src="design/screenshots/14-about-zh.png" width="220"> |
| 实时显示实际会拨出的号码。 | 说明它怎么工作，也可以在这里打开权限。 | 版本、项目地址、检查更新、隐私说明。 |

更多界面（中英文都有）见 [`design/screenshots/`](design/screenshots)。

## 为什么选它

- **不识字也能用。** 一张脸加一个绿色按钮；称呼是用来认出和读出来的。
- **字号调大不会挤坏排版。** 称呼换行而不是被截断，同一行的卡片保持齐平，触控区域不小于 56dp。
- **用系统自带的电话。** 双卡、来电等待、免提、通话记录，都和普通拨号一模一样。
- **同一份名单可以给第二台手机**，两位老人各用一台。
- **没有账号，也没有服务器。** 亲人、照片、设置都在应用自己的私有存储里。
- **体积小**：2.7 MB，支持 Android 6.0（API 23）及以上。

## 参考和范围

| | |
|---|---|
| 系统要求 | Android 6.0+（API 23），不需要 Google 服务，不需要账号 |
| 权限 | `CALL_PHONE`、`READ_CONTACTS`（都在用到的那一刻申请）、`INTERNET`（只用于手动检查更新） |
| 构建 | JDK 17 和 Android SDK 36；`./gradlew assembleDebug` |
| 测试 | `./gradlew testDebugUnitTest`（104 个）· `./gradlew connectedDebugAndroidTest`（57 个） |
| 文档 | [docs/spec](docs/spec) 四份设计文档 · [IMPLEMENTATION-NOTES.md](IMPLEMENTATION-NOTES.md) · [acceptance-results.md](acceptance-results.md) · [build-matrix.md](build-matrix.md) · [docs/RELEASING.md](docs/RELEASING.md) · [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) |

**故意不做的**：账号、同步、服务器、云备份、统计、广告、崩溃上报；上架应用商店；视频通话；消息；多用户档案；通话记录；拨号盘；从锁屏拨号。

**已知限制**：导入预览会把压缩包里的照片读进内存，低内存机型遇到超大压缩包可能失败——提交之前不写入任何数据，失败了重新导入即可。正式包用 SilverPhone 的发布密钥签名，证书 SHA-256 记在 [docs/RELEASING.md](docs/RELEASING.md)，可以用来核对下载到的包。

## 许可

MIT —— 可免费使用、修改和分发。见 [LICENSE](LICENSE)。
