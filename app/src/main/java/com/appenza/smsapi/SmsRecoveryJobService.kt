package com.appenza.smsapi

import android.app.job.JobParameters
import android.app.job.JobService
import android.provider.Telephony

/** Reads a rolling seven-day window every six hours when the user has explicitly enabled it. */
class SmsRecoveryJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            try {
                SmsRecovery.recover(this, automatic = true)
            } finally {
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

}

data class RecoveryResult(val scanned: Int, val imported: Int)

object SmsRecovery {
    fun recover(context: android.content.Context, automatic: Boolean): RecoveryResult {
        val preferences = RelayStore.preferences(context)
        if (!preferences.getBoolean(RelayStore.ENABLED, false) || (automatic && !preferences.getBoolean(RelayStore.RECOVERY_ENABLED, false))) return RecoveryResult(0, 0)

        val senders = RelayStore.senders(context)
        if (senders.isEmpty()) return RecoveryResult(0, 0)

        val earliest = System.currentTimeMillis() - RECOVERY_WINDOW_MILLIS
        var imported = 0
        var scanned = 0
        context.contentResolver.query(
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
                    RelayStore.recordReceipt(context, sender, body, cursor.getLong(dateColumn), if (automatic) "تم التقاطها بفحص التعافي" else "تم التقاطها بالفحص اليدوي")
                ) {
                    imported++
                }
            }
        }
        if (automatic) preferences.edit()
            .putLong(RelayStore.RECOVERY_LAST_AT, System.currentTimeMillis())
            .putInt(RelayStore.RECOVERY_LAST_IMPORTED, imported).apply()
        return RecoveryResult(scanned, imported)
    }

    private const val RECOVERY_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val MAX_RECOVERY_SCAN = 5_000
}
