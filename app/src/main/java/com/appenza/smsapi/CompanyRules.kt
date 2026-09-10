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

    fun inferCompany(sender: String, body: String): String? = when {
        body.contains("ARZ RESTURANT", ignoreCase = true) ||
            (sender.equals("AlRajhiBank", ignoreCase = true) && body.contains("From:2029", ignoreCase = true)) -> "شركة أرز"
        body.contains("MASHWEYAT ALMUALEM GRILL", ignoreCase = true) ||
            body.contains("مطعم مشويات المعلم الشامي", ignoreCase = true) ||
            (sender.equals("AlRajhiBank", ignoreCase = true) && (body.contains("From:5204", ignoreCase = true) || body.contains("From:1296", ignoreCase = true))) ||
            Regex("375\\*{3}204").containsMatchIn(body) -> "شركة المعلم الشامي"
        body.contains("مؤسسة دوحة المستهلك التجارية") ||
            (sender.equals("SNB-AlAhli", ignoreCase = true) && Regex("(?:من|حسابك)\\s*[:：]?\\s*0409\\*").containsMatchIn(body)) -> "مؤسسة دوحة المستهلك التجارية"
        sender.equals("SNB-AlAhli", ignoreCase = true) && body.contains("2237", ignoreCase = true) -> "نطاق شخصي — الأهلي"
        sender.equals("SNB-AlAhli", ignoreCase = true) && Regex("(?:من|حسابك)\\s*[:：]?\\s*\\*?0305\\*?").containsMatchIn(body) -> "نطاق شخصي — الأهلي"
        sender.equals("AlRajhiBank", ignoreCase = true) && body.contains("By:0187", ignoreCase = true) -> "عهدة — أسامة (مندوب المشتريات)"
        sender.equals("AlRajhiBank", ignoreCase = true) && body.contains("From:1994", ignoreCase = true) -> "عهدة — أسامة (مندوب المشتريات)"
        else -> null
    }

    fun ambiguousSuggestions() = emptyList<String>()
}
