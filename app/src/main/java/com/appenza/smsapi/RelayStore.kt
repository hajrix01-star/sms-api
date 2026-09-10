package com.appenza.smsapi

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class Receipt(
    val sender: String,
    val receivedAt: Long,
    val outcome: String,
    val preview: String,
)

object RelayStore {
    const val ENABLED = "enabled"
    const val SENDERS = "senders"
    const val RECOVERY_ENABLED = "recovery_enabled"
    const val RECOVERY_LAST_AT = "recovery_last_at"
    const val RECOVERY_LAST_IMPORTED = "recovery_last_imported"
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

    /**
     * Stores a small local receipt only once. The original message body is never stored here;
     * only a display preview and a non-reversible fingerprint are retained.
     */
    fun recordReceipt(context: Context, sender: String, body: String, receivedAt: Long, outcome: String): Boolean {
        val key = messageKey(sender, body, receivedAt)
        val current = receiptsJson(context)
        if ((0 until current.length()).any { index -> current.getJSONObject(index).optString("key") == key }) return false
        val stored = JSONArray()
        stored.put(
            JSONObject()
                .put("key", key)
                .put("sender", sender)
                .put("receivedAt", receivedAt)
                .put("outcome", outcome)
                .put("preview", preview(body)),
        )
        for (index in 0 until minOf(current.length(), MAX_RECEIPTS - 1)) stored.put(current.getJSONObject(index))
        preferences(context).edit().putString(RECEIPTS, stored.toString()).apply()
        return true
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

    /** Security and OTP messages are excluded completely from imports, the local ledger and JSON exports. */
    fun isOtp(value: String): Boolean = isSecurityMessage(value)

    fun isSecurityMessage(value: String): Boolean {
        val text = value
            .lowercase(Locale.ROOT)
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')

        return securityPatterns.any { it.containsMatchIn(text) }
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

    private fun messageKey(sender: String, body: String, receivedAt: Long): String {
        val source = "$sender|$receivedAt|$body".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(source).joinToString("") { "%02x".format(it) }
    }

    private fun preview(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 120) normalized else "${normalized.take(120)}…"
    }

    private val securityPatterns = listOf(
        Regex("\\b(?:otp|one[ -]?time(?: password)?|verification code|security code|temporary code|passcode)\\b", RegexOption.IGNORE_CASE),
        Regex("(?:رمز|كود)\\s*(?:موقت|مؤقت|التحقق|التاكد|التاكيد|التفعيل|الدخول|الامان|الامني)"),
        Regex("لا\\s+تشارك.{0,50}(?:رمز|كود)"),
    )
}
