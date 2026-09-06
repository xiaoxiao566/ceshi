# 打印工具

一个安卓打印小工具，支持 PDF、图片、文本和 Word（.docx），能无线打也能 OTG 接 USB 打印机。最低 Android 6.0（API 23），目标 Android 13（API 33）。

## 能干啥

- 文档：PDF、图片（JPG/PNG/GIF/WebP/BMP）、文本（TXT/MD/CSV 等）、Word（.docx）
- 无线打印：用系统自带的打印框架，交给 Mopria / 各品牌插件去打网络打印机
- OTG 打印：USB 直连，自动认 HP/Canon/Epson/Brother/Samsung 这些品牌，激光机走 PCL5；主机型（GDI，如 HP 1020）会自动下固件再走 ZjStream（ZjS + JBIG）直打
- 打印选项：双面、份数、纸张（A4/Letter/Legal）、彩色/黑白

## 环境

- minSdk 23 / targetSdk 33 / compileSdk 33
- Kotlin 1.8、AGP 7.4、Gradle 7.6

## 怎么打

### 无线

点「无线打印」弹系统对话框，装个 Mopria 或对应品牌插件，网络打印机就能打。适合本来就能联网的机型。

> 注意：HP LaserJet 1020 没有网口也没有 Wi-Fi，本身不支持无线，只能走下面的 OTG 直连（除非你接了 USB 打印服务器把它挂到网上）。

### OTG（接 USB）

OTG 线连打印机（手机得支持 USB Host），App 认品牌再决定用哪种数据：

- 普通激光机走 PCL5，黑白点阵逐行送（HP PCL、Brother、Canon 激光、Samsung、Kyocera、Lexmark、Xerox、Ricoh、Dell 这一票基本都行）
- 主机型（GDI，比如 HP 1020）第一次会先联网下固件 `sihp1020.dl` 推进打印机，然后页面用 ZjStream（ZjS 容器 + JBIG 压缩）直打

## 构建

```bash
# Android Studio 直接打开跑，或者：
./gradlew assembleDebug
# 产物在 app/build/outputs/apk/debug/app-debug.apk
```

## 目录

```
app/src/main/java/com/ceshi/printtool/
├── MainActivity.kt              # 主界面
├── document/
│   ├── DocumentSource.kt        # 统一渲染接口
│   ├── FileClassifier.kt        # 认文件类型
│   ├── DocumentSourceFactory.kt # 工厂 + DOCX 抽文字
│   ├── PdfDocumentSource.kt     # PDF
│   ├── ImageDocumentSource.kt   # 图片
│   └── TextDocumentSource.kt    # 文本分页
└── print/
    ├── WirelessPrintHelper.kt   # 无线
    ├── UsbPrinterDetector.kt    # 认 USB 打印机
    ├── PrinterBrands.kt         # 厂商 VID
    ├── Hp1020Firmware.kt        # 固件下载与上传
    ├── PclEncoder.kt            # PCL5 编码
    ├── ZjsEncoder.kt            # ZJS 容器（调用 libzjsencode.so 做 JBIG）
    └── OtgPrintManager.kt       # OTG 主流程
```

> `ZjsEncoder` 依赖 JNI 库 `libzjsencode.so`（放在 `app/src/main/jniLibs/` 下，覆盖 arm64-v8a / armeabi-v7a / x86 / x86_64 四种架构）。JBIG 压缩核心移植自 jbig-kit，ZJS 容器参数对齐 foo2zjs 对 HP 1020 的处理，做了字节级自检（编码后能无损解回原图）。

## 已知限制

- 老式 `.doc` 不支持，另存成 `.docx` 吧
- DOCX 只抽纯文本，复杂排版样式会丢
- PCL5 是单色的，打文档够用，打照片就算了
- OTG 需要手机支持 Host，还得有转接线

## 许可

仅供学习自用。HP 固件和相关商标归惠普所有。

---

> 本项目全部代码与文档均由 AI 辅助制作生成。