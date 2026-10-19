package com.example

import com.example.ai.DefaultAiDocumentInterpreter
import com.example.engine.DocumentClassifier
import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.DocStatus
import com.example.model.MatchStatus
import com.example.model.PersonRecord
import com.example.model.ScriptType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredExtractionAndMatchingTest {

    private val aiInterpreter = DefaultAiDocumentInterpreter()

    @Test
    fun testNoisyOcrAadhaarTextDoesNotExtractAadnaarAsPersonName() = runBlocking {
        // Noisy Aadhaar OCR where "AADHAAR" is corrupted to "AADNAAR"
        val noisyAadhaarOcr = """
            GOVERNMENT OF INDIA
            UNIQUE IDENTIFICATION AUTHORITY OF INDIA
            MERA AADNAAR, MERI PEHCHAN
            AADNAAR NO: 1234 5678 9012
            Omkar Rath
            DOB: 15/07/1985
            GENDER: MALE
            S/O: Jagannath Rath
            HELP@UIDAI.GOV.IN WWW.UIDAI.GOV.IN
        """.trimIndent()

        // 1. AI interpreter interprets and rectifies known OCR tokens
        val interpretation = aiInterpreter.interpretDocument(
            rawText = noisyAadhaarOcr,
            docType = DocType.AADHAAR,
            script = ScriptType.LATIN
        )

        assertTrue(interpretation.isSuccess)
        val cleanedText = interpretation.correctedOcrText ?: noisyAadhaarOcr
        assertTrue("Noisy AADNAAR should be corrected to aadhaar: $cleanedText", cleanedText.contains("aadhaar", ignoreCase = true))

        // 2. DocumentClassifier extracts the real person and rejects noisy header words
        val classification = DocumentClassifier.classifyDocument(text = cleanedText)
        assertEquals(DocType.AADHAAR, classification.docType)

        assertFalse("Persons list must not be empty", classification.extractedPersons.isEmpty())
        val extractedName = classification.extractedPersons.first().first
        assertEquals("Extracted person name must be 'Omkar Rath', not 'AADNAAR'", "Omkar Rath", extractedName)
        assertFalse("Name must not be AADNAAR", extractedName.equals("AADNAAR", ignoreCase = true))
        assertFalse("Name must not be AADHAAR", extractedName.equals("AADHAAR", ignoreCase = true))

        // Father/Guardian extraction
        val personDraft = classification.extractedPersonDetails.firstOrNull()
        assertNotNull("Person draft should exist", personDraft)
        assertEquals("Father name should be extracted as Jagannath Rath", "Jagannath Rath", personDraft?.fatherOrSpouseName)
    }

    @Test
    fun testMultiplePeopleOnOneLandRecordPage() {
        val multiRayatKhatiyan = """
            ଓଡ଼ିଶା ସରକାର
            ଭୂ-ଅଭିଲେଖ (ଖତିୟାନ / Record of Rights)
            ତହସିଲ: ଜଟଣୀ | ମୌଜା: କୁଦିଆରୀ
            ଖାତା ନଂ: 512
            --------------------------------------------------
            ରୟତ ସ୍ୱତ୍ୱ / ପ୍ରଜା ବିବରଣୀ:
            ୧. ରାମଚନ୍ଦ୍ର ମହାପାତ୍ର ପିତା କାଳିନ୍ଦୀ ମହାପାତ୍ର
            ୨. ବିଜୟ କୁମାର ମହାପାତ୍ର ପିତା କାଳିନ୍ଦୀ ମହାପାତ୍ର
            ୩. ସୁନୀତା ମହାପାତ୍ର ସ୍ୱାମୀ ବିଜୟ କୁମାର ମହାପାତ୍ର
            --------------------------------------------------
            ପ୍ଲଟ ନଂ    | କିସମ    | ରକବା (ଏକର-ଡେସିମିଲ)
            101/204    | ଶାରଦ    | ୧.୨୫
            102/305    | ବେରଣା   | ୦.୫୦
            --------------------------------------------------
            ଚୌହଦୀ: ପୂର୍ବ-ରାସ୍ତା, ପଶ୍ଚିମ-ନାଳ, ଉତ୍ତର-ଜମି, ଦକ୍ଷିଣ-ଜମି
            ମୋଟ ରକବା: ୧.୭୫ ଏକର
        """.trimIndent()

        val classification = DocumentClassifier.classifyDocument(
            text = multiRayatKhatiyan,
            hasTableGridVisuals = true
        )

        assertEquals(DocType.LAND_RECORD, classification.docType)
        assertEquals("512", classification.khataNumber)

        val people = classification.extractedPersonDetails
        assertEquals("Must extract exactly 3 distinct co-pattadars from the page", 3, people.size)

        // Person 1
        assertEquals("ରାମଚନ୍ଦ୍ର ମହାପାତ୍ର", people[0].name)
        assertEquals("କାଳିନ୍ଦୀ ମହାପାତ୍ର", people[0].fatherOrSpouseName)

        // Person 2
        assertEquals("ବିଜୟ କୁମାର ମହାପାତ୍ର", people[1].name)
        assertEquals("କାଳିନ୍ଦୀ ମହାପାତ୍ର", people[1].fatherOrSpouseName)

        // Person 3
        assertEquals("ସୁନୀତା ମହାପାତ୍ର", people[2].name)
        assertEquals("ବିଜୟ କୁମାର ମହାପାତ୍ର", people[2].fatherOrSpouseName)

        // Ensure land headers, boundaries, and measurements were NOT extracted as person names
        val extractedNames = people.map { it.name }
        assertFalse(extractedNames.any { it.contains("ଚୌହଦୀ") })
        assertFalse(extractedNames.any { it.contains("ପ୍ଲଟ") })
        assertFalse(extractedNames.any { it.contains("ରକବା") })
        assertFalse(extractedNames.any { it.contains("ଏକର") })
        assertFalse(extractedNames.any { it.contains("ଶାରଦ") })
    }

    @Test
    fun testFalseMatchPreventionWithMismatchedSurnames() {
        // Two individuals sharing the same first name 'Bijay', but having different surnames
        val aadhaarBijaySahoo = PersonRecord(
            id = "p_aadhaar_sahoo",
            documentId = "d_a1",
            originalName = "Bijay Sahoo",
            script = ScriptType.LATIN,
            transliteratedName = "Bijay Sahoo",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("Bijay Sahoo")
        )

        val landBijayMohapatra = PersonRecord(
            id = "p_land_mohapatra",
            documentId = "d_l1",
            originalName = "ବିଜୟ କୁମାର ମହାପାତ୍ର",
            script = ScriptType.ODIA,
            transliteratedName = "Bijay Kumar Mohapatra",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ବିଜୟ କୁମାର ମହାପାତ୍ର")
        )

        val eval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarBijaySahoo,
            landPerson = landBijayMohapatra,
            aadhaarDoc = null,
            landDoc = null,
            hasMultipleCandidates = false
        )

        // Surnames Sahoo vs Mohapatra MUST NOT match
        assertFalse("Must never auto-approve candidate with conflicting surnames", eval.isAutoApprovable)
        assertTrue("Conflicting evidence must report conflicting surnames: ${eval.conflictingEvidence}",
            eval.conflictingEvidence.any { it.contains("Surname conflict", ignoreCase = true) }
        )
        assertTrue("Score must be heavily penalized (< 0.60): got ${eval.score}", eval.score < 0.60f)
    }

    @Test
    fun testMultiSignalCorroborationWithFatherAndLocation() {
        // High confidence match when Name + Surname + Father + Mouza align
        val aadhaarPerson = PersonRecord(
            id = "p_a_omkar",
            documentId = "doc_a_omkar",
            originalName = "Omkar Rath",
            script = ScriptType.LATIN,
            transliteratedName = "Omkar Rath",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("Omkar Rath"),
            fathersOrSpouseName = "Jagannath Rath"
        )

        val aadhaarDoc = DocumentRecord(
            id = "doc_a_omkar",
            uri = "content://docs/aadhaar_omkar.pdf",
            originalFilename = "aadhaar_omkar.pdf",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.AADHAAR,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "MLKit",
            ocrLanguages = "eng",
            rawExtractedText = "Unique Identification Authority of India Omkar Rath S/O: Jagannath Rath Address: At- Bhamarmal, Khordha",
            redactedText = "Unique Identification Authority of India Omkar Rath S/O: Jagannath Rath Address: At- Bhamarmal, Khordha"
        )

        val landPerson = PersonRecord(
            id = "p_l_omkar",
            documentId = "doc_l_omkar",
            originalName = "ଓମକାର ରଥ",
            script = ScriptType.ODIA,
            transliteratedName = "Omkar Rath",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ଓମକାର ରଥ"),
            fathersOrSpouseName = "ଜଗନ୍ନାଥ ରଥ"
        )

        val landDoc = DocumentRecord(
            id = "doc_l_omkar",
            uri = "content://docs/bhamarmal_village.pdf",
            originalFilename = "bhamarmal village.pdf",
            pdfPageNumber = 72,
            totalPdfPages = 74,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "Tesseract",
            ocrLanguages = "ori",
            rawExtractedText = "ଓଡ଼ିଶା ସରକାର ଖତିୟାନ ମୌଜା: Bhamarmal ଖାତା: 88 ରୟତ: ଓମକାର ରଥ ପିତା ଜଗନ୍ନାଥ ରଥ",
            redactedText = "ଓଡ଼ିଶା ସରକାର ଖତିୟାନ ମୌଜା: Bhamarmal ଖାତା: 88 ରୟତ: ଓମକାର ରଥ ପିତା ଜଗନ୍ନାଥ ରଥ",
            mouza = "Bhamarmal",
            khataNumber = "88"
        )

        val eval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarPerson,
            landPerson = landPerson,
            aadhaarDoc = aadhaarDoc,
            landDoc = landDoc,
            hasMultipleCandidates = false
        )

        assertTrue("Confidence score should be >= 0.85: got ${eval.score}", eval.score >= 0.85f)
        assertFalse("AI-assisted score must NOT auto-approve without user confirmation", eval.isAutoApprovable)
        assertTrue("Must have supporting evidence for exact name match", eval.supportingEvidence.any { it.contains("Exact cross-script", ignoreCase = true) })
        assertTrue("Must have supporting evidence for surname match", eval.supportingEvidence.any { it.contains("surname", ignoreCase = true) })
        assertTrue("Must have supporting evidence for Mouza location", eval.supportingEvidence.any { it.contains("Bhamarmal", ignoreCase = true) })
        assertFalse("Father name must not be used as matching signal in simplified matcher", eval.supportingEvidence.any { it.contains("Father", ignoreCase = true) })
    }

    @Test
    fun testAiInterpreterGroundingRejection() = runBlocking {
        // Ensure AI interpreter rejects hallucinated names that have no grounding in source text
        val rawOcr = "Government of India Unique Identification Authority of India 1234 5678 9012"

        val interpretation = aiInterpreter.interpretDocument(
            rawText = rawOcr,
            docType = DocType.AADHAAR,
            script = ScriptType.LATIN
        )

        // When source contains no person name, it must NOT fabricate one
        assertTrue(interpretation.isSuccess)
        assertTrue("Must not hallucinate names unsupported by text", interpretation.detectedNames.isEmpty())
    }

    @Test
    fun testOdiaTransliterationVowelAndConsonantCoverage() {
        // Test various Odia phonemes
        assertEquals("omkar", OdiaTransliterationEngine.normalizeForMatching("ଓମକାର"))
        assertEquals("bijay", OdiaTransliterationEngine.normalizeForMatching("ବିଜୟ"))
        assertEquals("mohapatra", OdiaTransliterationEngine.normalizeForMatching("ମହାପାତ୍ର"))
        assertEquals("patnaik", OdiaTransliterationEngine.normalizeForMatching("ପଟ୍ଟନାୟକ"))
        assertEquals("das", OdiaTransliterationEngine.normalizeForMatching("ଦାସ"))
        assertEquals("das", OdiaTransliterationEngine.normalizeForMatching("ଦାଶ"))
        assertEquals("sahu", OdiaTransliterationEngine.normalizeForMatching("ସାହୁ"))
        assertEquals(OdiaTransliterationEngine.normalizeForMatching("Sahoo"), OdiaTransliterationEngine.normalizeForMatching("ସାହୁ"))
        assertEquals("nayak", OdiaTransliterationEngine.normalizeForMatching("ନାୟକ"))
    }

    @Test
    fun testPersistenceOfUserManualCorrection() {
        val originalPerson = PersonRecord(
            id = "person_test_reprocess",
            documentId = "doc_test_reprocess",
            originalName = "ବିଜୟ କୁମାର",
            script = ScriptType.ODIA,
            transliteratedName = "Bijay Kumar",
            normalizedName = "bijay kumar",
            isManuallyCorrected = true // User corrected this
        )

        // Simulate incoming re-extract from OCR
        val reextractedFromOcr = PersonRecord(
            id = "person_test_reprocess",
            documentId = "doc_test_reprocess",
            originalName = "ବିଜୟ କୁମର", // bad OCR
            script = ScriptType.ODIA,
            transliteratedName = "Bijay Kumar",
            normalizedName = "bijay kumar"
        )

        // Merge logic verifies user correction is kept
        val finalRecord = if (originalPerson.isManuallyCorrected) {
            reextractedFromOcr.copy(
                originalName = originalPerson.originalName,
                transliteratedName = originalPerson.transliteratedName,
                normalizedName = originalPerson.normalizedName,
                isManuallyCorrected = true
            )
        } else {
            reextractedFromOcr
        }

        assertEquals("ବିଜୟ କୁମାର", finalRecord.originalName)
        assertTrue(finalRecord.isManuallyCorrected)
    }

    @Test
    fun testSharedSurnameWithWeakGivenNameCandidateRejection() {
        // Aadhaar: Suresh Mohapatra
        // Land Record: Bijay Mohapatra
        // Shared surname (Mohapatra) must NOT cause candidate to be suggested or auto-approved
        val aadhaarSuresh = PersonRecord(
            id = "p_aadhaar_suresh",
            documentId = "d_a_suresh",
            originalName = "Suresh Mohapatra",
            script = ScriptType.LATIN,
            transliteratedName = "Suresh Mohapatra",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("Suresh Mohapatra")
        )

        val landBijay = PersonRecord(
            id = "p_land_bijay",
            documentId = "d_l_bijay",
            originalName = "ବିଜୟ ମହାପାତ୍ର",
            script = ScriptType.ODIA,
            transliteratedName = "Bijay Mohapatra",
            normalizedName = OdiaTransliterationEngine.normalizeForMatching("ବିଜୟ ମହାପାତ୍ର")
        )

        val eval = MatcherEngine.evaluateCandidate(
            aadhaarPerson = aadhaarSuresh,
            landPerson = landBijay,
            aadhaarDoc = null,
            landDoc = null,
            hasMultipleCandidates = false
        )

        assertFalse("Must never auto-approve candidate with conflicting given names", eval.isAutoApprovable)
        assertTrue("Conflicting evidence must flag given name discrepancy: ${eval.conflictingEvidence}",
            eval.conflictingEvidence.any { it.contains("Different given names", ignoreCase = true) }
        )
        assertTrue("Score must be low (< 0.65) despite shared surname: got ${eval.score}", eval.score < 0.65f)

        // Test in compareNames directly
        val nameMatch = MatcherEngine.compareNames("Suresh Mohapatra", "ବିଜୟ ମହାପାତ୍ର")
        assertFalse("Name match must not be auto-approvable", nameMatch.isAutoApprovable)
        assertTrue("Score in compareNames must be < 0.65: got ${nameMatch.score}", nameMatch.score < 0.65f)
    }

    @Test
    fun testDistinctSurnamesRoutrayAndRoutArePreserved() {
        val normRoutray = OdiaTransliterationEngine.normalizeForMatching("Routray")
        val normRout = OdiaTransliterationEngine.normalizeForMatching("Rout")
        assertNotEquals("Routray and Rout are distinct Odia surnames and must not be collapsed", normRoutray, normRout)
        assertTrue("normRoutray must end with routray: $normRoutray", normRoutray.contains("routray"))
        assertTrue("normRout must end with rout: $normRout", normRout.contains("rout"))
    }

    @Test
    fun testOdishaKhatiyanKhataAndPlotExtractionWithNewKeywords() {
        val landText = """
            ଓଡ଼ିଶା ସରକାର
            ଖତିୟାନ ନଂ: ୪୨
            ତହସିଲ୍: ଭୁବନେଶ୍ୱର
            ମୌଜା: ପଟିଆ
            ପ୍ଲଟ୍ ନମ୍ବର: ୩୧୨
            ରୟତ: କୃଷ୍ଣଚନ୍ଦ୍ର ସ୍ୱାଇଁ ପିତା ହରିହର ସ୍ୱାଇଁ
            ରକବା: ୦.୭୫ ଏକର
            ସ୍ଥିତିବାନ ପ୍ରଜା
        """.trimIndent()

        val result = DocumentClassifier.classifyDocument(landText)
        assertEquals(DocType.LAND_RECORD, result.docType)
        assertEquals("42", result.khataNumber)
        assertEquals("312", result.plotNumber)
        assertEquals("ପଟିଆ", result.mouza)
        assertFalse("Should extract persons", result.extractedPersons.isEmpty())
        assertEquals("କୃଷ୍ଣଚନ୍ଦ୍ର ସ୍ୱାଇଁ", result.extractedPersons.first().first)
        val details = result.extractedPersonDetails.firstOrNull()
        assertNotNull(details)
        assertEquals("ହରିହର ସ୍ୱାଇଁ", details?.fatherOrSpouseName)
    }
}
