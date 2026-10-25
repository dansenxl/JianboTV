# JianboTV

简播 TV is an Android TV IPTV client derived from [OwnTV](https://github.com/ahXN00/OwnTV). It opens directly to a live EPG Guide, keeps playlist-defined channel groups visible, and includes an in-app updater for releases published by this repository.

The app contains no playlists, TV streams, or subscriptions. Add sources that you are authorized to use.

## Build

Requirements: JDK 17, Android SDK platform 37, Android Build Tools 36.0.0, and the Android SDK licenses accepted on the build machine.

```sh
export ANDROID_HOME="$HOME/Android/Sdk"
VERSION_NAME=0.5 VERSION_CODE=5 ./gradlew :app:assembleStandardRelease
```

The standard flavor packages `arm64-v8a` and `armeabi-v7a`. Release builds from a fresh checkout are unsigned unless signing inputs are provided locally. Keep signing keys and passwords outside this repository. A release APK must use the existing JianboTV signing identity so Android can install it as an in-place update.

## In-app updates

The app checks the public GitHub Releases API for `dansenxl/JianboTV`. A stable release must include `JianboTV-vX.Y-arm.apk` (or the matching tag-derived name), and its release description must contain a line such as `versionCode: 5`. GitHub's SHA-256 asset digest is checked before install; a matching `.sha256` release asset is supported as a fallback. Downloads are accepted only from the expected repository release URL and trusted GitHub HTTPS asset hosts. Android's normal system installation confirmation remains required.

## License and attribution

JianboTV and the included OwnTV / OwnTV Core source are distributed under GPL-3.0; see [LICENSE](LICENSE) and the preserved upstream license notices. This repository contains the app and Core source used for its builds, plus the pinned OwnTV libmpv source/build recipe and third-party source references. See [THIRD_PARTY_SOURCES.md](THIRD_PARTY_SOURCES.md).
