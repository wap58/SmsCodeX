#!/data/data/com.termux/files/usr/bin/bash
# ============================================================
# 智码通 (SmsCodeX) Termux 一键构建脚本
# 首次运行会下载 gradle + 依赖 + android.jar（约 400MB，20-40 分钟）
# 之后每次构建约 3-8 分钟
# ============================================================
set -e
cd "$(dirname "$0")"

echo "== [1/6] 检查 JDK 17 =="
JAVA17=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
if [ ! -d "$JAVA17" ]; then
  echo "未找到 openjdk-17，请先执行: pkg install -y openjdk-17"
  exit 1
fi
export JAVA_HOME=$JAVA17
export PATH=$JAVA_HOME/bin:$PATH

echo "== [2/6] 准备 Android SDK（platform android-34）=="
SDK=$HOME/android-sdk
mkdir -p "$SDK/platforms/android-34" "$SDK/licenses"
if [ ! -s "$SDK/platforms/android-34/android.jar" ]; then
  echo "下载 android.jar (API 34)..."
  curl -L --retry 2 -o /tmp/android34.jar \
    "https://ghfast.top/https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar"
  mv /tmp/android34.jar "$SDK/platforms/android-34/android.jar"
fi
echo "24333f8a63b6825ea9c5514f83c2829b004d1fee" > "$SDK/licenses/android-sdk-license"
echo "84831b9409646a918e30573bab4c9c91346d8abd" > "$SDK/licenses/android-sdk-preview-license"
[ -f local.properties ] || echo "sdk.dir=$SDK" > local.properties

echo "== [3/6] 配置 aapt2 与内存 =="
AAPT2=$(command -v aapt2 || echo /data/data/com.termux/files/usr/bin/aapt2)
sed -i '/aapt2FromMavenOverride/d' gradle.properties
echo "android.aapt2FromMavenOverride=$AAPT2" >> gradle.properties
sed -i 's|org.gradle.jvmargs=.*|org.gradle.jvmargs=-Xmx2600m -XX:MaxMetaspaceSize=768m|' gradle.properties
echo "org.gradle.daemon=false" >> gradle.properties

echo "== [4/6] Gradle 构建（首次较慢，耐心等待）=="
chmod +x gradlew
./gradlew assembleRelease --no-daemon --console=plain 2>&1 | tail -30

echo "== [5/6] 定位产物 =="
APK=$(ls -t app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
if [ -z "$APK" ]; then
  echo "❌ 未找到 APK，构建失败。把上面的报错发给 Minis。"
  exit 1
fi

echo "== [6/6] 复制到下载目录 =="
mkdir -p ~/storage/downloads
cp -f "$APK" ~/storage/downloads/
echo ""
echo "✅ 构建成功: $APK"
echo "✅ 已复制到 Download 目录，直接安装即可"
