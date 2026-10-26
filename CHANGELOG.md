# JianboTV changes

## v0.5 (versionCode 5)

- Added public GitHub Releases update checks, with a 24-hour startup interval and manual checks.
- Added release asset, versionCode, SHA-256, package, and signing-certificate validation before install.
- Added download progress, cancellation, and Android's install-permission/system-installer path.
- Fixed Guide category entry focus to start at All channels; moving focus alone does not change the selected group.
- Preserved the v0.4 Guide and exit-confirmation behavior.

## v0.6 (versionCode 6)

- Added a dedicated Software update group in Settings, with a direct manual check action and a separate automatic-check toggle.
- Added a repeatable manual check action after a successful check; manual checks bypass the 24-hour startup interval.
- Kept status reporting for checking, current version, last check time, available releases, network/API errors, downloads, APK validation, and installer permissions.
- Standardized visible clock and programme times to 24-hour `HH:mm` display using the device time zone without changing stored timestamps or EPG conversion.
- Preserved automatic update checks (enabled by default), M3U and XMLTV refresh behavior, the Guide category focus rule, and the existing player paths.

## v0.7 (versionCode 7)

- Reorganized Guide settings into five directly visible Jianbo TV categories: Playlist management, Programme guide, Playback settings, Software update, and About Jianbo TV.
- Kept the existing M3U/XMLTV and player settings pages and hid the unrelated Quick setting for automatic next-episode playback without deleting saved preference data.
- Added a direct GitHub update check, retained the enabled-by-default 24-hour automatic check, and persisted the last check result and timestamp across app restarts.
- Added Jianbo TV project, upstream OwnTV/OwnTV_Core, and GPLv3 information to About.
- Added horizontal focus-safe padding to the Guide category row so edge focus borders remain inside the viewport.
- Preserved the Guide, playback, playlist refresh, and XMLTV refresh behavior.
