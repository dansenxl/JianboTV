# Third-party source and notices

## OwnTV

The client is derived from [OwnTV](https://github.com/ahXN00/OwnTV), GPL-3.0. The public source tree contains the client source used by this build. The upstream v5.2.1 release tag points to commit `8f47da68bf246b4ccbf9e53a53a81b4bf0620d2e`; JianboTV has subsequent project-specific changes.

## OwnTV Core

The included `vendor/OwnTV_Core` tree contains the Core and player-core source used by this build, with its upstream license and copyright notices. The imported base was Core 1.0.69 at commit `1de78b1e2690c22b50c060e986bce221454bf880`; this copy also contains project-specific changes.

## OwnTV libmpv / FFmpeg / mpv

The app includes `tv.own.owntv:libmpv:2026.10.4`. The corresponding OwnTV libmpv source tree is included under `third_party/OwnTV_libmpv`, from tag `v2026.10.4`, commit `f2b51ad0fa53ed21321b6a4bda7d236ecff37ef1`. Its README describes the binary and wrapper licensing, pinned mpv and FFmpeg inputs, carried patches, and build scripts. The pinned upstream inputs and patch sources are recorded in `third_party/OwnTV_libmpv/buildscripts/include/depinfo.sh` and `third_party/OwnTV_libmpv/buildscripts/patches/`.

## Other dependencies and assets

Gradle dependency coordinates and versions are recorded in `gradle/libs.versions.toml` and `vendor/OwnTV_Core/gradle/libs.versions.toml`. Font notices are kept under `extras/font-licenses/`. Each dependency retains its upstream license and notices where included; consult the linked source project and artifact metadata for its license terms.
