# 智码通修复笔记（2026-09-19）

## ★ 配置读取机制（最重要，勿改坏）

### 为什么不能用 LSPosed 官方机制
LSPosed 的 `xposedsharedprefs` + `MODE_WORLD_READABLE` 机制**只在"模块自身进程被注入"时生效**
（源码 `ConfigManager.getPrefsPath`：仅当 `module.appId == uid % PER_USER_RANGE` 才创建目录并 chown）。
本模块是**静态作用域**（system + com.android.phone），框架**从不注入模块自身进程** → 该机制永远无效。
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

## 装包与验证流程（每次必做）

1. 构建：`sh /tmp/rb_build.sh`（密钥从 `/var/minis/mounts/MT/smscodx-keys/` 读）
2. 装包：`cat <apk> | pm install -r -d -S <size>`（**sdcard 是 FUSE，pm 直接读会失败，必须流式**）
3. **重启手机**（LSPosed 静态作用域只在新进程注入）
4. 验证日志（`/data/adb/lspd/log/modules_*.log`，进程名**不带 `:satellite`**）：
   - `XSmsCode-Prefs: loaded N key(s) from file .../prefs_export.xml`
   - `Config diag: enabled=true block=true copy=true autoCancel=true`  ← **判据**

## ⚠️ 禁止事项（血泪教训）

- ❌ 反射 `ActivityThread.systemMain()` 取 Context → 在 system_server 执行会崩、触发 LSPosed 安全模式
- ❌ 在电话进程做**同步阻塞**的跨进程 Provider 调用 → app 冻结时卡死短信处理
- ❌ 在设备上跑全盘 `find` → 打崩 system_server
- ❌ 用 `monkey` 启动 app → 会打开自动旋转（压力测试注入随机事件），用 `am start`
- ❌ 改动系统设置（用户红线：手机系统只可查看）
