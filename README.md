# 打印工具（Print Tool）

一款 Android 打印工具，支持常见文档格式，可通过**无线（Wi‑Fi/网络）**或 **USB OTG** 打印。最低支持 Android 6.0（API 23），目标系统 Android 13（API 33）。

## 功能特性

- **文档格式**：PDF、图片（JPG/PNG/GIF/WebP/BMP）、纯文本（TXT/MD/CSV 等）、Word（.docx）
- **无线打印**：基于系统打印框架（Print Framework），可交给任意已安装的打印服务（Mopria、IPP、各品牌打印插件）打印到网络打印机
- **OTG 打印**：USB Host 直连打印机，自动识别常见品牌（HP/Canon/Epson/Brother/Samsung/Kyocera/Lexmark/Xerox/Ricoh/Dell 等），激光机型走 PCL5；主机型（GDI）自动上传固件
- **打印选项**：双面打印、份数（1–99）、纸张大小（A4/Letter/Legal）、彩色/黑白
- **支持文档预览与分页**、跨页渲染、A4 排版

## 系统要求

| 项目 | 值 |
| --- | --- |
| minSdk | 23（Android 6.0） |
| targetSdk | 33（Android 13） |
| compileSdk | 33 |
| 语言 | Kotlin 1.8 / AGP 7.4 / Gradle 7.6 |

## 支持的打印方式

### 1. 无线打印（推荐）

点击「无线打印」后调用系统打印对话框，可通过：

- **HP Print Service Plugin** / **Mopria Print Service** 打印到 HP 网络打印机（推荐用于 HP 打印机）
- 任意支持 IPP / AirPrint 的网络打印机
- Android 自带的 PDF 打印服务

### 2. USB/OTG 打印

通过 OTG 线连接打印机，App 使用 USB Host 直连，自动按品牌/设备归类：

- **常见 PCL5 激光打印机**：直接按 PCL5 单色栅格逐页发送（HP PCL 机型 / Brother / Canon 激光 / Samsung / Kyocera / Lexmark / Xerox / Ricoh / Dell 等绝大多数激光机型）
- **主机型（GDI）打印机**（如 HP 1020 等）：自动下载并上传固件 `sihp1020.dl`（从内置镜像源获取，见 `Hp1020Firmware.kt`）；固件上传后仍需 ZjStream（ZJS）栅格编码（尚未接入），此类机型建议改用无线打印

### 品牌支持与打印方式对照

| 品牌（vendor ID） | 无线（IPP/系统框架） | USB 直连（OTG） |
| --- | --- | --- |
| HP / 惠普 | ✅ 全机型 | ✅ PCL 激光机型；主机型固件上传+待接入 ZJS |
| Canon / 佳能 | ✅ 全机型 | ✅ 支持 PCL 的激光机型；喷墨/UFR 机型建议无线 |
| Epson / 爱普生 | ✅ 全机型 | 激光机型可尝试 PCL；喷墨建议无线 |
| Brother / 兄弟 | ✅ 全机型 | ✅ PCL 激光机型 |
| Samsung / 三星 | ✅ 全机型 | ✅ 支持 PCL 的激光机型 |
| Kyocera / 京瓷 | ✅ 全机型 | ✅ PCL 激光机型 |
| Lexmark / 利盟 | ✅ 全机型 | ✅ PCL 激光机型 |
| Xerox / 施乐 | ✅ 全机型 | ✅ PCL 激光机型 |
| Ricoh / 理光 | ✅ 全机型 | ✅ PCL 激光机型 |
| Dell / 戴尔 | ✅ 全机型 | ✅ PCL 激光机型 |

> 彩色喷墨、主机型（GDI）等专有协议机型，通过**无线打印**（系统打印框架 + 各品牌插件 / Mopria）即可覆盖全部常见品牌。

## 构建方法

```bash
# 方式一：Android Studio 打开本目录，直接 Run

# 方式二：命令行（需要 JDK 11、Android SDK platform 33 / build-tools 33）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 项目结构

```
app/src/main/java/com/ceshi/printtool/
├── MainActivity.kt              # 主界面（文件选择 + 打印入口）
├── document/
│   ├── DocumentSource.kt        # 统一文档渲染接口
│   ├── FileClassifier.kt        # 文件类型识别
│   ├── DocumentSourceFactory.kt # 文档源工厂 + DOCX 文本抽取
│   ├── PdfDocumentSource.kt     # PDF 渲染
│   ├── ImageDocumentSource.kt   # 图片渲染
│   └── TextDocumentSource.kt    # 文本分页渲染
└── print/
    ├── WirelessPrintHelper.kt   # 无线打印（Print Framework）
    ├── UsbPrinterDetector.kt    # USB 打印机识别与协议归类
    ├── PrinterBrands.kt         # 常见品牌 vendor ID 与名称
    ├── Hp1020Firmware.kt        # HP 1020 固件下载与上传
    ├── PclEncoder.kt            # PCL5 单色栅格编码
    ├── ZjsEncoder.kt            # ZjStream 编码（占位）
    └── OtgPrintManager.kt       # OTG 打印主流程
```

## 说明与限制

- 旧版二进制 `.doc`（OLE）格式暂不支持，请另存为 `.docx`。
- DOCX 采用纯文本抽取（保留段落结构），不渲染复杂排版样式。
- USB 打印目标设备需支持 OTG Host；手机需 OTG 转接线。
- PCL5 栅格为单色，适合文档打印。

## 许可

本项目仅供学习与个人使用。HP 固件与相关商标归 Hewlett-Packard 所有。

---

> 本项目全部代码与文档均由 AI（人工智能）辅助制作生成。