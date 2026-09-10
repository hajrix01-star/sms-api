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
import com.google.android.material.bottomsheet.BottomSheetDialog
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
    private lateinit var dashboardPeriodLabel: TextView
    private lateinit var arzFlowText: TextView
    private lateinit var muallamFlowText: TextView
    private lateinit var dohaFlowText: TextView
    private lateinit var keetaFlowText: TextView
    private lateinit var hungerFlowText: TextView
    private lateinit var jahezFlowText: TextView
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
    private var operationCustodyOnly = false
    private var operationBodyKeywords = emptyList<String>()
    private var operationScopeLabel: String? = null
    private var operationFiltersExpanded = false
    private var operationFromMillis: Long? = null
    private var operationToMillis: Long? = null
    private var operationMonthFilter: CalendarMonthFilter? = null
    private var dashboardDays: Int? = null
    private var dashboardFromMillis: Long? = null
    private var dashboardToMillis: Long? = null
    private var dashboardMonthFilter: CalendarMonthFilter? = null

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
        dashboardPeriodLabel = findViewById(R.id.dashboardPeriodLabel)
        arzFlowText = findViewById(R.id.arzFlowText)
        muallamFlowText = findViewById(R.id.muallamFlowText)
        dohaFlowText = findViewById(R.id.dohaFlowText)
        keetaFlowText = findViewById(R.id.keetaFlowText)
        hungerFlowText = findViewById(R.id.hungerFlowText)
        jahezFlowText = findViewById(R.id.jahezFlowText)
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
        setupDashboard()
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
        operationFiltersToggle.setOnClickListener { showOperationsFilterSheet() }
        findViewById<Button>(R.id.operationReanalyze).setOnClickListener {
            Thread {
                val scanned = LedgerDatabase(this).reanalyzeAll()
                runOnUiThread {
                    render()
                    Toast.makeText(this, "اكتملت إعادة تحليل $scanned عملية محلية", Toast.LENGTH_LONG).show()
                }
            }.start()
        }
        findViewById<Button>(R.id.operationExportReview).setOnClickListener { exportReviewQueue() }
        operationSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(value: Editable?) = renderOperations()
        })
    }

    private fun setupDashboard() {
        findViewById<View>(R.id.dashboardPeriodCard).setOnClickListener { showDashboardPeriodSheet() }
        findViewById<Button>(R.id.dashboardPeriodPickerButton).setOnClickListener { showDashboardPeriodSheet() }

        findViewById<View>(R.id.cardArz).setOnClickListener { openDashboardLedger("شركة أرز") }
        findViewById<View>(R.id.cardMuallam).setOnClickListener { openDashboardLedger("شركة المعلم الشامي") }
        findViewById<View>(R.id.cardDoha).setOnClickListener { openDashboardLedger("مؤسسة دوحة المستهلك التجارية") }
        findViewById<View>(R.id.cardKeeta).setOnClickListener { openDashboardLedger("شركة المعلم الشامي", listOf("KEETA", "كيتا", "1310"), "تحصيل كيتا") }
        findViewById<View>(R.id.cardHunger).setOnClickListener { openDashboardLedger("شركة المعلم الشامي", listOf("HUNGERSTATION", "HUNGER STATION", "HANQARSTISHN", "هنقرستيشن", "0605"), "تحصيل هنقرستيشن") }
        findViewById<View>(R.id.cardJahez).setOnClickListener { openDashboardLedger("شركة المعلم الشامي", listOf("JAHEZ", "جاهز", "7507"), "تحصيل جاهز") }
        findViewById<View>(R.id.cardOsamaCustody).setOnClickListener { openDashboardLedger(custodyOnly = true) }
    }

    private fun openDashboardLedger(
        companyName: String? = null,
        bodyKeywords: List<String> = emptyList(),
        scopeLabel: String? = null,
        custodyOnly: Boolean = false,
    ) {
        val (fromMillis, toMillis) = dashboardWindow()
        operationCompany = companyName
        operationSender = null
        operationCategory = null
        operationReviewOnly = false
        operationCustodyOnly = custodyOnly
        operationBodyKeywords = bodyKeywords
        operationScopeLabel = scopeLabel
        operationDays = null
        operationFromMillis = fromMillis
        operationToMillis = toMillis
        operationMonthFilter = dashboardMonthFilter
        operationSearch.setText("")
        showPage(R.id.operationsPage)
    }

    private fun dashboardWindow(): Pair<Long?, Long?> = when {
        dashboardFromMillis != null -> dashboardFromMillis to dashboardToMillis
        dashboardDays != null -> (System.currentTimeMillis() - dashboardDays!! * 86_400_000L) to System.currentTimeMillis()
        dashboardMonthFilter != null -> calendarMonthWindow(dashboardMonthFilter!!)
        else -> null to null
    }

    private fun operationWindow(): Pair<Long?, Long?> = when {
        operationFromMillis != null -> operationFromMillis to operationToMillis
        operationDays != null -> (System.currentTimeMillis() - operationDays!! * 86_400_000L) to System.currentTimeMillis()
        operationMonthFilter != null -> calendarMonthWindow(operationMonthFilter!!)
        else -> null to null
    }

    private fun calendarMonthWindow(filter: CalendarMonthFilter): Pair<Long, Long> =
        DateRangeCalculator.calendarMonthWindow(filter)

    private fun selectDashboardPeriod(
        days: Int? = null,
        month: CalendarMonthFilter? = null,
        window: Pair<Long, Long>? = null,
    ) {
        dashboardDays = days
        dashboardFromMillis = window?.first
        dashboardToMillis = window?.second
        dashboardMonthFilter = month
        render()
    }

    private fun showDashboardPeriodSheet() {
        val sheet = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.sheet_dashboard_period, null)
        sheet.setContentView(content)

        val currentWindow = dashboardWindow()
        val today = DateRangeCalculator.calendarDayWindow()
        val yesterday = DateRangeCalculator.calendarDayWindow(dayOffset = -1)
        content.findViewById<TextView>(R.id.sheetDashboardPeriodLabel).text =
            "المحدد حاليًا: ${formatPeriod(currentWindow.first, currentWindow.second)}"

        fun choose(days: Int? = null, month: CalendarMonthFilter? = null, window: Pair<Long, Long>? = null) {
            selectDashboardPeriod(days = days, month = month, window = window)
            sheet.dismiss()
        }

        content.findViewById<Chip>(R.id.sheetDashboardPeriodAll).apply {
            isChecked = dashboardDays == null && dashboardFromMillis == null && dashboardMonthFilter == null
            setOnClickListener { choose() }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriodToday).apply {
            isChecked = dashboardFromMillis == today.first && dashboardToMillis == today.second
            setOnClickListener { choose(window = today) }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriodYesterday).apply {
            isChecked = dashboardFromMillis == yesterday.first && dashboardToMillis == yesterday.second
            setOnClickListener { choose(window = yesterday) }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriodThisMonth).apply {
            isChecked = dashboardMonthFilter == CalendarMonthFilter.THIS_MONTH
            setOnClickListener { choose(month = CalendarMonthFilter.THIS_MONTH) }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriodLastMonth).apply {
            isChecked = dashboardMonthFilter == CalendarMonthFilter.LAST_MONTH
            setOnClickListener { choose(month = CalendarMonthFilter.LAST_MONTH) }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriod7Days).apply {
            isChecked = dashboardDays == 7
            setOnClickListener { choose(days = 7) }
        }
        content.findViewById<Chip>(R.id.sheetDashboardPeriod30Days).apply {
            isChecked = dashboardDays == 30
            setOnClickListener { choose(days = 30) }
        }
        content.findViewById<Button>(R.id.sheetDashboardSingleDay).setOnClickListener {
            sheet.dismiss()
            showDashboardSingleDatePicker()
        }
        content.findViewById<Button>(R.id.sheetDashboardDateRange).setOnClickListener {
            sheet.dismiss()
            showDashboardDateRangePicker()
        }
        sheet.show()
    }

    private fun showDashboardSingleDatePicker() {
        val calendar = Calendar.getInstance().apply {
            dashboardFromMillis?.let { timeInMillis = it }
        }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectDashboardPeriod(
                    window = dayBoundary(year, month, day, endOfDay = false) to
                        dayBoundary(year, month, day, endOfDay = true),
                )
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun showDashboardDateRangePicker() {
        val calendar = Calendar.getInstance()
        dashboardFromMillis?.let { calendar.timeInMillis = it }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val from = dayBoundary(year, month, day, endOfDay = false)
                val endCalendar = Calendar.getInstance().apply { timeInMillis = dashboardToMillis ?: from }
                DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDay ->
                        val to = dayBoundary(endYear, endMonth, endDay, endOfDay = true)
                        if (to < from) {
                            Toast.makeText(this, "تاريخ النهاية يجب أن يكون بعد تاريخ البداية", Toast.LENGTH_LONG).show()
                            render()
                        } else {
                            selectDashboardPeriod(window = from to to)
                        }
                    },
                    endCalendar.get(Calendar.YEAR), endCalendar.get(Calendar.MONTH), endCalendar.get(Calendar.DAY_OF_MONTH),
                ).show()
            },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun dayBoundary(year: Int, month: Int, day: Int, endOfDay: Boolean): Long =
        DateRangeCalculator.dayBoundary(year, month, day, endOfDay)

    private fun formatPeriod(fromMillis: Long?, toMillis: Long?): String {
        if (fromMillis == null) return "كل السجل"
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale("ar"))
        return when {
            toMillis == null -> "من ${formatter.format(Date(fromMillis))}"
            DateRangeCalculator.isSameCalendarDay(fromMillis, toMillis) -> formatter.format(Date(fromMillis))
            else -> "${formatter.format(Date(fromMillis))} – ${formatter.format(Date(toMillis))}"
        }
    }

    private fun renderOperations() {
        val database = LedgerDatabase(this)
        renderOperationFilterControls()
        val (operationFrom, operationTo) = operationWindow()
        val events = database.events(
            days = operationDays,
            fromMillis = operationFrom,
            toMillis = operationTo,
            sender = operationSender,
            category = operationCategory,
            companyName = operationCompany,
            search = operationSearch.text?.toString(),
            bodyKeywords = operationBodyKeywords,
            reviewOnly = operationReviewOnly,
            custodyOnly = operationCustodyOnly,
        )
        operationsAdapter.submit(events)
        operationsEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        val reviewCount = database.reviewCount()
        findViewById<Button>(R.id.operationExportReview).apply {
            text = if (reviewCount == 0) "لا توجد مراجعة للتصدير" else "تصدير $reviewCount رسالة تحتاج مراجعة (JSON)"
            isEnabled = reviewCount > 0
        }
        val window = when {
            operationFrom != null -> formatPeriod(operationFrom, operationTo)
            else -> when (operationDays) {
            7 -> "آخر 7 أيام"
            30 -> "آخر 30 يوم"
            else -> "كل المدة"
            }
        }
        val review = if (operationReviewOnly) " · تحتاج مراجعة" else ""
        val custody = if (operationCustodyOnly) " · عهدة أسامة" else ""
        operationFilterSummary.text = "عرض ${events.size} عملية · ${operationScopeLabel ?: operationCompany ?: "كل الشركات"} · $window$review$custody · الحد الأقصى 200"
    }

    private fun renderOperationFilterControls() {
        operationAdvancedFilters.visibility = View.GONE
        val activeFilters = listOf(
            operationCompany,
            operationSender,
            operationCategory,
            operationBodyKeywords.takeIf { it.isNotEmpty() },
            operationDays,
            operationFromMillis,
            operationMonthFilter,
        ).count { it != null } + if (operationReviewOnly) 1 else 0
        operationFiltersToggle.text = if (activeFilters > 0) "فلترة ($activeFilters)" else "فلترة"
    }

    private fun showOperationsFilterSheet() {
        val database = LedgerDatabase(this)
        val sheet = BottomSheetDialog(this)
        val content = layoutInflater.inflate(R.layout.sheet_operation_filters, null)
        sheet.setContentView(content)

        var draftDays = operationDays
        var draftFromMillis = operationFromMillis
        var draftToMillis = operationToMillis
        var draftMonthFilter = operationMonthFilter
        var draftCompany = operationCompany
        var draftSender = operationSender
        var draftCategory = operationCategory
        var draftReviewOnly = operationReviewOnly
        var draftCustodyOnly = operationCustodyOnly
        var draftBodyKeywords = operationBodyKeywords
        var draftScopeLabel = operationScopeLabel

        fun choosePeriod(days: Int? = null, month: CalendarMonthFilter? = null) {
            draftDays = days
            draftFromMillis = null
            draftToMillis = null
            draftMonthFilter = month
        }
        fun clearSpecialScope() {
            draftScopeLabel = null
            draftCustodyOnly = false
            draftBodyKeywords = emptyList()
        }
        fun applyDraft() {
            operationDays = draftDays
            operationFromMillis = draftFromMillis
            operationToMillis = draftToMillis
            operationMonthFilter = draftMonthFilter
            operationCompany = draftCompany
            operationSender = draftSender
            operationCategory = draftCategory
            operationReviewOnly = draftReviewOnly
            operationCustodyOnly = draftCustodyOnly
            operationBodyKeywords = draftBodyKeywords
            operationScopeLabel = draftScopeLabel
        }
        content.findViewById<Chip>(R.id.sheetPeriodAll).setOnClickListener { choosePeriod() }
        content.findViewById<Chip>(R.id.sheetPeriodThisMonth).setOnClickListener { choosePeriod(month = CalendarMonthFilter.THIS_MONTH) }
        content.findViewById<Chip>(R.id.sheetPeriodLastMonth).setOnClickListener { choosePeriod(month = CalendarMonthFilter.LAST_MONTH) }
        content.findViewById<Chip>(R.id.sheetPeriod7Days).setOnClickListener { choosePeriod(days = 7) }
        content.findViewById<Chip>(R.id.sheetPeriod30Days).setOnClickListener { choosePeriod(days = 30) }
        content.findViewById<Chip>(R.id.sheetPeriodAll).isChecked = draftDays == null && draftFromMillis == null && draftMonthFilter == null
        content.findViewById<Chip>(R.id.sheetPeriodThisMonth).isChecked = draftMonthFilter == CalendarMonthFilter.THIS_MONTH
        content.findViewById<Chip>(R.id.sheetPeriodLastMonth).isChecked = draftMonthFilter == CalendarMonthFilter.LAST_MONTH
        content.findViewById<Chip>(R.id.sheetPeriod7Days).isChecked = draftDays == 7
        content.findViewById<Chip>(R.id.sheetPeriod30Days).isChecked = draftDays == 30
        content.findViewById<Chip>(R.id.sheetReviewChip).isChecked = draftReviewOnly
        content.findViewById<Chip>(R.id.sheetReviewChip).setOnClickListener { chip -> draftReviewOnly = (chip as Chip).isChecked }

        renderOperationChips(
            content.findViewById(R.id.sheetCompanyChips),
            "كل الجهات والحسابات",
            database.companies().map { it.name },
            draftCompany,
        ) {
            draftCompany = it
            clearSpecialScope()
        }
        renderOperationChips(
            content.findViewById(R.id.sheetBankChips),
            "كل البنوك والمرسلين",
            database.sendersWithEvents(),
            draftSender,
        ) {
            draftSender = it
            clearSpecialScope()
        }
        renderOperationChips(
            content.findViewById(R.id.sheetCategoryChips),
            "كل الأنواع",
            database.categoriesWithEvents(),
            draftCategory,
        ) {
            draftCategory = it
            clearSpecialScope()
        }

        content.findViewById<Button>(R.id.sheetClearFilters).setOnClickListener {
            draftDays = null
            draftFromMillis = null
            draftToMillis = null
            draftMonthFilter = null
            draftCompany = null
            draftSender = null
            draftCategory = null
            draftReviewOnly = false
            draftCustodyOnly = false
            draftBodyKeywords = emptyList()
            draftScopeLabel = null
            applyDraft()
            operationSearch.setText("")
            renderOperations()
            sheet.dismiss()
        }
        content.findViewById<Button>(R.id.sheetApplyFilters).setOnClickListener {
            applyDraft()
            renderOperations()
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun renderOperationChips(
        group: ChipGroup,
        allLabel: String,
        values: List<String>,
        selected: String?,
        onSelect: (String?) -> Unit,
    ) {
        group.removeAllViews()
        group.isSelectionRequired = true
        (listOf<String?>(null) + values).forEach { value ->
            group.addView(Chip(this).apply {
                text = value ?: allLabel
                textSize = 13f
                chipMinHeight = 36f * resources.displayMetrics.density
                setEnsureMinTouchTargetSize(false)
                isCheckable = true
                isChecked = value == selected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) onSelect(value)
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

    /** Shares only local ledger items that still require a rule or a company/account link. */
    private fun exportReviewQueue() {
        Thread {
            val result = runCatching {
                val database = LedgerDatabase(this)
                val totalReviewCount = database.reviewCount()
                val events = database.events(limit = MAX_REVIEW_EXPORT, reviewOnly = true)
                    // Defensive guarantee: OTP/security messages are never exported even if an old local database contains one.
                    .filterNot { RelayStore.isSecurityMessage(it.body) }
                val messages = JSONArray()
                events.forEach { event ->
                    val reasons = JSONArray().apply {
                        if (event.category == "غير مصنف") put("نوع العملية غير معروف")
                        if (event.companyName == null) put("لا يوجد ربط بجهة أو حساب")
                    }
                    messages.put(
                        JSONObject()
                            .put("sender", event.sender)
                            .put("received_at_millis", event.receivedAt)
                            .put("received_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date(event.receivedAt)))
                            .put("category", event.category)
                            .put("amount_sar", event.amount)
                            .put("instrument_reference", event.instrument)
                            .put("counterparty", event.counterparty)
                            .put("company_name", event.companyName)
                            .put("custody_type", event.custodyType)
                            .put("review_reasons", reasons)
                            .put("body", event.body),
                    )
                }
                val root = JSONObject()
                    .put("schema_version", 1)
                    .put("purpose", "bank_sms_review_rule_analysis")
                    .put("generated_at_millis", System.currentTimeMillis())
                    .put("security_messages_excluded", true)
                    .put("scope", "messages_needing_review_only")
                    .put("review_count_at_export", totalReviewCount)
                    .put("exported_count", messages.length())
                    .put("max_export_count", MAX_REVIEW_EXPORT)
                    .put("truncated", totalReviewCount > messages.length())
                    .put("messages", messages)
                ReviewExport(root.toString(2), messages.length(), totalReviewCount)
            }
            runOnUiThread {
                result.onSuccess { export ->
                    if (export.messageCount == 0) {
                        Toast.makeText(this, "لا توجد رسائل تحتاج مراجعة للتصدير", Toast.LENGTH_LONG).show()
                    } else {
                        shareReviewExport(export)
                    }
                }.onFailure {
                    Toast.makeText(this, "تعذر تصدير قائمة المراجعة: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
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

    private fun shareReviewExport(export: ReviewExport) {
        val exportDir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "bank-sms-review-${System.currentTimeMillis()}.json")
        file.writeText(export.json, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val omitted = (export.totalReviewCount - export.messageCount).coerceAtLeast(0)
        val message = buildString {
            append("تم إنشاء ملف JSON يضم ${export.messageCount} رسالة تحتاج مراجعة فقط. ")
            if (omitted > 0) append("لم تُصدَّر $omitted رسالة بسبب حد التصدير الآمن. ")
            append("رسائل OTP والأمن مستبعدة. اختر ChatGPT لإرفاق الملف.")
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, "قائمة مراجعة رسائل البنك")
            clipData = ClipData.newRawUri("bank-sms-review", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "إرفاق قائمة المراجعة JSON"))
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
        dashboardStatus.text = if (isEnabled && hasPermission) "الاستقبال المباشر يعمل. لا توجد خدمة دائمة في الذاكرة." else "الاستقبال غير مفعّل أو يحتاج إذن SMS."
        val reviewCount = database.reviewCount()
        val (dashboardFrom, dashboardTo) = dashboardWindow()
        dashboardPeriodLabel.text = formatPeriod(dashboardFrom, dashboardTo)
        renderFlow(arzFlowText, database.financialFlow("شركة أرز", dashboardFrom, dashboardTo))
        renderFlow(muallamFlowText, database.financialFlow("شركة المعلم الشامي", dashboardFrom, dashboardTo))
        renderFlow(dohaFlowText, database.financialFlow("مؤسسة دوحة المستهلك التجارية", dashboardFrom, dashboardTo))
        renderPlatformFlow(keetaFlowText, database.financialFlow("شركة المعلم الشامي", dashboardFrom, dashboardTo, listOf("KEETA", "كيتا", "1310")))
        renderPlatformFlow(hungerFlowText, database.financialFlow("شركة المعلم الشامي", dashboardFrom, dashboardTo, listOf("HUNGERSTATION", "HUNGER STATION", "HANQARSTISHN", "هنقرستيشن", "0605")))
        renderPlatformFlow(jahezFlowText, database.financialFlow("شركة المعلم الشامي", dashboardFrom, dashboardTo, listOf("JAHEZ", "جاهز", "7507")))
        findViewById<Button>(R.id.openReviewQueue).apply {
            text = if (reviewCount == 0) "لا توجد رسائل تحتاج مراجعة" else "مراجعة $reviewCount رسالة غير مكتملة الربط أو التصنيف"
            setEnabled(reviewCount > 0)
            setOnClickListener {
                operationReviewOnly = true
                operationCustodyOnly = false
                operationBodyKeywords = emptyList()
                operationScopeLabel = null
                operationCompany = null
                operationDays = null
                operationFromMillis = null
                operationToMillis = null
                operationMonthFilter = null
                showPage(R.id.operationsPage)
            }
        }
        renderCustodySummary(database.osamaCustodySummary(dashboardFrom, dashboardTo))
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

    private fun renderFlow(view: TextView, summary: FinancialFlowSummary) {
        val money = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        fun sar(value: Double) = "SAR ${money.format(value)}"
        view.text = "وارد ${sar(summary.incoming)}  ·  خارج ${sar(summary.outgoing)}\n" +
            "رسوم ${sar(summary.fees)}  ·  ${summary.transactions} عملية"
    }

    private fun renderPlatformFlow(view: TextView, summary: FinancialFlowSummary) {
        val money = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        view.text = "تحصيل وارد: SAR ${money.format(summary.incoming)}  ·  ${summary.transactions} حوالة"
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
        const val MAX_REVIEW_EXPORT = 500
    }

    private data class HistoricalMessage(val sender: String, val body: String, val receivedAt: Long)
    private data class ImportResult(val imported: Int, val scanned: Int, val visibleSenders: List<String>, val securityExcluded: Int)
    private data class ExportSample(val json: String, val messageCount: Int, val counts: Map<String, Int>, val securityExcluded: Map<String, Int>)
    private data class ReviewExport(val json: String, val messageCount: Int, val totalReviewCount: Int)
    private enum class ReadAction { IMPORT, PICK_SENDERS, EXPORT_SAMPLE, ENABLE_RECOVERY, RECOVER_NOW }
}
