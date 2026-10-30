#!/bin/sh
# Compile route/AppRoute.java into app/src/main/assets/approute.dex (needed before building the APK).
# Needs: JDK 17, Android SDK with platforms;android-36 and any build-tools release (the newest one installed is used; ANDROID_HOME set).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME}}"
AJ="$SDK/platforms/android-36/android.jar"
[ -f "$AJ" ] || { echo "missing $AJ (sdkmanager \"platforms;android-36\")" >&2; exit 1; }
BT="$(ls -1 "$SDK/build-tools" 2>/dev/null | sort -V | tail -1)"
D8="$SDK/build-tools/$BT/d8"
[ -n "$BT" ] && [ -x "$D8" ] || { echo "no build-tools with d8 under $SDK/build-tools (sdkmanager \"build-tools;36.0.0\")" >&2; exit 1; }
echo "using platforms/android-36 and build-tools/$BT"
rm -rf out && mkdir -p out/classes
javac --release 8 -nowarn -cp "$AJ" -d out/classes AppRoute.java
"$D8" --release --min-api 31 --lib "$AJ" --output out out/classes/*.class
cp out/classes.dex ../app/src/main/assets/approute.dex
rm -rf out
echo "built app/src/main/assets/approute.dex"
