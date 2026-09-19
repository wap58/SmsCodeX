#!/bin/sh
# ============================================================
# 智码通 APK 安装脚本（默认安装方式，2026-09-19 用户确认）
#
# 用法：sh install_apk.sh <apk文件名或完整路径>
#   例：sh install_apk.sh SmsCodeX_v3.0.4_260919_CI19.apk
#
# 为什么用管道而不是 pm install <路径>：
#   system_server 读不了 /sdcard 的 fuse 文件
#   （SELinux: System server has no access to read file context u:object_r:fuse:s0），
#   直接传路径必然失败。走 stdin 管道可绕开该限制，
#   且无需把 APK 拷到 /data/local/tmp（用户要求那里不放交付文件）。
#
# 覆盖安装保留数据（-r），签名一致时不会丢配置与授权。
# ============================================================
set -e

ARG="$1"
if [ -z "$ARG" ]; then
    echo "用法: sh install_apk.sh <apk文件名或完整路径>"
    exit 1
fi

# 允许只传文件名，默认到交付目录找
case "$ARG" in
    /*) APK="$ARG" ;;
    *)  APK="/storage/emulated/0/Download/MT/apks/$ARG" ;;
esac

echo "== 目标: $APK =="

# 1. 确认文件存在并取大小（走 JSON 解析，避免从转义文本里误抓数字）
SIZE=$(android-shizuku-cli exec "stat -c %s '$APK' 2>/dev/null || echo 0" 2>/dev/null \
       | python3 -c "
import sys,json,re
t=sys.stdin.read()
m=re.search(r'\{.*\}', t, re.S)
try:
    out=json.loads(m.group(0))['data']['stdout'].strip()
    print(out if out.isdigit() else 0)
except Exception:
    print(0)
" 2>/dev/null)
if [ -z "$SIZE" ] || [ "$SIZE" = "0" ]; then
    echo "错误：设备上找不到该文件（或大小为 0）"
    exit 1
fi
echo "   大小: $SIZE 字节"

# 2. 记录安装前的版本，便于对比
BEFORE=$(android-shizuku-cli package info com.smscodf.zhuxf 2>/dev/null \
         | python3 -c "
import sys,json,re
t=sys.stdin.read(); m=re.search(r'\{.*\}', t, re.S)
try:
    d=json.loads(m.group(0))['data']; print(d.get('versionName'), d.get('lastUpdateTime'))
except Exception: print('未安装')
" 2>/dev/null | head -1)
echo "   安装前: $BEFORE"

# 3. 走 stdin 管道安装（关键步骤）
echo "== 安装中 =="
android-shizuku-cli exec "cat '$APK' | pm install -r -S $SIZE" 2>/dev/null \
    | python3 -c "
import sys,json,re
t=sys.stdin.read()
m=re.search(r'\{.*\}', t, re.S)
try:
    d=json.loads(m.group(0))['data']
    print('   ' + (d.get('stdout','').strip() or d.get('stderr','').strip()[:200]))
except Exception:
    print('   安装命令未返回可解析结果')
"

# 4. 验证
AFTER=$(android-shizuku-cli package info com.smscodf.zhuxf 2>/dev/null \
        | python3 -c "
import sys,json,re
t=sys.stdin.read(); m=re.search(r'\{.*\}', t, re.S)
try:
    d=json.loads(m.group(0))['data']; print(d.get('versionName'), d.get('lastUpdateTime'))
except Exception: print('读取失败')
" 2>/dev/null | head -1)
echo "   安装后: $AFTER"

# 5. 确认没有在设备上留临时文件
LEFTOVER=$(android-shizuku-cli exec "ls /data/local/tmp/*.apk 2>/dev/null | wc -l" 2>/dev/null \
           | python3 -c "
import sys,json,re
t=sys.stdin.read()
m=re.search(r'\{.*\}', t, re.S)
try:
    print(json.loads(m.group(0))['data']['stdout'].strip())
except Exception:
    print('?')
" 2>/dev/null)
echo "   /data/local/tmp 残留 apk: ${LEFTOVER:-?} 个（本脚本不产生，若有为历史遗留）"

echo "== 完成 =="
