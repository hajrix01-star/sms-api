package com.appenza.smsapi

internal enum class CashDirection { INCOMING, OUTGOING, NEUTRAL }

internal object OperationDirectionResolver {
    fun resolve(category: String): CashDirection = when {
        category.contains("إيداع") || category.contains("تسوية") || category.contains("وارد") ->
            CashDirection.INCOMING
        category.contains("شراء") || category.contains("تحويل") || category.contains("سحب") ||
            category.contains("سداد") || category.contains("رسوم") -> CashDirection.OUTGOING
        else -> CashDirection.NEUTRAL
    }
}
