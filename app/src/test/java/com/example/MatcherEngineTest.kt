package com.example

import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherEngineTest {

    @Test
    fun testOdiaTransliterationBasic() {
        // ଓମକାର -> Omkara
        val translit = OdiaTransliterationEngine.transliterateOdiaToEnglish("ଓମକାର")
        assertTrue("Transliteration should contain Omkar: $translit", translit.contains("Omkar", ignoreCase = true))

        // ରଥ -> Ratha
        val translitRath = OdiaTransliterationEngine.transliterateOdiaToEnglish("ରଥ")
        assertTrue("Transliteration should contain Rath: $translitRath", translitRath.contains("Rath", ignoreCase = true))
    }

    @Test
    fun testOdiaTransliterationWithConjuncts() {
        // ପଟ୍ଟନାୟକ -> Pattanaik / Patnaik
        val translit = OdiaTransliterationEngine.transliterateOdiaToEnglish("ପଟ୍ଟନାୟକ")
        val normalized = OdiaTransliterationEngine.normalizeForMatching("ପଟ୍ଟନାୟକ")
        assertTrue("Should normalize to patnaik variant: $normalized", normalized.contains("patnaik"))
    }

    @Test
    fun testExactCrossScriptMatch() {
        val result = MatcherEngine.compareNames("Omkar Rath", "ଓମକାର ରଥ")
        assertTrue("Expected score >= 0.90 for Omkar Rath vs ଓମକାର ରଥ, got: ${result.score}", result.score >= 0.90f)
        assertTrue("Should be auto-approvable", result.isAutoApprovable)
    }

    @Test
    fun testWordOrderPermutation() {
        val result = MatcherEngine.compareNames("Rath Omkar", "ଓମକାର ରଥ")
        assertTrue("Expected score >= 0.90 for reversed word order, got: ${result.score}", result.score >= 0.90f)
    }

    @Test
    fun testPhoneticVariantMatch() {
        // Mohapatra vs Mahapatra
        val result = MatcherEngine.compareNames("Bijay Mohapatra", "ବିଜୟ କୁମାର ମହାପାତ୍ର")
        assertTrue("Expected score >= 0.70 for Mohapatra vs Mahapatra with middle name, got: ${result.score}", result.score >= 0.70f)
    }

    @Test
    fun testSurnamePreservation() {
        // Same first name 'Bijay', but completely different surnames (Sahoo vs Mohapatra)
        val result = MatcherEngine.compareNames("Bijay Sahoo", "ବିଜୟ ମହାପାତ୍ର")
        assertFalse("Different surnames must NOT be auto-approved", result.isAutoApprovable)
        assertTrue("Score should be heavily penalized (< 0.65), got: ${result.score}", result.score < 0.65f)
    }

    @Test
    fun testWeakSimilarityDoesNotAutoApprove() {
        val result = MatcherEngine.compareNames("Manas Behera", "ସୁଭଶ୍ରୀ ପ୍ରଧାନ")
        assertFalse("Unrelated names must not be auto-approved", result.isAutoApprovable)
        assertTrue("Score must be low (< 0.50), got: ${result.score}", result.score < 0.50f)
    }
}
