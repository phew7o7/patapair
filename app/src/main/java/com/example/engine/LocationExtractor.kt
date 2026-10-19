package com.example.engine

import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.PersonRecord
import java.util.Locale

data class LocationInfo(
    val canonicalName: String,
    val odiaName: String,
    val displayName: String,
    val isMajorOdishaLocation: Boolean = true
)

data class LocationGroupData(
    val locationKey: String,
    val displayName: String,
    val odiaName: String,
    val pataDocuments: List<DocumentRecord>,
    val aadhaarDocuments: List<DocumentRecord>,
    val matchedPairs: List<MatchPairRecord>,
    val totalDocuments: Int,
    val matchedCount: Int
)

object LocationExtractor {

    // Major Odisha administrative towns, tahasils, and mouzas (especially Kalahandi and western Odisha)
    val KNOWN_LOCATIONS = listOf(
        LocationInfo("Kesinga", "କେସିଙ୍ଗା", "Kesinga (କେସିଙ୍ଗା)"),
        LocationInfo("Bhawanipatna", "ଭବାନୀପାଟଣା", "Bhawanipatna (ଭବାନୀପାଟଣା)"),
        LocationInfo("Dharamgarh", "ଧର୍ମଗଡ଼", "Dharamgarh (ଧର୍ମଗଡ଼)"),
        LocationInfo("Junagarh", "ଜୁନାଗଡ଼", "Junagarh (ଜୁନାଗଡ଼)"),
        LocationInfo("Karlamunda", "କର୍ଲାମୁଣ୍ଡା", "Karlamunda (କର୍ଲାମୁଣ୍ଡା)"),
        LocationInfo("Narla", "ନର୍ଲା", "Narla (ନର୍ଲା)"),
        LocationInfo("Lanjigarh", "ଲାଞ୍ଜିଗଡ଼", "Lanjigarh (ଲାଞ୍ଜିଗଡ଼)"),
        LocationInfo("M.Rampur", "ରାମପୁର", "M.Rampur (ରାମପୁର)"),
        LocationInfo("Jaipatna", "ଜୟପାଟଣା", "Jaipatna (ଜୟପାଟଣା)"),
        LocationInfo("Koksara", "କୋକସରା", "Koksara (କୋକସରା)"),
        LocationInfo("Golamunda", "ଗୋଲାମୁଣ୍ଡା", "Golamunda (ଗୋଲାମୁଣ୍ଡା)"),
        LocationInfo("Kalampur", "କଲମପୁର", "Kalampur (କଲମପୁର)"),
        LocationInfo("Kalahandi", "କଳାହାଣ୍ଡି", "Kalahandi (କଳାହାଣ୍ଡି)"),
        LocationInfo("Jatni", "ଜଟଣୀ", "Jatni (ଜଟଣୀ)"),
        LocationInfo("Kudiary", "କୁଦିଆରୀ", "Kudiary (କୁଦିଆରୀ)"),
        LocationInfo("Khordha", "ଖୋର୍ଦ୍ଧା", "Khordha (ଖୋର୍ଦ୍ଧା)"),
        LocationInfo("Bhubaneswar", "ଭୁବନେଶ୍ୱର", "Bhubaneswar (ଭୁବନେଶ୍ୱର)"),
        LocationInfo("Cuttack", "କଟକ", "Cuttack (କଟକ)"),
        LocationInfo("Sambalpur", "ସମ୍ବଲପୁର", "Sambalpur (ସମ୍ବଲପୁର)"),
        LocationInfo("Titilagarh", "ଟିଟିଲାଗଡ଼", "Titilagarh (ଟିଟିଲାଗଡ଼)"),
        LocationInfo("Bolangir", "ବଲାଙ୍ଗୀର", "Bolangir (ବଲାଙ୍ଗୀର)"),
        LocationInfo("Bargarh", "ବରଗଡ଼", "Bargarh (ବରଗଡ଼)"),
        LocationInfo("Nuapada", "ନୂଆପଡ଼ା", "Nuapada (ନୂଆପଡ଼ା)"),
        LocationInfo("Khariar", "ଖଡ଼ିଆଳ", "Khariar (ଖଡ଼ିଆଳ)"),
        LocationInfo("Rayagada", "ରାୟଗଡ଼", "Rayagada (ରାୟଗଡ଼)")
    )

    private val LOCATION_ALIASES = mapOf(
        "kesinga" to "Kesinga",
        "keshinga" to "Kesinga",
        "କେସିଙ୍ଗା" to "Kesinga",
        "bhawanipatna" to "Bhawanipatna",
        "bhawanipatana" to "Bhawanipatna",
        "bhavanipatna" to "Bhawanipatna",
        "ଭବାନୀପାଟଣା" to "Bhawanipatna",
        "ଭବାନିପାଟଣା" to "Bhawanipatna",
        "dharamgarh" to "Dharamgarh",
        "dharmagarh" to "Dharamgarh",
        "ଧର୍ମଗଡ଼" to "Dharamgarh",
        "ଧର୍ମଗଡ" to "Dharamgarh",
        "junagarh" to "Junagarh",
        "ଜୁନାଗଡ଼" to "Junagarh",
        "ଜୁନାଗଡ" to "Junagarh",
        "karlamunda" to "Karlamunda",
        "କର୍ଲାମୁଣ୍ଡା" to "Karlamunda",
        "narla" to "Narla",
        "ନର୍ଲା" to "Narla",
        "lanjigarh" to "Lanjigarh",
        "ଲାଞ୍ଜିଗଡ଼" to "Lanjigarh",
        "m rampur" to "M.Rampur",
        "rampur" to "M.Rampur",
        "ରାମପୁର" to "M.Rampur",
        "jaipatna" to "Jaipatna",
        "ଜୟପାଟଣା" to "Jaipatna",
        "koksara" to "Koksara",
        "କୋକସରା" to "Koksara",
        "golamunda" to "Golamunda",
        "ଗୋଲାମୁଣ୍ଡା" to "Golamunda",
        "kalampur" to "Kalampur",
        "କଲମପୁର" to "Kalampur",
        "kalahandi" to "Kalahandi",
        "କଳାହାଣ୍ଡି" to "Kalahandi",
        "jatni" to "Jatni",
        "jatani" to "Jatni",
        "ଜଟଣୀ" to "Jatni",
        "kudiary" to "Kudiary",
        "କୁଦିଆରୀ" to "Kudiary",
        "khordha" to "Khordha",
        "khurda" to "Khordha",
        "ଖୋର୍ଦ୍ଧା" to "Khordha",
        "bhubaneswar" to "Bhubaneswar",
        "ଭୁବନେଶ୍ୱର" to "Bhubaneswar",
        "cuttack" to "Cuttack",
        "କଟକ" to "Cuttack",
        "sambalpur" to "Sambalpur",
        "ସମ୍ବଲପୁର" to "Sambalpur",
        "titilagarh" to "Titilagarh",
        "ଟିଟିଲାଗଡ଼" to "Titilagarh",
        "bolangir" to "Bolangir",
        "balangir" to "Bolangir",
        "ବଲାଙ୍ଗୀର" to "Bolangir",
        "bargarh" to "Bargarh",
        "ବରଗଡ଼" to "Bargarh",
        "nuapada" to "Nuapada",
        "ନୂଆପଡ଼ା" to "Nuapada",
        "khariar" to "Khariar",
        "ଖଡ଼ିଆଳ" to "Khariar",
        "rayagada" to "Rayagada",
        "ରାୟଗଡ଼" to "Rayagada"
    )

    /**
     * Extracts canonical location name from a document (e.g., "Kesinga", "Bhawanipatna").
     * Checks mouza, tahasil, district, and raw OCR text.
     */
    fun extractCanonicalLocation(doc: DocumentRecord): String {
        // 1. Direct fields check
        val fieldCandidates = listOfNotNull(doc.mouza, doc.tahasil, doc.district)
        for (field in fieldCandidates) {
            val loc = matchKnownLocation(field)
            if (loc != null) return loc
        }

        // 2. Scan raw extracted text for known locations
        val rawText = doc.rawExtractedText
        if (!rawText.isNullOrBlank()) {
            val locFromText = findLocationInText(rawText)
            if (locFromText != null) return locFromText

            // Regex extraction from address/mouza patterns
            val extracted = extractVillageOrTahasilRegex(rawText)
            if (extracted != null) {
                val loc = matchKnownLocation(extracted)
                if (loc != null) return loc
                // Clean title-cased name
                return cleanLocationString(extracted)
            }
        }

        // 3. Fallback to any non-blank field
        val fallback = doc.mouza ?: doc.tahasil ?: doc.district
        if (!fallback.isNullOrBlank()) {
            return cleanLocationString(fallback)
        }

        return "Unassigned / General"
    }

    /**
     * Finds any known Odisha location mentioned in raw text.
     */
    fun findLocationInText(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        // Check specific towns first before broad district names
        val priorityKeys = listOf(
            "kesinga", "କେସିଙ୍ଗା", "keshinga",
            "bhawanipatna", "ଭବାନୀପାଟଣା", "ଭବାନିପାଟଣା", "bhawanipatana",
            "dharamgarh", "ଧର୍ମଗଡ଼", "dharmagarh",
            "junagarh", "ଜୁନାଗଡ଼",
            "karlamunda", "କର୍ଲାମୁଣ୍ଡା",
            "narla", "ନର୍ଲା",
            "lanjigarh", "ଲାଞ୍ଜିଗଡ଼",
            "m rampur", "ରାମପୁର",
            "jaipatna", "ଜୟପାଟଣା",
            "koksara", "କୋକସରା",
            "golamunda", "ଗୋଲାମୁଣ୍ଡା",
            "kalampur", "କଲମପୁର",
            "kudiary", "କୁଦିଆରୀ",
            "jatni", "ଜଟଣୀ",
            "kalahandi", "କଳାହାଣ୍ଡି",
            "khordha", "ଖୋର୍ଦ୍ଧା",
            "titilagarh", "ଟିଟିଲାଗଡ଼",
            "bolangir", "balangir", "ବଲାଙ୍ଗୀର",
            "bargarh", "ବରଗଡ଼",
            "sambalpur", "ସମ୍ବଲପୁର",
            "bhubaneswar", "ଭୁବନେଶ୍ୱର",
            "cuttack", "କଟକ"
        )

        for (key in priorityKeys) {
            if (lower.contains(key) || text.contains(key)) {
                return LOCATION_ALIASES[key]
            }
        }
        return null
    }

    /**
     * Matches string against known aliases.
     */
    fun matchKnownLocation(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val clean = input.lowercase(Locale.ROOT)
            .replace("[^a-z\\u0B00-\\u0B7F]".toRegex(), " ")
            .trim()

        for ((alias, canonical) in LOCATION_ALIASES) {
            if (clean.contains(alias) || alias.contains(clean)) {
                return canonical
            }
        }
        return null
    }

    /**
     * Extracts village or tahasil using regex.
     */
    private fun extractVillageOrTahasilRegex(text: String): String? {
        val regex = "(?:(?:village|vill|mouza|tahasil|tehsil|ମୌଜା|ତହସିଲ|ଗ୍ରାମ|ଜିଲ୍ଲା|ଠିକଣା)[:\\s\\-]+|(?:at[/\\-\\s]+po[:\\s\\-]+)|(?:at[:\\-\\s]+))([A-Za-z\\u0B00-\\u0B7F]+)".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.find(text)?.groupValues?.getOrNull(1)?.trim()
        if (!match.isNullOrBlank() && match.length >= 3) {
            return match
        }
        return null
    }

    private fun cleanLocationString(raw: String): String {
        var s = raw.trim()
            .replace("[^A-Za-z\\u0B00-\\u0B7F\\s]".toRegex(), " ")
            .trim()
        if (s.isBlank()) return "Unassigned / General"

        // Title case for English
        return s.split("\\s+".toRegex()).filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    /**
     * Returns the formatted display name with Odia script (e.g. "Kesinga (କେସିଙ୍ଗା)").
     */
    fun getDisplayName(canonicalName: String): String {
        val known = KNOWN_LOCATIONS.firstOrNull { it.canonicalName.equals(canonicalName, ignoreCase = true) }
        return known?.displayName ?: canonicalName
    }

    fun getOdiaName(canonicalName: String): String {
        val known = KNOWN_LOCATIONS.firstOrNull { it.canonicalName.equals(canonicalName, ignoreCase = true) }
        return known?.odiaName ?: ""
    }

    /**
     * Groups all documents and match pairs by their canonical location.
     */
    fun groupDocumentsAndPairs(
        documents: List<DocumentRecord>,
        matchPairs: List<MatchPairRecord>
    ): List<LocationGroupData> {
        val nonDuplicates = documents.filter { !it.isDuplicate }
        val docMap = nonDuplicates.associateBy { it.id }

        // Associate documents with location
        val docLocationMap = nonDuplicates.associate { it.id to extractCanonicalLocation(it) }

        // Group documents by location
        val groups = nonDuplicates.groupBy { docLocationMap[it.id] ?: "Unassigned / General" }

        // Determine which pairs belong to each location
        // A pair belongs to a location if either the Land Record or Aadhaar belongs to that location
        val locationPairs = mutableMapOf<String, MutableList<MatchPairRecord>>()

        for (pair in matchPairs) {
            val landDoc = docMap[pair.landDocId]
            val aadhaarDoc = docMap[pair.aadhaarDocId]

            val landLoc = landDoc?.let { docLocationMap[it.id] }
            val aadhaarLoc = aadhaarDoc?.let { docLocationMap[it.id] }

            val primaryLoc = when {
                landLoc != null && landLoc != "Unassigned / General" -> landLoc
                aadhaarLoc != null && aadhaarLoc != "Unassigned / General" -> aadhaarLoc
                else -> "Unassigned / General"
            }

            locationPairs.getOrPut(primaryLoc) { mutableListOf() }.add(pair)
            // If aadhaar location is different and valid, link it there as well
            if (aadhaarLoc != null && aadhaarLoc != primaryLoc && aadhaarLoc != "Unassigned / General") {
                locationPairs.getOrPut(aadhaarLoc) { mutableListOf() }.add(pair)
            }
        }

        // Build sorted list of LocationGroupData
        val allLocationKeys = (groups.keys + locationPairs.keys).distinct().sortedWith(
            Comparator { a, b ->
                // Keep Unassigned at the end, Kesinga & Bhawanipatna near the top
                when {
                    a == "Unassigned / General" -> 1
                    b == "Unassigned / General" -> -1
                    a.equals("Kesinga", ignoreCase = true) -> -1
                    b.equals("Kesinga", ignoreCase = true) -> 1
                    a.equals("Bhawanipatna", ignoreCase = true) -> -1
                    b.equals("Bhawanipatna", ignoreCase = true) -> 1
                    else -> a.compareTo(b)
                }
            }
        )

        return allLocationKeys.map { locKey ->
            val docsInGroup = groups[locKey] ?: emptyList()
            val pataDocs = docsInGroup.filter { it.docType == DocType.LAND_RECORD }
            val aadhaarDocs = docsInGroup.filter { it.docType == DocType.AADHAAR }
            val pairsInGroup = locationPairs[locKey]?.distinctBy { it.id } ?: emptyList()

            LocationGroupData(
                locationKey = locKey,
                displayName = getDisplayName(locKey),
                odiaName = getOdiaName(locKey),
                pataDocuments = pataDocs,
                aadhaarDocuments = aadhaarDocs,
                matchedPairs = pairsInGroup,
                totalDocuments = pataDocs.size + aadhaarDocs.size,
                matchedCount = pairsInGroup.size
            )
        }
    }
}
