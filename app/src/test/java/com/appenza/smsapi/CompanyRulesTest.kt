package com.appenza.smsapi

import org.junit.Assert.assertEquals
import org.junit.Test

class CompanyRulesTest {
    @Test
    fun incomingAlertFromKnownCompanySourceBecomesOutgoing() {
        assertEquals(
            "تحويل صادر",
            CompanyRules.categoryForKnownSource("إيداع / وارد", isKnownCompanySource = true),
        )
    }

    @Test
    fun incomingAlertWithoutKnownCompanySourceStaysIncoming() {
        assertEquals(
            "إيداع / وارد",
            CompanyRules.categoryForKnownSource("إيداع / وارد", isKnownCompanySource = false),
        )
    }
}
