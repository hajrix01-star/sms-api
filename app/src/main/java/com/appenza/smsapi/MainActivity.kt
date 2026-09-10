package com.appenza.smsapi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var chips: ChipGroup
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var history: TextView
    private val senders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chips = findViewById(R.id.senderChips)
        status = findViewById(R.id.status)
        input = findViewById(R.id.senderInput)
        history = findViewById(R.id.receiptHistory)
        senders.addAll(RelayStore.senders(this))

        findViewById<Button>(R.id.addSender).setOnClickListener {
            val sender = input.text.toString().trim()
            if (sender.isNotEmpty() && senders.none { it.equals(sender, ignoreCase = true) }) {
                senders.add(sender)
                input.text.clear()
                render()
            }
        }
        findViewById<Button>(R.id.enable).setOnClickListener { if (saveSenders()) requestSmsPermission() }
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

    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
            enable()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), REQUEST_SMS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_SMS && results.all { it == PackageManager.PERMISSION_GRANTED }) {
            enable()
        } else if (requestCode == REQUEST_SMS) {
            Toast.makeText(this, "لن يعمل سجل الاستلام بدون إذن SMS", Toast.LENGTH_LONG).show()
        }
    }

    private fun enable() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.ENABLED, true).apply()
        render()
    }

    private fun render() {
        chips.removeAllViews()
        senders.forEach { sender ->
            chips.addView(Chip(this).apply {
                text = sender
                isCloseIconVisible = true
                setOnCloseIconClickListener { senders.remove(sender); render() }
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
        }.ifBlank { "لا توجد رسائل مستلمة بعد. فعّل الاستقبال ثم أرسل SMS عادية من رقم مطابق." }
    }

    private companion object { const val REQUEST_SMS = 8 }
}
