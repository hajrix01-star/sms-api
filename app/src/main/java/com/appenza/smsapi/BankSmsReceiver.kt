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
                    val allowedSenders = RelayStore.senders(context)
                    Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
                        val sender = message.displayOriginatingAddress ?: return@forEach
                        val body = message.messageBody.orEmpty()
                        if (RelayStore.matchesSender(sender, allowedSenders) && !RelayStore.isSecurityMessage(body)) {
                            RelayStore.recordReceipt(context, sender, body, message.timestampMillis, "تم الاستلام ومطابقة المرسل")
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
