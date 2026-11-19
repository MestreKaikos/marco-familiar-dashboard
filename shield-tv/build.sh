#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_HOME:?Indica el directori de l’Android SDK}"
bt="$sdk/build-tools/35.0.0"
platform="$sdk/platforms/android-35/android.jar"
out="$PWD/build"
mkdir -p "$out"
work=$(mktemp -d "$out/work.XXXXXX")
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/classes" "$work/dex"
"$bt/aapt2" compile --dir res -o "$work/resources.zip"
"$bt/aapt2" link -o "$work/base.apk" -I "$platform" --manifest AndroidManifest.xml "$work/resources.zip"
java -m jdk.compiler/com.sun.tools.javac.Main -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
  -bootclasspath "$bt/core-lambda-stubs.jar:$platform" -d "$work/classes" src/cat/marcfamiliar/tv/*.java
java -m jdk.jartool/sun.tools.jar.Main cf "$work/classes.jar" -C "$work/classes" .
"$bt/d8" --min-api 28 --lib "$platform" --output "$work/dex" "$work/classes.jar"
(cd "$work/dex" && zip -q -0 "$work/base.apk" classes.dex)
"$bt/zipalign" -f 4 "$work/base.apk" "$work/aligned.apk"
"$bt/apksigner" sign --ks "${MARC_TV_KEYSTORE:?}" --ks-key-alias "${MARC_SIGNING_ALIAS:?}" \
  --ks-pass env:MARC_SIGNING_PASSWORD --key-pass env:MARC_SIGNING_KEY_PASSWORD \
  --out "$work/signed.apk" "$work/aligned.apk"
"$bt/apksigner" verify --verbose "$work/signed.apk"
mv "$work/signed.apk" "$out/Marc-Familiar-TV-1.0.apk"
