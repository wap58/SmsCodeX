#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# 智码通 (SmsCodeX) 目标手机诊断采集脚本
# 用途：在"无法自动输入"的那台手机上运行，一键收齐全部证据
# 用法：Termux 里执行  bash smscodx-diag.sh
# 需要：root 权限（LSPosed 模块环境必有）
# 产物：~/smscodx-diag-HHMMSS.tar.gz （把该文件发回给 Minis 分析）
# ============================================================
set -u
cd "$HOME"
STAMP=$(date +%H%M%S)
OUT="$HOME/smscodx-diag-$STAMP.tar.gz"
TMP=$(mktemp -d)
cd "$TMP"

echo "[1/8] 收集 LSPosed 模块日志..."
mkdir -p lspd_log
cp /data/adb/lspd/log/modules_*.log lspd_log/ 2>/dev/null || echo "no lspd logs" > lspd_log/none.txt

echo "[2/8] 收集 LSPosed 模块配置数据库..."
cp /data/adb/lspd/config/modules_config.db lspd_config.db 2>/dev/null || true

echo "[3/8] 收集模块共享设置..."
mkdir -p mod_prefs
find /data/data/com.smscodf.zhuxf/shared_prefs -name '*.xml' -exec cat {} \; > mod_prefs/all.xml 2>/dev/null || echo "no prefs" > mod_prefs/all.xml

echo "[4/8] 抓取 logcat（模块相关）..."
logcat -d -v time 2>/dev/null | grep -E "XSmsCode|LSPosed-Bridge|LSPosedFramework" > logcat.txt
wc -l logcat.txt

echo "[5/8] 电话进程注入证据 + 权限状态..."
{
  echo "=== phone pid: $(pidof com.android.phone || echo 'NOT RUNNING') ==="
  grep smscodf "/proc/$(pidof com.android.phone)/maps" 2>/dev/null | head -8 || echo "NO MODULE MAPPING IN PHONE PROCESS"
  echo "=== INJECT_EVENTS / KILL_BACKGROUND_PROCESSES ==="
  dumpsys package com.android.phone 2>/dev/null | grep -iE "INJECT_EVENTS|KILL_BACKGROUND" || echo "dumpsys grep empty"
} > phone_state.txt

echo "[6/8] 短信记录库（最近 10 条）..."
mkdir -p db
cp /data/data/com.smscodf.zhuxf/databases/sms-code.db db/ 2>/dev/null || echo "no db" > db/none.txt

echo "[7/8] 系统信息..."
{
  echo "MODEL: $(getprop ro.product.model)"
  echo "BRAND: $(getprop ro.product.brand)"
  echo "RELEASE: $(getprop ro.build.version.release)"
  echo "SDK: $(getprop ro.build.version.sdk)"
  echo "SEC_PATCH: $(getprop ro.build.version.security_patch)"
  echo "ABI: $(getprop ro.product.cpu.abilist)"
  echo "BOOT_TIME: $(uptime)"
} > sysinfo.txt

echo "[8/8] 打包..."
tar czf "$OUT" . 2>/dev/null
rm -rf "$TMP"
echo "============================================"
echo "✅ 诊断包已生成: $OUT"
echo "   把这个文件发回给 Minis（微信文件/网盘均可）"
echo "============================================"
