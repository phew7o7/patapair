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
        "ପ୍ଲଟ୍" to 15f,      // Plot (with halant)
        "ରୟତ" to 20f,       // Rayat (Tenant / Landholder)
        "ତହସିଲ" to 15f,     // Tahasil
        "ତହସିଲ୍" to 15f,    // Tahasil (with halant)
        "ମୌଜା" to 15f,      // Mouza
        "ଜିଲ୍ଲା" to 10f,     // District
        "କିସମ" to 15f,       // Land class
        "ରକବା" to 15f,       // Area
        "କ୍ଷେତ୍ରଫଳ" to 15f,  // Area (Acres/Decimals)
        "ସ୍ୱତ୍ୱ" to 15f,      // Rights
        "ସ୍ଥିତିବାନ" to 15f,  // Stitiban tenure
        "ଦାଗ" to 15f,       // Daag (Plot)
        "ଓଡ଼ିଶା ସରକାର" to 20f, // Govt of Odisha
        "ଓଡିଶା ସରକାର" to 20f,  // Govt of Odisha (without nukta)
        "ଭୂ-ଅଭିଲେଖ" to 20f,   // Land Records / Bhulekh
        "ପଟ୍ଟା" to 15f        // Patta
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
        val aadhaarRegex = "\\b(\\d{4}[ -]\\d{4}[ -]\\d{4})\\b".toRegex()
        val maskedAadhaarRegex = "\\b([Xx]{4}[ -][Xx]{4}[ -]\\d{4})\\b".toRegex()
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

        // Extract metadata if land record (supporting both Odia numerals [୦-୯] and ASCII [0-9])
        val khataRegex = "(?:ଖାତା\\s*(?:ନଂ|ନମ୍ବର)?|khata\\s*(?:no|number)?|ଖତିୟାନ\\s*(?:ନଂ|ନମ୍ବର)?|khatian\\s*(?:no|number)?)[:\\s\\-\\.]*([୦-୯0-9]+(?:/[୦-୯0-9]+)?)".toRegex(RegexOption.IGNORE_CASE)
        val plotRegex = "(?:ପ୍ଲଟ୍?\\s*(?:ନଂ|ନମ୍ବର)?|plot\\s*(?:no|number)?|ଚକ\\s*(?:ନଂ)?|ଦାଗ\\s*(?:ନଂ|ନମ୍ବର)?)[:\\s\\-\\.]*([୦-୯0-9]+(?:/[୦-୯0-9]+)?)".toRegex(RegexOption.IGNORE_CASE)
        val tahasilRegex = "(?:ତହସିଲ୍?|tahasil|tehsil)[:\\s\\-]+([A-Za-z\\u0B00-\\u0B7F\\s]+)".toRegex(RegexOption.IGNORE_CASE)
        val mouzaRegex = "(?:ମୌଜା|mouza|village)[:\\s\\-]+([A-Za-z\\u0B00-\\u0B7F\\s]+)".toRegex(RegexOption.IGNORE_CASE)

        var rawKhata = khataRegex.find(text)?.groupValues?.getOrNull(1)?.trim()
        var rawPlot = plotRegex.find(text)?.groupValues?.getOrNull(1)?.trim()

        // Multi-line table header fallback: if label was on line N and number on line N+1
        if (rawKhata.isNullOrBlank()) {
            val textLines = text.lines().map { it.trim() }
            for (idx in textLines.indices) {
                val l = textLines[idx]
                if (l.contains("ଖାତା") || l.contains("khata", ignoreCase = true) || l.contains("ଖତିୟାନ ନଂ") || l.contains("khatian no", ignoreCase = true)) {
                    val nextLine = textLines.getOrNull(idx + 1)
                    if (nextLine != null) {
                        val numMatch = "([୦-୯0-9]+(?:/[୦-୯0-9]+)?)".toRegex().find(nextLine)
                        if (numMatch != null && numMatch.value.isNotBlank()) {
                            rawKhata = numMatch.groupValues[1]
                            break
                        }
                    }
                }
            }
        }

        if (rawPlot.isNullOrBlank()) {
            val textLines = text.lines().map { it.trim() }
            for (idx in textLines.indices) {
                val l = textLines[idx]
                if (l.contains("ପ୍ଲଟ") || l.contains("ପ୍ଲଟ୍") || l.contains("plot", ignoreCase = true) || l.contains("ଦାଗ") || l.contains("ଚକ")) {
                    val nextLine = textLines.getOrNull(idx + 1)
                    if (nextLine != null) {
                        val numMatch = "([୦-୯0-9]+(?:/[୦-୯0-9]+)?)".toRegex().find(nextLine)
                        if (numMatch != null && numMatch.value.isNotBlank()) {
                            rawPlot = numMatch.groupValues[1]
                            break
                        }
                    }
                }
            }
        }

        val khata = rawKhata?.let { OdiaTransliterationEngine.convertOdiaDigitsToAscii(it) }
        val plot = rawPlot?.let { OdiaTransliterationEngine.convertOdiaDigitsToAscii(it) }
        val tahasil = tahasilRegex.find(text)?.groupValues?.getOrNull(1)?.lines()?.firstOrNull()?.trim()
        val mouza = mouzaRegex.find(text)?.groupValues?.getOrNull(1)?.lines()?.firstOrNull()?.trim()

        // Decision logic
        if (aadhaarScore >= 50f && aadhaarScore > landScore) {
            val (personPairs, personDetails) = extractPersonsFromAadhaarWithDetails(text)
            return DocumentClassifierResult(
                docType = DocType.AADHAAR,
                confidence = aadhaarScore, // Store raw classifier heuristic score
                reason = "Aadhaar Card detected (Rule Score: ${aadhaarScore.toInt()} pts)",
                rulesFired = rulesFired,
                extractedPersons = personPairs,
                aadhaarMasked = aadhaarNumberMasked,
                extractedPersonDetails = personDetails
            )
        }

        if (landScore >= 35f) {
            val (personPairs, personDetails) = extractPersonsFromLandRecordWithDetails(text)
            return DocumentClassifierResult(
                docType = DocType.LAND_RECORD,
                confidence = landScore, // Store raw classifier heuristic score
                reason = "Odisha Land Record / Khatiyan detected (Rule Score: ${landScore.toInt()} pts)",
                rulesFired = rulesFired,
                extractedPersons = personPairs,
                khataNumber = khata,
                plotNumber = plot,
                tahasil = tahasil,
                mouza = mouza,
                extractedPersonDetails = personDetails
            )
        }

        // Low confidence land record check (e.g. weak OCR where only 1 keyword or layout matched)
        if (landScore >= 20f || (hasTableStructure && detectedScript == ScriptType.ODIA)) {
            val (personPairs, personDetails) = extractPersonsFromLandRecordWithDetails(text)
            return DocumentClassifierResult(
                docType = DocType.LAND_RECORD,
                confidence = landScore,
                reason = "Probable Land Record (Weak OCR / Single keyword, Rule Score: ${landScore.toInt()} pts)",
                rulesFired = rulesFired,
                extractedPersons = personPairs,
                khataNumber = khata,
                plotNumber = plot,
                tahasil = tahasil,
                mouza = mouza,
                extractedPersonDetails = personDetails
            )
        }

        // Genuinely unknown document
        return DocumentClassifierResult(
            docType = DocType.UNKNOWN,
            confidence = 0f,
            reason = "No matching Aadhaar or Land Record patterns found in extracted text",
            rulesFired = rulesFired,
            extractedPersons = emptyList(),
            extractedPersonDetails = emptyList()
        )
    }

    private val AADHAAR_NON_NAME_PHRASES = listOf(
        "government of india", "unique identification authority of india", "unique identification authority",
        "mera aadhaar", "meri pehchan", "help@uidai.gov.in", "www.uidai.gov.in", "enrollment no",
        "republic of india", "download date", "issue date", "care of", "s/o:", "d/o:", "w/o:", "c/o:"
    )

    private val AADHAAR_NON_NAME_WORDS = setOf(
        "aadhaar", "aadnaar", "aadh4ar", "aadhmar", "aadha", "aadhar", "aadhhar", "uidai", "uidat", "uid", "vid",
        "government", "govemment", "govement", "india", "unique", "identification", "authority", "autharity", "enrollment",
        "male", "female", "transgender", "dob", "birth", "year", "date",
        "help", "download", "meraaadhaar", "mera", "meri", "pehchan", "address", "pincode", "pin",
        "state", "district", "father", "husband", "mother", "guardian", "card",
        "republic", "resident", "electronic", "information", "web", "online",
        "your", "no", "number", "of", "to", "for", "all", "the", "and", "in", "at", "by", "signature", "valid"
    )

    private val LAND_ADMIN_KEYWORDS = setOf(
        "ଖତିୟାନ", "ଖାତା", "ପ୍ଲଟ", "ପ୍ଲଟ୍", "ରୟତ", "ରୟତଙ୍କ", "ପଟ୍ଟାଦାର", "ସ୍ୱତ୍ୱ", "ସ୍ଥିତିବାନ", "ତହସିଲ", "ତହସିଲ୍",
        "ମୌଜା", "ଜିଲ୍ଲା", "ଥାନା", "ପ୍ରଜା", "ପ୍ରଜାଙ୍କ", "କିସମ", "ରକବା", "କ୍ଷେତ୍ରଫଳ", "ଚୌହଦୀ", "ଚଉହଦି",
        "ସୀମା", "ଉତ୍ତର", "ଦକ୍ଷିଣ", "ପୂର୍ବ", "ପଶ୍ଚିମ", "ଏକର", "ଡେସିମିଲ", "ହେକ୍ଟର", "ଓଡ଼ିଶା", "ଓଡିଶା",
        "ସରକାର", "ଭୂ-ଅଭିଲେଖ", "ଭୂମି", "କ୍ରମିକ", "ମନ୍ତବ୍ୟ", "ଦାଖଲ", "ଦଖଲକାର", "ଖାରଜ", "ରାଜସ୍ୱ",
        "ଜମାବନ୍ଦୀ", "ଖଜଣା", "ଉପକର", "ସେସ", "ମିଶ୍ରିତ", "ନଂ", "ନମ୍ବର", "ବର୍ଗ", "ଫୁଟ",
        "ବିବରଣୀ", "ବିବରଣ", "ନାମ", "ଜମି", "ସୂଚନା", "ଶାରଦ", "ଜଳାଶୟ", "ଘରବାରୀ", "ତୋଟା", "ପଟ୍ଟା", "ଚକ", "ଦାଗ",
        "khatiyan", "khata", "plot", "rayat", "pattadar", "tahasil", "mouza", "district",
        "thana", "kisam", "rakaba", "area", "acre", "decimal", "hectare", "odisha", "government",
        "bhulekh", "ror", "record of rights", "serial", "remarks", "revenue", "cess", "details"
    )

    private fun extractPersonsFromAadhaarWithDetails(text: String): Pair<List<Pair<String, ScriptType>>, List<com.example.model.ExtractedPersonDraft>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val personDrafts = mutableListOf<com.example.model.ExtractedPersonDraft>()

        // 1. Look for father/guardian if present (e.g., S/O: Jagannath Rath)
        var fatherOrGuardianName: String? = null
        for (line in lines) {
            val relationMatch = "(?:s/o|d/o|w/o|c/o|father|husband)[:\\s\\-]+([A-Za-z\\s]+)".toRegex(RegexOption.IGNORE_CASE).find(line)
            if (relationMatch != null) {
                val rel = relationMatch.groupValues[1].trim()
                val cleanRel = cleanAadhaarCandidate(rel)
                if (isValidAadhaarName(cleanRel)) {
                    fatherOrGuardianName = cleanRel
                    break
                }
            }
        }

        // 2. Identify cardholder name: preceding DOB or Date of Birth line
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("DOB", ignoreCase = true) || line.contains("Year of Birth", ignoreCase = true) || line.contains("Date of Birth", ignoreCase = true)) {
                // Search backwards for the nearest valid name line
                for (j in (i - 1) downTo 0) {
                    val candidate = cleanAadhaarCandidate(lines[j])
                    if (isValidAadhaarName(candidate)) {
                        val script = OdiaTransliterationEngine.detectScript(candidate)
                        personDrafts.add(
                            com.example.model.ExtractedPersonDraft(
                                name = candidate,
                                script = script,
                                fatherOrSpouseName = fatherOrGuardianName,
                                role = "Aadhaar Cardholder"
                            )
                        )
                        break
                    }
                }
                if (personDrafts.isNotEmpty()) break
            }
        }

        // 3. Fallback: select first plausible non-header, non-blacklisted alphabetic name
        if (personDrafts.isEmpty()) {
            for (line in lines) {
                val candidate = cleanAadhaarCandidate(line)
                if (isValidAadhaarName(candidate)) {
                    val script = OdiaTransliterationEngine.detectScript(candidate)
                    personDrafts.add(
                        com.example.model.ExtractedPersonDraft(
                            name = candidate,
                            script = script,
                            fatherOrSpouseName = fatherOrGuardianName,
                            role = "Aadhaar Cardholder"
                        )
                    )
                    break
                }
            }
        }

        val pairs = personDrafts.map { it.name to it.script }
        return pairs to personDrafts
    }

    private fun cleanAadhaarCandidate(raw: String): String {
        return raw
            .replace("^(?:to|name|shri|smt|mr|mrs)[:\\s\\-]+".toRegex(RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun extractPersonsFromLandRecordWithDetails(text: String): Pair<List<Pair<String, ScriptType>>, List<com.example.model.ExtractedPersonDraft>> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val personDrafts = mutableListOf<com.example.model.ExtractedPersonDraft>()

        var inRayatSection = false

        for (line in lines) {
            // Check for rayat / tenant section trigger (headers like ରୟତଙ୍କ ନାମ, ପ୍ରଜାଙ୍କ ନାମ, Rayat Name, Pattadar)
            val isRayatHeader = line.contains("ରୟତ") || line.contains("Rayat", ignoreCase = true) ||
                                line.contains("ପଟ୍ଟାଦାର") || line.contains("Pattadar", ignoreCase = true) ||
                                line.contains("ପ୍ରଜାଙ୍କ ନାମ") || line.contains("ମାଲିକଙ୍କ ନାମ") ||
                                line.contains("ଦଖଲକାର")

            if (isRayatHeader) {
                inRayatSection = true
                val clean = line
                    .replace("ରୟତଙ୍କ(?: ନାମ)?".toRegex(), "")
                    .replace("ପଟ୍ଟାଦାରଙ୍କ(?: ନାମ)?".toRegex(), "")
                    .replace("ପ୍ରଜାଙ୍କ(?: ନାମ)?".toRegex(), "")
                    .replace("ମାଲିକଙ୍କ(?: ନାମ)?".toRegex(), "")
                    .replace("ଦଖଲକାରଙ୍କ(?: ନାମ)?".toRegex(), "")
                    .replace("ରୟତ", "")
                    .replace("ପଟ୍ଟାଦାର", "")
                    .replace("Rayat", "", ignoreCase = true)
                    .replace("Pattadar", "", ignoreCase = true)
                    .replace("Tenant", "", ignoreCase = true)
                    .replace("ସ୍ଥିତିବାନ", "")
                    .replace("ସ୍ୱତ୍ୱ", "")
                    .replace("ପ୍ରଜା", "")
                    .replace("ନାମ", "")
                    .replace("ବିବରଣୀ", "")
                    .replace("[/|:\\-]".toRegex(), " ")
                    .trim()

                if (clean.isNotBlank() && clean.any { it.isLetter() }) {
                    val cleanTokens = clean.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
                    if (!cleanTokens.all { LAND_ADMIN_KEYWORDS.contains(it) }) {
                        parseAndAddLandPerson(clean, personDrafts)
                    }
                }
                continue
            }

            if (inRayatSection) {
                // If line contains next major section header, stop the rayat section
                if ((line.contains("ଚୌହଦୀ") || line.contains("ଜମିଦାର") || line.contains("ମୋଟ ରକବା")) &&
                    !line.contains("ରୟତ") && !line.contains("ପିତା") && !line.contains("ପି:")
                ) {
                    inRayatSection = false
                } else {
                    parseAndAddLandPerson(line, personDrafts)
                }
            }
        }

        // Fallback: If section headers were obscured by noisy OCR or not triggered,
        // scan lines containing Odia script
        if (personDrafts.isEmpty()) {
            for (line in lines) {
                if (OdiaTransliterationEngine.detectScript(line) == ScriptType.ODIA) {
                    parseAndAddLandPerson(line, personDrafts)
                    if (personDrafts.size >= 8) break // Support multiple co-pattadars per page
                }
            }
        }

        val distinctDrafts = personDrafts.distinctBy { it.name.trim() }
        val pairs = distinctDrafts.map { it.name to it.script }
        return pairs to distinctDrafts
    }

    private fun parseAndAddLandPerson(
        rawLine: String,
        targetList: MutableList<com.example.model.ExtractedPersonDraft>
    ) {
        // Support multiple co-tenants listed on one line separated by comma, semicolon, or Odia 'ଓ' (and)
        val segments = rawLine.split("[,;]|\\s+ଓ\\s+".toRegex()).map { it.trim() }.filter { it.isNotBlank() }
        for (seg in segments) {
            processSingleLandPersonSegment(seg, targetList)
        }
    }

    private fun processSingleLandPersonSegment(
        rawSegment: String,
        targetList: MutableList<com.example.model.ExtractedPersonDraft>
    ) {
        // 1. Strip leading numbering e.g. "୧.", "୨.", "1.", "2.", "(୧)", "1-"
        var cleaned = rawSegment
            .replace("^[\\(]?([୦-୯0-9]+)[\\)\\.\\-\\s/]+".toRegex(), "")
            .replace("^(?:ଶ୍ରୀ|ଶ୍ରୀମତୀ|ସ୍ୱର୍ଗତ|ଡାକ୍ତର|ମି|late|shri|smt)\\s+".toRegex(RegexOption.IGNORE_CASE), "")
            .trim()

        // 2. Remove tenure and metadata prefixes if present on segment
        cleaned = cleaned
            .replace("^(?:ରୟତ|ରୟତଙ୍କ|ପଟ୍ଟାଦାର|ପ୍ରଜା|ସ୍ଥିତିବାନ|ସ୍ୱତ୍ୱ|ଦଖଲକାର)[:\\s\\-]+".toRegex(), "")
            .trim()

        // 3. Remove caste annotations (e.g. ଜାତି: ଖଣ୍ଡାୟତ, ଜାତି- ଚାଷୀ)
        cleaned = cleaned.replace("(?:ଜାତି|caste)[:\\s\\-]+[A-Za-z\\u0B00-\\u0B7F]+".toRegex(RegexOption.IGNORE_CASE), " ")
        // 4. Remove residence/village annotations (e.g. ସାକିନ- ..., ବାସିନ୍ଦା- ...)
        cleaned = cleaned.replace("(?:ବାସିନ୍ଦା|ସାକିନ|ନିବାସୀ|village|at)[:\\s\\-]+[A-Za-z\\u0B00-\\u0B7F]+".toRegex(RegexOption.IGNORE_CASE), " ")
        // 5. Remove share fractions e.g. (୧/୨), (1/4)
        cleaned = cleaned.replace("[\\(]?[୦-୯0-9]+/[୦-୯0-9]+[\\)]?".toRegex(), " ")
        cleaned = cleaned.trim()

        if (cleaned.length < 2) return

        // 6. Split by father / spouse / relationship marker
        var primaryName = cleaned
        var fatherOrSpouse: String? = null

        val relationSplit = cleaned.split("(?:ପିତାଙ୍କ(?: ନାମ)?|ପିତା|ପି[:\\-\\.]|ସ୍ୱାମୀଙ୍କ(?: ନାମ)?|ସ୍ୱାମୀ|ସ୍ୱା[:\\-\\.]|ମାତାଙ୍କ|ମାତା|ପତ୍ନୀଙ୍କ|ପତ୍ନୀ|father|husband|s/o|d/o|w/o)[:\\s\\-]+".toRegex(RegexOption.IGNORE_CASE))
        if (relationSplit.size >= 2) {
            val candidatePrimary = cleanExtractedNameString(relationSplit[0])
            val candidateRelation = cleanExtractedNameString(relationSplit[1])

            if (isValidLandPersonName(candidatePrimary)) {
                primaryName = candidatePrimary
                if (isValidLandPersonName(candidateRelation)) {
                    fatherOrSpouse = candidateRelation
                }
            }
        } else {
            primaryName = cleanExtractedNameString(cleaned)
        }

        if (isValidLandPersonName(primaryName)) {
            val script = OdiaTransliterationEngine.detectScript(primaryName)
            targetList.add(
                com.example.model.ExtractedPersonDraft(
                    name = primaryName,
                    script = script,
                    fatherOrSpouseName = fatherOrSpouse,
                    role = "Rayat / Pattadar"
                )
            )
        }
    }

    private fun cleanExtractedNameString(str: String): String {
        val tokens = str
            .replace("[,;:/\\|\\(\\)\\[\\]\\*\\#]".toRegex(), " ")
            .replace("[୦-୯0-9]".toRegex(), "")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }

        // Filter out non-name administrative noise tokens (e.g. ଏକର, ଡେସିମିଲ, ହେକ୍ଟର, ଶାରଦ, କିସମ)
        val filteredTokens = tokens.filter { tok ->
            val lower = tok.lowercase(java.util.Locale.ROOT)
            !LAND_ADMIN_KEYWORDS.contains(lower)
        }

        return filteredTokens.joinToString(" ").trim()
    }

    private fun isValidAadhaarName(str: String): Boolean {
        val trimmed = str.trim()
        if (trimmed.length < 2 || trimmed.length > 50) return false
        if (trimmed.any { it.isDigit() }) return false
        if (trimmed.contains("@") || trimmed.contains("http") || trimmed.contains(".com") || trimmed.contains(".gov")) return false

        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        // Check whole phrases
        if (AADHAAR_NON_NAME_PHRASES.any { lower.contains(it) }) return false

        // Check word set
        val words = lower.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.all { AADHAAR_NON_NAME_WORDS.contains(it) }) return false
        if (words.any { it == "aadhaar" || it == "aadnaar" || it == "uidai" || it == "dob" || it == "government" || it == "india" }) return false

        val letterCount = trimmed.count { it.isLetter() }
        return letterCount >= 3
    }

    private fun isValidLandPersonName(str: String): Boolean {
        val trimmed = str.trim()
        if (trimmed.length < 2 || trimmed.length > 45) return false
        if (trimmed.contains("@") || trimmed.contains("http") || trimmed.contains(".com") || trimmed.contains(".gov")) return false

        val lower = trimmed.lowercase(java.util.Locale.ROOT)
        val tokens = lower.split("\\s+".toRegex()).filter { it.isNotBlank() }

        // If all tokens are purely land administrative keywords, reject
        if (tokens.all { LAND_ADMIN_KEYWORDS.contains(it) }) return false

        // Reject if single token matches exact administrative header
        if (tokens.size == 1 && LAND_ADMIN_KEYWORDS.contains(tokens.first())) return false

        val letterCount = trimmed.count { it.isLetter() }
        return letterCount >= 2
    }

    fun extractPersonsFromAadhaar(text: String): List<Pair<String, ScriptType>> {
        return extractPersonsFromAadhaarWithDetails(text).first
    }

    fun extractPersonsFromLandRecord(text: String): List<Pair<String, ScriptType>> {
        return extractPersonsFromLandRecordWithDetails(text).first
    }
}
