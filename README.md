# 智码通 SmsCodeX

基于开源项目 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode)（GPL-3.0）复活并深度修复增强的 LSPosed 模块：**短信验证码自动复制、自动填充、记录与企微转发**。

> 原项目已长期未维护，在新系统（Android 14/15、ColorOS 等）上自动填充失效。本仓库以"AI 辅助复活"的方式完成了新系统适配，并在 OnePlus PGZ110 (ColorOS A15) 上实测通过。

## 功能

- 🔢 验证码短信拦截、解析与**自动复制**
- ⌨️ 验证码**自动填充**到当前输入框（免 ROOT 权限补授，见下文修复）
- 🔔 验证码通知、历史记录（首页"记录"tab）
- ✈️ **转发到企业微信**（自建应用通道，字段对齐 [xinyi-relay](https://gitlab.com/magisk3171/xinyi-relay) 的 WeworkAgent）
- 🚫 屏蔽名单、短信去重/拦截、标记已读
- 🎨 底栏双 tab（记录 | 设置）、卡片式折叠分类、深浅主题自适应

## 相对上游的关键修复

| 问题 | 修复方案 |
|---|---|
| Android 14+ 权限补授 hook 找不到类（嵌套类 `.` 应为 `$`） | 运行时按方法形状匹配 `restorePermissionState` |
| ColorOS A15 重构权限服务，`checkInjectEventsPermission` 消失、权限查询走 AccessCheckDelegate，注入始终被拒 | **身份切换**：hook `InputManagerService.injectInputEvent*` 入口，phone 进程调用时 `Binder.clearCallingIdentity()`，内联检查读到 system uid 放行，调用后恢复；仅影响 phone uid |
| `XposedHelpers.callMethod(obj,name,Class[],Object[])` shim 缺失重载导致注入无声失败 | InputHelper 纯 JDK 反射重写，双通道 + 返回值检查 |
| XSharedPreferences 在部分 ROM 读取不可靠 | 设置读取链路加固 + 转发配置由应用进程侧读取 |
| 日志难排障 | XLog 双写 logcat + XposedBridge.log（持久化到 LSPosed modules.log）；详细日志开关在 设置→试验性功能 |

## 构建

- **电脑 / Linux 沙箱**：`./gradlew assembleRelease`（JDK 17；aapt2 按平台配置 `android.aapt2FromMavenOverride`）
- **手机 Termux**：参见 [README-ONDEVICE.md](README-ONDEVICE.md)（一键 `build-termux.sh`）

签名：`ci.keystore` 不入库（见 `.gitignore`），自行生成或改用 debug 签名。

## 使用

1. LSPosed 中启用模块，作用域勾选 **系统框架 (android)** 与 **电话 (com.android.phone)**，重启手机
2. 企微转发：企微管理后台创建自建应用，填入 企业ID / AgentId / Secret，**并在应用详情页配置"企业可信IP"**（否则 errcode 60020）
3. 日志排障：LSPosed 管理器 → 日志 → 模块日志（含开机 hook 安装与短信处理全链路）

## 许可证

[GPL-3.0](LICENSE)（继承上游许可证）。基于 [tianma8023/XposedSmsCode](https://github.com/tianma8023/XposedSmsCode) 修改，致敬原作者与所有贡献者。
