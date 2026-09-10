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

class MainActivity : AppCompatActivity() {
    private lateinit var chips: ChipGroup
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var url: EditText
    private lateinit var token: EditText
    private val senders = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chips = findViewById(R.id.senderChips)
        status = findViewById(R.id.status)
        input = findViewById(R.id.senderInput)
        url = findViewById(R.id.apiUrl)
        token = findViewById(R.id.apiToken)
        senders.addAll(RelayStore.senders(this))
        url.setText(RelayStore.p(this).getString(RelayStore.URL, ""))
        token.setText(RelayStore.token(this).orEmpty())
        render()

        findViewById<Button>(R.id.addSender).setOnClickListener {
            val sender = input.text.toString().trim()
            if (sender.isNotEmpty() && senders.none { it.equals(sender, ignoreCase = true) }) {
                senders.add(sender)
                input.text.clear()
                render()
            }
        }
        findViewById<Button>(R.id.enable).setOnClickListener { if (save()) requestSmsPermission() }
        findViewById<Button>(R.id.disable).setOnClickListener {
            RelayStore.p(this).edit().putBoolean(RelayStore.ENABLED, false).apply()
            render()
        }
        findViewById<Button>(R.id.test).setOnClickListener {
            if (save()) RelayWorker.enqueue(this, "BANK_RELAY_TEST", "Bank Relay connection test", System.currentTimeMillis())
        }
    }

    private fun save(): Boolean {
        val endpoint = url.text.toString().trim()
        val deviceToken = token.text.toString()
        if (senders.isEmpty() || !endpoint.startsWith("https://") || deviceToken.isBlank()) {
            Toast.makeText(this, "أضف مرسلاً ورابط HTTPS ومفتاح الجهاز", Toast.LENGTH_LONG).show()
            return false
        }
        RelayStore.saveSenders(this, senders)
        RelayStore.p(this).edit().putString(RelayStore.URL, endpoint).apply()
        RelayStore.saveToken(this, deviceToken)
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
        if (requestCode == REQUEST_SMS && results.all { it == PackageManager.PERMISSION_GRANTED }) enable()
    }

    private fun enable() {
        RelayStore.p(this).edit().putBoolean(RelayStore.ENABLED, true).apply()
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
        status.text = if (RelayStore.p(this).getBoolean(RelayStore.ENABLED, false)) {
            "الحالة: التحويل مفعّل"
        } else {
            "الحالة: غير مفعّل"
        }
    }

    private companion object { const val REQUEST_SMS = 8 }
}
