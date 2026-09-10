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
    val companyName: String? = null,
    val custodyType: String? = null,
)

data class LedgerSummary(val total: Int, val incoming: Int, val outgoing: Int, val fees: Int, val unknown: Int)
data class Company(val id: Long, val name: String)
data class FinancialInstrument(val reference: String, val bankSender: String, val kind: String, val companyName: String?, val role: String?, val parentReference: String?, val events: Int)
data class CustodySummary(
    val funded: Double,
    val cardPurchases: Double,
    val cashWithdrawals: Double,
    val transfersOut: Double,
    val bankWithOsama: Double,
    val cashWithOsama: Double,
    val totalHeldByOsama: Double,
    val fundingByCompany: Map<String, Double>,
)

/** Local-only event ledger. SQLite handles indexed reads without keeping messages in memory. */
class LedgerDatabase(context: Context) : SQLiteOpenHelper(context, "bank_ledger.db", null, 11) {
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
                body TEXT NOT NULL,
                company_name TEXT,
                custody_type TEXT
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX events_received_at_idx ON events(received_at DESC)")
        db.execSQL("CREATE INDEX events_category_idx ON events(category)")
        db.execSQL("CREATE INDEX events_company_idx ON events(company_name)")
        db.execSQL("CREATE INDEX events_custody_idx ON events(custody_type)")
        createDirectoryTables(db)
        seedCompanyDirectory(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDirectoryTables(db)
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE events ADD COLUMN company_name TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS events_company_idx ON events(company_name)")
            backfillCompanyNames(db)
        }
        if (oldVersion < 4) db.execSQL("ALTER TABLE instruments ADD COLUMN parent_reference TEXT")
        if (oldVersion < 5) db.execSQL("UPDATE events SET instrument = TRIM(instrument, '*') WHERE instrument IS NOT NULL")
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE events ADD COLUMN custody_type TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS events_custody_idx ON events(custody_type)")
            backfillCustodyTypes(db)
        }
        if (oldVersion < 7) {
            // Old versions could retain OTP or beneficiary-setup notices in the local ledger.
            // They are security/administrative messages, not financial operations.
            removeSecurityEvents(db)
            backfillCompanyNames(db)
        }
        if (oldVersion < 8) {
            // Re-evaluate existing messages using the RTL-safe masked-account rules.
            backfillCompanyNames(db)
        }
        if (oldVersion < 9) {
            // SNB masks the Doha account as 078*409 in point-of-sale settlements.
            backfillCompanyNames(db)
        }
        if (oldVersion < 10) {
            // Link historic SNB settlement messages for Keeta, HungerStation and Jahez to Muallam.
            backfillCompanyNames(db)
        }
        if (oldVersion < 11) {
            // Remove rejected/non-financial notices and re-parse historic messages using the expanded bank rules.
            removeSecurityEvents(db)
            backfillCompanyNames(db)
            backfillParsedEvents(db)
        }
        seedCompanyDirectory(db)
        linkUnassignedEventsToDirectory(db)
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
            put("company_name", event.companyName ?: CompanyRules.inferCompany(event.sender, event.body))
            put("custody_type", event.custodyType ?: BankEventParser.custodyType(event.sender, event.body, event.category))
        }
        return writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    /** Re-applies the current local parser and account/company rules without reading SMS again. */
    fun reanalyzeAll(): Int {
        val db = writableDatabase
        var scanned = 0
        db.beginTransaction()
        try {
            db.query(
                "events",
                arrayOf("id", "sender", "received_at", "body", "company_name", "custody_type"),
                null, null, null, null, null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    scanned++
                    val id = cursor.getLong(0)
                    val sender = cursor.getString(1)
                    val receivedAt = cursor.getLong(2)
                    val body = cursor.getString(3)
                    val currentCompany = cursor.getString(4)
                    val currentCustody = cursor.getString(5)
                    val parsed = BankEventParser.parse(sender, body, receivedAt)
                    val inferredCompany = CompanyRules.inferCompany(sender, body)
                    val values = ContentValues().apply {
                        put("category", parsed.category)
                        put("amount", parsed.amount)
                        put("instrument", parsed.instrument)
                        put("counterparty", parsed.counterparty)
                        // Preserve a manual/company-directory association when no automatic rule matches.
                        put("company_name", inferredCompany ?: currentCompany)
                        put("custody_type", parsed.custodyType ?: currentCustody)
                    }
                    db.update("events", values, "id = ?", arrayOf(id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return scanned
    }

    fun latest(limit: Int = 100): List<LedgerEvent> = readableDatabase.query(
        "events",
        arrayOf("sender", "received_at", "category", "amount", "instrument", "counterparty", "body", "company_name", "custody_type"),
        null, null, null, null, "received_at DESC", limit.toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(
                LedgerEvent(
                    sender = cursor.getString(0), receivedAt = cursor.getLong(1), category = cursor.getString(2),
                    amount = if (cursor.isNull(3)) null else cursor.getDouble(3),
                    instrument = cursor.getString(4), counterparty = cursor.getString(5), body = cursor.getString(6), companyName = cursor.getString(7), custodyType = cursor.getString(8),
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
        companyName: String? = null,
        search: String? = null,
        reviewOnly: Boolean = false,
    ): List<LedgerEvent> {
        val filters = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (days != null) {
            filters += "received_at >= ?"
            args += (System.currentTimeMillis() - days * 86_400_000L).toString()
        }
        sender?.let { filters += "sender = ?"; args += it }
        category?.let { filters += "category = ?"; args += it }
        companyName?.let { filters += "company_name = ?"; args += it }
        if (reviewOnly) {
            filters += "(category = ? OR company_name IS NULL)"
            args += "غير مصنف"
        }
        search?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
            filters += "(body LIKE ? OR counterparty LIKE ? OR instrument LIKE ?)"
            repeat(3) { args += "%$value%" }
        }
        return readableDatabase.query(
            "events",
            arrayOf("sender", "received_at", "category", "amount", "instrument", "counterparty", "body", "company_name", "custody_type"),
            filters.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.takeIf { it.isNotEmpty() }?.toTypedArray(),
            null, null, "received_at DESC", limit.toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(
                    LedgerEvent(
                        sender = cursor.getString(0), receivedAt = cursor.getLong(1), category = cursor.getString(2),
                        amount = if (cursor.isNull(3)) null else cursor.getDouble(3),
                        instrument = cursor.getString(4), counterparty = cursor.getString(5), body = cursor.getString(6), companyName = cursor.getString(7), custodyType = cursor.getString(8),
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

    /** A review item is either an unrecognised financial pattern or a message without a company/account link. */
    fun reviewCount(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM events WHERE category = ? OR company_name IS NULL",
        arrayOf("غير مصنف"),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

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

    /** SQL aggregation keeps the shared-custody statement lightweight even with a large ledger. */
    fun osamaCustodySummary(): CustodySummary {
        val amounts = mutableMapOf<String, Double>()
        readableDatabase.rawQuery(
            "SELECT custody_type, COALESCE(SUM(amount), 0) FROM events WHERE custody_type IS NOT NULL GROUP BY custody_type",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) amounts[cursor.getString(0)] = cursor.getDouble(1)
        }
        val fundingByCompany = linkedMapOf<String, Double>()
        readableDatabase.rawQuery("""
            SELECT COALESCE(company_name, 'مصدر غير مربوط'), COALESCE(SUM(amount), 0)
            FROM events WHERE custody_type = ? GROUP BY company_name ORDER BY SUM(amount) DESC
        """.trimIndent(), arrayOf(CustodyType.FUNDING)).use { cursor ->
            while (cursor.moveToNext()) fundingByCompany[cursor.getString(0)] = cursor.getDouble(1)
        }
        val funded = amounts[CustodyType.FUNDING] ?: 0.0
        val cardPurchases = amounts[CustodyType.CARD_PURCHASE] ?: 0.0
        val cashWithdrawals = amounts[CustodyType.CASH_WITHDRAWAL] ?: 0.0
        val transfersOut = amounts[CustodyType.TRANSFER_OUT] ?: 0.0
        val bankWithOsama = funded - cardPurchases - cashWithdrawals - transfersOut
        val cashWithOsama = cashWithdrawals
        return CustodySummary(
            funded = funded,
            cardPurchases = cardPurchases,
            cashWithdrawals = cashWithdrawals,
            transfersOut = transfersOut,
            bankWithOsama = bankWithOsama,
            cashWithOsama = cashWithOsama,
            totalHeldByOsama = bankWithOsama + cashWithOsama,
            fundingByCompany = fundingByCompany,
        )
    }

    fun clear() = writableDatabase.delete("events", null, null)

    fun companies(): List<Company> = readableDatabase.rawQuery("SELECT id, name FROM companies ORDER BY name", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(Company(cursor.getLong(0), cursor.getString(1))) }
    }

    fun addCompany(name: String): Boolean = writableDatabase.insertWithOnConflict(
        "companies", null, ContentValues().apply { put("name", name.trim()) }, SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

    fun instruments(): List<FinancialInstrument> {
        val linked = readableDatabase.rawQuery("""
            SELECT i.reference, i.bank_sender, i.kind, c.name, i.role, i.parent_reference, COUNT(e.id)
            FROM instruments i
            JOIN companies c ON c.id = i.company_id
            LEFT JOIN events e ON e.instrument = i.reference
            GROUP BY i.reference, i.bank_sender, i.kind, c.name, i.role, i.parent_reference
            ORDER BY c.name, i.reference
        """.trimIndent(), null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(FinancialInstrument(
                    reference = cursor.getString(0), bankSender = cursor.getString(1), kind = cursor.getString(2),
                    companyName = cursor.getString(3), role = cursor.getString(4), parentReference = cursor.getString(5), events = cursor.getInt(6),
                ))
            }
        }
        val unlinked = readableDatabase.rawQuery("""
            SELECT e.instrument, e.sender, MAX(e.body), COUNT(*)
            FROM events e
            LEFT JOIN instruments i ON i.reference = e.instrument
            WHERE e.instrument IS NOT NULL AND e.instrument != '' AND i.reference IS NULL
            GROUP BY e.instrument, e.sender
            ORDER BY COUNT(*) DESC
        """.trimIndent(), null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val sample = cursor.getString(2).orEmpty()
                    add(FinancialInstrument(
                        reference = cursor.getString(0), bankSender = cursor.getString(1),
                        kind = if (sample.contains("بطاقة") || sample.contains("مدى")) "بطاقة" else "حساب",
                        companyName = null, role = null, parentReference = null, events = cursor.getInt(3),
                    ))
                }
            }
        }
        return linked + unlinked
    }

    fun assignInstrument(reference: String, sender: String, kind: String, companyId: Long, role: String, parentReference: String? = null) {
        val preservedParent = parentReference ?: readableDatabase.query(
            "instruments", arrayOf("parent_reference"), "reference = ?", arrayOf(reference), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        writableDatabase.insertWithOnConflict("instruments", null, ContentValues().apply {
            put("reference", reference); put("bank_sender", sender); put("kind", kind); put("company_id", companyId); put("role", role); preservedParent?.let { put("parent_reference", it) }
        }, SQLiteDatabase.CONFLICT_REPLACE)
        // Keep a confirmed source classification intact (for example ARZ → Osama),
        // but complete historic messages that had no company when this reference is linked.
        writableDatabase.execSQL(
            "UPDATE events SET company_name = (SELECT name FROM companies WHERE id = ?) WHERE instrument = ? AND company_name IS NULL",
            arrayOf(companyId, reference),
        )
    }

    private fun fingerprint(event: LedgerEvent): String = MessageDigest.getInstance("SHA-256")
        .digest("${event.sender}|${event.receivedAt}|${event.body}".toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun createDirectoryTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS companies (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS instruments (
            reference TEXT PRIMARY KEY, bank_sender TEXT NOT NULL, kind TEXT NOT NULL,
            company_id INTEGER NOT NULL, role TEXT NOT NULL, parent_reference TEXT,
            FOREIGN KEY(company_id) REFERENCES companies(id)
        )""".trimIndent())
    }

    private fun seedCompanyDirectory(db: SQLiteDatabase) {
        CompanyRules.defaultCompanies.forEach { name ->
            db.insertWithOnConflict("companies", null, ContentValues().apply { put("name", name) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
        CompanyRules.proposals.forEach { proposal ->
            val companyId = db.rawQuery("SELECT id FROM companies WHERE name = ?", arrayOf(proposal.companyName)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return@forEach
            db.insertWithOnConflict("instruments", null, ContentValues().apply {
                put("reference", proposal.reference)
                put("bank_sender", proposal.bankSender)
                put("kind", proposal.kind)
                put("company_id", companyId)
                put("role", proposal.role)
                proposal.parentReference?.let { put("parent_reference", it) }
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun linkUnassignedEventsToDirectory(db: SQLiteDatabase) {
        db.execSQL("""
            UPDATE events
            SET company_name = (
                SELECT c.name FROM instruments i
                JOIN companies c ON c.id = i.company_id
                WHERE i.reference = events.instrument
                LIMIT 1
            )
            WHERE company_name IS NULL
              AND instrument IS NOT NULL
              AND EXISTS (SELECT 1 FROM instruments i WHERE i.reference = events.instrument)
        """.trimIndent())
    }

    private fun backfillCompanyNames(db: SQLiteDatabase) {
        db.query("events", arrayOf("id", "sender", "body"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val company = CompanyRules.inferCompany(cursor.getString(1), cursor.getString(2)) ?: continue
                db.update("events", ContentValues().apply { put("company_name", company) }, "id = ?", arrayOf(cursor.getLong(0).toString()))
            }
        }
    }

    private fun backfillCustodyTypes(db: SQLiteDatabase) {
        db.query("events", arrayOf("id", "sender", "body", "category"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val custodyType = BankEventParser.custodyType(cursor.getString(1), cursor.getString(2), cursor.getString(3)) ?: continue
                db.update("events", ContentValues().apply { put("custody_type", custodyType) }, "id = ?", arrayOf(cursor.getLong(0).toString()))
            }
        }
    }

    private fun backfillParsedEvents(db: SQLiteDatabase) {
        db.query(
            "events",
            arrayOf("id", "sender", "received_at", "body", "company_name", "custody_type"),
            null, null, null, null, null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val sender = cursor.getString(1)
                val parsed = BankEventParser.parse(sender, cursor.getString(3), cursor.getLong(2))
                val company = CompanyRules.inferCompany(sender, cursor.getString(3)) ?: cursor.getString(4)
                db.update("events", ContentValues().apply {
                    put("category", parsed.category)
                    put("amount", parsed.amount)
                    put("instrument", parsed.instrument)
                    put("counterparty", parsed.counterparty)
                    put("company_name", company)
                    put("custody_type", parsed.custodyType ?: cursor.getString(5))
                }, "id = ?", arrayOf(id.toString()))
            }
        }
    }

    private fun removeSecurityEvents(db: SQLiteDatabase) {
        val ids = mutableListOf<String>()
        db.query("events", arrayOf("id", "body"), null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                if (RelayStore.isSecurityMessage(cursor.getString(1))) ids += cursor.getLong(0).toString()
            }
        }
        if (ids.isNotEmpty()) {
            db.delete("events", "id IN (${ids.joinToString(",")})", null)
        }
    }
}

object CustodyType {
    const val FUNDING = "تمويل عهدة أسامة"
    const val CARD_PURCHASE = "شراء بطاقة أسامة"
    const val CASH_WITHDRAWAL = "تحويل إلى كاش مع أسامة"
    const val TRANSFER_OUT = "حوالة خارجة من عهدة أسامة"
}

object BankEventParser {
    fun parse(sender: String, body: String, receivedAt: Long): LedgerEvent {
        val category = when {
            body.containsAny("تسوية نقطة البيع") -> "تسوية POS"
            body.containsAny("خصم رسوم", "رسوم بنكية") -> "رسوم بنكية"
            body.containsAny("سداد", "sadad") -> "سداد"
            body.containsAny("سحب صراف", "سحب نقدي -", "Withdrawal:ATM") -> "سحب صراف"
            body.containsAny("داخلي", "Internal") -> "تحويل داخلي"
            body.containsAny("تم رفض", "Declined", "Insufficient funds") -> "عملية مرفوضة"
            body.containsAny("حوالة صادرة محلية", "حوالة فورية صادرة") ||
                (body.containsAny("حوالة صادرة", "تم سحب") && body.containsAny("تحويل", "Transfer")) -> "تحويل صادر"
            body.containsAny("تم ايداع", "تم إيداع", "حوالة واردة", "حوالة محلية واردة", "Credit Transfer", "إيداع في حساب") -> "إيداع / وارد"
            body.containsAny("شراء-POS", "POS Purchase", "شراء محلي", "شراء انترنت", "شراء إنترنت", "Online Purchase") -> "شراء"
            else -> "غير مصنف"
        }
        return LedgerEvent(sender, receivedAt, category, amount(body), instrument(body), counterparty(body), body, custodyType = custodyType(sender, body, category))
    }

    fun custodyType(sender: String, body: String, category: String): String? = when {
        sender.equals("AlRajhiBank", ignoreCase = true) && targetIs(body, "1994") -> CustodyType.FUNDING
        sender.equals("AlRajhiBank", ignoreCase = true) && usesCard(body, "0187") && category == "سحب صراف" -> CustodyType.CASH_WITHDRAWAL
        sender.equals("AlRajhiBank", ignoreCase = true) && usesCard(body, "0187") && category == "شراء" -> CustodyType.CARD_PURCHASE
        sender.equals("AlRajhiBank", ignoreCase = true) && sourceIs(body, "1994") && category in setOf("تحويل داخلي", "تحويل صادر") -> CustodyType.TRANSFER_OUT
        else -> null
    }

    private fun amount(body: String): Double? {
        val patterns = listOf(
            Regex("(?:Amount|المبلغ|مبلغ)\\s*[:：]?\\s*(?:SAR|SR|ريال)?\\s*([0-9]+(?:[.,][0-9]{1,2})?)", RegexOption.IGNORE_CASE),
            Regex("(?:بـ|ب)\\s*([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:SAR|SR|ريال)", RegexOption.IGNORE_CASE),
            Regex("([0-9]+(?:[.,][0-9]{1,2})?)\\s*(?:SAR|SR|ريال)", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() }
    }

    private fun instrument(body: String): String? = listOf(
        Regex("(?:البطاقة|بطاقه|إئتمانية|ائتمانية|مدى-أثير|By)\\s*[:：;]?\\s*([0-9*]{4,})", RegexOption.IGNORE_CASE),
        Regex("(?:حسابك|من)\\s*[:：]?\\s*([0-9*]{4,})", RegexOption.IGNORE_CASE),
        Regex("From\\s*:\\s*([0-9*]{4,})", RegexOption.IGNORE_CASE),
        Regex("To\\s*:\\s*([0-9*]{4,})", RegexOption.IGNORE_CASE),
    ).firstNotNullOfOrNull { it.find(body)?.groupValues?.getOrNull(1)?.trim('*') }

    private fun counterparty(body: String): String? = Regex("(?:At|من)\\s*[:：]?\\s*([^\\n]{3,60})", RegexOption.IGNORE_CASE)
        .find(body)?.groupValues?.getOrNull(1)?.trim()

    private fun targetIs(body: String, reference: String) = Regex("(?:To|إلى|ل)\\s*[:：]?\\s*\\*?$reference\\*?", RegexOption.IGNORE_CASE).containsMatchIn(body)
    private fun sourceIs(body: String, reference: String) = Regex("(?:From|من)\\s*[:：]?\\s*\\*?$reference\\*?", RegexOption.IGNORE_CASE).containsMatchIn(body)
    private fun usesCard(body: String, reference: String) = Regex("(?:By|مدى|البطاقة|بطاقه)[^0-9]{0,12}\\*?$reference\\*?", RegexOption.IGNORE_CASE).containsMatchIn(body)

    private fun String.containsAny(vararg values: String) = values.any { contains(it, ignoreCase = true) }
}
