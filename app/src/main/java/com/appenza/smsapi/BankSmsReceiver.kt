package com.appenza.smsapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class BankSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val pendingResult = goAsync()
        Thread {
            try {
                if (RelayStore.preferences(context).getBoolean(RelayStore.ENABLED, false)) {
                    val allowedSenders = RelayStore.senders(context).map(String::lowercase)
                    Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
                        val sender = message.displayOriginatingAddress ?: return@forEach
                        val body = message.messageBody.orEmpty()
                        if (allowedSenders.any { sender.lowercase().contains(it) }) {
                            val outcome = if (RelayStore.isOtp(body)) {
                                "تم الاستلام ثم استبعادها: رمز تحقق"
                            } else {
                                "تم الاستلام ومطابقة المرسل"
                            }
                            val storedBody = if (RelayStore.isOtp(body)) "محتوى مخفي لحماية رمز التحقق" else body
                            RelayStore.recordReceipt(context, sender, storedBody, message.timestampMillis, outcome)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
