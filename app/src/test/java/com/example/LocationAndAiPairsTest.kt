package com.example

import com.example.engine.LocationExtractor
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAndAiPairsTest {

    @Test
    fun testLocationExtractorKesingaAndBhawanipatna() {
        val docKesingaOdia = DocumentRecord(
            id = "d1",
            uri = "uri://1",
            originalFilename = "pata_kesinga.pdf",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "tess",
            ocrLanguages = "ori",
            rawExtractedText = "ଓଡ଼ିଶା ସରକାର\nତହସିଲ: କେସିଙ୍ଗା\nମୌଜା: କେସିଙ୍ଗା\nଖାତା: 104\nରୟତ: ଦିଲୀପ ସାହୁ",
            redactedText = "",
            tahasil = "କେସିଙ୍ଗା",
            mouza = "କେସିଙ୍ଗା"
        )

        val docKesingaAadhaar = DocumentRecord(
            id = "d2",
            uri = "uri://2",
            originalFilename = "aadhaar_kesinga.jpg",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.AADHAAR,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "tess",
            ocrLanguages = "eng",
            rawExtractedText = "Government of India\nDillip Kumar Sahu\nAddress: At/Po: Kesinga, Dist: Kalahandi - 766012",
            redactedText = ""
        )

        val docBhawanipatna = DocumentRecord(
            id = "d3",
            uri = "uri://3",
            originalFilename = "pata_bhawani.pdf",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "tess",
            ocrLanguages = "ori",
            rawExtractedText = "ତହସିଲ: ଭବାନୀପାଟଣା\nମୌଜା: ଭବାନୀପାଟଣା\nଖାତା: 215",
            redactedText = "",
            mouza = "ଭବାନୀପାଟଣା"
        )

        val loc1 = LocationExtractor.extractCanonicalLocation(docKesingaOdia)
        val loc2 = LocationExtractor.extractCanonicalLocation(docKesingaAadhaar)
        val loc3 = LocationExtractor.extractCanonicalLocation(docBhawanipatna)

        assertEquals("Kesinga", loc1)
        assertEquals("Kesinga", loc2)
        assertEquals("Bhawanipatna", loc3)

        // Test grouping
        val groups = LocationExtractor.groupDocumentsAndPairs(
            documents = listOf(docKesingaOdia, docKesingaAadhaar, docBhawanipatna),
            matchPairs = emptyList()
        )

        val kesingaGroup = groups.firstOrNull { it.locationKey == "Kesinga" }
        assertNotNull("Kesinga group must exist", kesingaGroup)
        assertEquals(1, kesingaGroup!!.pataDocuments.size)
        assertEquals(1, kesingaGroup.aadhaarDocuments.size)
        assertEquals(2, kesingaGroup.totalDocuments)

        val bhawaniGroup = groups.firstOrNull { it.locationKey == "Bhawanipatna" }
        assertNotNull("Bhawanipatna group must exist", bhawaniGroup)
        assertEquals(1, bhawaniGroup!!.pataDocuments.size)
    }

    @Test
    fun testCrossScriptMatchingWithLocationBoost() {
        val pA = PersonRecord("pA", "dA", "Dillip Kumar Sahu", ScriptType.LATIN, "Dillip Kumar Sahu", "dilip kumar sahu")
        val pL = PersonRecord("pL", "dL", "ଦିଲୀପ ସାହୁ", ScriptType.ODIA, "Dillip Sahu", "dilip sahu")

        val docA = DocumentRecord("dA", "uri", "a.jpg", 1, 1, DocType.AADHAAR, DocStatus.DETECTED, "Tess", "eng", "Address: Kesinga, Kalahandi", "")
        val docL = DocumentRecord("dL", "uri", "l.jpg", 1, 1, DocType.LAND_RECORD, DocStatus.DETECTED, "Tess", "ori", "କେସିଙ୍ଗା", "",
            mouza = "କେସିଙ୍ଗା",
            tahasil = "କେସିଙ୍ଗା"
        )

        val match = MatcherEngine.evaluateCandidate(
            aadhaarPerson = pA,
            landPerson = pL,
            aadhaarDoc = docA,
            landDoc = docL,
            hasMultipleCandidates = false
        )

        assertTrue("Confidence score should be high (>= 0.75): got ${match.score}", match.score >= 0.75f)
        assertTrue("Must have location match signal", match.signals.any { it.signalName == "LOCATION_MATCH" })
        assertTrue("Must have supporting evidence for location", match.supportingEvidence.any { it.contains("corroborated", ignoreCase = true) })
    }

    @Test
    fun testAllDiverseLocationsExtractionAndGrouping() {
        val docPuri = DocumentRecord("dP", "u1", "pata_puri.pdf", 1, 1, DocType.LAND_RECORD, DocStatus.DETECTED, "tess", "ori", "ଓଡ଼ିଶା ସରକାର\nତହସିଲ: ପୁରୀ\nମୌଜା: ବଳଗଣ୍ଡି", "", tahasil = "ପୁରୀ")
        val docNuapada = DocumentRecord("dN", "u2", "aadhaar_nuapada.jpg", 1, 1, DocType.AADHAAR, DocStatus.DETECTED, "tess", "eng", "Address: At: Khariar, Dist: Nuapada - 766107", "")
        val docBalasore = DocumentRecord("dB", "u3", "pata_balasore.pdf", 1, 1, DocType.LAND_RECORD, DocStatus.DETECTED, "tess", "ori", "ଜିଲ୍ଲା: ବାଲେଶ୍ୱର", "", district = "ବାଲେଶ୍ୱର")
        val docBargarh = DocumentRecord("dBar", "u4", "aadhaar_bargarh.jpg", 1, 1, DocType.AADHAAR, DocStatus.DETECTED, "tess", "eng", "Address: Bargarh, Odisha", "")

        val locP = LocationExtractor.extractCanonicalLocation(docPuri)
        val locN = LocationExtractor.extractCanonicalLocation(docNuapada)
        val locB = LocationExtractor.extractCanonicalLocation(docBalasore)
        val locBar = LocationExtractor.extractCanonicalLocation(docBargarh)

        assertEquals("Puri", locP)
        assertEquals("Khariar", locN) // Town Khariar prioritized over district Nuapada
        assertEquals("Balasore", locB)
        assertEquals("Bargarh", locBar)

        val groups = LocationExtractor.groupDocumentsAndPairs(
            documents = listOf(docPuri, docNuapada, docBalasore, docBargarh),
            matchPairs = emptyList()
        )

        // All locations must be present
        val locationKeys = groups.map { it.locationKey }.toSet()
        assertTrue("Must contain Puri", locationKeys.contains("Puri"))
        assertTrue("Must contain Khariar", locationKeys.contains("Khariar"))
        assertTrue("Must contain Balasore", locationKeys.contains("Balasore"))
        assertTrue("Must contain Bargarh", locationKeys.contains("Bargarh"))
    }
}
