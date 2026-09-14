# 智码通 手机本地开发/调试指南（目标手机操作）

## 前提
1. 目标手机已 root（Magisk/KernelSU）
2. 已安装 LSPosed（与第一台手机同版本为佳）
3. 已安装 **Termux**（F-Droid 或 GitHub 版；**不要用 Play 商店版**，已废弃）
4. 把本 zip 解压到 Termux 主目录：
   ```
   cd ~ && termux-setup-storage   # 首次授权
   unzip ~/storage/downloads/xxx-dev.zip -d ~/smscodx
   cd ~/smscodx
   ```

## 一次性环境准备（约 20-40 分钟，下载 ~400MB 依赖）
```
pkg update
pkg install openjdk-17 unzip curl
```

## 构建
```
sh build-termux.sh
```
- 首次：下载 gradle/依赖/android.jar，较慢
- 之后每次：约 3-8 分钟
- 产物在 `~/storage/downloads/SmsCodeX_xxx_r.apk`（构建脚本自动复制）

> 若卡在 gradle 依赖下载（国内网络）：把 build.gradle 的 repositories
> 换成阿里云镜像后重试，或保持手机走代理。

## 安装 + 生效
```
# 方式1：文件管理器里点 APK 安装
# 方式2（Termux，root）：
su pm install -r ~/storage/downloads/SmsCodeX_*.apk
```
然后：LSPosed 管理器 → 模块 → 智码通 → **启用** → 勾作用域 `com.android.phone`（和 `system`）→ **重启手机**。

## 调试循环（改代码后）
```
sh build-termux.sh          # 重新编译
su pm install -r 新APK      # 重装模块（不用重启手机）
su kill $(pidof com.android.phone)   # 让电话进程带新代码重启
```
触发一条验证码短信测试自动输入。

## 看日志（定位问题的三板斧，都在 Termux 里跑）
```
# 1. 模块自身日志（自动输入各层结果都在这里）
logcat -d -v time | grep -E "XSmsCode|LSPosed-Bridge" | tail -60

# 2. LSPosed 框架日志（模块是否被加载、hook 是否挂上）
cat /data/adb/lspd/log/modules_*.log | grep -i smscodf | tail -20

# 3. 注入铁证（phone 进程里有没有模块的 dex）
grep smscodf /proc/$(pidof com.android.phone)/maps | head -5

# 4. 注入按键权限（自动输入依赖 INJECT_EVENTS）
dumpsys package com.android.phone | grep -i INJECT_EVENTS
```

## 先别急着调，先跑诊断
在开始改代码之前，先跑一次一键诊断包：
```
bash smscodx-diag.sh
```
把生成的 `~/smscodx-diag-*.tar.gz` 发回给 Minis，我直接告诉你卡在哪一层。
