#!/bin/sh
# Builds the release APK, installs it on the USB-connected phone, and compiles it ahead of time
# (a sideloaded app otherwise runs partly interpreted until Android compiles it during idle maintenance).
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/opt/android-studio/jbr}"
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"
./gradlew --console=plain -q :app:assembleRelease
# Only for the profile in use: a plain install also lands in Private space and other profiles, where a leftover
# copy signed with another key later blocks installing the GitHub release.
"$ADB" -d install -r --user current app/build/outputs/apk/release/app-release.apk
"$ADB" -d shell cmd package compile -m speed -f com.s1nn3rv2.kartka
