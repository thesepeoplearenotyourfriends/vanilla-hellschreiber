# HellScribe Android APK

HellScribe is a dependency-free Android version of the Feld-Hell / Hellschreiber toy. It mirrors the browser playground's controls and strip preview, but is tailored for phones:

- text-to-Hell rendering,
- speaker playback using `AudioTrack`,
- microphone receive/visualization using `AudioRecord`,
- large touch-friendly buttons,
- dark system-friendly colors,
- no Gradle and no runtime libraries beyond the Android framework.

## Build

This directory is laid out for the Termux-friendly shell build script shown by the project maintainer, not for Gradle:

- `AndroidManifest.xml`
- `res/`
- `assets/`
- `src/com/helloworld/MainActivity.java`

From the parent repository, pass this directory as the project argument to that script:

```sh
/path/to/your/build-apk.sh hell-android-apk
```

The manifest targets Android SDK 34 and sets `minSdkVersion` 24 to match the script's dexing/signing assumptions. The final APK is expected at `hell-android-apk/build/final.apk`.
