package com.appenza.smsapi

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest

data class LedgerEvent(
    val sender: String,
    val receivedAt: Long,
    val category: String,
    val amount: Double?,
    val instrument: String?,
    val counterparty: String?,
    val body: String,
)

data class LedgerSummary(val total: Int, val incoming: Int, val outgoing: Int, val fees: Int, val unknown: Int)

/** Local-only event ledger. SQLite handles indexed reads without keeping messages in memory. */
class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "bank_ledger.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fingerprint TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                received_at INTEGER NOT NULL,
                category TEXT NOT NULL,
                amount REAL,
                instrument TEXT,
                counterparty TEXT,
                body TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX events_received_at_idx ON events(received_at DESC)")
        db.execSQL("CREATE INDEX events_category_idx ON events(category)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(event: LedgerEvent): Boolean {
        val values = ContentValues().apply {
            put("fingerprint", fingerprint(event))
            put("sender", event.sender)
            put("received_at", event.receivedAt)
            put("category", event.category)
            event.amount?.let { put("amount", it) }
            event.instrument?.let { put("instrument", it) }
            event.counterparty?.let { put("counterparty", it) }
            put("body", event.body)
        }
        return writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun latest(limit: Int = 100): List<LedgerEvent> = readableDatabase.query(
        "events",
        arrayOf("sender", "received_at", "category", "amount", "instrument", "counterparty", "body"),
        null, null, null, null, "received_at DESC", limit.toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                LedgerEvent(
                    sender = cursor.getString(0), receivedAt = cursor.getLong(1), category = cursor.getString(2),
                    amount = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    instrument = cursor.getString(4), counterparty = cursor.getString(5), body = cursor.getString(6),
                )
            )
        }
    }

    fun summary(): LedgerSummary {
        val counts = mutableMapOf<String, Int>()
        readableDatabase.rawQuery("SELECT category, COUNT(*) FROM events GROUP BY category", null).use { cursor ->
            while (cursor.moveToNext()) counts[cursor.getString(0)] = cursor.getInt(1)
        }
        return LedgerSummary(
            total = counts.values.sum(),
            incoming = (counts["إيداع / وارد"] ?: 0) + (counts["تسوية POS"] ?: 0),
            outgoing = (counts["شراء"] ?: 0) + (counts["تحويل صادر"] ?: 0) + (counts["سحب صراف"] ?: 0),
            fees = counts["رسوم بنكية"] ?: 0,
            unknown = counts["غير مصنف"] ?: 0,
        )
    }

    fun clear() = writableDatabase.delete("events", null, null)

    private fun fingerprint(event: LedgerEvent): String = MessageDigest.getInstance("SHA-256")
        .digest("${event.sender}|${event.receivedAt}|${event.body}".toByteArray())
        .joinToString("") { "%02x".format(it) }
}

object BankEventParser {
    fun parse(sender: String, body: String, receivedAt: Long): LedgerEvent {
        val category = when {
            body.containsAny("تسوية نقطة البيع") -> "تسوية POS"
            body.containsAny("خصم رسوم", "رسوم بنكية") -> "رسوم بنكية"
            body.containsAny("سداد", "sadad") -> "سداد"
            body.containsAny("سحب صراف", "Withdrawal:ATM") -> "سحب صراف"
            body.containsAny("داخلي", "Internal") -> "تحويل داخلي"
            body.containsAny("حوالة صادرة", "تم سحب") && body.containsAny("تحويل", "Transfer") -> "تحويل صادر"
            body.containsAny("تم ايداع", "تم إيداع", "حوالة واردة", "Credit Transfer", "إيداع في حساب") -> "إيداع / وارد"
            body.containsAny("شراء-POS", "POS Purchase", "شراء محلي", "شراء انترنت", "Online Purchase") -> "شراء"
            body.containsAny("تم رفض", "Declined", "Insufficient funds") -> "عملية مرفوضة"
            else -> "غير مصنف"
        }
        return LedgerEvent(sender, receivedAt, category, amount(body), instrument(body), counterparty(body), body)
    }

    private fun amount(body: String): Double? {
        val patterns = listOf(
            Regex("(?:Amount|المبلغ|مبلغ)\\s*[:：]?\\s*(?:SAR|SR|ريال)?\\s*([0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("(?:بـ|ب)\\s*([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:SAR|SR|ريال)", RegexOption.IGNORE_CASE),
            Regex("([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:SAR|SR|ريال)", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
    }

    private fun instrument(body: String): String? = Regex("(?:البطاقة|بطاقه|حسابك|حساب|مدى-أثير|By)\\s*[:：]?\\s*([0-9*]{4,})", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.getOrNull(1)

    private fun counterparty(body: String): String? = Regex("(?:At|من)\\s*[:：]?\\s*([^\\n]{3,60})", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.getOrNull(1)?.trim()

    private fun String.containsAny(vararg values: String) = values.any { contains(it, ignoreCase = true) }
}
