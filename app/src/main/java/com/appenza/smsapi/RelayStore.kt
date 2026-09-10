package com.appenza.smsapi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Receipt(
    val sender: String,
    val receivedAt: Long,
    val outcome: String,
    val preview: String,
)

object RelayStore {
    const val ENABLED = "enabled"
    const val SENDERS = "senders"
    private const val RECEIPTS = "receipts"
    private const val MAX_RECEIPTS = 200

    fun preferences(context: Context) = context.getSharedPreferences("sms_api", Context.MODE_PRIVATE)

    fun senders(context: Context): List<String> {
        val values = JSONArray(preferences(context).getString(SENDERS, "[]") ?: "[]")
        return (0 until values.length()).map { values.getString(it) }
    }

    fun saveSenders(context: Context, values: List<String>) {
        preferences(context).edit().putString(SENDERS, JSONArray(values).toString()).apply()
    }

    fun recordReceipt(context: Context, sender: String, body: String, receivedAt: Long, outcome: String) {
        val stored = JSONArray()
        stored.put(
            JSONObject()
                .put("sender", sender)
                .put("receivedAt", receivedAt)
                .put("outcome", outcome)
                .put("preview", preview(body)),
        )
        receiptsJson(context).let { current ->
            for (index in 0 until minOf(current.length(), MAX_RECEIPTS - 1)) stored.put(current.getJSONObject(index))
        }
        preferences(context).edit().putString(RECEIPTS, stored.toString()).apply()
    }

    fun receipts(context: Context): List<Receipt> = runCatching {
        val values = receiptsJson(context)
        (0 until values.length()).map { index ->
            values.getJSONObject(index).let {
                Receipt(
                    sender = it.getString("sender"),
                    receivedAt = it.getLong("receivedAt"),
                    outcome = it.getString("outcome"),
                    preview = it.getString("preview"),
                )
            }
        }
    }.getOrDefault(emptyList())

    fun clearReceipts(context: Context) {
        preferences(context).edit().remove(RECEIPTS).apply()
    }

    fun isOtp(value: String): Boolean {
        val text = value.lowercase()
        return listOf("otp", "one-time", "verification code", "رمز التحقق", "رمز التاكيد", "كود التحقق")
            .any(text::contains)
    }

    fun matchesSender(actualSender: String, configuredSenders: List<String>): Boolean {
        val normalizedActual = canonical(actualSender)
        val actualDigits = actualSender.filter(Char::isDigit)
        return configuredSenders.any { configured ->
            val normalizedConfigured = canonical(configured)
            val configuredDigits = configured.filter(Char::isDigit)
            (configured.any(Char::isLetter) && normalizedConfigured.isNotEmpty() && normalizedActual.contains(normalizedConfigured)) ||
                (configuredDigits.length >= 6 && actualDigits.endsWith(configuredDigits))
        }
    }

    private fun canonical(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun receiptsJson(context: Context) = JSONArray(preferences(context).getString(RECEIPTS, "[]") ?: "[]")

    private fun preview(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 120) normalized else "${normalized.take(120)}…"
    }
}
