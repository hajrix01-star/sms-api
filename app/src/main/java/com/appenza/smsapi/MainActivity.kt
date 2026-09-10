package com.appenza.smsapi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var chips: ChipGroup
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var history: TextView
    private lateinit var importUntil: EditText
    private val senders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chips = findViewById(R.id.senderChips)
        status = findViewById(R.id.status)
        input = findViewById(R.id.senderInput)
        history = findViewById(R.id.receiptHistory)
        importUntil = findViewById(R.id.importUntil)
        senders.addAll(RelayStore.senders(this))

        findViewById<Button>(R.id.addSender).setOnClickListener {
            val sender = input.text.toString().trim()
            if (sender.isNotEmpty() && senders.none { it.equals(sender, ignoreCase = true) }) {
                senders.add(sender)
                RelayStore.saveSenders(this, senders)
                input.text.clear()
                render()
            }
        }
        findViewById<Button>(R.id.enable).setOnClickListener { if (saveSenders()) requestReceiveSmsPermission() }
        findViewById<Button>(R.id.importHistory).setOnClickListener { if (saveSenders()) requestReadSmsPermission() }
        findViewById<Button>(R.id.disable).setOnClickListener {
            RelayStore.preferences(this).edit().putBoolean(RelayStore.ENABLED, false).apply()
            render()
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            RelayStore.recordReceipt(
                this,
                "SMS_API_TEST",
                "هذه رسالة اختبار محلية. لا يتم إرسال أي بيانات إلى الإنترنت.",
                System.currentTimeMillis(),
                "حدث اختبار محلي",
            )
            render()
        }
        findViewById<Button>(R.id.clearHistory).setOnClickListener {
            RelayStore.clearReceipts(this)
            render()
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) render()
    }

    private fun saveSenders(): Boolean {
        if (senders.isEmpty()) {
            Toast.makeText(this, "أضف مرسلًا واحدًا على الأقل", Toast.LENGTH_LONG).show()
            return false
        }
        RelayStore.saveSenders(this, senders)
        return true
    }

    private fun requestReceiveSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            enable()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), REQUEST_RECEIVE_SMS)
        }
    }

    private fun requestReadSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            importHistory()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val granted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQUEST_RECEIVE_SMS -> if (granted) enable() else Toast.makeText(this, "لن يعمل الاستقبال المباشر بدون إذن SMS", Toast.LENGTH_LONG).show()
            REQUEST_READ_SMS -> if (granted) importHistory() else Toast.makeText(this, "لن يعمل الاستيراد بدون إذن قراءة الرسائل", Toast.LENGTH_LONG).show()
        }
    }

    private fun enable() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.ENABLED, true).apply()
        render()
    }

    private fun importHistory() {
        val until = parseEndDate(importUntil.text.toString().trim())
        if (until == null) {
            Toast.makeText(this, "اكتب تاريخ النهاية بصيغة 2026-09-10", Toast.LENGTH_LONG).show()
            return
        }
        val allowedSenders = RelayStore.senders(this)
        Thread {
            val result = runCatching {
                val matches = mutableListOf<HistoricalMessage>()
                contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.DATE} <= ?",
                    arrayOf(until.toString()),
                    "${Telephony.Sms.DATE} DESC",
                )?.use { cursor ->
                    val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    while (cursor.moveToNext() && matches.size < MAX_IMPORT) {
                        val sender = cursor.getString(addressColumn).orEmpty()
                        val body = cursor.getString(bodyColumn).orEmpty()
                        val receivedAt = cursor.getLong(dateColumn)
                        if (RelayStore.matchesSender(sender, allowedSenders)) {
                            matches += HistoricalMessage(sender, body, receivedAt)
                        }
                    }
                }
                matches.asReversed().forEach { message ->
                    val outcome = if (RelayStore.isOtp(message.body)) "تم استيراد السجل ثم استبعاده: رمز تحقق" else "تم استيراده من سجل الرسائل"
                    val storedBody = if (RelayStore.isOtp(message.body)) "محتوى مخفي لحماية رمز التحقق" else message.body
                    RelayStore.recordReceipt(this, message.sender, storedBody, message.receivedAt, outcome)
                }
                matches.size
            }
            runOnUiThread {
                result.onSuccess { imported ->
                    render()
                    Toast.makeText(this, "تم استيراد $imported رسالة مطابقة", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "تعذر استيراد الرسائل: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun parseEndDate(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            Calendar.getInstance().apply {
                time = parser.parse(value) ?: throw IllegalArgumentException()
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        }.getOrNull()
    }

    private fun render() {
        chips.removeAllViews()
        senders.forEach { sender ->
            chips.addView(Chip(this).apply {
                text = sender
                isCloseIconVisible = true
                setOnCloseIconClickListener {
                    senders.remove(sender)
                    RelayStore.saveSenders(this@MainActivity, senders)
                    render()
                }
            })
        }
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val isEnabled = RelayStore.preferences(this).getBoolean(RelayStore.ENABLED, false)
        status.text = when {
            isEnabled && hasPermission -> "الحالة: استقبال الرسائل مفعّل"
            hasPermission -> "الحالة: الإذن مسموح، الاستقبال غير مفعّل"
            else -> "الحالة: إذن SMS مطلوب للاختبار"
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar"))
        history.text = RelayStore.receipts(this).joinToString("\n\n") { receipt ->
            "${receipt.sender}  •  ${formatter.format(Date(receipt.receivedAt))}\n${receipt.outcome}\n${receipt.preview}"
        }.ifBlank { "لا توجد رسائل مستلمة بعد. أضف الاسم المرسل للبنك أو آخر 6–8 أرقام من الرقم الحقيقي." }
    }

    private companion object {
        const val REQUEST_RECEIVE_SMS = 8
        const val REQUEST_READ_SMS = 9
        const val MAX_IMPORT = 500
    }

    private data class HistoricalMessage(val sender: String, val body: String, val receivedAt: Long)
}
