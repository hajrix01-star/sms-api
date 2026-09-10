package com.appenza.smsapi

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Intent
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
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var chips: ChipGroup
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var history: TextView
    private lateinit var importUntil: EditText
    private lateinit var importResult: TextView
    private val senders = mutableListOf<String>()
    private var readAction = ReadAction.IMPORT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chips = findViewById(R.id.senderChips)
        status = findViewById(R.id.status)
        input = findViewById(R.id.senderInput)
        history = findViewById(R.id.receiptHistory)
        importUntil = findViewById(R.id.importUntil)
        importResult = findViewById(R.id.importResult)
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
        findViewById<Button>(R.id.importHistory).setOnClickListener { if (saveSenders()) requestReadSmsPermission(ReadAction.IMPORT) }
        findViewById<Button>(R.id.exportTrainingSample).setOnClickListener { if (saveSenders()) requestReadSmsPermission(ReadAction.EXPORT_SAMPLE) }
        findViewById<Button>(R.id.pickSenders).setOnClickListener { requestReadSmsPermission(ReadAction.PICK_SENDERS) }
        importUntil.setOnClickListener { showDatePicker() }
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

    private fun requestReadSmsPermission(action: ReadAction) {
        readAction = action
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            runReadAction()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        val granted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQUEST_RECEIVE_SMS -> if (granted) enable() else Toast.makeText(this, "لن يعمل الاستقبال المباشر بدون إذن SMS", Toast.LENGTH_LONG).show()
            REQUEST_READ_SMS -> if (granted) runReadAction() else Toast.makeText(this, "لن يعمل هذا الإجراء بدون إذن قراءة الرسائل", Toast.LENGTH_LONG).show()
        }
    }

    private fun runReadAction() {
        when (readAction) {
            ReadAction.IMPORT -> importHistory()
            ReadAction.PICK_SENDERS -> showSenderPicker()
            ReadAction.EXPORT_SAMPLE -> exportTrainingSample()
        }
    }

    private fun enable() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.ENABLED, true).apply()
        render()
    }

    private fun importHistory() {
        val until = parseEndDate(importUntil.text.toString().trim())
        if (until == null) {
            Toast.makeText(this, "اختر تاريخ النهاية من التقويم", Toast.LENGTH_LONG).show()
            return
        }
        val allowedSenders = RelayStore.senders(this)
        Thread {
            val result = runCatching {
                val matches = mutableListOf<HistoricalMessage>()
                val visibleSenders = linkedSetOf<String>()
                var scanned = 0
                val cursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    "${Telephony.Sms.DATE} <= ?",
                    arrayOf(until.toString()),
                    "${Telephony.Sms.DATE} DESC",
                ) ?: throw IllegalStateException("تعذر فتح سجل الرسائل")
                cursor.use {
                    val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    while (cursor.moveToNext() && scanned < MAX_SCAN) {
                        scanned++
                        val sender = cursor.getString(addressColumn).orEmpty()
                        val body = cursor.getString(bodyColumn).orEmpty()
                        val receivedAt = cursor.getLong(dateColumn)
                        if (sender.isNotBlank() && visibleSenders.size < MAX_VISIBLE_SENDERS) visibleSenders += sender
                        if (matches.size < MAX_IMPORT && RelayStore.matchesSender(sender, allowedSenders)) {
                            matches += HistoricalMessage(sender, body, receivedAt)
                        }
                    }
                }
                matches.asReversed().forEach { message ->
                    val outcome = if (RelayStore.isOtp(message.body)) "تم استيراد السجل ثم استبعاده: رمز تحقق" else "تم استيراده من سجل الرسائل"
                    val storedBody = if (RelayStore.isOtp(message.body)) "محتوى مخفي لحماية رمز التحقق" else message.body
                    RelayStore.recordReceipt(this, message.sender, storedBody, message.receivedAt, outcome)
                }
                ImportResult(matches.size, scanned, visibleSenders.toList())
            }
            runOnUiThread {
                result.onSuccess { import ->
                    render()
                    val message = if (import.imported == 0) {
                        "فُحصت ${import.scanned} رسالة ولم تطابق «${senders.joinToString("، ")}" +
                            "». المرسلون المرئيون: ${import.visibleSenders.ifEmpty { listOf("لا توجد") }.joinToString("، ")}"
                    } else {
                        "تم استيراد ${import.imported} رسالة مطابقة بعد فحص ${import.scanned} رسالة"
                    }
                    importResult.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }.onFailure {
                    val message = "تعذر استيراد الرسائل: ${it.message ?: "خطأ غير معروف"}"
                    importResult.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        parseEndDate(importUntil.text.toString().trim())?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                importUntil.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun showSenderPicker() {
        Thread {
            val result = runCatching {
                val discovered = linkedSetOf<String>()
                val cursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                ) ?: throw IllegalStateException("تعذر فتح سجل الرسائل")
                cursor.use {
                    val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    var scanned = 0
                    while (cursor.moveToNext() && scanned < MAX_SCAN && discovered.size < MAX_PICKABLE_SENDERS) {
                        scanned++
                        cursor.getString(addressColumn)?.trim()?.takeIf(String::isNotEmpty)?.let(discovered::add)
                    }
                }
                discovered.toList()
            }
            runOnUiThread {
                result.onSuccess(::displaySenderPicker).onFailure {
                    Toast.makeText(this, "تعذر فتح قائمة المرسلين: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun exportTrainingSample() {
        val selectedSenders = RelayStore.senders(this)
        Thread {
            val result = runCatching {
                val countBySender = selectedSenders.associateWith { 0 }.toMutableMap()
                val messages = JSONArray()
                val cursor = contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                    null,
                    null,
                    "${Telephony.Sms.DATE} DESC",
                ) ?: throw IllegalStateException("تعذر فتح سجل الرسائل")
                cursor.use {
                    val addressColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                    val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                    val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                    var scanned = 0
                    while (cursor.moveToNext() && scanned < MAX_SAMPLE_SCAN && countBySender.values.any { it < SAMPLE_PER_SENDER }) {
                        scanned++
                        val actualSender = cursor.getString(addressColumn).orEmpty()
                        val configuredSender = selectedSenders.firstOrNull { configured ->
                            countBySender.getValue(configured) < SAMPLE_PER_SENDER && RelayStore.matchesSender(actualSender, listOf(configured))
                        } ?: continue
                        val body = cursor.getString(bodyColumn).orEmpty()
                        val receivedAt = cursor.getLong(dateColumn)
                        messages.put(
                            JSONObject()
                                .put("sender", actualSender)
                                .put("selected_sender", configuredSender)
                                .put("received_at_millis", receivedAt)
                                .put("is_otp", RelayStore.isOtp(body))
                                .put("body_sanitized", sanitizeForTraining(body)),
                        )
                        countBySender[configuredSender] = countBySender.getValue(configuredSender) + 1
                    }
                }
                val counts = JSONObject().apply {
                    countBySender.forEach { (sender, count) -> put(sender, count) }
                }
                val root = JSONObject()
                    .put("schema_version", 1)
                    .put("purpose", "bank_sms_rule_learning")
                    .put("generated_at_millis", System.currentTimeMillis())
                    .put("max_messages_per_sender", SAMPLE_PER_SENDER)
                    .put("selected_senders", JSONArray(selectedSenders))
                    .put("counts_by_sender", counts)
                    .put("messages", messages)
                ExportSample(root.toString(2), messages.length(), countBySender)
            }
            runOnUiThread {
                result.onSuccess { sample ->
                    if (sample.messageCount == 0) {
                        val message = "لم يعثر التطبيق على رسائل للمرسلين المحددين"
                        importResult.text = message
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    } else {
                        shareTrainingSample(sample)
                    }
                }.onFailure {
                    val message = "تعذر إنشاء عينة JSON: ${it.message ?: "خطأ غير معروف"}"
                    importResult.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun shareTrainingSample(sample: ExportSample) {
        val exportDir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "bank-sms-training-${System.currentTimeMillis()}.json")
        file.writeText(sample.json, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val counts = sample.counts.entries.joinToString("، ") { "${it.key}: ${it.value}" }
        importResult.text = "تم إنشاء ${sample.messageCount} رسالة للعينة ($counts). اختر ChatGPT من المشاركة لإرسال الملف."
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("bank-sms-training", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "إرسال عينة JSON"))
    }

    private fun sanitizeForTraining(body: String): String {
        if (!RelayStore.isOtp(body)) return body
        return body.replace(Regex("(?<![0-9٠-٩])[0-9٠-٩]{4,8}(?![0-9٠-٩])"), "[OTP]")
    }

    private fun displaySenderPicker(discovered: List<String>) {
        if (discovered.isEmpty()) {
            Toast.makeText(this, "لم يعثر التطبيق على مرسلين في سجل الرسائل", Toast.LENGTH_LONG).show()
            return
        }
        val chosen = discovered.map { candidate -> senders.any { it.equals(candidate, ignoreCase = true) } }.toBooleanArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر المرسلين من سجل SMS")
            .setMultiChoiceItems(discovered.toTypedArray(), chosen) { _, index, checked -> chosen[index] = checked }
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إضافة المحدد") { _, _ ->
                discovered.forEachIndexed { index, sender ->
                    if (chosen[index] && senders.none { it.equals(sender, ignoreCase = true) }) senders += sender
                }
                RelayStore.saveSenders(this, senders)
                render()
            }
            .show()
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
        const val MAX_SCAN = 2_000
        const val MAX_VISIBLE_SENDERS = 12
        const val MAX_PICKABLE_SENDERS = 80
        const val SAMPLE_PER_SENDER = 200
        const val MAX_SAMPLE_SCAN = 20_000
    }

    private data class HistoricalMessage(val sender: String, val body: String, val receivedAt: Long)
    private data class ImportResult(val imported: Int, val scanned: Int, val visibleSenders: List<String>)
    private data class ExportSample(val json: String, val messageCount: Int, val counts: Map<String, Int>)
    private enum class ReadAction { IMPORT, PICK_SENDERS, EXPORT_SAMPLE }
}
