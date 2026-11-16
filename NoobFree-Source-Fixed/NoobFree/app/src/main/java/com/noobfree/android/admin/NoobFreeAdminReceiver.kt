package com.noobfree.android.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Optional legacy Device Administrator for user-approved local security actions.
 * This does not grant Device Owner privileges or permission to remove other admins.
 */
class NoobFreeAdminReceiver : DeviceAdminReceiver()
