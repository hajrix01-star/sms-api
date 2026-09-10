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
                if (RelayStore.p(context).getBoolean(RelayStore.ENABLED, false)) {
                    val allowedSenders = RelayStore.senders(context).map(String::lowercase)
                    Telephony.Sms.Intents.getMessagesFromIntent(intent).forEach { message ->
                        val sender = message.displayOriginatingAddress ?: return@forEach
                        val body = message.messageBody.orEmpty()
                        if (allowedSenders.any { sender.lowercase().contains(it) } && !RelayStore.isOtp(body)) {
                            RelayWorker.enqueue(context, sender, body, message.timestampMillis)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
