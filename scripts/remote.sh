#!/usr/bin/env bash
# Remote Android build/test helper for this phone. Credentials come from env:
#   POCKET_HOST=root@host POCKET_PASS=... scripts/remote.sh sync|test Class|build|dev
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HOST="${POCKET_HOST:?set POCKET_HOST}"
PASS="${POCKET_PASS:?set POCKET_PASS}"
SSH=(sshpass -p "$PASS" ssh -o StrictHostKeyChecking=no "$HOST")
SCP=(sshpass -p "$PASS" scp -o StrictHostKeyChecking=no)

remote_env='export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/root/android-sdk PATH=/usr/lib/jvm/java-17-openjdk-amd64/bin:/root/gradle-8.11.1/bin:/usr/local/bin:/usr/bin:/bin'

sync_src() {
  TMP="${TMPDIR:-/data/data/com.termux/files/usr/tmp}"
  tar czf "$TMP/pocket-src.tar.gz" -C "$ROOT" \
    --exclude=./app/src/main/jniLibs --exclude=./.git --exclude=./.superpowers \
    --exclude=./app/build --exclude=./build --exclude=./.gradle .
  "${SCP[@]}" "$TMP/pocket-src.tar.gz" "$HOST:/root/pocket-src.tar.gz"
  "${SSH[@]}" "rm -rf /root/pocket && mkdir -p /root/pocket && tar xzf /root/pocket-src.tar.gz -C /root/pocket --strip-components=1 && mkdir -p /root/pocket/app/src/main/jniLibs/arm64-v8a && cp /root/ocserver/libocserver.so /root/pocket/app/src/main/jniLibs/arm64-v8a/libocserver.so && echo 'sdk.dir=/root/android-sdk' > /root/pocket/local.properties && chmod +x /root/pocket/gradlew"
}

case "${1:-}" in
  sync) sync_src ;;
  test)
    cls="${2:?test class name}"
    "${SSH[@]}" "$remote_env; cd /root/pocket && ./gradlew testStandardDebugUnitTest --tests 'com.pocket.opencode.$cls' --no-daemon --console=plain"
    ;;
  results)
    out=$("${SSH[@]}" 'grep -h "<testsuite" /root/pocket/app/build/test-results/testStandardDebugUnitTest/*.xml' | sed 's/ timestamp.*//')
    printf '%s\n' "$out"
    if printf '%s\n' "$out" | grep -qE 'failures="[1-9]|errors="[1-9]'; then exit 1; fi
    ;;
  build)
    "${SSH[@]}" "$remote_env; cd /root/pocket && ./gradlew assembleStandardDebug --no-daemon --console=plain"
    "${SCP[@]}" "$HOST:/root/pocket/app/build/outputs/apk/standard/debug/app-standard-debug.apk" "$HOME/tmp/pocket.apk"
    ls -lh "$HOME/tmp/pocket.apk"
    ;;
  dev)
    "${SSH[@]}" "$remote_env; cd /root/pocket && ./gradlew assembleDevDebug --no-daemon --console=plain"
    "${SCP[@]}" "$HOST:/root/pocket/app/build/outputs/apk/dev/debug/app-dev-debug.apk" "$HOME/tmp/pocket-dev.apk"
    ls -lh "$HOME/tmp/pocket-dev.apk"
    ;;
  dev-release)
    "${SSH[@]}" "$remote_env; cd /root/pocket && ./gradlew assembleDevRelease --no-daemon --console=plain"
    "${SCP[@]}" "$HOST:/root/pocket/app/build/outputs/apk/dev/release/app-dev-release.apk" "$HOME/tmp/pocket-dev-release.apk"
    ls -lh "$HOME/tmp/pocket-dev-release.apk"
    ;;
  release)
    "${SSH[@]}" "$remote_env; cd /root/pocket && ./gradlew assembleStandardRelease --no-daemon --console=plain"
    "${SCP[@]}" "$HOST:/root/pocket/app/build/outputs/apk/standard/release/app-standard-release.apk" "$HOME/tmp/pocket-release.apk"
    ls -lh "$HOME/tmp/pocket-release.apk"
    ;;
  *) echo "usage: $0 sync|test <Class>|build|dev|dev-release|release" >&2; exit 2 ;;
esac
