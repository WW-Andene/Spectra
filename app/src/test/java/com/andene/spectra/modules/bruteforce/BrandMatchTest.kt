package com.andene.spectra.modules.bruteforce

import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.brandTokens
import com.andene.spectra.modules.bruteforce.IrBruteForce.Companion.matchesBrand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Word-level token matching used by the brute-force brand-narrowing
 * prefix. The earlier substring approach over-matched: any manufacturer
 * whose first word was a substring of the detected brand qualified, so a
 * mangled brand like 'lginsignia' would float LG entries to the front
 * even though no whole word was shared. These tests pin the new
 * intersection-based behaviour.
 */
class BrandMatchTest {

    @Test
    fun `tokens lowercase, drop punctuation, drop one-char tokens`() {
        assertEquals(setOf("lg", "electronics"), "LG Electronics".brandTokens())
        assertEquals(setOf("samsung"), "Samsung".brandTokens())
        assertEquals(setOf("lg", "soundbar"), "LG / Soundbar".brandTokens())
        assertEquals(emptySet<String>(), null.brandTokens())
        assertEquals(emptySet<String>(), "".brandTokens())
        assertEquals(emptySet<String>(), "a b c".brandTokens()) // all single-char
    }

    @Test
    fun `single-word brand matches multi-word manufacturer with shared token`() {
        assertTrue(matchesBrand("LG TV (native)", setOf("lg")))
        assertTrue(matchesBrand("Samsung TV", setOf("samsung")))
    }

    @Test
    fun `no shared token means no match`() {
        assertFalse(matchesBrand("Insignia", setOf("lg")))
        assertFalse(matchesBrand("Sony BD Player", setOf("samsung")))
    }

    @Test
    fun `over-match guard - brand without word boundary does not match`() {
        // Old substring-based matcher said yes here ('lg' is a substring of
        // 'lginsignia'). The new word-token matcher says no — there's no
        // whole word shared.
        assertFalse(matchesBrand("LG TV", setOf("lginsignia")))
    }

    @Test
    fun `multi-word brand and manufacturer share any token`() {
        assertTrue(matchesBrand("Sony BD Player", setOf("sony", "soundbar")))
        // Manufacturer 'Sony Soundbar' and brand 'sony tv' both contain 'sony'.
        assertTrue(matchesBrand("Sony Soundbar", setOf("sony", "tv")))
    }

    @Test
    fun `empty brand token set never matches`() {
        assertFalse(matchesBrand("Anything", emptySet()))
    }
}
