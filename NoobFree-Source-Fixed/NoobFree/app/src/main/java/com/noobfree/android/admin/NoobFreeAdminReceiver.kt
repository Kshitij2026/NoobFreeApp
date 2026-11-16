package com.noobfree.android.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Optional legacy Device Administrator for user-approved local security actions
 * (immediate screen lock and authorized wipeData requests where permitted by the OS).
 *
 * Important platform boundary:
 * Activating this receiver grants ordinary Device Administrator status only.
 * It does NOT grant Device Owner or Profile Owner privileges, and cannot forcibly
 * deactivate or uninstall another administrator's Device Owner/Profile Owner role.
 */
class NoobFreeAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun getComponentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, NoobFreeAdminReceiver::class.java)
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Deactivating NoobFree Device Administrator will disable in-app screen lock and wipe-data requests, and allow NoobFree to be uninstalled normally."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }
}
