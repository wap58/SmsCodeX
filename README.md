# 智码通 SmsCodeX

基于开源项目 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode)（GPL-3.0）复活并深度重构的 LSPosed 模块：**短信验证码自动复制、自动填充、记录，以及短信转发到多种通知通道**。

> 原项目已长期未维护，在新系统（Android 14/15、ColorOS 等）上自动填充失效、且易被环境检测工具识别。本仓库以"AI 辅助重构"的方式完成新系统适配，并在 OnePlus PGZ110 (ColorOS A15) 上实测通过。

## 功能

- 🔢 验证码短信拦截、解析与**自动复制**
- ⌨️ 验证码**自动填充**到当前输入框（免 ROOT 权限补授，见下文修复）
- 🔔 验证码通知、历史记录（首页"记录"tab）
- ✈️ **短信转发**，支持 6 种通道：企业微信应用 / 企业微信机器人 / 钉钉机器人 / 飞书机器人 / 息知 / 推送加
  - **转发范围可选**：仅验证码短信（默认）或全部短信
  - 息屏可达（电话进程直发 HTTP，不依赖 App 进程存活）
  - 记录页支持**手动转发**（兼作转发功能测试入口）
- 🚫 屏蔽名单、短信去重/拦截、标记已读
- 🎨 底栏双 tab（记录 | 设置）、卡片式折叠分类、深浅主题自适应

## 相对上游的关键修复

| 问题 | 修复方案 |
|---|---|
| Android 14+ 权限补授 hook 找不到类（嵌套类 `.` 应为 `$`） | 运行时按方法形状匹配 `restorePermissionState` |
| ColorOS A15 重构权限服务，`checkInjectEventsPermission` 消失、权限查询走 AccessCheckDelegate，注入始终被拒 | **身份切换**：hook `InputManagerService.injectInputEvent*` 入口，phone 进程调用时 `Binder.clearCallingIdentity()`，内联检查读到 system uid 放行，调用后恢复；仅影响 phone uid |
| `XposedHelpers.callMethod(obj,name,Class[],Object[])` shim 缺失重载导致注入无声失败 | InputHelper 纯 JDK 反射重写，双通道 + 返回值检查 |
| 模块（电话进程）读不到用户配置，所有开关退回默认值 | **应用主动导出配置**到世界可读文件 + 模块侧多通道读取（详见 `FIX-NOTES.md`）|
| 息屏时 App 进程被冻结，转发失败 | **电话进程直发 HTTP**（`DirectForwarder`），不依赖 App 进程 |
| 模块激活状态无法判定 | **libxposed 官方 service 机制**（见 `FIX-NOTES.md`）|
| 日志难排障 | XLog 双写 logcat + XposedBridge.log（持久化到 LSPosed modules.log）；详细日志开关在 设置→试验性功能 |

## 构建

### 方式一：GitHub Actions（推荐）

推送 `main` 分支后自动构建（约 2-3 分钟），产物在 Actions 的 Artifacts 中。

- 版本号格式：`<VERSION_NAME> (<CI run_number>)`，如 `3.0.6 (71)`
- `run_number` 由 GitHub 自动递增，同时用作 `versionCode`
- 需要仓库 Secrets：`CI_KEYSTORE_B64`、`KEYSTORE_PASSWORD`、`KEYSTORE_ALIAS`

### 方式二：本地构建

```sh
./gradlew assembleRelease      # JDK 17
```

- Android SDK：需 `compileSdk 34`
- `local.properties` 写 `sdk.dir=<你的 SDK 路径>`
- 签名：`ci.keystore` 不入库（见 `.gitignore`），需自备（见下方"重建清单"）
- 部分环境需配置 `android.aapt2FromMavenOverride` 指向平台可用的 aapt2

### 方式三：手机 Termux

参见 [README-ONDEVICE.md](README-ONDEVICE.md)（一键 `build-termux.sh`）

---

## 🔧 重建清单（仓库内容说明）

> 若本地代码丢失，可直接 `git clone` 本仓库恢复。以下说明**仓库里有什么、缺什么**。

### ✅ 仓库已包含（克隆即得）

| 类别 | 内容 |
|---|---|
| 全部源码 | `app/src/main/java/`（168 个 Java 文件）、`app/src/main/res/` |
| 构建脚本 | `build.gradle`、`app/build.gradle`、`settings.gradle`、`gradlew` + wrapper |
| **第三方依赖 jar** | `app/libs/libxposed-service-102.0.0.jar`（libxposed service，30 个类）<br>`stub-libs/libxposed-stub.jar`（libxposed API stub） |
| CI 配置 | `.github/workflows/build.yml` |
| 技术文档 | `FIX-NOTES.md`（机制说明 + 全部踩坑记录）|
| 其他 | `LICENSE`、`README.md`、`README-ONDEVICE.md`、`build-termux.sh`、`mirror-china.init.gradle` |

### ❌ 仓库不包含（需自备，克隆后必须补上）

| 文件 | 用途 | 如何恢复 |
|---|---|---|
| **`ci.keystore`** | **签名密钥（最关键）** | 用户已单独备份。**丢失后果：无法覆盖安装，必须卸载重装 → 配置与授权全丢** |
| `local.properties` | 指向本机 Android SDK | 内容一行：`sdk.dir=/path/to/android-sdk`，自建即可 |

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
# 或直接 git push 触发 GitHub Actions

# 5. 安装（覆盖安装保留数据；需签名一致）
cat app/build/outputs/apk/release/*.apk | pm install -r -S <字节数>
```

### 依赖 jar 的来源（如需重新获取）

| jar | 来源 | 注意 |
|---|---|---|
| `libxposed-service-102.0.0.jar` | `io.github.libxposed:service:102.0.0` **+** `io.github.libxposed:interface:102.0.0` | **必须合并两个 artifact**（后者含 AIDL 生成的 `IXposedService$Stub` 等），否则运行时报 `NoClassDefFoundError` |
| `libxposed-stub.jar` | libxposed API stub | 编译期用 |

详见 `FIX-NOTES.md` 的「激活状态检测」章节。

---

## 使用

1. **LSPosed 中启用模块**，作用域勾选 **系统框架（system）** 与 **电话服务（com.android.phone）**，重启手机
   > ⚠️ 系统框架的包标识是 `system`（界面上显示"系统框架"），不是 `android`。
   > 模块**不需要**勾选自身——现代 API 下框架不会注入模块自身进程。

2. **配置转发**：设置 → 短信转发 → 选通道 → 填「通道参数」
   - 企微自建应用需在后台配置「**企业可信 IP**」，否则报 errcode 60020
   - 各通道详细配置步骤见 App 内 **设置 → 关于 → 常见问题**

3. **日志排障**：LSPosed 管理器 → 日志 → 模块日志（含开机 hook 安装与短信处理全链路）

### 系统要求

- Android 8.1+（minSdk 23，实际使用环境需 26+）
- LSPosed（libxposed API 100+，实测 API 102 / LSPosed 2.2.0）
- **不需要 root**（自动输入的第三级兜底会尝试 `su`，正常情况用不到）

---

## 🛠 开发工具

本项目的**全部开发工作**（代码编写、编译调试、真机部署、日志分析、版本发布）都是在一台手机上完成的，**全程未使用电脑**。

### ⭐ 重点推荐：Minis

> **在手机上就把智码通做出来了 —— 全流程无需电脑。**

[**Minis**](https://github.com/OpenMinis/OpenMinis) 是一款跨平台 AI Agent 应用，它把**完整的 Linux 环境**搬进了手机：

- 🐧 **真实的 Linux 沙箱** —— 设备本地运行 Alpine Linux，可 `apk add` 装包、跑脚本、操作真实文件
- 🤖 **自带模型** —— 支持 Claude / GPT / Gemini 等，用自己的 API Key 即可
- 📱 **深度系统集成** —— 日历、联系人、剪贴板、通知、定位、TTS 等都以工具形式开放给 Agent
- 🌐 **浏览器自动化** —— Agent 可代你浏览并操作网页
- 🧩 **Skills & 记忆** —— 可扩展技能 + 跨会话持久记忆
- 🔓 **完全免费、开源**（GPL-3.0）

**在本项目中 Minis 承担了什么：**

| 环节 | 具体工作 |
|---|---|
| 代码开发 | 全部 Java / XML 编写与重构 |
| 编译构建 | 通过 GitHub Actions 触发、拉取产物（本地 aapt2 架构不匹配，故走 CI）|
| 真机部署 | 经 Shizuku 以 stdin 管道静默安装 APK |
| 调试排障 | 读 LSPosed 日志、分析 SELinux 标签、导出并解析 LSPosed 配置库 |
| 版本发布 | 创建 GitHub Release、上传 APK、校验 MD5 |
| 文档维护 | 本 README 与 `FIX-NOTES.md` 的撰写 |

> 项目主页：[**openminis.app**](https://openminis.app) ｜ 仓库：[**OpenMinis/OpenMinis**](https://github.com/OpenMinis/OpenMinis)
> 相关资源：[AwesomeMinis](https://github.com/OpenMinis/AwesomeMinis)（用例合集）、[MinisSkills](https://github.com/OpenMinis/MinisSkills)（技能库）

### 其他工具

- **[GitHub Actions](https://github.com/features/actions)** —— 云端构建 APK（本地沙箱 aapt2 为 x86_64、设备为 aarch64，无法本地编译）
- **[Shizuku](https://github.com/RikkaApps/Shizuku)**（MIT）—— 免 root 调用系统 API，用于静默安装 APK、查询包信息、执行特权命令
- **[LSPosed](https://github.com/LSPosed/LSPosed)**（GPL-3.0）—— 模块运行框架，本项目的作用域调试与日志分析均依赖其模块日志

---

## 许可证

[GPL-3.0](LICENSE)（继承上游许可证）。基于 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) 修改，致敬原作者与所有贡献者。
