package com.appenza.smsapi

import android.content.Context
import org.json.JSONArray
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
    const val DARK_MODE = "dark_mode"

    fun preferences(context: Context) = context.getSharedPreferences("sms_api", Context.MODE_PRIVATE)

    fun senders(context: Context): List<String> {
        val values = JSONArray(preferences(context).getString(SENDERS, "[]") ?: "[]")
        return (0 until values.length()).map { values.getString(it) }
    }

    fun saveSenders(context: Context, values: List<String>) {
        preferences(context).edit().putString(SENDERS, JSONArray(values).toString()).apply()
    }

    fun recordReceipt(context: Context, sender: String, body: String, receivedAt: Long, outcome: String): Boolean {
        return LedgerDatabase(context).insert(BankEventParser.parse(sender, body, receivedAt))
    }

    fun receipts(context: Context): List<Receipt> = LedgerDatabase(context).latest().map {
        Receipt(it.sender, it.receivedAt, it.category, preview(it.body))
    }

    fun summary(context: Context) = LedgerDatabase(context).summary()

    fun clearReceipts(context: Context) {
        LedgerDatabase(context).clear()
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

    private fun preview(value: String): String {
        val normalized = value.replace(Regex("\\s+"), " ").trim()
        return if (normalized.length <= 120) normalized else "${normalized.take(120)}…"
    }

    private val securityPatterns = listOf(
        Regex("\\b(?:otp|one[ -]?time(?: password)?|verification code|security code|temporary code|passcode)\\b", RegexOption.IGNORE_CASE),
        Regex("(?:رمز|كود)\\s*(?:موقت|مؤقت|التحقق|التاكد|التاكيد|التفعيل|الدخول|الامان|الامني)"),
        Regex("لا\\s+تشارك.{0,50}(?:رمز|كود)"),
        Regex("(?:اضافة|إضافة)\\s+(?:المستفيد|مستفيد)", RegexOption.IGNORE_CASE),
        Regex("(?:add|added)\\s+(?:a\\s+)?beneficiary", RegexOption.IGNORE_CASE),
    )
}
