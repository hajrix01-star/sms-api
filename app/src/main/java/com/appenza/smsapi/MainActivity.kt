package com.appenza.smsapi

import android.Manifest
import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import java.text.DateFormat
import java.text.NumberFormat
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
    private lateinit var recoveryStatus: TextView
    private lateinit var companyDirectory: TextView
    private lateinit var instrumentDirectory: TextView
    private lateinit var dashboardStatus: TextView
    private lateinit var dashboardSummary: TextView
    private lateinit var custodySummary: TextView
    private lateinit var operationsAdapter: OperationsAdapter
    private lateinit var operationFilterSummary: TextView
    private lateinit var operationsEmpty: TextView
    private lateinit var operationAdvancedFilters: View
    private lateinit var operationFiltersToggle: Button
    private lateinit var operationCompanyChips: ChipGroup
    private lateinit var operationBankChips: ChipGroup
    private lateinit var operationCategoryChips: ChipGroup
    private lateinit var operationSearch: EditText
    private lateinit var accountsOverview: TextView
    private lateinit var accountSuggestions: TextView
    private val senders = mutableListOf<String>()
    private var readAction = ReadAction.IMPORT
    private var operationDays: Int? = null
    private var operationCompany: String? = null
    private var operationSender: String? = null
    private var operationCategory: String? = null
    private var operationReviewOnly = false
    private var operationFiltersExpanded = false

    override fun attachBaseContext(newBase: Context) {
        val darkMode = newBase.getSharedPreferences("sms_api", Context.MODE_PRIVATE)
            .getBoolean(RelayStore.DARK_MODE, true)
        AppCompatDelegate.setDefaultNightMode(
            if (darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
        )
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shell)
        applySystemBarInsets()
        setupDrawer()

        chips = findViewById(R.id.senderChips)
        status = findViewById(R.id.status)
        input = findViewById(R.id.senderInput)
        history = findViewById(R.id.receiptHistory)
        importUntil = findViewById(R.id.importUntil)
        importResult = findViewById(R.id.importResult)
        recoveryStatus = findViewById(R.id.recoveryStatus)
        companyDirectory = findViewById(R.id.companyDirectory)
        instrumentDirectory = findViewById(R.id.instrumentDirectory)
        dashboardStatus = findViewById(R.id.dashboardStatus)
        dashboardSummary = findViewById(R.id.dashboardSummary)
        custodySummary = findViewById(R.id.custodySummary)
        operationFilterSummary = findViewById(R.id.operationsFilterSummary)
        operationsEmpty = findViewById(R.id.operationsEmpty)
        operationAdvancedFilters = findViewById(R.id.operationAdvancedFilters)
        operationFiltersToggle = findViewById(R.id.operationFiltersToggle)
        operationCompanyChips = findViewById(R.id.operationCompanyChips)
        operationBankChips = findViewById(R.id.operationBankChips)
        operationCategoryChips = findViewById(R.id.operationCategoryChips)
        operationSearch = findViewById(R.id.operationSearch)
        accountsOverview = findViewById(R.id.accountsOverview)
        accountSuggestions = findViewById(R.id.accountSuggestions)
        setupOperations()
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
        findViewById<Button>(R.id.toggleRecovery).setOnClickListener {
            if (!saveSenders()) return@setOnClickListener
            if (!RelayStore.preferences(this).getBoolean(RelayStore.ENABLED, false)) {
                Toast.makeText(this, "فعّل الاستقبال المباشر أولًا", Toast.LENGTH_LONG).show()
            } else if (RelayStore.preferences(this).getBoolean(RelayStore.RECOVERY_ENABLED, false)) {
                disableRecovery()
            } else {
                requestReadSmsPermission(ReadAction.ENABLE_RECOVERY)
            }
        }
        findViewById<Button>(R.id.recoverNow).setOnClickListener {
            if (saveSenders()) requestReadSmsPermission(ReadAction.RECOVER_NOW)
        }
        findViewById<Button>(R.id.addCompany).setOnClickListener { showAddCompanyDialog() }
        findViewById<Button>(R.id.manageInstruments).setOnClickListener { showInstrumentManager() }
        findViewById<Button>(R.id.openAccountManager).setOnClickListener { showInstrumentManager() }
        importUntil.setOnClickListener { showDatePicker() }
        findViewById<Button>(R.id.disable).setOnClickListener {
            RelayStore.preferences(this).edit()
                .putBoolean(RelayStore.ENABLED, false)
                .putBoolean(RelayStore.RECOVERY_ENABLED, false)
                .apply()
            RecoveryScheduler.cancel(this)
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

    /**
     * Android 15+ renders target-SDK 35+ apps edge-to-edge by default.  Apply
     * the real device insets instead of assuming a status-bar or gesture-bar size.
     */
    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.shellRoot)
        val topNavigation = findViewById<View>(R.id.topAppBar)
        val pages = findViewById<View>(R.id.pageContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val safeArea = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            topNavigation.setPadding(
                topNavigation.paddingLeft,
                safeArea.top,
                topNavigation.paddingRight,
                0,
            )
            pages.setPadding(
                pages.paddingLeft,
                0,
                pages.paddingRight,
                safeArea.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun setupDrawer() {
        val drawer = findViewById<DrawerLayout>(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.topAppBar)
        val navigation = findViewById<NavigationView>(R.id.navigationView)

        toolbar.setNavigationOnClickListener { drawer.openDrawer(android.view.Gravity.RIGHT) }
        navigation.setNavigationItemSelectedListener { item ->
            val page = when (item.itemId) {
                R.id.menuDashboard -> R.id.dashboardPage
                R.id.menuOperations -> R.id.operationsPage
                R.id.menuAccounts -> R.id.accountsPage
                R.id.menuSettings -> R.id.settingsPage
                else -> null
            }
            if (page == null) {
                false
            } else {
                showPage(page)
                drawer.closeDrawer(android.view.Gravity.RIGHT)
                true
            }
        }

        val toggle = navigation.getHeaderView(0).findViewById<MaterialSwitch>(R.id.themeToggle)
        toggle.isChecked = RelayStore.preferences(this).getBoolean(RelayStore.DARK_MODE, true)
        toggle.setOnCheckedChangeListener { _, isDark ->
            RelayStore.preferences(this).edit().putBoolean(RelayStore.DARK_MODE, isDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO,
            )
        }
    }

    private fun setupOperations() {
        operationsAdapter = OperationsAdapter()
        findViewById<RecyclerView>(R.id.operationsRecycler).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = operationsAdapter
            setHasFixedSize(true)
        }
        findViewById<Chip>(R.id.periodAll).setOnClickListener { operationDays = null; renderOperations() }
        findViewById<Chip>(R.id.period7Days).setOnClickListener { operationDays = 7; renderOperations() }
        findViewById<Chip>(R.id.period30Days).setOnClickListener { operationDays = 30; renderOperations() }
        findViewById<Chip>(R.id.operationReviewChip).setOnClickListener { chip ->
            operationReviewOnly = (chip as Chip).isChecked
            renderOperations()
        }
        operationFiltersToggle.setOnClickListener {
            operationFiltersExpanded = !operationFiltersExpanded
            renderOperationFilterControls()
        }
        operationSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) = renderOperations()
        })
    }

    private fun renderOperations() {
        val database = LedgerDatabase(this)
        renderOperationFilterControls()
        findViewById<Chip>(R.id.operationReviewChip).isChecked = operationReviewOnly
        renderOperationChips(
            operationCompanyChips,
            "كل الجهات والحسابات",
            database.companies().map { it.name },
            operationCompany,
        ) { operationCompany = it }
        renderOperationChips(
            operationBankChips,
            "كل البنوك والمرسلين",
            database.sendersWithEvents(),
            operationSender,
        ) { operationSender = it }
        renderOperationChips(
            operationCategoryChips,
            "كل الأنواع",
            database.categoriesWithEvents(),
            operationCategory,
        ) { operationCategory = it }
        val events = database.events(
            days = operationDays,
            sender = operationSender,
            category = operationCategory,
            companyName = operationCompany,
            search = operationSearch.text?.toString(),
            reviewOnly = operationReviewOnly,
        )
        operationsAdapter.submit(events)
        operationsEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        val window = when (operationDays) {
            7 -> "آخر 7 أيام"
            30 -> "آخر 30 يوم"
            else -> "كل المدة"
        }
        val review = if (operationReviewOnly) " · تحتاج مراجعة" else ""
        operationFilterSummary.text = "عرض ${events.size} عملية · ${operationCompany ?: "كل الشركات"} · $window$review · الحد الأقصى 200"
    }

    private fun renderOperationFilterControls() {
        operationAdvancedFilters.visibility = if (operationFiltersExpanded) View.VISIBLE else View.GONE
        val activeFilters = listOf(operationCompany, operationSender, operationCategory).count { it != null }
        operationFiltersToggle.text = when {
            operationFiltersExpanded -> "إخفاء الفلاتر"
            activeFilters > 0 -> "فلاتر ($activeFilters)"
            else -> "فلاتر"
        }
    }

    private fun renderOperationChips(
        group: ChipGroup,
        allLabel: String,
        values: List<String>,
        selected: String?,
        onSelect: (String?) -> Unit,
    ) {
        group.removeAllViews()
        (listOf<String?>(null) + values).forEach { value ->
            group.addView(Chip(this).apply {
                text = value ?: allLabel
                textSize = 13f
                chipMinHeight = 36f * resources.displayMetrics.density
                setEnsureMinTouchTargetSize(false)
                isCheckable = true
                isChecked = value == selected
                setOnClickListener {
                    if (value == selected) {
                        isChecked = true
                    } else {
                        onSelect(value)
                        renderOperations()
                    }
                }
            })
        }
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
            ReadAction.ENABLE_RECOVERY -> enableRecovery()
            ReadAction.RECOVER_NOW -> recoverNow()
        }
    }

    private fun enable() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.ENABLED, true).apply()
        if (RelayStore.preferences(this).getBoolean(RelayStore.RECOVERY_ENABLED, false)) RecoveryScheduler.schedule(this)
        render()
    }

    private fun enableRecovery() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.RECOVERY_ENABLED, true).apply()
        RecoveryScheduler.schedule(this)
        Toast.makeText(this, "تم تفعيل فحص التعافي اليومي. يحدد أندرويد وقت التنفيذ لتقليل الأثر على البطارية.", Toast.LENGTH_LONG).show()
        render()
    }

    private fun disableRecovery() {
        RelayStore.preferences(this).edit().putBoolean(RelayStore.RECOVERY_ENABLED, false).apply()
        RecoveryScheduler.cancel(this)
        render()
    }

    private fun recoverNow() {
        Thread {
            val result = runCatching { SmsRecovery.recover(this, automatic = false) }
            runOnUiThread {
                result.onSuccess {
                    render()
                    val message = "اكتمل الفحص اليدوي لآخر 7 أيام: فُحصت ${it.scanned} رسالة وأُضيفت ${it.imported} عملية جديدة."
                    importResult.text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this, "تعذر الفحص اليدوي: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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
                var securityExcluded = 0
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
                        if (RelayStore.matchesSender(sender, allowedSenders) && RelayStore.isSecurityMessage(body)) {
                            securityExcluded++
                        } else if (matches.size < MAX_IMPORT && RelayStore.matchesSender(sender, allowedSenders)) {
                            matches += HistoricalMessage(sender, body, receivedAt)
                        }
                    }
                }
                val storedCount = matches.asReversed().count { message ->
                    RelayStore.recordReceipt(this, message.sender, message.body, message.receivedAt, "تم استيراده من سجل الرسائل")
                }
                ImportResult(storedCount, scanned, visibleSenders.toList(), securityExcluded)
            }
            runOnUiThread {
                result.onSuccess { import ->
                    render()
                    val message = if (import.imported == 0) {
                        if (import.securityExcluded > 0) {
                            "لم تُضف رسائل مالية جديدة. استُبعدت ${import.securityExcluded} رسالة OTP أو أمن، أو أن الرسائل المطابقة استوردت سابقًا."
                        } else {
                            "فُحصت ${import.scanned} رسالة ولم تطابق «${senders.joinToString("، ")}" +
                                "». المرسلون المرئيون: ${import.visibleSenders.ifEmpty { listOf("لا توجد") }.joinToString("، ")}"
                        }
                    } else {
                        "تم استيراد ${import.imported} رسالة مطابقة بعد فحص ${import.scanned} رسالة. استُبعدت ${import.securityExcluded} رسالة أمنية أو OTP."
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
                val securityExcludedBySender = selectedSenders.associateWith { 0 }.toMutableMap()
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
                        if (RelayStore.isSecurityMessage(body)) {
                            securityExcludedBySender[configuredSender] = securityExcludedBySender.getValue(configuredSender) + 1
                            continue
                        }
                        val receivedAt = cursor.getLong(dateColumn)
                        messages.put(
                            JSONObject()
                                .put("sender", actualSender)
                                .put("selected_sender", configuredSender)
                                .put("received_at_millis", receivedAt)
                                .put("body_sanitized", body),
                        )
                        countBySender[configuredSender] = countBySender.getValue(configuredSender) + 1
                    }
                }
                val counts = JSONObject().apply {
                    countBySender.forEach { (sender, count) -> put(sender, count) }
                }
                val excludedCounts = JSONObject().apply {
                    securityExcludedBySender.forEach { (sender, count) -> put(sender, count) }
                }
                val root = JSONObject()
                    .put("schema_version", 2)
                    .put("purpose", "bank_sms_rule_learning")
                    .put("generated_at_millis", System.currentTimeMillis())
                    .put("max_messages_per_sender", SAMPLE_PER_SENDER)
                    .put("security_messages_excluded", true)
                    .put("selected_senders", JSONArray(selectedSenders))
                    .put("counts_by_sender", counts)
                    .put("security_messages_excluded_by_sender", excludedCounts)
                    .put("messages", messages)
                ExportSample(root.toString(2), messages.length(), countBySender, securityExcludedBySender)
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
        val excluded = sample.securityExcluded.values.sum()
        importResult.text = "تم إنشاء ${sample.messageCount} رسالة للعينة ($counts). استُبعدت $excluded رسالة OTP أو أمن. اختر ChatGPT من المشاركة لإرسال الملف."
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("bank-sms-training", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "إرسال عينة JSON"))
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

    private fun showAddCompanyDialog() {
        val field = EditText(this).apply { hint = "مثال: مطعم المعلم الشامي" }
        MaterialAlertDialogBuilder(this)
            .setTitle("إضافة شركة أو نطاق شخصي")
            .setView(field)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("إضافة") { _, _ ->
                val name = field.text.toString().trim()
                if (name.isNotBlank()) {
                    val added = LedgerDatabase(this).addCompany(name)
                    Toast.makeText(this, if (added) "تمت إضافة $name" else "هذه الشركة موجودة بالفعل", Toast.LENGTH_LONG).show()
                    render()
                }
            }.show()
    }

    private fun showPage(pageId: Int) {
        listOf(R.id.dashboardPage, R.id.operationsPage, R.id.accountsPage, R.id.settingsPage).forEach { id ->
            findViewById<View>(id).visibility = if (id == pageId) View.VISIBLE else View.GONE
        }
        val (title, menuItem) = when (pageId) {
            R.id.dashboardPage -> "الرئيسية" to R.id.menuDashboard
            R.id.operationsPage -> "العمليات" to R.id.menuOperations
            R.id.accountsPage -> "الحسابات والبطاقات" to R.id.menuAccounts
            else -> "الإعدادات والاستيراد" to R.id.menuSettings
        }
        findViewById<MaterialToolbar>(R.id.topAppBar).title = title
        findViewById<NavigationView>(R.id.navigationView).setCheckedItem(menuItem)
        render()
    }

    private fun showInstrumentManager() {
        val database = LedgerDatabase(this)
        val companies = database.companies()
        if (companies.isEmpty()) {
            Toast.makeText(this, "أضف شركة أو نطاقًا شخصيًا أولًا", Toast.LENGTH_LONG).show()
            return
        }
        val instruments = database.instruments()
        if (instruments.isEmpty()) {
            Toast.makeText(this, "لا توجد حسابات أو بطاقات مكتشفة بعد. استقبل رسالة أو استورد السجل أولًا.", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("الحسابات والبطاقات المكتشفة")
            .setItems(instruments.map { instrument ->
                "${instrument.reference} · ${instrument.kind} · ${instrument.bankSender}\n${instrument.companyName ?: "غير مربوط"}${instrument.role?.let { " — $it" } ?: ""}${instrument.parentReference?.let { " · مرتبط بـ $it" } ?: ""} · ${instrument.events} حركة"
            }.toTypedArray()) { _, index -> showInstrumentAssignment(instruments[index], companies) }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun showInstrumentAssignment(instrument: FinancialInstrument, companies: List<Company>) {
        var selectedCompany = 0
        MaterialAlertDialogBuilder(this)
            .setTitle("ربط ${instrument.reference} بـ شركة")
            .setSingleChoiceItems(companies.map { it.name }.toTypedArray(), 0) { _, index -> selectedCompany = index }
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("التالي") { _, _ -> showRolePicker(instrument, companies[selectedCompany]) }
            .show()
    }

    private fun showRolePicker(instrument: FinancialInstrument, company: Company) {
        val roles = arrayOf("حساب تشغيل وإيداع", "حساب تحويلات ومدفوعات", "بطاقة مشتريات", "بطاقة مندوب مشتريات", "حساب عهدة", "حساب شخصي وسيط", "تسوية نقاط بيع", "غير محدد")
        var selectedRole = 0
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر الدور التشغيلي")
            .setSingleChoiceItems(roles, 0) { _, index -> selectedRole = index }
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                LedgerDatabase(this).assignInstrument(instrument.reference, instrument.bankSender, instrument.kind, company.id, roles[selectedRole])
                Toast.makeText(this, "تم ربط ${instrument.reference} بـ ${company.name}", Toast.LENGTH_LONG).show()
                render()
            }.show()
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
            isEnabled && hasPermission -> "الحالة: الاستقبال المباشر مفعّل — لا توجد خدمة تعمل باستمرار"
            hasPermission -> "الحالة: الإذن مسموح، الاستقبال غير مفعّل"
            else -> "الحالة: إذن SMS مطلوب للاختبار"
        }
        val formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar"))
        val recoveryEnabled = RelayStore.preferences(this).getBoolean(RelayStore.RECOVERY_ENABLED, false)
        val lastRecovery = RelayStore.preferences(this).getLong(RelayStore.RECOVERY_LAST_AT, 0L)
        val lastImported = RelayStore.preferences(this).getInt(RelayStore.RECOVERY_LAST_IMPORTED, 0)
        recoveryStatus.text = if (recoveryEnabled) {
            val lastRun = if (lastRecovery == 0L) "لم يعمل بعد" else "آخر فحص: ${formatter.format(Date(lastRecovery))} ($lastImported جديدة)"
            "فحص التعافي: مفعّل. يراجع آخر 7 أيام كل 6 ساعات تقريبًا حسب أندرويد. $lastRun"
        } else {
            "فحص التعافي: غير مفعّل. الاستقبال المباشر وحده خفيف وكافٍ في الوضع الطبيعي."
        }
        history.text = RelayStore.receipts(this).joinToString("\n\n") { receipt ->
            "${receipt.sender}  •  ${formatter.format(Date(receipt.receivedAt))}\n${receipt.outcome}\n${receipt.preview}"
        }.ifBlank { "لا توجد رسائل مستلمة بعد. أضف الاسم المرسل للبنك أو آخر 6–8 أرقام من الرقم الحقيقي." }
        val database = LedgerDatabase(this)
        val companies = database.companies()
        companyDirectory.text = if (companies.isEmpty()) "لا توجد جهات أو حسابات بعد." else companies.joinToString(" • ") { it.name }
        val instruments = database.instruments()
        val linked = instruments.count { it.companyName != null }
        instrumentDirectory.text = if (instruments.isEmpty()) "لا توجد أدوات مالية مكتشفة بعد." else "تم اكتشاف ${instruments.size} حساب/بطاقة؛ المرتبط منها $linked، وغير المرتبط ${instruments.size - linked}."
        val summary = RelayStore.summary(this)
        dashboardStatus.text = if (isEnabled && hasPermission) "الاستقبال المباشر يعمل. لا توجد خدمة دائمة في الذاكرة." else "الاستقبال غير مفعّل أو يحتاج إذن SMS."
        val reviewCount = database.reviewCount()
        dashboardSummary.text = "إجمالي العمليات: ${summary.total}\nإيداعات وتسويات: ${summary.incoming}\nمشتريات وتحويلات وسحب: ${summary.outgoing}\nرسوم بنكية: ${summary.fees}\nتحتاج مراجعة: $reviewCount"
        findViewById<Button>(R.id.openReviewQueue).apply {
            text = if (reviewCount == 0) "لا توجد رسائل تحتاج مراجعة" else "مراجعة $reviewCount رسالة غير مكتملة الربط أو التصنيف"
            setEnabled(reviewCount > 0)
            setOnClickListener {
                operationReviewOnly = true
                showPage(R.id.operationsPage)
            }
        }
        renderCustodySummary(database.osamaCustodySummary())
        renderOperations()
        accountsOverview.text = if (instruments.isEmpty()) "استورد رسائل البنك أولًا ليكتشف التطبيق المراجع المموهة للحسابات والبطاقات." else "${companies.size} شركات/نطاقات مسجلة. ${instruments.size} أدوات مالية مكتشفة، منها $linked مربوطة."
        accountSuggestions.text = (
            CompanyRules.proposals.map { proposal ->
                "${proposal.companyName} · ${proposal.bankSender} · ${proposal.reference}\n${proposal.role}"
            } + CompanyRules.ambiguousSuggestions()
        ).joinToString("\n\n")
    }

    private fun renderCustodySummary(summary: CustodySummary) {
        val amounts = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        fun sar(value: Double) = "SAR ${amounts.format(value)}"
        val sources = summary.fundingByCompany.entries.joinToString("، ") { (name, amount) -> "$name ${sar(amount)}" }
        custodySummary.text = if (summary.funded == 0.0 && summary.cardPurchases == 0.0 && summary.cashWithdrawals == 0.0 && summary.transfersOut == 0.0) {
            "لا توجد حركات عهدة مطابقة بعد. يظهر الملخص عند استيراد أو استقبال تحويل إلى 1994، أو عملية ببطاقة 0187."
        } else {
            "تمويل العهدة: ${sar(summary.funded)}${if (sources.isBlank()) "" else "\nالمصادر: $sources"}\n" +
                "مشتريات بطاقة 0187: ${sar(summary.cardPurchases)}\n" +
                "كاش سُحب مع أسامة: ${sar(summary.cashWithOsama)}\n" +
                "حوالات خرجت من العهدة: ${sar(summary.transfersOut)}\n\n" +
                "رصيد البنك لدى أسامة: ${sar(summary.bankWithOsama)}\n" +
                "إجمالي العهدة معه: ${sar(summary.totalHeldByOsama)}"
        }
    }

    private companion object {
        const val REQUEST_RECEIVE_SMS = 8
        const val REQUEST_READ_SMS = 9
        const val MAX_IMPORT = 500
        const val MAX_SCAN = 2_000
        const val MAX_VISIBLE_SENDERS = 12
        const val MAX_PICKABLE_SENDERS = 80
        const val SAMPLE_PER_SENDER = 600
        const val MAX_SAMPLE_SCAN = 60_000
    }

    private data class HistoricalMessage(val sender: String, val body: String, val receivedAt: Long)
    private data class ImportResult(val imported: Int, val scanned: Int, val visibleSenders: List<String>, val securityExcluded: Int)
    private data class ExportSample(val json: String, val messageCount: Int, val counts: Map<String, Int>, val securityExcluded: Map<String, Int>)
    private enum class ReadAction { IMPORT, PICK_SENDERS, EXPORT_SAMPLE, ENABLE_RECOVERY, RECOVER_NOW }
}
