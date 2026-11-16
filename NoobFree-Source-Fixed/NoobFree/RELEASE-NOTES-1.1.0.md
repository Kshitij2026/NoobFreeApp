# NoobFree 1.1.0 – Reset, admin and usability sprint

## Implemented in source
- Registered an optional legacy Device Administrator receiver for NoobFree.
- Requests only the `wipe-data` policy; Android shows its own activation UI.
- Added an in-app destructive reset confirmation. It checks that NoobFree's admin receiver is active, then calls `DevicePolicyManager.wipeData(0)`. Android may reject this for ordinary device-admin apps on newer Android versions or where management policy prevents it. NoobFree does not claim Device Owner.
- Added a manual recovery-mode reset guide. Hardware key combinations vary by device; this guide deliberately does not guess an Acer key sequence.
- Added a Shizuku-independent seven-tap Developer Options guide and About tablet shortcut. Android does not expose an API for an ordinary app to simulate taps in Settings or override `DISALLOW_DEBUGGING_FEATURES`.
- Applied translucent theme surfaces and a gradient behind the app navigation to make card surfaces visibly glass-like across screens.
- Preserved existing app scanner, wallpaper picker, policy inspector, and Shizuku package operations.

## Important limits
- This app cannot forcibly remove Google Android Device Policy, Halo management, Device Owner, or organization policies.
- Adding NoobFree as a legacy Device Administrator does not grant Device Owner privileges.
- A reset may trigger zero-touch / automatic enterprise re-enrollment.
- The wallpaper action is still subject to Android's `DISALLOW_SET_WALLPAPER` restriction and OEM behavior.
- This source has not been built with Gradle or tested on the Acer tablet in this environment. GitHub Actions and real-device validation are required.
