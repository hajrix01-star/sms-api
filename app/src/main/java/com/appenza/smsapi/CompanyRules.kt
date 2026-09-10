package com.appenza.smsapi

/**
 * Local accounting rules inferred from the sample and confirmed by the owner.
 * Only masked references are stored; full account numbers are never embedded.
 */
data class CompanyProposal(
    val companyName: String,
    val reference: String,
    val bankSender: String,
    val kind: String,
    val role: String,
    val evidence: String,
    val parentReference: String? = null,
)

object CompanyRules {
    val defaultCompanies = listOf(
        "شركة أرز",
        "شركة المعلم الشامي",
        "مؤسسة دوحة المستهلك التجارية",
        "عهدة — أسامة (مندوب المشتريات)",
        "نطاق شخصي — الأهلي",
    )

    val proposals = listOf(
        CompanyProposal(
            "شركة أرز", "2029", "AlRajhiBank", "حساب",
            "الحساب الرئيسي: مبيعات، سداد، فواتير، حوالات وعهد",
            "تم تأكيده من المالك؛ يظهر في الرسائل باسم ARZ RESTAURANT أو المرجع 2029.",
        ),
        CompanyProposal(
            "شركة أرز", "0214", "AlRajhiBank", "حساب",
            "حساب عهدة بطاقة SIFI CARD",
            "التحويل إليه من ARZ يُعامل كتغذية عهدة بطاقة SIFI CARD.",
        ),
        CompanyProposal(
            "شركة المعلم الشامي", "5204", "AlRajhiBank", "حساب",
            "الحساب الرئيسي: سداد، فواتير، حوالات وعهد",
            "تم تأكيده من المالك؛ يظهر باسم MASHWEYAT ALMUALEM GRILL أو المرجع 5204.",
        ),
        CompanyProposal(
            "شركة المعلم الشامي", "1296", "AlRajhiBank", "حساب",
            "الحساب الفرعي: سداد، فواتير، حوالات وعهد",
            "تم تأكيد الحساب الفرعي من المالك؛ يطبّق عند ظهور المرجع 1296 في الرسائل.",
        ),
        CompanyProposal(
            "شركة المعلم الشامي", "1310", "SNB-AlAhli", "حساب",
            "حساب تحصيل منصة كيتا",
            "حساب تحصيل مخصص لاستلام حوالات كيتا فقط؛ يُحفظ المرجع المختصر دون رقم الحساب الكامل.",
        ),
        CompanyProposal(
            "شركة المعلم الشامي", "0605", "SNB-AlAhli", "حساب",
            "حساب تحصيل منصة هنقرستيشن",
            "حساب تحصيل مخصص لاستلام حوالات هنقرستيشن فقط؛ يُحفظ المرجع المختصر دون رقم الحساب الكامل.",
        ),
        CompanyProposal(
            "شركة المعلم الشامي", "7507", "SNB-AlAhli", "حساب",
            "حساب تحصيل منصة جاهز",
            "حساب تحصيل مخصص لاستلام حوالات جاهز فقط؛ يُحفظ المرجع المختصر دون رقم الحساب الكامل.",
        ),
        CompanyProposal(
            "مؤسسة دوحة المستهلك التجارية", "0409", "SNB-AlAhli", "حساب",
            "حساب تشغيل إلكتروني: تحويلات، سداد ومدفوعات",
            "تم تأكيده من المالك؛ يظهر المرجع في رسائل الأهلي ورسومه وتحويلاته.",
        ),
        CompanyProposal(
            "عهدة — أسامة (مندوب المشتريات)", "1994", "AlRajhiBank", "حساب",
            "حساب عهدة مندوب المشتريات أسامة",
            "تصل إليه عهدة من ARZ والمعلم الشامي؛ تم تأكيد وظيفته من المالك.",
        ),
        CompanyProposal(
            "نطاق شخصي — الأهلي", "0305", "SNB-AlAhli", "حساب",
            "حساب شخصي في الأهلي",
            "تم تأكيد الحساب من المالك؛ يظهر المرجع مموهًا في رسائل الأهلي.",
        ),
        CompanyProposal(
            "نطاق شخصي — الأهلي", "2237", "SNB-AlAhli", "بطاقة",
            "بطاقة شخصية مرتبطة بالحساب 0305",
            "تم تأكيد البطاقة والحساب المرتبطين بها من المالك؛ ظهرت في 86 عملية شراء ضمن العينة.",
            parentReference = "0305",
        ),
        CompanyProposal(
            "عهدة — أسامة (مندوب المشتريات)", "0187", "AlRajhiBank", "بطاقة",
            "بطاقة مندوب المشتريات أسامة المرتبطة بعهدة 1994",
            "تم تأكيد البطاقة والحساب المرتبطين بها من المالك؛ ظهرت في 186 عملية شراء وسحب ضمن العينة.",
            parentReference = "1994",
        ),
    )

    fun inferCompany(sender: String, body: String): String? {
        // Some Android SMS apps insert invisible RTL/LTR markers around masked account values.
        // Remove them before matching, otherwise 375***204 is missed despite being visible on screen.
        val text = body.replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        return when {
        text.contains("ARZ RESTURANT", ignoreCase = true) ||
            (sender.equals("AlRajhiBank", ignoreCase = true) && text.contains("From:2029", ignoreCase = true)) ||
            hasMaskedAccount(text, "989", "029") -> "شركة أرز"
        text.contains("MASHWEYAT ALMUALEM GRILL", ignoreCase = true) ||
            text.contains("مطعم مشويات المعلم الشامي", ignoreCase = true) ||
            (sender.equals("AlRajhiBank", ignoreCase = true) && (text.contains("From:5204", ignoreCase = true) || text.contains("From:1296", ignoreCase = true))) ||
            hasMaskedAccount(text, "375", "204") || hasMaskedAccount(text, "375", "296") ||
            (sender.equals("SNB-AlAhli", ignoreCase = true) && (
                text.contains("KEETA", ignoreCase = true) || text.contains("كيتا") ||
                    text.contains("HUNGERSTATION", ignoreCase = true) || text.contains("HUNGER STATION", ignoreCase = true) || text.contains("HANQARSTISHN", ignoreCase = true) || text.contains("هنقرستيشن") ||
                    text.contains("JAHEZ", ignoreCase = true) || text.contains("جاهز") ||
                    hasMaskedReference(text, "1310") || hasMaskedReference(text, "0605") || hasMaskedReference(text, "7507")
                )) -> "شركة المعلم الشامي"
        text.contains("مؤسسة دوحة المستهلك التجارية") ||
            (sender.equals("SNB-AlAhli", ignoreCase = true) && (
                // الأهلي قد يخفي حساب دوحة بصيغة 078*409 أو 078***409.
                hasMaskedAccount(text, "078", "409") ||
                Regex("(?:من|حسابك)\\s*[:：]?\\s*0409\\*").containsMatchIn(text)
            )) -> "مؤسسة دوحة المستهلك التجارية"
        sender.equals("SNB-AlAhli", ignoreCase = true) && text.contains("2237", ignoreCase = true) -> "نطاق شخصي — الأهلي"
        sender.equals("SNB-AlAhli", ignoreCase = true) && (
            Regex("(?:من|حسابك)\\s*[:：]?\\s*\\*?0305\\*?").containsMatchIn(text) ||
                hasMaskedAccount(text, "078", "305")
            ) -> "نطاق شخصي — الأهلي"
        sender.equals("AlRajhiBank", ignoreCase = true) && text.contains("By:0187", ignoreCase = true) -> "عهدة — أسامة (مندوب المشتريات)"
        sender.equals("AlRajhiBank", ignoreCase = true) && text.contains("From:1994", ignoreCase = true) -> "عهدة — أسامة (مندوب المشتريات)"
        else -> null
        }
    }

    /**
     * Bank alerts describe direction from the receiving bank account's point of view.
     * When an SNB "incoming" alert explicitly identifies ARZ account 2029 as the
     * source, the same movement is outgoing from ARZ's point of view.
     */
    fun categoryForCompany(
        sender: String,
        body: String,
        companyName: String?,
        parsedCategory: String,
    ): String {
        if (companyName != "شركة أرز" || parsedCategory != "إيداع / وارد") return parsedCategory
        if (!sender.equals("SNB-AlAhli", ignoreCase = true)) return parsedCategory

        val text = body.replace(Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"), "")
        val isArzSource = Regex("(?:من|From)\\s*[:：]?\\s*\\*?2029(?:\\D|$)", RegexOption.IGNORE_CASE)
            .containsMatchIn(text) ||
            text.contains("ARZ RESTURANT", ignoreCase = true) ||
            text.contains("ARZ RESTAURANT", ignoreCase = true)
        return categoryForKnownSource(parsedCategory, isArzSource)
    }

    internal fun categoryForKnownSource(parsedCategory: String, isKnownCompanySource: Boolean): String =
        if (isKnownCompanySource && parsedCategory == "إيداع / وارد") "تحويل صادر" else parsedCategory

    private fun hasMaskedAccount(value: String, prefix: String, suffix: String): Boolean =
        Regex("$prefix\\*+$suffix").containsMatchIn(value) ||
            Regex("$suffix\\*+$prefix").containsMatchIn(value)

    /** Matches only a masked account suffix such as 014***1310, never the full account number. */
    private fun hasMaskedReference(value: String, suffix: String): Boolean =
        Regex("\\*+$suffix(?:\\D|$)").containsMatchIn(value)

    fun ambiguousSuggestions() = emptyList<String>()
}
