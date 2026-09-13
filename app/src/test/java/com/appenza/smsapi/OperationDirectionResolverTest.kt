package com.appenza.smsapi

import org.junit.Assert.assertEquals
import org.junit.Test

class OperationDirectionResolverTest {
    @Test
    fun incomingCategoriesUseIncomingDirection() {
        assertEquals(CashDirection.INCOMING, OperationDirectionResolver.resolve("إيداع / وارد"))
        assertEquals(CashDirection.INCOMING, OperationDirectionResolver.resolve("تسوية POS"))
    }

    @Test
    fun outgoingCategoriesUseOutgoingDirection() {
        listOf("تحويل صادر", "تحويل داخلي", "شراء", "سحب صراف", "سداد", "رسوم بنكية").forEach {
            assertEquals(CashDirection.OUTGOING, OperationDirectionResolver.resolve(it))
        }
    }

    @Test
    fun rejectedAndUnknownOperationsStayNeutral() {
        assertEquals(CashDirection.NEUTRAL, OperationDirectionResolver.resolve("عملية مرفوضة"))
        assertEquals(CashDirection.NEUTRAL, OperationDirectionResolver.resolve("غير مصنف"))
    }

    @Test
    fun custodyOperationsKeepTheirSpecificLabels() {
        assertEquals("تغذية عهدة", OperationDirectionResolver.label(event(custodyType = "تمويل عهدة")))
        assertEquals("مصروف بطاقة العهدة", OperationDirectionResolver.label(event(custodyType = "مشتريات عهدة")))
        assertEquals("نقد مع المندوب", OperationDirectionResolver.label(event(custodyType = "سحب نقدي عهدة")))
    }

    @Test
    fun otherOperationsUseTheCashDirectionLabel() {
        assertEquals("↑ داخل الحساب", OperationDirectionResolver.label(event(category = "إيداع")))
        assertEquals("↓ خارج الحساب", OperationDirectionResolver.label(event(category = "تحويل صادر")))
        assertEquals("شركة أرز", OperationDirectionResolver.label(event(category = "غير مصنف", companyName = "شركة أرز")))
    }

    private fun event(
        category: String = "غير مصنف",
        companyName: String? = null,
        custodyType: String? = null,
    ) = LedgerEvent(
        sender = "BANK",
        receivedAt = 0L,
        category = category,
        amount = null,
        instrument = null,
        counterparty = null,
        body = "رسالة اختبار",
        companyName = companyName,
        custodyType = custodyType,
    )
}
