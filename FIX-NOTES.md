# 智码通修复笔记（2026-09-20 更新）

> 本文件是仓库内的技术笔记，与 `/var/minis/memory/smscodx-PROJECT-MEMORY.md`
> （跨会话项目记忆）内容互补：此处记录**机制与踩坑细节**，项目记忆记录**流程与偏好**。

## ★ 配置读取机制（最重要，勿改坏）

### 为什么不能用 LSPosed 官方机制
LSPosed 的 `xposedsharedprefs` + `MODE_WORLD_READABLE` 机制**只在"模块自身进程被注入"时生效**
（源码 `ConfigManager.getPrefsPath`：仅当 `module.appId == uid % PER_USER_RANGE` 才创建目录并 chown）。
**现代 API 下框架不注入模块自身进程**（官方文档："module apps are no longer hooked
by themselves"；LSPosed 源码 `ScopeAdapter.refresh()` 更直接排除
`packageName.equals(module.packageName)`，即模块自身既不能勾选也不会被注入）
→ 该机制永远无效。
另：电话进程(radio 域)受 SELinux 隔离，读不了 `/data/data/<pkg>/shared_prefs/`（app_data_file）。

### 实际方案：应用主动导出 → 模块读导出文件
```
应用侧 (PrefsExporter.java)
  写 /sdcard/Android/data/<pkg>/files/prefs_export.xml   ← media_rw_data_file，phone 进程可读
  setFileWorldWritable(dir, 2) + setFileWorldWritable(file, 0)
  触发点：SmsCodeApplication.onCreate() / SettingsFragment.onPause()

模块侧 (ModulePrefs.java)
  读取优先级：导出文件 → /data/misc/apexdata/*/prefs/<pkg>/ → /data/data/<pkg>/shared_prefs/
  带 sCache + lastModified 校验
  XSPUtils 内部转调 ModulePrefs（对外方法签名不变，调用点无需改动）
```

### ⚠️ XML 解析的关键坑
Android SharedPreferences XML 有两种节点形态：
```xml
<boolean name="x" value="true" />      <!-- 值在 value 属性（自闭合） -->
<string  name="y">文本</string>         <!-- 值在文本节点 -->
```
**必须**先取 `getAttributeValue(null, "value")`，为空且类型是 string 时才用 `nextText()`。
（曾因统一用 `nextText()` 导致所有 boolean 解析为 false → 总开关 `pref_enable=false` → 模块直接退出、全程无反应）

## 息屏转发机制

息屏时 ColorOS 冻结 app 进程（`do_freezer_trap`），Provider 不可达 → 转发失败。
方案：**电话进程直发 HTTP**（`xp/hook/forward/DirectForwarder.java`），不依赖 app 进程。
`CodeWorker` 顺序：① DirectForwarder 直发 → ② 失败才回退 Provider。
DirectForwarder 的配置读取必须走 `ModulePrefs`（不能用框架 XSharedPreferences）。

## ★ 激活状态检测（2026-09-20 定案，勿改坏）

### 结论：用 libxposed 官方 service 机制

```java
// app 侧（SmsCodeApplication.onCreate）
XposedServiceHelper.registerListener(listener)
  → onServiceBind(XposedService service)   // service 非空 = 已激活
```

框架在 app 启动时**主动下发 service**（经 `XposedProvider` 的 `SendBinder` call），
不依赖模块自身注入，不受 SELinux/权限限制。

### 为什么其他方案全部不可行（均已实测）

| 方案 | 失败原因 |
|---|---|
| 读 LSPosed 勾选状态 | `/data/adb` 权限 0700，应用进程读不到 |
| 模块进程写 /sdcard、/data/local/tmp | `Permission denied`（radio/system 均无权限）|
| 模块进程写 app 数据目录 | MCS 分类拦截（`app_data_file:s0:c165,...`）|
| `XposedInterface.getRemotePreferences()` 写 | `UnsupportedOperationException: Read only implementation` |
| 开机时调 app 的 Provider | `Unknown authority`（app 进程尚未启动）|
| **self-hook 写标记文件** | **框架根本不注入模块自身**（见下）|

### ★ 关键事实：模块自身不会被注入

两处权威依据：
1. 官方文档《Develop Xposed Modules Using Modern Xposed API》：
   > *As a result, module apps are no longer hooked by themselves.*
2. LSPosed 源码 `ScopeAdapter.refresh()` 显式排除：
   ```java
   if (packageName.equals("system") && userId != 0 ||
           packageName.equals(module.packageName) ||        // ← 模块自身
           packageName.equals(BuildConfig.APPLICATION_ID)) {
       return;   // 不出现在作用域列表、无法勾选、不会被注入
   }
   ```

**推论**：Xposed 作者 rovo89 在 issue #64 推荐的 "hook your own app" best practice
**在现代 API 下已不适用**；`scope.list` 里声明模块自身包名也没有意义。

### ⚠️ 依赖打包的坑（必读）

`io.github.libxposed:service` 的 Maven 版本**要求 compileSdk 37**（本项目 34），
AAR 元数据检查会失败。故改用**本地 jar**，且必须**合并两个 artifact**：

```
io.github.libxposed:service:102.0.0    → classes.jar（15 个类，业务类）
io.github.libxposed:interface:102.0.0  → classes.jar（15 个类，AIDL 生成的接口）
合并后 → app/libs/libxposed-service-102.0.0.jar（30 个类）
```

**只放 service 的 jar 会运行时报**：
```
NoClassDefFoundError: io.github.libxposed.service.IXposedService$Stub
```
（现象：`XposedProvider: binder received` 成功，但 `onServiceBind` 不触发）

另需在 `AndroidManifest.xml` **手动声明** Provider（本地 jar 不会自动合并 AAR 清单）：
```xml
<provider
    android:name="io.github.libxposed.service.XposedProvider"
    android:authorities="${applicationId}.XposedService"
    android:exported="true"
    tools:ignore="ExportedContentProvider" />
```

### 可用的框架信息

绑定成功后可从 `XposedService` 取：
- `getFrameworkName()` / `getFrameworkVersion()` — 如 "LSPosed" / "2.2.0-it"
- `getApiVersion()` — 如 102
- `getScope()` — **当前作用域**（实测返回 `[com.android.phone, system]`）
- `getRunningTargets()` — 正在被 hook 的进程（含 pid、state）
- `getRemotePreferences(name)` — **可读写**（与 `XposedInterface` 的只读实现不同）

## ★ 配置读取的实时性（2026-09-20 实测澄清）

**改通道 / 参数 / 开关不需要重启，即时生效。**

`ModulePrefs.loadViaFile()` 用 `lastModified` 校验文件是否变化，
而 `DirectForwarder.forward()` 每次都重新调 `ModulePrefs.getString()`，
故文件一改，下次转发即读到新值。

### ⚠️ Config diag 日志有误导性（排查时注意）

```
Config diag: ... channel=xizhi ...   ← 第一次 load 的值（可能是缓存）
（毫秒之后）
实际转发时再 load → pushplus        ← 真正使用的值
```

实测：用户切到推送加后发短信，日志显示 `xizhi` 但**实际收到推送加**。
**排查时不要只看 Config diag 的 channel 字段**，要结合"实际收到哪个通道"。

### 手动转发 vs 自动转发的配置来源（不同路径！）

| | 执行进程 | 配置来源 | 用途 |
|---|---|---|---|
| 手动转发（记录页）| **app 进程** | app 的 SharedPreferences | 可验证通道参数是否正确 |
| 自动转发 | **电话进程** | `ModulePrefs` 文件通道 | 实际转发路径 |

手动转发走 `DBProvider.call("forward")`，在 app 进程内读自己的 SP——
**不能用来验证模块侧（电话进程）的配置读取**。

### 调试方法（不用重启手机）

```sh
# 强制重启 app 并抓激活日志
android-shizuku-cli exec "am force-stop com.smscodf.zhuxf; sleep 2; \
  am start -n com.smscodf.zhuxf/com.tianma.xsmscode.ui.home.HomeActivity; sleep 8; \
  logcat -d | grep -iE 'ActivationService|XposedServiceHelper|XposedProvider' | tail -15"
```

正常应见：
```
ActivationService: listener registered
XposedProvider: binder received: android.os.BinderProxy@xxx
ActivationService: service bound, framework=LSPosed 2.2.0-it, api=102, scope=[...]
```

## 装包与验证流程（每次必做）

1. **构建**：一律走 GitHub Actions CI（本机沙箱 aapt2 是 x86_64、设备是 aarch64，无法本地构建）。
   推送 main 后自动构建，run_number 即版本号括号内的数字：`3.0.5(68)`。
2. **装包**：`sh /var/minis/workspace/install_apk.sh <apk文件名>`
   （内部走 `cat <apk> | pm install -r -S <size>`；**sdcard 是 FUSE，pm 直接读路径会失败**）
3. **重启手机**：仅当改动涉及 `scope.list` / 模块侧 hook 代码时才需要
   （LSPosed 静态作用域只在新进程注入）。
   **改 app 侧代码（含激活检测）不需要重启**，`am force-stop` 重启 app 即可。
4. **验证日志**（`/data/adb/lspd/log/modules_*.log`，进程名**不带 `:satellite`**）：
   - `XSmsCode-Prefs: loaded N key(s) from file .../prefs_export.xml`
   - `Config diag: enabled=true block=true copy=true autoCancel=true`  ← **判据**
   - 激活态：`ActivationService: service bound, framework=LSPosed ...`

## ★ 转发范围（仅验证码 / 全部短信）实测结论（2026-09-20）

切「全部短信」后，普通短信正常转发且**不被删除**（用户实测确认）。

**日志判据**：

普通短信（scope=all 时）：
```
Config diag: ... scope=all ...
Sender: "13138345715"                       ← 手机号（非服务号）
Non-code SMS, forwarded due to scope=all    ← 走非验证码分支
Forward direct (attempt 1): succeed
```
★ **不出现** `Copy to clipboard succeed`、**不出现** `Blocking code SMS...`

验证码短信：
```
Sender: "10010"
Copy to clipboard succeed
Forward direct (attempt 1): succeed
Blocking code SMS...                        ← 这条出现 = 短信被拦截删除
```

**关键判据：`Blocking code SMS` 是否出现** —— 出现=已删，不出现=保留。

代码保障：`CodeWorker.buildParseResult(isCodeMsg)` 中
```java
parseResult.setBlockSms(isCodeMsg && XSPUtils.blockSmsEnabled(xsp));
```
非验证码短信恒为 false，杜绝误删。

## ⚠️ 禁止事项（血泪教训）

- ❌ 反射 `ActivityThread.systemMain()` 取 Context → 在 system_server 执行会崩、触发 LSPosed 安全模式
- ❌ 在电话进程做**同步阻塞**的跨进程 Provider 调用 → app 冻结时卡死短信处理
- ❌ 在设备上跑全盘 `find` → 打崩 system_server
- ❌ 用 `monkey` 启动 app → 会打开自动旋转（压力测试注入随机事件），用 `am start`
- ❌ 改动系统设置（用户红线：手机系统只可查看）
- ❌ **别让用户勾选"模块自身"当作用域** → LSPosed 源码硬性排除模块自身，
  界面上根本看不到、也勾不上；`scope.list` 里声明自身同样无效
- ❌ **别把系统框架的标识写成 `android`** → 现代 LSPosed 中系统框架的标识是 **`system`**
  （界面显示"系统框架"，实际包名为 system；LSPosed 源码 `shouldHideApp()` 中
  `if (info.packageName.equals("system")) return false;` 亦印证）。
  `android` 是更早期框架时代的写法，已弃用。
- ❌ **别让用户做无用操作** → 曾让用户在 LSPosed 里"勾选模块自身"/点"勾选推荐"，
  但框架源码硬性排除模块自身（`ScopeAdapter.refresh()` 中
  `packageName.equals(module.packageName)` 直接 return），界面上根本不显示该选项。
  **用户白折腾两轮、重启三次。** 让用户操作前先确认该操作在实机上真的可行。
- ❌ **别用真实换行符写 strings.xml 长文本** → aapt2 会把真实换行压成空格，
  导致文字挤成一坨。必须用**字面 `\n`**（见 `privacy_dialog_content`）。
- ❌ **别在 XML 里用 `\"` 转义引号** → 会原样显示反斜杠。文本内双引号无需转义；
  裸 `&` 须写成 `&amp;`。
- ❌ **注释掉 menu 的 item 会让 R.id 消失** → 用 `android:visible="false"` 隐藏，
  否则 Java 里引用的 `R.id.xxx` 编译失败。
- ❌ **别假设"看源码就能得出结论"** → 用户的实机使用经验（多年、多设备、多模块）
  往往比源码推断更接近现实。本次连续误判两次（模块自身注入、system vs android），
  均因只看代码未核对实机状态。**先查设备实际数据，再下结论。**
