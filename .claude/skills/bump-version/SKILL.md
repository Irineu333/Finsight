---
name: bump-version
description: Bump the app version across Android, iOS (XcodeGen), and Desktop
---

Bump the project version to `$ARGUMENTS` across all platforms.

## Version files

| Platform | File | Fields |
|----------|------|--------|
| Android  | `composeApp/build.gradle.kts` | `versionCode` (int), `versionName` (string) |
| Desktop  | `composeApp/build.gradle.kts` | `packageVersion` (string) |
| iOS      | `iosApp/project.yml`          | `CFBundleShortVersionString` (string), `CFBundleVersion` (int) |

## Rules

- `versionName`, `packageVersion`, and `CFBundleShortVersionString` must all be set to the new version string (e.g. `"1.2.0"`).
- `versionCode` and `CFBundleVersion` must be incremented by 1 from their current values.
- After editing `iosApp/project.yml`, run `cd iosApp && ./generate-project.sh` to regenerate the Xcode project.

## Steps

1. If `$ARGUMENTS` is empty, ask the user for the target version before proceeding.
2. Read the current values from the three files above.
3. Edit `composeApp/build.gradle.kts`: update `versionCode`, `versionName`, and `packageVersion`.
4. Edit `iosApp/project.yml`: update `CFBundleShortVersionString` and `CFBundleVersion`.
5. Run `cd iosApp && ./generate-project.sh` to regenerate the Xcode project.
6. Report a summary table of old → new values for each platform.
