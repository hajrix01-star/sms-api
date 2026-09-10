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
}
