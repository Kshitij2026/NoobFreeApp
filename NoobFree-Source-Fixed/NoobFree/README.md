# NoobFree

NoobFree is an Android device transparency and control utility. It reports only capabilities and restrictions that Android exposes to the app and requires explicit user confirmation for operations that modify device state.

## Current implementation

- Existing device information, developer-settings, animation, refresh-rate and Shizuku screens are retained.
- App inventory with package names, app-info shortcuts, user-app uninstall confirmation, updated-system-app update-removal flow, wallpaper selection/application, and Shizuku-gated user-app enable/disable operations.
- Privacy & Admin Inspector reports overlay AppOps modes, enabled accessibility services visible to the current profile, active device-admin components visible to the app, NoobFree's own owner role, and selected current-user restrictions.
- Shizuku UserService command allow-list includes the existing settings commands plus narrowly formatted `pm` enable/disable/verification commands. Shell input is constrained by regular expressions; arbitrary shell commands remain blocked.

## Important Android limitations

- NoobFree cannot grant itself Device Owner/Profile Owner status or bypass an organisation's administrator policy.
- A normal APK cannot silently activate Developer Options on every Android device. The existing developer-settings operation requires an already authorised Shizuku shell and can still be rejected by OEM/enterprise policy.
- Package enable/disable in this build requires Shizuku. System/updated-system package disable operations are intentionally locked in the UI pending a verified, device-specific safety catalogue. Updated system apps may expose Android's own Remove updates confirmation.
- Uninstalling an app always relies on Android's system confirmation and may be blocked by device policy.
- The privacy inspector reports only information exposed by Android to this app. It cannot prove that an app is transmitting data or determine the full behaviour of an opaque management agent.
- The OEM privilege bridge and direct Android Management API enrollment are not implemented. Those require manufacturer/organisation authorisation and supporting infrastructure.

## Build

The GitHub Actions workflow at `.github/workflows/android-ci.yml` installs JDK 17 and Gradle 8.9, runs unit tests, and assembles debug and release APKs. It invokes the installed `gradle` executable directly. The checked-in `gradlew` is a small delegating script, not a self-contained Gradle Wrapper distribution.

## Privacy

The manifest does not request the INTERNET permission. The app inventory uses `QUERY_ALL_PACKAGES` for its stated app-management function. The app should remain offline-first unless a future, separately disclosed feature requires network access.
