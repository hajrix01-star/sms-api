package com.appenza.smsapi

import android.app.job.JobParameters
import android.app.job.JobService
import android.provider.Telephony

/** Reads only the recent 48-hour window, once daily when the user has explicitly enabled it. */
class SmsRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                recoverRecentMessages()
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private fun recoverRecentMessages() {
        val preferences = RelayStore.preferences(this)
        if (!preferences.getBoolean(RelayStore.ENABLED, false) || !preferences.getBoolean(RelayStore.RECOVERY_ENABLED, false)) return

        val senders = RelayStore.senders(this)
        if (senders.isEmpty()) return

        val earliest = System.currentTimeMillis() - RECOVERY_WINDOW_MILLIS
        var imported = 0
        var scanned = 0
        contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(earliest.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext() && scanned < MAX_RECOVERY_SCAN) {
                scanned++
                val sender = cursor.getString(addressColumn).orEmpty()
                val body = cursor.getString(bodyColumn).orEmpty()
                if (
                    RelayStore.matchesSender(sender, senders) &&
                    !RelayStore.isSecurityMessage(body) &&
                    RelayStore.recordReceipt(this, sender, body, cursor.getLong(dateColumn), "تم التقاطها بفحص التعافي اليومي")
                ) {
                    imported++
                }
            }
        }
        preferences.edit()
            .putLong(RelayStore.RECOVERY_LAST_AT, System.currentTimeMillis())
            .putInt(RelayStore.RECOVERY_LAST_IMPORTED, imported)
            .apply()
    }

    private companion object {
        const val RECOVERY_WINDOW_MILLIS = 48L * 60L * 60L * 1000L
        const val MAX_RECOVERY_SCAN = 500
    }
}
