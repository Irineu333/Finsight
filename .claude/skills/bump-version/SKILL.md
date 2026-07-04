---
name: bump-version
description: Bump the app version across Android, iOS (XcodeGen), and Desktop
---

Bump the project version to `$ARGUMENTS` across all platforms.

## Version files

| Platform | File | Fields |
|----------|------|--------|
| Android  | `app/android/build.gradle.kts` | `versionCode` (int), `versionName` (string) |
| Desktop  | `app/desktop/build.gradle.kts` | `packageVersion` (string) |
| iOS      | `iosApp/project.yml`          | `CFBundleShortVersionString` (string), `CFBundleVersion` (int) |

## Rules

- `versionName` and `CFBundleShortVersionString` must be set to the full version string, including any suffix (e.g. `"1.5.0-rc01"`).
- `packageVersion` (Desktop) does **not** support suffixes — use only the base `MAJOR.MINOR.PATCH` part (e.g. `"1.5.0"`).
- `versionCode` and `CFBundleVersion` must be incremented by 1 from their current values.
- After editing `iosApp/project.yml`, run `cd iosApp && ./generate-project.sh` to regenerate the Xcode project.

## Steps

1. If `$ARGUMENTS` is empty, ask the user for the target version before proceeding.
2. Read the current values from the files above.
3. Edit `app/android/build.gradle.kts`: update `versionCode` and `versionName`. Edit
   `app/desktop/build.gradle.kts`: update `packageVersion`.
4. Edit `iosApp/project.yml`: update `CFBundleShortVersionString` and `CFBundleVersion`.
5. Run `cd iosApp && ./generate-project.sh` to regenerate the Xcode project.
6. Report a summary table of old → new values for each platform.
