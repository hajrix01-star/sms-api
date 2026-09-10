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
data class Company(val id: Long, val name: String)
data class FinancialInstrument(val reference: String, val bankSender: String, val kind: String, val companyName: String?, val role: String?, val events: Int)

/** Local-only event ledger. SQLite handles indexed reads without keeping messages in memory. */
class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "bank_ledger.db", null, 2) {
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
        createDirectoryTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDirectoryTables(db)
    }

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

    /** Indexed, bounded read for the operations screen. Nothing is kept in memory outside the visible list. */
    fun events(
        limit: Int = 200,
        days: Int? = null,
        sender: String? = null,
        category: String? = null,
        search: String? = null,
    ): List<LedgerEvent> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (days != null) {
            filters += "received_at >= ?"
            args += (System.currentTimeMillis() - days * 86_400_000L).toString()
        }
        sender?.let { filters += "sender = ?"; args += it }
        category?.let { filters += "category = ?"; args += it }
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            filters += "(body LIKE ? OR counterparty LIKE ? OR instrument LIKE ?)"
            repeat(3) { args += "%$value%" }
        }
        return readableDatabase.query(
            "events",
            arrayOf("sender", "received_at", "category", "amount", "instrument", "counterparty", "body"),
            filters.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null, null, "received_at DESC", limit.toString(),
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
    }

    fun sendersWithEvents(): List<String> = readableDatabase.rawQuery(
        "SELECT sender FROM events GROUP BY sender ORDER BY MAX(received_at) DESC", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun categoriesWithEvents(): List<String> = readableDatabase.rawQuery(
        "SELECT category FROM events GROUP BY category ORDER BY COUNT(*) DESC", null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

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

    fun companies(): List<Company> = readableDatabase.rawQuery("SELECT id, name FROM companies ORDER BY name", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Company(cursor.getLong(0), cursor.getString(1))) }
    }

    fun addCompany(name: String): Boolean = writableDatabase.insertWithOnConflict(
        "companies", null, ContentValues().apply { put("name", name.trim()) }, SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

    fun instruments(): List<FinancialInstrument> = readableDatabase.rawQuery("""
        SELECT e.instrument, e.sender, MAX(e.body), COUNT(*), c.name, i.role
        FROM events e
        LEFT JOIN instruments i ON i.reference = e.instrument
        LEFT JOIN companies c ON c.id = i.company_id
        WHERE e.instrument IS NOT NULL AND e.instrument != ''
        GROUP BY e.instrument, e.sender
        ORDER BY c.name IS NULL DESC, COUNT(*) DESC
    """.trimIndent(), null).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                val sample = cursor.getString(2).orEmpty()
                add(FinancialInstrument(
                    reference = cursor.getString(0), bankSender = cursor.getString(1),
                    kind = if (sample.contains("بطاقة") || sample.contains("مدى")) "بطاقة" else "حساب",
                    companyName = cursor.getString(4), role = cursor.getString(5), events = cursor.getInt(3),
                ))
            }
        }
    }

    fun assignInstrument(reference: String, sender: String, kind: String, companyId: Long, role: String) {
        writableDatabase.insertWithOnConflict("instruments", null, ContentValues().apply {
            put("reference", reference); put("bank_sender", sender); put("kind", kind); put("company_id", companyId); put("role", role)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun fingerprint(event: LedgerEvent): String = MessageDigest.getInstance("SHA-256")
        .digest("${event.sender}|${event.receivedAt}|${event.body}".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun createDirectoryTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS companies (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS instruments (
            reference TEXT PRIMARY KEY, bank_sender TEXT NOT NULL, kind TEXT NOT NULL,
            company_id INTEGER NOT NULL, role TEXT NOT NULL,
            FOREIGN KEY(company_id) REFERENCES companies(id)
        )""".trimIndent())
    }
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
