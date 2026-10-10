#!/usr/bin/env bash
set -euo pipefail
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
BT=$(ls -d "$SDK"/build-tools/[0-9]* | sort -V | tail -1)
PL=$(ls -d "$SDK"/platforms/android-[0-9]* | sort -V | tail -1)
JAR="$PL/android.jar"
echo "build-tools: $BT | platform: $PL"
rm -rf out soundpad.apk && mkdir -p out/classes
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -cp "$JAR" -d out/classes $(find src -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 26 --output out $(find out/classes -name '*.class')
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 34 --version-code 1 --version-name 1.0
(cd out && zip -q base.apk classes.dex)
"$BT/zipalign" -f -p 4 out/base.apk out/aligned.apk
SIGN="--ks-pass pass:android --key-pass pass:android --out soundpad.apk out/aligned.apk"
if [ -f soundpad.p12 ] && "$BT/apksigner" sign --ks soundpad.p12 $SIGN; then
  echo "signed with soundpad.p12"
else
  echo "fallback: temporary key (reinstall needed on update)"
  rm -f debug.keystore
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias k -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Soundpad" >/dev/null
  "$BT/apksigner" sign --ks debug.keystore $SIGN
fi
echo "OK: soundpad.apk"
