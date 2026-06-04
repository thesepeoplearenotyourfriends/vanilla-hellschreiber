#!/usr/bin/env bash
# Build a tiny Android APK from the command line (Termux-friendly, no Gradle).
#
# Defaults are intentionally modern:
#   * targetSdkVersion 35 (Android 15)
#   * minSdkVersion 24 so APK Signature Scheme v2+ can cover every supported device
#   * release-style signing with v2/v3 enabled and v4 attempted when supported
#
# Override knobs:
#   ANDROID_JAR=/path/to/android.jar   Prefer an Android 15/API 35 platform jar.
#   JAVA_HOME=/path/to/jdk             JDK 17+ is recommended.
#   KEYSTORE_PASSWORD=...              Defaults to the demo password: password
#   KEY_ALIAS=...                      Defaults to mykey.
#   ENABLE_V4_SIGNING=false            Skip .idsig generation for older apksigner.

export JAVA_HOME="/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk/"
export ANDROID_JAR="/data/data/com.termux/files/home/compile-apk/compile-apk-helloworld-in-termux-main/project/toolz/android.jar"
export PATH="$PATH:$JAVA_HOME/bin"

set -Eeuo pipefail

log() {
  printf '\n--------------- %s ---------------\n' "$1"
}

fail() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

resolve_project_dir() {
  local input="${1:-project}"
  if [[ "$input" = /* ]]; then
    printf '%s\n' "$input"
  else
    printf '%s/%s\n' "$PWD" "$input"
  fi
}

find_tool() {
  local name="$1"
  if [[ -x "$BUILD_TOOLS/$name" ]]; then
    printf '%s/%s\n' "$BUILD_TOOLS" "$name"
  elif command -v "$name" >/dev/null 2>&1; then
    command -v "$name"
  else
    return 1
  fi
}

find_android_jar() {
  if [[ -n "${ANDROID_JAR:-}" ]]; then
    [[ -f "$ANDROID_JAR" ]] || fail "ANDROID_JAR is set but does not exist: $ANDROID_JAR"
    printf '%s\n' "$ANDROID_JAR"
    return
  fi

  local candidate
  for candidate in \
    "${ANDROID_HOME:-}/platforms/android-35/android.jar" \
    "${ANDROID_SDK_ROOT:-}/platforms/android-35/android.jar" \
    "$PROJECT_DIR/toolz/android.jar"; do
    if [[ -f "$candidate" ]]; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  fail "No android.jar found. Install Android API 35, set ANDROID_JAR, or place android.jar in project/toolz/."
}

PROJECT_DIR="$(resolve_project_dir "${1:-project}")"
printf 'Work Dir: %s\n' "$PROJECT_DIR"
[[ -d "$PROJECT_DIR" ]] || fail "Directory does not exist: $PROJECT_DIR"
cd "$PROJECT_DIR"

BUILD_TOOLS="$PROJECT_DIR/toolz"
if [[ -d "$BUILD_TOOLS" ]]; then
  for bundled_tool in aapt2 apksigner d8 dx zipalign; do
    [[ -f "$BUILD_TOOLS/$bundled_tool" ]] && chmod a+x "$BUILD_TOOLS/$bundled_tool"
  done
fi

if [[ -z "${JAVA_HOME:-}" && -n "${PREFIX:-}" && -x "$PREFIX/opt/openjdk/bin/javac" ]]; then
  export JAVA_HOME="$PREFIX/opt/openjdk"
fi

JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
command -v "$JAVAC" >/dev/null 2>&1 || fail "javac not found. Install OpenJDK 17+ or set JAVA_HOME."

AAPT2="$(find_tool aapt2)" || fail "aapt2 not found. Install Android build-tools (for Termux: pkg install aapt2)."
ZIPALIGN="$(find_tool zipalign)" || fail "zipalign not found. Install Android build-tools or place it in project/toolz/."
APKSIGNER="$(find_tool apksigner)" || fail "apksigner not found. Install Android build-tools or place it in project/toolz/."
ANDROID_PLATFORM_JAR="$(find_android_jar)"
D8="$(find_tool d8 || true)"
DX="$(find_tool dx || true)"
[[ -n "$D8" || -n "$DX" ]] || fail "Neither d8 nor dx was found. Install d8 (preferred) or dx."

KEYSTORE="$PROJECT_DIR/key.keystore"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-password}"
KEY_ALIAS="${KEY_ALIAS:-mykey}"
ENABLE_V4_SIGNING="${ENABLE_V4_SIGNING:-true}"
if "$APKSIGNER" sign --help 2>&1 | grep -q -- "--v4-signing-enabled"; then
  APKSIGNER_SUPPORTS_V4=true
else
  APKSIGNER_SUPPORTS_V4=false
fi

rm -rf build
mkdir -p build/classes build/dex

log "aapt2 compile"
"$AAPT2" compile -v \
  --dir res \
  -o build/resources.zip

log "aapt2 link"
"$AAPT2" link -v \
  -I "$ANDROID_PLATFORM_JAR" \
  --manifest AndroidManifest.xml \
  --java build/ \
  -A assets \
  -o build/link.apk \
  build/resources.zip \
  --auto-add-overlay

log "javac ($("$JAVAC" --version 2>&1))"
"$JAVAC" --release 8 \
  -d build/classes \
  --class-path "$ANDROID_PLATFORM_JAR" \
  src/com/hellscribe/MainActivity.java \
  build/com/hellscribe/R.java

log "dex"
if [[ -n "$D8" ]]; then
  "$D8" \
    --min-api 24 \
    --classpath "$ANDROID_PLATFORM_JAR" \
    --output build/dex \
    build/classes/com/hellscribe/*.class
  cp build/dex/classes.dex build/classes.dex
else
  (
    cd build/classes
    "$DX" --dex --verbose --output=../classes.dex com/hellscribe/*.class
  )
fi

log "zip"
(
  cd build
  zip -q -u link.apk classes.dex
)

log "zipalign"
"$ZIPALIGN" -v -f -p 4 build/link.apk build/aligned.apk

if [[ ! -f "$KEYSTORE" ]]; then
  log "keytool"
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEYSTORE_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -sigalg SHA256withRSA \
    -validity 10000 \
    -dname "CN=Termux Demo, OU=Development, O=Example, L=Local, ST=Local, C=US"
fi

sign_args=(
  sign
  --verbose
  --min-sdk-version 24
  --v1-signing-enabled false
  --v2-signing-enabled true
  --v3-signing-enabled true
  --ks "$KEYSTORE"
  --ks-pass "pass:$KEYSTORE_PASSWORD"
  --ks-key-alias "$KEY_ALIAS"
  --out build/final.apk
)

log "apksigner"
if [[ "$ENABLE_V4_SIGNING" == "true" && "$APKSIGNER_SUPPORTS_V4" == "true" ]]; then
  if "$APKSIGNER" "${sign_args[@]}" --v4-signing-enabled true build/aligned.apk; then
    :
  else
    printf 'apksigner could not create v4 signing; retrying with v2/v3 only.\n' >&2
    "$APKSIGNER" "${sign_args[@]}" --v4-signing-enabled false build/aligned.apk
  fi
elif [[ "$APKSIGNER_SUPPORTS_V4" == "true" ]]; then
  "$APKSIGNER" "${sign_args[@]}" --v4-signing-enabled false build/aligned.apk
else
  printf 'apksigner does not support v4 signing; using v2/v3 only.\n' >&2
  "$APKSIGNER" "${sign_args[@]}" build/aligned.apk
fi

log "verify"
"$APKSIGNER" verify --verbose --min-sdk-version 24 build/final.apk

printf '\nSuccess: %s\n' "$PROJECT_DIR/build/final.apk"
if [[ -f build/final.apk.idsig ]]; then
  printf 'V4 idsig: %s\n' "$PROJECT_DIR/build/final.apk.idsig"
fi
