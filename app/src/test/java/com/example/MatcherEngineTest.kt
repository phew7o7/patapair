package com.example

import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.PersonRecord
import com.example.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherEngineTest {

    @Test
    fun testOdiaTransliterationBasic() {
        val translit = OdiaTransliterationEngine.transliterateOdiaToEnglish("ଓମକାର")
        assertTrue("Transliteration should contain Omkar: $translit", translit.contains("Omkar", ignoreCase = true))

        val translitRath = OdiaTransliterationEngine.transliterateOdiaToEnglish("ରଥ")
        assertTrue("Transliteration should contain Rath: $translitRath", translitRath.contains("Rath", ignoreCase = true))
    }

    @Test
    fun testOdiaTransliterationWithConjuncts() {
        val translit = OdiaTransliterationEngine.transliterateOdiaToEnglish("ପଟ୍ଟନାୟକ")
        val normalized = OdiaTransliterationEngine.normalizeForMatching("ପଟ୍ଟନାୟକ")
        assertTrue("Should normalize to patnaik variant: $normalized", normalized.contains("patnaik"))
    }

    // 1. Exact cross-script name matches
    @Test
    fun testExactCrossScriptMatch() {
        val result = MatcherEngine.compareNames("Omkar Rath", "ଓମକାର ରଥ")
        assertTrue("Expected score >= 0.90 for Omkar Rath vs ଓମକାର ରଥ, got: ${result.score}", result.score >= 0.90f)
        assertFalse("Matches must never be auto-approved without user confirmation", result.isAutoApprovable)
    }

    @Test
    fun testWordOrderPermutation() {
        val result = MatcherEngine.compareNames("Rath Omkar", "ଓମକାର ରଥ")
        assertTrue("Expected score >= 0.90 for reversed word order, got: ${result.score}", result.score >= 0.90f)
    }

    // 2. Plausible transliteration variants
    @Test
    fun testPhoneticVariantMatch() {
        val result = MatcherEngine.compareNames("Bijay Mohapatra", "ବିଜୟ କୁମାର ମହାପାତ୍ର")
        assertTrue("Expected score >= 0.70 for Mohapatra vs Mahapatra with middle name, got: ${result.score}", result.score >= 0.70f)

        val patnaikResult = MatcherEngine.compareNames("Manas Patnaik", "ମନସ ପଟ୍ଟନାୟକ")
        assertTrue("Expected score >= 0.80 for Patnaik vs Pattanaik, got: ${patnaikResult.score}", patnaikResult.score >= 0.80f)

        val sahuResult = MatcherEngine.compareNames("Bijay Kumar Sahoo", "ବିଜୟ କୁମାର ସାହୁ")
        assertTrue("Expected score >= 0.90 for Sahoo vs Sahu, got: ${sahuResult.score}", sahuResult.score >= 0.90f)
    }

    // 3. Same surname but different given names
    @Test
    fun testSameSurnameDifferentGivenNames() {
        // Suresh Mohapatra vs Ramesh Mohapatra (same family/caste surname, completely different brothers)
        val result = MatcherEngine.compareNames("Suresh Mohapatra", "Ramesh Mohapatra")
        assertFalse("Same surname with different given names must NOT be auto-approved", result.isAutoApprovable)
        assertTrue("Score must be low (< 0.50) despite shared surname: got ${result.score}", result.score < 0.50f)
        assertTrue("Explanation must mention divergent given name: ${result.explanation}",
            result.explanation.contains("weak/divergent given name", ignoreCase = true) ||
            result.explanation.contains("given name", ignoreCase = true)
        )

        // Evaluate candidate with person records
        val pA = PersonRecord("pa1", "da1", "Suresh Mohapatra", ScriptType.LATIN, "Suresh Mohapatra", "suresh mohapatra")
        val pL = PersonRecord("pl1", "dl1", "ରମେଶ ମହାପାତ୍ର", ScriptType.ODIA, "Ramesh Mohapatra", "ramesh mohapatra")
        val eval = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        assertTrue("Candidate score must be < 0.50: got ${eval.score}", eval.score < 0.50f)
        assertTrue("Conflicting evidence must flag given name discrepancy: ${eval.conflictingEvidence}",
            eval.conflictingEvidence.any { it.contains("Different given names", ignoreCase = true) }
        )
    }

    // 4. Short incomplete names
    @Test
    fun testShortIncompleteNames() {
        val r1 = MatcherEngine.compareNames("Mai", "Mal")
        assertTrue("Score for single 3-letter OCR noise must be < 0.40, got: ${r1.score}", r1.score < 0.40f)
        assertTrue(r1.explanation.contains("Short or incomplete name fragment", ignoreCase = true))

        val r2 = MatcherEngine.compareNames("AARA", "Om")
        assertTrue("Score for short fragment must be < 0.40, got: ${r2.score}", r2.score < 0.40f)

        val r3 = MatcherEngine.compareNames("Roy", "Rout")
        assertTrue("Score for short token fragment must be < 0.40, got: ${r3.score}", r3.score < 0.40f)
    }

    // 5. Unrelated names that share a few letters
    @Test
    fun testUnrelatedNamesSharingFewLetters() {
        // "AARA" vs "Jamidarangk" (the real-world physical phone test case)
        val r1 = MatcherEngine.compareNames("AARA", "Jamidarangk")
        assertTrue("AARA vs Jamidarangk must score < 0.35, got: ${r1.score}", r1.score < 0.35f)

        val pA = PersonRecord("pa_aara", "da_aara", "AARA", ScriptType.LATIN, "AARA", "aara")
        val pL = PersonRecord("pl_jami", "dl_jami", "ଜମିଦାରଙ୍କ", ScriptType.ODIA, "Jamidarangk", "jamidarangk")
        val eval = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        assertTrue("Candidate evaluation for AARA vs Jamidarangk must be < 0.35: got ${eval.score}", eval.score < 0.35f)
        assertFalse("Must be far below the review threshold (0.55)", eval.score >= MatcherEngine.THRESHOLD_REVIEW)

        // "Das" vs "Dinabandhu"
        val r2 = MatcherEngine.compareNames("Das", "Dinabandhu")
        assertTrue("Score must be < 0.40, got: ${r2.score}", r2.score < 0.40f)
    }

    // 6. Missing location
    @Test
    fun testMissingLocationHandling() {
        val pA = PersonRecord("pA", "dA", "Omkar Rath", ScriptType.LATIN, "Omkar Rath", "omkar rath")
        val pL = PersonRecord("pL", "dL", "ଓମକାର ରଥ", ScriptType.ODIA, "Omkar Rath", "omkar rath")

        // No document records provided (locations null)
        val evalNoDocs = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        assertTrue("Score should be based on 100% name similarity: ${evalNoDocs.score}", evalNoDocs.score >= 0.90f)
        assertFalse("Missing location must NOT appear in supporting evidence", evalNoDocs.supportingEvidence.any { it.contains("village", ignoreCase = true) || it.contains("mouza", ignoreCase = true) })
        assertFalse("Missing location must NOT appear in conflicting evidence", evalNoDocs.conflictingEvidence.any { it.contains("village", ignoreCase = true) || it.contains("mouza", ignoreCase = true) })
        assertTrue("Signal must record LOCATION_UNAVAILABLE", evalNoDocs.signals.any { it.signalName == "LOCATION_UNAVAILABLE" })
        assertTrue("Explanation must note location unavailable", evalNoDocs.explanation.contains("Location: Unknown/Unavailable in source"))

        // Only one doc has location
        val docA = DocumentRecord("dA", "uri", "a.jpg", 1, 1, DocType.AADHAAR, DocStatus.DETECTED, "Tess", "eng", "", "", mouza = "Bhamarmal")
        val evalOneDoc = MatcherEngine.evaluateCandidate(pA, pL, docA, null, false)
        assertTrue("One missing location must still be marked unavailable", evalOneDoc.signals.any { it.signalName == "LOCATION_UNAVAILABLE" })
        assertFalse("Must NOT mark as conflict when location is missing on one side", evalOneDoc.conflictingEvidence.any { it.contains("Conflicting location") })
    }

    // 7. Matching and conflicting locations
    @Test
    fun testMatchingAndConflictingLocations() {
        val pA = PersonRecord("pA", "dA", "Omkar Rath", ScriptType.LATIN, "Omkar Rath", "omkar rath")
        val pL = PersonRecord("pL", "dL", "ଓମକାର ରଥ", ScriptType.ODIA, "Omkar Rath", "omkar rath")

        val docA = DocumentRecord("dA", "uri", "a.jpg", 1, 1, DocType.AADHAAR, DocStatus.DETECTED, "Tess", "eng", "", "", mouza = "Bhamarmal")
        val docLMatching = DocumentRecord("dL", "uri", "l.jpg", 1, 1, DocType.LAND_RECORD, DocStatus.DETECTED, "Tess", "ori", "", "", mouza = "Bhamarmal")
        val docLConflicting = DocumentRecord("dL", "uri", "l.jpg", 1, 1, DocType.LAND_RECORD, DocStatus.DETECTED, "Tess", "ori", "", "", mouza = "Cuttack")

        // Matching locations
        val evalMatching = MatcherEngine.evaluateCandidate(pA, pL, docA, docLMatching, false)
        assertTrue("Matching location should yield high score: ${evalMatching.score}", evalMatching.score >= 0.90f)
        assertTrue("Supporting evidence must corroborate location", evalMatching.supportingEvidence.any { it.contains("corroborated", ignoreCase = true) })
        assertTrue("Signal must record LOCATION_MATCH", evalMatching.signals.any { it.signalName == "LOCATION_MATCH" })

        // Conflicting locations
        val evalConflicting = MatcherEngine.evaluateCandidate(pA, pL, docA, docLConflicting, false)
        assertTrue("Conflicting evidence must flag location conflict", evalConflicting.conflictingEvidence.any { it.contains("Conflicting location", ignoreCase = true) })
        assertTrue("Signal must record LOCATION_CONFLICT", evalConflicting.signals.any { it.signalName == "LOCATION_CONFLICT" })
        assertTrue("Score with conflicting location must be penalized compared to matching", evalConflicting.score < evalMatching.score)
    }

    // 8. Repeated matcher execution idempotence
    @Test
    fun testRepeatedMatcherExecutionIdempotence() {
        val pA = PersonRecord("pa_rep", "da_rep", "Omkar Rath", ScriptType.LATIN, "Omkar Rath", "omkar rath")
        val pL = PersonRecord("pl_rep", "dl_rep", "ଓମକାର ରଥ", ScriptType.ODIA, "Omkar Rath", "omkar rath")

        val run1 = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        val run2 = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        val run3 = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)

        assertEquals("Scores must be identical across repeated runs", run1.score, run2.score, 0.0001f)
        assertEquals("Scores must be identical across repeated runs", run2.score, run3.score, 0.0001f)
        assertEquals("Explanations must be identical across repeated runs", run1.explanation, run2.explanation)
        assertEquals("Signals count must be identical", run1.signals.size, run2.signals.size)
    }

    // 9. Real-world Odia Patta matching test: Tiran Sahu (English) vs ତିରନ ସାହୁ (Odia)
    @Test
    fun testRealWorldTiranSahuMatch() {
        val transliterated = OdiaTransliterationEngine.transliterateOdiaToEnglish("ତିରନ ସାହୁ")
        val normA = OdiaTransliterationEngine.normalizeForMatching("Tiran Sahu")
        val normL = OdiaTransliterationEngine.normalizeForMatching("ତିରନ ସାହୁ")

        assertEquals("Odia 'ତିରନ ସାହୁ' must normalize to same key as 'Tiran Sahu'", normA, normL)

        val pA = PersonRecord("pa_tiran", "da_tiran", "Tiran Sahu", ScriptType.LATIN, "Tiran Sahu", normA)
        val pL = PersonRecord("pl_tiran", "dl_tiran", "ତିରନ ସାହୁ", ScriptType.ODIA, transliterated, normL)

        val eval = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        assertTrue("Real-world Odia landholder ତିରନ ସାହୁ must match Aadhaar Tiran Sahu with score >= 0.85: got ${eval.score}", eval.score >= 0.85f)
        assertTrue("Supporting evidence must confirm cross-script match", eval.supportingEvidence.any { it.contains("cross-script", ignoreCase = true) })
    }

    // 10. Verification that noise tokens like 'Mai' and 'Mal' are rejected
    @Test
    fun testRejectionOfNoiseTokensMaiAndMal() {
        // Aadhaar noise "Mai" (misread "Male") vs Land fragment "ମାଲ" (Mal from Uttaramal)
        val pA = PersonRecord("pa_mai", "da_mai", "Mai", ScriptType.LATIN, "Mai", "mai")
        val pL = PersonRecord("pl_mal", "dl_mal", "ମାଲ", ScriptType.ODIA, "Mal", "mal")

        val eval = MatcherEngine.evaluateCandidate(pA, pL, null, null, false)
        assertTrue("Short noise fragments 'Mai' and 'Mal' must score < 0.55: got ${eval.score}", eval.score < 0.55f)
        assertTrue("Must flag short/incomplete name weak evidence", eval.conflictingEvidence.any { it.contains("weak", ignoreCase = true) || it.contains("short", ignoreCase = true) })
    }
}
