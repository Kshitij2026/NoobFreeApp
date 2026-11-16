# NoobFree source review and test fix

## Root cause addressed
The uploaded source read `Build.MANUFACTURER.lowercase()` directly in `DeveloperManager`. Local JVM unit tests do not run on an Android device, so Android framework `Build` fields may be unavailable/null and cause a NullPointerException on each DeveloperManager path.

## Changes
- Added an injectable manufacturer provider to `DeveloperManager`.
- Centralized manufacturer lookup and safely falls back to `Unknown` if the Android field is unavailable.
- Updated `DeviceInfoManager` to safely read manufacturer, brand, model and device strings for the same class of framework-stub issue.
- Added a regression test for a blank manufacturer provider.

## Verification
These are source-level changes based on the uploaded ZIP and the GitHub Actions test output. A fresh GitHub Actions run is required to confirm all tests pass and the APK builds.
