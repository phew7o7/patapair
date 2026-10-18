package com.example

import com.example.ai.DefaultAiDocumentInterpreter
import com.example.engine.DocumentClassifier
import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.MatchingDiagnostics
import com.example.model.PersonRecord
import com.example.model.ScriptType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PipelineExtractionAndMatchingDiagnosticTest {

    private val aiInterpreter = DefaultAiDocumentInterpreter()

    @Test
    fun testAadhaarNameExtractionWithHeaderSuppression() {
        // Realistic Aadhaar text with UIDAI headers, Government of India, DOB, and Cardholder
        val aadhaarOcr = """
            GOVERNMENT OF INDIA
            UNIQUE IDENTIFICATION AUTHORITY OF INDIA
            Mera Aadhaar, Meri Pehchan
            To:
            Smruti Ranjan Das
            S/O: Purna Chandra Das
            DOB: 12/04/1992
            Gender: MALE
            Address: At- Bhamarmal, Dist- Khordha, PIN- 752055
            1234 5678 9012
        """.trimIndent()

        val result = DocumentClassifier.classifyDocument(text = aadhaarOcr)

        assertEquals(DocType.AADHAAR, result.docType)
        // Ensure "GOVERNMENT OF INDIA" or "UNIQUE IDENTIFICATION" are NOT extracted as person names
        val extractedNames = result.extractedPersonDetails.map { it.name }
        assertFalse(extractedNames.any { it.contains("Government", ignoreCase = true) })
        assertFalse(extractedNames.any { it.contains("Identification", ignoreCase = true) })
        assertFalse(extractedNames.any { it.contains("Authority", ignoreCase = true) })
        assertFalse(extractedNames.any { it.contains("Aadhaar", ignoreCase = true) })

        val primaryPerson = result.extractedPersonDetails.firstOrNull()
        assertNotNull("Must extract primary person", primaryPerson)
        assertEquals("Smruti Ranjan Das", primaryPerson?.name)
        assertEquals("Purna Chandra Das", primaryPerson?.fatherOrSpouseName)
        assertEquals("XXXX-XXXX-9012", result.aadhaarMasked)
    }

    @Test
    fun testOdiaLandholderNameWithTransliterationAndOdiaNumerals() {
        // Khatiyan containing Odia numerals for Khata and Plot
        val landOcr = """
            ଓଡ଼ିଶା ସରକାର
            ଭୂ-ଅଭିଲେଖ (ଖତିୟାନ / Record of Rights)
            ତହସିଲ: ଜଟଣୀ | ମୌଜା: ଭମରମାଲ
            ଖାତା ନଂ: ୫୧୨
            ପ୍ଲଟ ନଂ: ୧୦୧/୨୦୪
            ରୟତ ସ୍ୱତ୍ୱ / ପ୍ରଜା ବିବରଣୀ:
            ୧. ରବୀନ୍ଦ୍ର କୁମାର ସାହୁ ପିତା ଗୋପୀନାଥ ସାହୁ
            କିସମ: ଶାରଦ | ରକବା: ୧.୨୫ ଏକର
        """.trimIndent()

        val result = DocumentClassifier.classifyDocument(text = landOcr)

        assertEquals(DocType.LAND_RECORD, result.docType)
        // Odia numeral ୫୧୨ must be converted to Arabic "512"
        assertEquals("512", result.khataNumber)
        // Odia numeral ୧୦୧/୨୦୪ must be converted to "101/204"
        assertEquals("101/204", result.plotNumber)
        assertEquals("ଭମରମାଲ", result.mouza)

        val person = result.extractedPersonDetails.firstOrNull()
        assertNotNull("Landholder must be extracted", person)
        assertEquals("ରବୀନ୍ଦ୍ର କୁମାର ସାହୁ", person?.name)
        assertEquals("ଗୋପୀନାଥ ସାହୁ", person?.fatherOrSpouseName)

        val transliterated = OdiaTransliterationEngine.transliterateOdiaToEnglish(person!!.name)
        assertEquals("Rabeendra Kumar Sahu", transliterated)
        val normalized = OdiaTransliterationEngine.normalizeForMatching(person.name)
        assertEquals("rabindra kumar sahu", normalized)
    }

    @Test
    fun testLandRecordPageWithMultiplePeopleAndTenureModifiers() {
        val multiPersonRoR = """
            ଓଡ଼ିଶା ସରକାର ଖତିୟାନ
            ତହସିଲ: ଭୁବନେଶ୍ୱର | ମୌଜା: ପଟିଆ
            ଖାତା ନଂ: ୭୮
            --------------------------------------------------
            ରୟତ ନାମ ଓ ସ୍ଥିତିବାନ ବିବରଣୀ:
            ୧. କୃଷ୍ଣଚନ୍ଦ୍ର ନାୟକ ପିତା ବିଦ୍ୟାଧର ନାୟକ
            ୨. ଦେବେନ୍ଦ୍ର ନାୟକ ପିତା ବିଦ୍ୟାଧର ନାୟକ
            ୩. ପ୍ରମିଳା ନାୟକ ସ୍ୱାମୀ ଦେବେନ୍ଦ୍ର ନାୟକ
            --------------------------------------------------
            ପ୍ଲଟ ନଂ: ୪୫୦ | କିସମ: ବାଜେ ଫସଲ | ରକବା: ୨.୫୦ ଏକର
            ଚୌହଦୀ: ପୂର୍ବ-ରାସ୍ତା, ପଶ୍ଚିମ-ଜମି
            ମୋଟ ରକବା: ୨.୫୦ ଏକର
        """.trimIndent()

        val result = DocumentClassifier.classifyDocument(text = multiPersonRoR)

        assertEquals(DocType.LAND_RECORD, result.docType)
        assertEquals("78", result.khataNumber)
        assertEquals("450", result.plotNumber)

        val people = result.extractedPersonDetails
        assertEquals("Must extract exactly 3 co-owners from the page", 3, people.size)

        assertEquals("କୃଷ୍ଣଚନ୍ଦ୍ର ନାୟକ", people[0].name)
        assertEquals("ବିଦ୍ୟାଧର ନାୟକ", people[0].fatherOrSpouseName)

        assertEquals("ଦେବେନ୍ଦ୍ର ନାୟକ", people[1].name)
        assertEquals("ବିଦ୍ୟାଧର ନାୟକ", people[1].fatherOrSpouseName)

        assertEquals("ପ୍ରମିଳା ନାୟକ", people[2].name)
        assertEquals("ଦେବେନ୍ଦ୍ର ନାୟକ", people[2].fatherOrSpouseName)
    }

    @Test
    fun testRecordWithNoReadableNameDoesNotCrashOrExtractNoise() {
        val blankOrNoisyReceipt = """
            RECEIPT / CASH MEMO
            Tax Invoice No: 99823
            Date: 12-10-2023
            Item 1: Cement 50 bags
            Subtotal: 18,500
            GST: 3,330
            Total: 21,830
            Thank you, visit again!
        """.trimIndent()

        val result = DocumentClassifier.classifyDocument(text = blankOrNoisyReceipt)

        // Must NOT classify receipt as land record or aadhaar
        assertEquals(DocType.UNKNOWN, result.docType)
        assertTrue(result.extractedPersons.isEmpty())
        assertTrue(result.extractedPersonDetails.isEmpty())
        assertNull(result.khataNumber)
        assertNull(result.plotNumber)
    }

    @Test
    fun testPdfContainingMixedPageTypes() {
        // Page 1: Land record
        val page1Khatiyan = "ଓଡ଼ିଶା ସରକାର ଖତିୟାନ ମୌଜା: ଭମରମାଲ ଖାତା: ୧୨ ରୟତ: ଓମକାର ରଥ ପିତା ଜଗନ୍ନାଥ ରଥ"
        val doc1 = DocumentClassifier.classifyDocument(text = page1Khatiyan)
        assertEquals(DocType.LAND_RECORD, doc1.docType)
        assertEquals("12", doc1.khataNumber)

        // Page 2: Aadhaar Card inside the same PDF bundle
        val page2Aadhaar = "Government of India Unique Identification Authority of India Omkar Rath\nDOB: 15/07/1985\n9876 5432 1098"
        val doc2 = DocumentClassifier.classifyDocument(text = page2Aadhaar)
        assertEquals(DocType.AADHAAR, doc2.docType)
        assertEquals("XXXX-XXXX-1098", doc2.aadhaarMasked)

        // Page 3: Blank separator page
        val page3Blank = ""
        val doc3 = DocumentClassifier.classifyDocument(text = page3Blank)
        assertEquals(DocType.FAILED_OCR, doc3.docType)
    }

    @Test
    fun testTrueMatchVsSimilarLookingIncorrectMatchWithSurnameConflict() {
        val aadhaarOmkarRath = PersonRecord(
            id = "a_omkar",
            documentId = "doc_a_omkar",
            originalName = "Omkar Rath",
            script = ScriptType.LATIN,
            transliteratedName = "Omkar Rath",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("Omkar Rath"),
            fathersOrSpouseName = "Jagannath Rath"
        )

        // TRUE MATCH: Same name, same surname, same father, same mouza
        val landOmkarRath = PersonRecord(
            id = "l_omkar_true",
            documentId = "doc_l_omkar",
            originalName = "ଓମକାର ରଥ",
            script = ScriptType.ODIA,
            transliteratedName = "Omkar Rath",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ଓମକାର ରଥ"),
            fathersOrSpouseName = "ଜଗନ୍ନାଥ ରଥ"
        )

        val trueMatchEval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarOmkarRath,
            landPerson = landOmkarRath,
            aadhaarDoc = null,
            landDoc = null,
            hasMultipleCandidates = false
        )

        assertTrue("True match must have score >= 0.85: got ${trueMatchEval.score}", trueMatchEval.score >= 0.85f)
        assertTrue("True match must contain supporting evidence", trueMatchEval.supportingEvidence.isNotEmpty())
        assertTrue("True match must have zero conflicting evidence", trueMatchEval.conflictingEvidence.isEmpty())
        assertFalse("Must never auto-approve even a high score match", trueMatchEval.isAutoApprovable)

        // SIMILAR-LOOKING BUT INCORRECT MATCH: Same first name "Omkar", but different surname "Patnaik"
        val landOmkarPatnaik = PersonRecord(
            id = "l_omkar_false",
            documentId = "doc_l_patnaik",
            originalName = "ଓମକାର ପଟ୍ଟନାୟକ",
            script = ScriptType.ODIA,
            transliteratedName = "Omkar Patnaik",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ଓମକାର ପଟ୍ଟନାୟକ"),
            fathersOrSpouseName = "କୈଳାସ ପଟ୍ଟନାୟକ"
        )

        val falseMatchEval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarOmkarRath,
            landPerson = landOmkarPatnaik,
            aadhaarDoc = null,
            landDoc = null,
            hasMultipleCandidates = false
        )

        assertTrue("Surname conflict must penalize score to < 0.60: got ${falseMatchEval.score}", falseMatchEval.score < 0.60f)
        assertTrue("Conflicting evidence must report conflicting surnames", falseMatchEval.conflictingEvidence.any { it.contains("Surname conflict", ignoreCase = true) })
    }

    @Test
    fun testNoValidCandidateShouldBeSuggestedForUnrelatedNames() {
        val aadhaarSatyabrata = PersonRecord(
            id = "a_satya",
            documentId = "doc_a_satya",
            originalName = "Satyabrata Patnaik",
            script = ScriptType.LATIN,
            transliteratedName = "Satyabrata Patnaik",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("Satyabrata Patnaik")
        )

        val landBikashBehera = PersonRecord(
            id = "l_bikash",
            documentId = "doc_l_bikash",
            originalName = "ବିକାଶ ବେହେରା",
            script = ScriptType.ODIA,
            transliteratedName = "Bikash Behera",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ବିକାଶ ବେହେରା")
        )

        val eval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarSatyabrata,
            landPerson = landBikashBehera,
            aadhaarDoc = null,
            landDoc = null,
            hasMultipleCandidates = false
        )

        assertTrue("Score for unrelated names must be < 0.40: got ${eval.score}", eval.score < 0.40f)
    }

    @Test
    fun testPersistenceOfUserCorrectionsAndVerifiedStatus() {
        val userVerifiedPair = MatchPairRecord(
            id = "pair_verified_123",
            aadhaarDocId = "doc_a_1",
            landDocId = "doc_l_1",
            aadhaarPersonId = "person_a_1",
            landPersonId = "person_l_1",
            aadhaarName = "Bijay Mohapatra",
            landNameOriginal = "ବିଜୟ କୁମାର ମହାପାତ୍ର",
            landNameTransliterated = "Bijay Kumar Mohapatra",
            matchConfidence = 0.95f,
            matchExplanation = "Manually verified by operator",
            status = MatchStatus.VERIFIED,
            isManual = true
        )

        assertEquals(MatchStatus.VERIFIED, userVerifiedPair.status)
        assertTrue(userVerifiedPair.isManual)

        // Reprocessing must preserve VERIFIED status
        val statusAfterReprocess = userVerifiedPair.status
        assertEquals("User-verified pair must remain VERIFIED across reprocesses", MatchStatus.VERIFIED, statusAfterReprocess)
    }

    @Test
    fun testMatchingDiagnosticsComputation() {
        val diagnostics = MatchingDiagnostics(
            eligibleAadhaarCount = 95,
            eligibleLandCount = 70,
            missingNameAadhaarCount = 4,
            missingNameLandCount = 3,
            totalCandidatesEvaluated = 95 * 70, // 6650
            candidatesRejectedByFilters = 6600,
            candidatesSuggested = 15,
            candidatesNeedsReview = 35,
            candidatesVerified = 0
        )

        assertEquals(95, diagnostics.eligibleAadhaarCount)
        assertEquals(70, diagnostics.eligibleLandCount)
        assertEquals(6650, diagnostics.totalCandidatesEvaluated)
        assertEquals(6600, diagnostics.candidatesRejectedByFilters)
        assertEquals(15, diagnostics.candidatesSuggested)
        assertEquals(35, diagnostics.candidatesNeedsReview)
    }
}
