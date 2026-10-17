package com.example.engine

import com.example.model.ClassificationRuleResult
import com.example.model.DocType
import com.example.model.DocumentClassifierResult
import com.example.model.ScriptType

object DocumentClassifier {

    // Verified Odia Land Record Keywords (Real Odisha Bhulekh / RoR terminology)
    private val ODIA_LAND_KEYWORDS = listOf(
        "ଖତିୟାନ" to 25f,    // Khatiyan (Record of Rights)
        "ଖାତା" to 20f,       // Khata
        "ପ୍ଲଟ" to 15f,       // Plot
        "ରୟତ" to 20f,       // Rayat (Tenant / Landholder)
        "ତହସିଲ" to 15f,     // Tahasil
        "ମୌଜା" to 15f,      // Mouza
        "ଜିଲ୍ଲା" to 10f,     // District
        "କିସମ" to 15f,       // Land class
        "ରକବା" to 15f,       // Area
        "କ୍ଷେତ୍ରଫଳ" to 15f,  // Area (Acres/Decimals)
        "ସ୍ୱତ୍ୱ" to 15f,      // Rights
        "ଓଡ଼ିଶା ସରକାର" to 20f, // Govt of Odisha
        "ଭୂ-ଅଭିଲେଖ" to 20f    // Land Records / Bhulekh
    )

    // English Land Record Keywords (as seen in bilingual Khatiyans / Bhulekh headers)
    private val ENGLISH_LAND_KEYWORDS = listOf(
        "khatiyan" to 25f,
        "khata" to 20f,
        "record of rights" to 25f,
        "ror" to 15f,
        "tahasil" to 15f,
        "mouza" to 15f,
        "rayat" to 20f,
        "pattadar" to 20f,
        "plot no" to 15f,
        "kisam" to 15f,
        "bhulekh" to 20f,
        "odisha" to 10f,
        "district" to 5f
    )

    // Aadhaar Keywords
    private val AADHAAR_KEYWORDS = listOf(
        "government of india" to 30f,
        "unique identification authority" to 30f,
        "uidai" to 30f,
        "aadhaar" to 35f,
        "enrollment no" to 20f,
        "help@uidai.gov.in" to 25f,
        "www.uidai.gov.in" to 25f,
        "meraaadhaar" to 20f,
        "dob:" to 15f,
        "date of birth" to 15f,
        "female" to 10f,
        "male" to 10f
    )

    // Unrelated document markers (Receipts, invoices, bank passbooks)
    private val UNRELATED_DOC_MARKERS = listOf(
        "invoice" to 25f,
        "bill no" to 25f,
        "cash receipt" to 30f,
        "tax invoice" to 30f,
        "total amount" to 20f,
        "subtotal" to 20f,
        "gstin" to 30f,
        "menu" to 30f,
        "price list" to 25f
    )

    fun classifyDocument(
        text: String,
        ocrError: String? = null,
        hasTableGridVisuals: Boolean = false,
        imageAspectRatio: Float = 1.0f,
        isWaitingForModel: Boolean = false
    ): DocumentClassifierResult {
        val rulesFired = mutableListOf<ClassificationRuleResult>()

        // 1. Check for missing model
        if (isWaitingForModel) {
            rulesFired.add(ClassificationRuleResult("WAITING_FOR_OCR_MODEL", 0f, "Awaiting Tesseract Odia model (ori.traineddata)"))
            return DocumentClassifierResult(
                docType = DocType.UNKNOWN,
                confidence = 0f,
                reason = "Waiting for OCR model",
                rulesFired = rulesFired,
                extractedPersons = emptyList()
            )
        }

        // 2. Check for OCR failure
        if (ocrError != null || (text.isBlank() && !hasTableGridVisuals)) {
            val errReason = ocrError ?: "No text recognized by OCR engine"
            rulesFired.add(ClassificationRuleResult("OCR_FAILURE_RULE", 0f, errReason))
            return DocumentClassifierResult(
                docType = DocType.FAILED_OCR,
                confidence = 0f,
                reason = "OCR failed: $errReason",
                rulesFired = rulesFired,
                extractedPersons = emptyList()
            )
        }

        val lowerText = text.lowercase()
        val detectedScript = OdiaTransliterationEngine.detectScript(text)

        // 2. Aadhaar Classification
        var aadhaarScore = 0f
        var aadhaarNumberMasked: String? = null

        // Check 12-digit number (e.g. 1234 5678 9012 or XXXX XXXX 1234)
        val aadhaarRegex = "\\b(\\d{4}[\\s-]\\d{4}[\\s-]\\d{4})\\b".toRegex()
        val maskedAadhaarRegex = "\\b([Xx]{4}[\\s-][Xx]{4}[\\s-]\\d{4})\\b".toRegex()
        val rawMatch = aadhaarRegex.find(text)
        val maskedMatch = maskedAadhaarRegex.find(text)

        if (rawMatch != null) {
            val num = rawMatch.value.replace("[\\s-]".toRegex(), "")
            aadhaarNumberMasked = "XXXX-XXXX-${num.takeLast(4)}"
            aadhaarScore += 45f
            rulesFired.add(ClassificationRuleResult("AADHAAR_NUMBER_DETECTED", 45f, "Found 12-digit UID pattern (Redacted to $aadhaarNumberMasked)"))
        } else if (maskedMatch != null) {
            aadhaarNumberMasked = maskedMatch.value
            aadhaarScore += 45f
            rulesFired.add(ClassificationRuleResult("MASKED_AADHAAR_DETECTED", 45f, "Found masked Aadhaar UID pattern ($aadhaarNumberMasked)"))
        }

        for ((kw, weight) in AADHAAR_KEYWORDS) {
            if (lowerText.contains(kw)) {
                aadhaarScore += weight
                rulesFired.add(ClassificationRuleResult("AADHAAR_KEYWORD_$kw", weight, "Matched Aadhaar keyword '$kw'"))
            }
        }

        // 3. Land Record Classification
        var landScore = 0f

        // Check Odia keywords
        for ((kw, weight) in ODIA_LAND_KEYWORDS) {
            if (text.contains(kw)) {
                landScore += weight
                rulesFired.add(ClassificationRuleResult("ODIA_LAND_KEYWORD_$kw", weight, "Matched Odia land record keyword '$kw'"))
            }
        }

        // Check English keywords
        for ((kw, weight) in ENGLISH_LAND_KEYWORDS) {
            if (lowerText.contains(kw)) {
                landScore += weight
                rulesFired.add(ClassificationRuleResult("ENG_LAND_KEYWORD_$kw", weight, "Matched English land record keyword '$kw'"))
            }
        }

        // Structural and visual classification for land records
        val tableRowPattern = "(\\||\\t|\\s{3,})".toRegex()
        val lineCount = text.lines().size
        val multiColumnLines = text.lines().count { line -> tableRowPattern.findAll(line).count() >= 2 }

        val hasTableStructure = hasTableGridVisuals || (lineCount >= 4 && multiColumnLines >= 2)

        if (hasTableStructure) {
            // Check if unrelated document marker is present
            var unrelatedScore = 0f
            for ((marker, weight) in UNRELATED_DOC_MARKERS) {
                if (lowerText.contains(marker)) {
                    unrelatedScore += weight
                    rulesFired.add(ClassificationRuleResult("UNRELATED_MARKER_$marker", -weight, "Found non-land document marker '$marker'"))
                }
            }

            if (unrelatedScore >= 30f) {
                // Ordinary table / receipt / bill
                return DocumentClassifierResult(
                    docType = DocType.UNKNOWN,
                    confidence = 0.85f,
                    reason = "Unrelated document with tabular layout (invoice/receipt)",
                    rulesFired = rulesFired,
                    extractedPersons = emptyList()
                )
            }

            // Land records have tabular layout
            if (landScore > 0f) {
                landScore += 25f
                rulesFired.add(ClassificationRuleResult("TABLE_LAYOUT_WITH_LAND_KEYWORDS", 25f, "Detected Khatiyan tabular grid structure with verified land keywords"))
            } else {
                // If there is a table but NO land keywords at all, do NOT classify as land record
                rulesFired.add(ClassificationRuleResult("GENERIC_TABLE_WITHOUT_LAND_KEYWORDS", 0f, "Table detected but no land record terminology found"))
            }
        }

        // Extract metadata if land record
        val khataRegex = "(?:ଖାତା|khata)[^0-9\\n]{0,10}([0-9/]+)".toRegex(RegexOption.IGNORE_CASE)
        val plotRegex = "(?:ପ୍ଲଟ|plot)[^0-9\\n]{0,10}([0-9/]+)".toRegex(RegexOption.IGNORE_CASE)
        val tahasilRegex = "(?:ତହସିଲ|tahasil)[:\\s]+([A-Za-z\\u0B00-\\u0B7F]+)".toRegex(RegexOption.IGNORE_CASE)
        val mouzaRegex = "(?:ମୌଜା|mouza)[:\\s]+([A-Za-z\\u0B00-\\u0B7F]+)".toRegex(RegexOption.IGNORE_CASE)
        val khata = khataRegex.find(text)?.groupValues?.getOrNull(1)
        val plot = plotRegex.find(text)?.groupValues?.getOrNull(1)
        val tahasil = tahasilRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        val mouza = mouzaRegex.find(text)?.groupValues?.getOrNull(1)?.trim()

        // Decision logic
        if (aadhaarScore >= 50f && aadhaarScore > landScore) {
            val persons = extractPersonsFromAadhaar(text)
            return DocumentClassifierResult(
                docType = DocType.AADHAAR,
                confidence = (aadhaarScore / 100f).coerceIn(0.5f, 1.0f),
                reason = "Aadhaar Card detected (Score: ${aadhaarScore.toInt()})",
                rulesFired = rulesFired,
                extractedPersons = persons,
                aadhaarMasked = aadhaarNumberMasked
            )
        }

        if (landScore >= 35f) {
            val persons = extractPersonsFromLandRecord(text)
            return DocumentClassifierResult(
                docType = DocType.LAND_RECORD,
                confidence = (landScore / 100f).coerceIn(0.5f, 1.0f),
                reason = "Odisha Land Record / Khatiyan detected (Score: ${landScore.toInt()})",
                rulesFired = rulesFired,
                extractedPersons = persons,
                khataNumber = khata,
                plotNumber = plot,
                tahasil = tahasil,
                mouza = mouza
            )
        }

        // Low confidence land record check (e.g. weak OCR where only 1 keyword or layout matched)
        if (landScore >= 20f || (hasTableStructure && detectedScript == ScriptType.ODIA)) {
            val persons = extractPersonsFromLandRecord(text)
            return DocumentClassifierResult(
                docType = DocType.LAND_RECORD,
                confidence = 0.55f,
                reason = "Probable Land Record (Weak OCR / Single keyword match) - Needs Review",
                rulesFired = rulesFired,
                extractedPersons = persons,
                khataNumber = khata,
                plotNumber = plot,
                tahasil = tahasil,
                mouza = mouza
            )
        }

        // Genuinely unknown document
        return DocumentClassifierResult(
            docType = DocType.UNKNOWN,
            confidence = 0.90f,
            reason = "No matching Aadhaar or Land Record patterns found in extracted text",
            rulesFired = rulesFired,
            extractedPersons = emptyList()
        )
    }

    private fun extractPersonsFromAadhaar(text: String): List<Pair<String, ScriptType>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val names = mutableListOf<Pair<String, ScriptType>>()

        // Aadhaar cards typically place the person's name on the line directly before DOB or after header
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("DOB", ignoreCase = true) || line.contains("Year of Birth", ignoreCase = true)) {
                if (i > 0) {
                    val candidate = lines[i - 1]
                    if (isValidName(candidate)) {
                        val script = OdiaTransliterationEngine.detectScript(candidate)
                        names.add(candidate to script)
                        break
                    }
                }
            }
        }

        if (names.isEmpty()) {
            for (line in lines) {
                if (isValidName(line) && !line.contains("Government", ignoreCase = true) && !line.contains("India", ignoreCase = true) && !line.contains("UIDAI", ignoreCase = true)) {
                    val script = OdiaTransliterationEngine.detectScript(line)
                    names.add(line to script)
                    break
                }
            }
        }

        return names
    }

    private fun extractPersonsFromLandRecord(text: String): List<Pair<String, ScriptType>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val names = mutableListOf<Pair<String, ScriptType>>()

        // Look for Rayat / Tenant section: ରୟତ or Rayat
        var inRayatSection = false
        for (line in lines) {
            if (line.contains("ରୟତ") || line.contains("Rayat", ignoreCase = true) || line.contains("ପଟ୍ଟାଦାର") || line.contains("Pattadar", ignoreCase = true)) {
                inRayatSection = true
                val clean = line
                    .replace("ରୟତ", "")
                    .replace("ପଟ୍ଟାଦାର", "")
                    .replace("Rayat", "", ignoreCase = true)
                    .replace("Pattadar", "", ignoreCase = true)
                    .replace("[/|:\\-]".toRegex(), " ")
                    .trim()
                if (isValidName(clean)) {
                    names.add(clean to OdiaTransliterationEngine.detectScript(clean))
                }
                continue
            }

            if (inRayatSection) {
                // If line contains next header like "ଖାତା", "କିସମ", stop
                if (line.contains("ଖାତା") || line.contains("କିସମ") || line.contains("ପ୍ଲଟ")) {
                    inRayatSection = false
                } else if (isValidName(line)) {
                    names.add(line to OdiaTransliterationEngine.detectScript(line))
                }
            }
        }

        // Fallback: look for Odia name sequences
        if (names.isEmpty()) {
            for (line in lines) {
                if (OdiaTransliterationEngine.detectScript(line) == ScriptType.ODIA && isValidName(line)) {
                    // Check it's not a header word
                    if (!ODIA_LAND_KEYWORDS.any { line.contains(it.first) }) {
                        names.add(line to ScriptType.ODIA)
                        if (names.size >= 3) break // Max 3 extracted candidates per page
                    }
                }
            }
        }

        return names.distinct()
    }

    private fun isValidName(str: String): Boolean {
        if (str.length < 3 || str.length > 50) return false
        if (str.any { it.isDigit() }) return false
        // Exclude lines with url/email/common non-name terms
        if (str.contains("@") || str.contains("http") || str.contains(".com") || str.contains(".gov")) return false
        return true
    }
}
