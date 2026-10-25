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
