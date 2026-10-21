#!/usr/bin/env bash
# Builds VoxCoach without Gradle: aapt -> javac -> dx -> zipalign -> apksigner
# Needs: JDK (8+), Android platform android.jar (API 23+), aapt, dx (dalvik-exchange), zipalign, apksigner
# Debian/Ubuntu: apt install android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
DX="${DX:-$(command -v dalvik-exchange || command -v dx)}"
OUT=build
rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] aapt: resources + R.java"
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -F "$OUT/res.apk" --min-sdk-version 23 --target-sdk-version 34

echo "[2/6] javac"
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -nowarn \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "[3/6] dex"
"$DX" --dex --min-sdk-version=23 --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "[4/6] package"
cp "$OUT/res.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex)

echo "[5/6] zipalign"
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] sign"
KS=voxcoach.keystore
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass voxcoach -keypass voxcoach -alias voxcoach \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=VoxCoach, O=xyb, C=JP" >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass pass:voxcoach --key-pass pass:voxcoach --ks-key-alias voxcoach \
  --out VoxCoach.apk "$OUT/aligned.apk"
apksigner verify --print-certs VoxCoach.apk | head -3
ls -la VoxCoach.apk
