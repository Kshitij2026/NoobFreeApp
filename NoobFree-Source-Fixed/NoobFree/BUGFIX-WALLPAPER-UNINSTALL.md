# Wallpaper and uninstall diagnostic fix (16 Nov 2026)

## Why the earlier behaviour was misleading

The prior Fix 4 implementation called `WallpaperManager.setStream()` but relied on a single stream path and did not provide a robust diagnostic path for every selection/permission/decoding failure. A successful return from the API was also not independently verified on the home screen. The current patch decodes the chosen image, downsamples it to a display-appropriate size, calls `WallpaperManager.setBitmap(..., FLAG_SYSTEM)`, and surfaces the exact exception class/message for security or decode failures. Cancelling the picker now displays an explicit message.

The previous uninstall button used `ACTION_UNINSTALL_PACKAGE` and was rendered only for apps classified as non-system. This meant preinstalled/updated-system apps had no uninstall action at all, and the app did not inspect the result after the Android package installer returned. The current patch uses the standard `ACTION_DELETE` package URI, launches it through `StartActivityForResult`, then checks whether the package is still installed and rescans. For preinstalled apps the UI says “Remove updates”; Android may refuse removal or offer only update removal depending on OEM and package policy.

## Files changed

- `app/src/main/java/com/noobfree/android/ui/AppManagementScreen.kt`

The manifest already includes `REQUEST_DELETE_PACKAGES` and `SET_WALLPAPER` in the merged handoff source, so it is not changed by this patch.

## Device test checklist

1. Install the newly built APK (check the version/build variant actually installed).
2. In NoobFree > App & Restriction Manager, choose a normal JPG/PNG from local storage.
3. Wait for the result card. Expected success: “Home-screen wallpaper applied. Check the home screen.” Expected failure: a message with the Android exception detail.
4. Check the home screen, not only the lock screen. The code sets `FLAG_SYSTEM` only.
5. For uninstall, test with a harmless user-installed app. Tap Uninstall and confirm in Android's package installer. When returning, NoobFree should report whether the package is still installed and rescan.
6. For preinstalled apps, “Remove updates” may be refused by Android or only remove updates. This is controlled by the OS/OEM and device policy.

## Limitations

- This patch has not been compiled or tested on physical hardware in this environment. A successful GitHub Actions build and tablet test are still required.
- A device-owner/admin policy can block wallpaper changes and package removal. NoobFree cannot bypass such policy.
- Android's package installer remains responsible for the final uninstall decision and user confirmation.
