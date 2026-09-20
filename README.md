<div align="center">

# 智码通 SmsCodeX

**短信验证码自动复制 · 自动填充 · 记录 · 多通道转发**

[![License](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%208.1%2B-green.svg)](#系统要求)
[![Framework](https://img.shields.io/badge/Framework-LSPosed%20API%20100%2B-orange.svg)](#系统要求)
[![Release](https://img.shields.io/github/v/release/wap58/SmsCodeX?label=Release&color=brightgreen)](https://github.com/wap58/SmsCodeX/releases/latest)

[下载最新版](https://github.com/wap58/SmsCodeX/releases/latest) · [常见问题](#常见问题) · [技术笔记](FIX-NOTES.md)

</div>

---

基于 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode)（GPL-3.0）深度重构的 LSPosed 模块。

> 原项目已长期未维护，在 Android 14/15、ColorOS 等新系统上自动填充失效，且易被环境检测工具识别。本项目以 **AI 辅助重构** 的方式完成新系统适配，已在 OnePlus PGZ110（ColorOS A15）上实测通过。

## 目录

- [功能特性](#功能特性)
- [快速开始](#快速开始)
- [转发通道配置](#转发通道配置)
- [相对上游的关键修复](#相对上游的关键修复)
- [构建](#构建)
- [开发工具](#-开发工具)
- [重建清单](#-重建清单)
- [常见问题](#常见问题)
- [系统要求](#系统要求)
- [许可证](#许可证)

---

## 功能特性

| 功能 | 说明 |
|---|---|
| 🔢 **自动复制** | 拦截并解析验证码短信，自动复制到剪贴板 |
| ⌨️ **自动填充** | 验证码自动填入当前输入框，无需 root |
| 🔔 **通知与记录** | 验证码通知（可设保留时长）、历史记录查询 |
| ✈️ **多通道转发** | 支持 6 种通道，可转发验证码或全部短信 |
| 🚫 **短信管理** | 屏蔽名单、去重、标记已读、自动删除验证码短信 |
| 🎨 **主题** | 底栏双 tab、卡片式折叠分类、深浅主题自适应 |

### 转发通道

| 通道 | 说明 |
|---|---|
| 企业微信应用 | 自建应用推送，字段对齐 [xinyi-relay](https://gitlab.com/magisk3171/xinyi-relay) |
| 企业微信机器人 | 群机器人 Webhook |
| 钉钉机器人 | 支持加签校验 |
| 飞书机器人 | 群机器人 Webhook |
| 息知 | [xz.qqoq.net](https://xz.qqoq.net) 微信通知 |
| 推送加 | [pushplus.plus](https://pushplus.plus) 微信通知 |

**转发范围**可选：`仅验证码短信`（默认）或 `全部短信`。

> 💡 **息屏可达**：转发由电话进程直发 HTTP，不依赖 App 进程存活，息屏状态下同样工作。

## 快速开始

1. **安装**：从 [Releases](https://github.com/wap58/SmsCodeX/releases/latest) 下载 APK 并安装
2. **启用模块**：LSPosed 中启用本模块，作用域勾选：
   - **系统框架**（包标识 `system`）
   - **电话服务**（`com.android.phone`）
3. **重启手机**
4. **配置**：打开 App → 设置 → 短信转发 → 选择通道 → 填写「通道参数」

> ⚠️ **两个易错点**
>
> - 系统框架的包标识是 `system`（界面上显示"系统框架"），**不是** `android`
> - 模块**不需要**勾选自身 —— 现代 API 下框架不会注入模块自身进程

## 转发通道配置

各通道的详细配置步骤（含后台获取路径、填写位置）见 **App 内 → 设置 → 关于 → 常见问题**。

> ⚠️ 企业微信自建应用需在管理后台配置「**企业可信 IP**」，否则报 `errcode 60020`。

---

## 相对上游的关键修复

| 问题 | 修复方案 |
|---|---|
| Android 14+ 权限补授 hook 找不到类（嵌套类 `.` 应为 `$`） | 运行时按方法形状匹配 `restorePermissionState` |
| ColorOS A15 重构权限服务，`checkInjectEventsPermission` 消失、权限查询走 AccessCheckDelegate，注入始终被拒 | **身份切换**：hook `InputManagerService.injectInputEvent*` 入口，phone 进程调用时 `Binder.clearCallingIdentity()`，内联检查读到 system uid 放行，调用后恢复；仅影响 phone uid |
| `XposedHelpers.callMethod(obj,name,Class[],Object[])` shim 缺失重载导致注入无声失败 | InputHelper 纯 JDK 反射重写，双通道 + 返回值检查 |
| 模块（电话进程）读不到用户配置，所有开关退回默认值 | **应用主动导出配置**到世界可读文件 + 模块侧多通道读取 |
| 息屏时 App 进程被冻结，转发失败 | **电话进程直发 HTTP**（`DirectForwarder`），不依赖 App 进程 |
| 模块激活状态无法判定 | **libxposed 官方 service 机制** |
| 日志难排障 | XLog 双写 logcat + `XposedBridge.log`（持久化到 LSPosed modules.log） |

> 以上修复的**完整根因分析与踩坑记录**见 [FIX-NOTES.md](FIX-NOTES.md)。

## 构建

### 方式一：GitHub Actions（推荐）

推送 `main` 分支后自动构建（约 2-3 分钟），产物在 Actions 的 Artifacts 中。

- 版本号格式：`<VERSION_NAME> (<CI run_number>)`，如 `3.0.6 (71)`
- `run_number` 由 GitHub 自动递增，同时用作 `versionCode`
- 需要仓库 Secrets：`CI_KEYSTORE_B64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`

### 方式二：本地构建

```sh
./gradlew assembleRelease      # 需要 JDK 17
```

- Android SDK：需 `compileSdk 34`，并在 `local.properties` 写 `sdk.dir=<SDK 路径>`
- 签名：`ci.keystore` 不入库（见 `.gitignore`），需自备（见[重建清单](#-重建清单)）
- 部分环境需配置 `android.aapt2FromMavenOverride` 指向平台可用的 aapt2

### 方式三：手机 Termux

参见 [README-ONDEVICE.md](README-ONDEVICE.md)（一键 `build-termux.sh`）。

---

## 🛠 开发工具

> **本项目的全部开发工作 —— 代码编写、编译调试、真机部署、日志分析、版本发布 —— 都是在一台手机上完成的，全程未使用电脑。**

### ⭐ 重点推荐：Minis

<div align="center">

**在手机上就把智码通做出来了 —— 全流程无需电脑**

[项目主页](https://openminis.app) · [GitHub 仓库](https://github.com/OpenMinis/OpenMinis)

</div>

[**Minis**](https://github.com/OpenMinis/OpenMinis) 是一款跨平台 AI Agent 应用，它把**完整的 Linux 环境**搬进了手机：

| 能力 | 说明 |
|---|---|
| 🐧 **真实 Linux 沙箱** | 设备本地运行 Alpine Linux，可 `apk add` 装包、跑脚本、操作真实文件 |
| 🤖 **自带模型** | 支持 Claude / GPT / Gemini 等，用自己的 API Key 即可 |
| 📱 **深度系统集成** | 日历、联系人、剪贴板、通知、定位、TTS 等以工具形式开放给 Agent |
| 🌐 **浏览器自动化** | Agent 可代你浏览并操作网页 |
| 🧩 **Skills 与记忆** | 可扩展技能 + 跨会话持久记忆 |
| 🔓 **完全免费开源** | GPL-3.0 |

**在本项目中 Minis 承担的工作：**

| 环节 | 具体工作 |
|---|---|
| 代码开发 | 全部 Java / XML 的编写与重构 |
| 编译构建 | 触发 GitHub Actions 并拉取产物 |
| 真机部署 | 经 Shizuku 以 stdin 管道静默安装 APK |
| 调试排障 | 分析 LSPosed 日志、SELinux 标签，导出并解析 LSPosed 配置库 |
| 版本发布 | 创建 GitHub Release、上传 APK、校验 MD5 |
| 文档维护 | 本 README 与 [FIX-NOTES.md](FIX-NOTES.md) 的撰写 |

> 📦 相关资源：[AwesomeMinis](https://github.com/OpenMinis/AwesomeMinis)（用例合集） · [MinisSkills](https://github.com/OpenMinis/MinisSkills)（技能库）

### 其他工具

| 工具 | 用途 |
|---|---|
| [GitHub Actions](https://github.com/features/actions) | 云端构建 APK（本地沙箱 aapt2 为 x86_64、设备为 aarch64，无法本地编译）|
| [Shizuku](https://github.com/RikkaApps/Shizuku)（MIT）| 免 root 调用系统 API，用于静默安装、查询包信息、执行特权命令 |
| [LSPosed](https://github.com/LSPosed/LSPosed)（GPL-3.0）| 模块运行框架，作用域调试与日志分析依赖其模块日志 |

---

## 🔧 重建清单

> 若本地代码丢失，可直接 `git clone` 本仓库恢复。以下说明**仓库里有什么、缺什么**。

### ✅ 仓库已包含（克隆即得）

| 类别 | 内容 |
|---|---|
| 全部源码 | `app/src/main/java/`（168 个 Java 文件）、`app/src/main/res/` |
| 构建脚本 | `build.gradle`、`app/build.gradle`、`settings.gradle`、`gradlew` + wrapper |
| **第三方依赖 jar** | `app/libs/libxposed-service-102.0.0.jar`（30 个类）<br>`stub-libs/libxposed-stub.jar`（libxposed API stub） |
| CI 配置 | `.github/workflows/build.yml` |
| 技术文档 | [FIX-NOTES.md](FIX-NOTES.md)（机制说明 + 全部踩坑记录）|

### ❌ 仓库不包含（需自备）

| 文件 | 用途 | 恢复方式 |
|---|---|---|
| **`ci.keystore`** | **签名密钥（最关键）** | 从个人备份恢复。<br>⚠️ **丢失后果：无法覆盖安装，必须卸载重装 → 配置与授权全丢** |
| `local.properties` | 指向本机 Android SDK | 一行内容：`sdk.dir=/path/to/android-sdk` |

### 克隆后重建步骤

```sh
# 1. 克隆
git clone https://github.com/wap58/SmsCodeX.git
cd SmsCodeX

# 2. 补 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 3. 补签名密钥（从备份恢复）
cp /path/to/backup/ci.keystore .

# 4. 构建（二选一）
./gradlew assembleRelease        # 本地构建
# 或 git push 触发 GitHub Actions

# 5. 安装（覆盖安装保留数据；需签名一致）
cat app/build/outputs/apk/release/*.apk | pm install -r -S <字节数>
```

### 依赖 jar 的来源

| jar | 来源 | 注意 |
|---|---|---|
| `libxposed-service-102.0.0.jar` | `io.github.libxposed:service:102.0.0` **+** `io.github.libxposed:interface:102.0.0` | **必须合并两个 artifact**（后者含 AIDL 生成的 `IXposedService$Stub` 等），否则运行时报 `NoClassDefFoundError` |
| `libxposed-stub.jar` | libxposed API stub | 编译期使用 |

> 详见 [FIX-NOTES.md](FIX-NOTES.md) 的「激活状态检测」章节。

## 常见问题

**Q：需要 root 吗？**

不需要。短信转发、验证码拦截、通知、自动输入等功能均基于 LSPosed 实现，不依赖 root。
仅「自动输入」的第三级兜底会尝试 `su` 命令（前两级：按键注入、Ctrl+V 粘贴），正常情况下用不到。

**Q：修改配置后需要重启手机吗？**

不需要。改通道 / 参数 / 开关均**即时生效**。
仅当改动涉及模块作用域（`scope.list`）或模块侧 hook 代码时才需重启。

**Q：转发失败怎么办？**

1. 检查是否已配置「企业可信 IP」（企微自建应用）
2. 查看 LSPosed 模块日志：LSPosed 管理器 → 日志 → 模块日志
3. 记录页可**手动转发**，用于快速验证通道配置是否正确

**Q：短信被删除了？**

「自动删除验证码短信」为可选功能，可在 设置 → 验证码 中关闭。
若转发范围为「全部短信」，普通短信**只会被转发，不会被删除**。

**Q：更多问题？**

见 App 内 **设置 → 关于 → 常见问题**（含 6 种通道的详细配置步骤）。

## 系统要求

| 项 | 要求 |
|---|---|
| 系统 | Android 8.1+（minSdk 23；实际使用环境需 26+）|
| 框架 | LSPosed，libxposed API 100+（实测 API 102 / LSPosed 2.2.0）|
| Root | **不需要** |

## 许可证

[GPL-3.0](LICENSE) —— 继承上游许可证。

本项目基于 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) 修改，致敬原作者与所有贡献者。
