package com.appenza.smsapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-registers only the optional daily recovery after a device restart. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (RelayStore.preferences(context).getBoolean(RelayStore.RECOVERY_ENABLED, false)) {
            RecoveryScheduler.schedule(context)
        }
    }
}
