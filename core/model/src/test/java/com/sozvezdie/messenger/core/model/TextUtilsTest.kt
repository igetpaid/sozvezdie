package com.sozvezdie.messenger.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Простые тесты текстовых функций. Они не требуют телефона
 * и проверяют то, что легко сломать при изменениях.
 */
class TextUtilsTest {

    @Test
    fun searchIgnoresCaseAndYo() {
        assertEquals("максим елкин", normalizeSearch("  МАКСИМ Ёлкин  "))
    }

    @Test
    fun searchRemovesDogSymbol() {
        assertEquals("maksim", normalizeSearch("@Maksim"))
    }

    @Test
    fun groupLinkFromFullAddress() {
        assertEquals(
            "groupk7x2",
            normalizeGroupLink("https://sozvezdie.app/g/GroupK7X2"),
        )
    }

    @Test
    fun groupLinkFromShortCode() {
        assertEquals("group_k7", normalizeGroupLink("@group_k7"))
    }

    @Test
    fun emptyGroupLinkStaysEmpty() {
        assertEquals("", normalizeGroupLink("   "))
    }
}
