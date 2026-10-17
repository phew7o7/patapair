package com.example

import com.example.engine.DocumentClassifier
import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.PersonRecord
import com.example.model.ScriptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class OcrAndCrossScriptTest {

    @Test
    fun testSyntheticOdiaImageCreationAndClassification() {
        val syntheticOdiaLandRecord = """
            ଓଡ଼ିଶା ସରକାର
            ଭୂ-ଅଭିଲେଖ (ଖତିୟାନ / Record of Rights)
            ଜିଲ୍ଲା: ଖୋର୍ଦ୍ଧା | ତହସିଲ: ଜଟଣୀ | ମୌଜା: କୁଦିଆରୀ
            ଖାତା ନଂ: 142
            ରୟତ: ଓମକାର ରଥ
            --------------------------------------------------
            ପ୍ଲଟ ନଂ    | କିସମ    | ରକବା (ଏକର-ଡେସିମିଲ)
            204/1105   | ଶାରଦ-୨  | ୦.୪୫
            --------------------------------------------------
        """.trimIndent()

        val classification = DocumentClassifier.classifyDocument(
            text = syntheticOdiaLandRecord,
            hasTableGridVisuals = true
        )

        assertEquals("Document must be classified as LAND_RECORD", DocType.LAND_RECORD, classification.docType)
        assertTrue("Classification confidence should be >= 0.70", classification.confidence >= 0.70f)
        assertEquals("Khata number must be 142", "142", classification.khataNumber)
        assertFalse("Extracted persons must not be empty", classification.extractedPersons.isEmpty())
        assertEquals("Extracted person must be ଓମକାର ରଥ", "ଓମକାର ରଥ", classification.extractedPersons.first().first)
    }

    @Test
    fun testAadhaarCardClassificationAndMasking() {
        val rawAadhaarText = """
            Government of India
            Unique Identification Authority of India (UIDAI)
            Enrollment No: 2041/11902/88124
            Omkar Rath
            DOB: 14/08/1988
            Gender: Male
            9876 5432 1098
            help@uidai.gov.in
        """.trimIndent()

        val classification = DocumentClassifier.classifyDocument(text = rawAadhaarText)

        assertEquals("Document must be classified as AADHAAR", DocType.AADHAAR, classification.docType)
        assertNotNull("Aadhaar mask should be present", classification.aadhaarMasked)
        assertEquals("Full 12-digit number must be redacted", "XXXX-XXXX-1098", classification.aadhaarMasked)
        assertFalse("Extracted persons must contain Omkar Rath", classification.extractedPersons.isEmpty())
        assertEquals("Omkar Rath", classification.extractedPersons.first().first)
    }

    @Test
    fun testUnrelatedTableNotClassifiedAsLandRecord() {
        val invoiceText = """
            SUPERMARKET CASH RECEIPT
            Tax Invoice / Bill No: 9942
            --------------------------------------------------
            Item Name       | Qty | Price | Total
            Cooking Oil     | 2   | 140   | 280.00
            Basmati Rice    | 5   | 85    | 425.00
            --------------------------------------------------
            Total Amount: Rs 705.00
            GSTIN: 21ABCDE1234F1Z5
        """.trimIndent()

        val classification = DocumentClassifier.classifyDocument(
            text = invoiceText,
            hasTableGridVisuals = true
        )

        assertEquals("Invoice table must NOT be classified as LAND_RECORD", DocType.UNKNOWN, classification.docType)
        assertTrue(classification.reason.contains("invoice", ignoreCase = true) || classification.reason.contains("receipt", ignoreCase = true))
    }

    @Test
    fun testOcrFailureRemainsVisiblyFailed() {
        val failedOcrResult = DocumentClassifier.classifyDocument(
            text = "",
            ocrError = "Tesseract traineddata missing or unreadable"
        )

        assertEquals("Failed OCR must produce DocType.FAILED_OCR", DocType.FAILED_OCR, failedOcrResult.docType)
        assertTrue("Reason should report actual error", failedOcrResult.reason.contains("Tesseract"))
    }

    @Test
    fun testMultipagePdfTraceability() {
        val parentPdfUri = "content://com.android.providers.media.documents/document/101"
        val page1 = DocumentRecord(
            id = "doc_p1",
            uri = "$parentPdfUri#page=1",
            originalFilename = "Khatiyan_Khordha.pdf",
            pdfPageNumber = 1,
            totalPdfPages = 2,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "Hybrid Tesseract",
            ocrLanguages = "ori",
            rawExtractedText = "ଖତିୟାନ ଖାତା ୧",
            redactedText = "ଖତିୟାନ ଖାତା ୧"
        )
        val page2 = DocumentRecord(
            id = "doc_p2",
            uri = "$parentPdfUri#page=2",
            originalFilename = "Khatiyan_Khordha.pdf",
            pdfPageNumber = 2,
            totalPdfPages = 2,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "Hybrid Tesseract",
            ocrLanguages = "ori",
            rawExtractedText = "ଖତିୟାନ ଖାତା ୨",
            redactedText = "ଖତିୟାନ ଖାତା ୨"
        )

        assertEquals("Khatiyan_Khordha.pdf", page1.originalFilename)
        assertEquals(1, page1.pdfPageNumber)
        assertEquals(2, page2.pdfPageNumber)
        assertEquals(2, page1.totalPdfPages)
    }

    @Test
    fun testManualCorrectionPersistenceAcrossReprocessing() {
        val personId = "person_test_1"
        val docId = "doc_test_1"

        var storedPerson = PersonRecord(
            id = personId,
            documentId = docId,
            originalName = "ଓମକର ରଥ", // slightly garbled OCR
            script = ScriptType.ODIA,
            transliteratedName = "Omkara Ratha",
            normalizedName = "omkara ratha"
        )

        // User corrects name manually
        storedPerson = storedPerson.copy(
            originalName = "ଓମକାର ରଥ",
            transliteratedName = "Omkar Rath",
            normalizedName = "omkar rath",
            isManuallyCorrected = true
        )

        // Reprocessing: when manually corrected, preserved values are retained
        val incomingReprocessed = PersonRecord(
            id = personId,
            documentId = docId,
            originalName = "ଓମକର ରଥ",
            script = ScriptType.ODIA,
            transliteratedName = "Omkara Ratha",
            normalizedName = "omkara ratha"
        )

        val merged = if (storedPerson.isManuallyCorrected) {
            incomingReprocessed.copy(
                originalName = storedPerson.originalName,
                transliteratedName = storedPerson.transliteratedName,
                normalizedName = storedPerson.normalizedName,
                isManuallyCorrected = true
            )
        } else {
            incomingReprocessed
        }

        assertEquals("ଓମକାର ରଥ", merged.originalName)
        assertEquals("Omkar Rath", merged.transliteratedName)
        assertTrue(merged.isManuallyCorrected)
    }

    @Test
    fun testManualPairingWorkflow() {
        val aadhaarPerson = PersonRecord(
            id = "p_aadhaar_1",
            documentId = "d_aadhaar_1",
            originalName = "Omkar Rath",
            script = ScriptType.LATIN,
            transliteratedName = "Omkar Rath",
            normalizedName = "omkar rath"
        )
        val landPerson = PersonRecord(
            id = "p_land_1",
            documentId = "d_land_1",
            originalName = "ଓମକାର ରଥ",
            script = ScriptType.ODIA,
            transliteratedName = "Omkar Rath",
            normalizedName = "omkar rath"
        )

        val match = MatcherEngine.compareNames(aadhaarPerson.normalizedName, landPerson.normalizedName)
        val pairId = UUID.randomUUID().toString()
        val pair = MatchPairRecord(
            id = pairId,
            aadhaarDocId = "d_aadhaar_1",
            landDocId = "d_land_1",
            aadhaarPersonId = "p_aadhaar_1",
            landPersonId = "p_land_1",
            aadhaarName = aadhaarPerson.originalName,
            landNameOriginal = landPerson.originalName,
            landNameTransliterated = landPerson.transliteratedName,
            matchConfidence = match.score,
            matchExplanation = "Manual pair test",
            status = MatchStatus.VERIFIED,
            isManual = true,
            personCode = "Person_001"
        )

        assertEquals(MatchStatus.VERIFIED, pair.status)
        assertTrue(pair.isManual)
        assertNotNull(pair.personCode)
        assertTrue("Person code should follow Person_001 format: ${pair.personCode}", pair.personCode?.startsWith("Person_") == true)
        assertTrue("Match score should be high for Omkar Rath: ${match.score}", match.score >= 0.90f)
    }

    @Test
    fun testMissingOdiaModelProducesWaitingForModelStatusNotFailedOcr() {
        val waitingResult = DocumentClassifier.classifyDocument(
            text = "",
            isWaitingForModel = true
        )

        assertEquals("When waiting for model, docType must NOT be FAILED_OCR", DocType.UNKNOWN, waitingResult.docType)
        assertEquals("Reason must be Waiting for OCR model", "Waiting for OCR model", waitingResult.reason)
        assertTrue(waitingResult.rulesFired.any { it.ruleName == "WAITING_FOR_OCR_MODEL" })
    }

    @Test
    fun testEnglishLandRecordClassificationWithoutTesseract() {
        val englishLandRecordText = """
            Government of Odisha
            Revenue & Disaster Management Department
            Record of Rights (Khatiyan)
            District: Khordha | Tahasil: Jatni | Mouza: Kudiary
            Khata No: 142
            Rayat / Pattadar: Omkar Rath
            Plot No: 204/1105 | Kisam: Sarada-2 | Area: 0.45 Acres
        """.trimIndent()

        val classification = DocumentClassifier.classifyDocument(
            text = englishLandRecordText,
            hasTableGridVisuals = true,
            isWaitingForModel = false
        )

        assertEquals(DocType.LAND_RECORD, classification.docType)
        assertEquals("142", classification.khataNumber)
        assertEquals("204/1105", classification.plotNumber)
        assertEquals("Jatni", classification.tahasil)
        assertFalse("Extracted persons should contain Omkar Rath", classification.extractedPersons.isEmpty())
        assertEquals("Omkar Rath", classification.extractedPersons.first().first)
    }

    @Test
    fun testRedactSensitiveIdentifiers() {
        val text = "Customer Aadhaar: 1234 5678 9012, verified."
        val redacted = com.example.ocr.DocumentOcrEngine.redactSensitiveIdentifiers(text)
        assertEquals("Customer Aadhaar: XXXX-XXXX-9012, verified.", redacted)
    }
}
