package com.appenza.smsapi

internal enum class CashDirection { INCOMING, OUTGOING, NEUTRAL }

internal object OperationDirectionResolver {
    fun label(event: LedgerEvent): String = when {
        event.custodyType == "تمويل عهدة" -> "تغذية عهدة"
        event.custodyType == "مشتريات عهدة" -> "مصروف بطاقة العهدة"
        event.custodyType == "سحب نقدي عهدة" -> "نقد مع المندوب"
        resolve(event.category) == CashDirection.INCOMING -> "↑ داخل الحساب"
        resolve(event.category) == CashDirection.OUTGOING -> "↓ خارج الحساب"
        else -> event.companyName ?: "بانتظار الربط"
    }

    fun resolve(category: String): CashDirection = when {
        category.contains("إيداع") || category.contains("تسوية") || category.contains("وارد") ->
            CashDirection.INCOMING
        category.contains("شراء") || category.contains("تحويل") || category.contains("سحب") ||
            category.contains("سداد") || category.contains("رسوم") -> CashDirection.OUTGOING
        else -> CashDirection.NEUTRAL
    }
}
