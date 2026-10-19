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
        LocationInfo("Rayagada", "ରାୟଗଡ଼", "Rayagada (ରାୟଗଡ଼)"),
        LocationInfo("Puri", "ପୁରୀ", "Puri (ପୁରୀ)"),
        LocationInfo("Balasore", "ବାଲେଶ୍ୱର", "Balasore (ବାଲେଶ୍ୱର)"),
        LocationInfo("Bhadrak", "ଭଦ୍ରକ", "Bhadrak (ଭଦ୍ରକ)"),
        LocationInfo("Jajpur", "ଯାଜପୁର", "Jajpur (ଯାଜପୁର)"),
        LocationInfo("Kendrapara", "କେନ୍ଦ୍ରାପଡ଼ା", "Kendrapara (କେନ୍ଦ୍ରାପଡ଼ା)"),
        LocationInfo("Jagatsinghpur", "ଜଗତସିଂହପୁର", "Jagatsinghpur (ଜଗତସିଂହପୁର)"),
        LocationInfo("Angul", "ଅନୁଗୋଳ", "Angul (ଅନୁଗୋଳ)"),
        LocationInfo("Dhenkanal", "ଢେଙ୍କାନାଳ", "Dhenkanal (ଢେଙ୍କାନାଳ)"),
        LocationInfo("Keonjhar", "କେନ୍ଦୁଝର", "Keonjhar (କେନ୍ଦୁଝର)"),
        LocationInfo("Baripada", "ବାରିପଦା", "Baripada (ବାରିପଦା)"),
        LocationInfo("Rourkela", "ରାଉରକେଲା", "Rourkela (ରାଉରକେଲା)"),
        LocationInfo("Sundargarh", "ସୁନ୍ଦରଗଡ଼", "Sundargarh (ସୁନ୍ଦରଗଡ଼)"),
        LocationInfo("Jharsuguda", "ଝାରସୁଗୁଡ଼ା", "Jharsuguda (ଝାରସୁଗୁଡ଼ା)"),
        LocationInfo("Deogarh", "ଦେବଗଡ଼", "Deogarh (ଦେବଗଡ଼)"),
        LocationInfo("Sonepur", "ସୁବର୍ଣ୍ଣପୁର", "Sonepur (ସୁବର୍ଣ୍ଣପୁର)"),
        LocationInfo("Boudh", "ବୌଦ୍ଧ", "Boudh (ବୌଦ୍ଧ)"),
        LocationInfo("Phulbani", "ଫୁଲବାଣୀ", "Phulbani (ଫୁଲବାଣୀ)"),
        LocationInfo("Koraput", "କୋରାପୁଟ", "Koraput (କୋରାପୁଟ)"),
        LocationInfo("Jeypore", "ଜୟପୁର", "Jeypore (ଜୟପୁର)"),
        LocationInfo("Nabarangpur", "ନବରଙ୍ଗପୁର", "Nabarangpur (ନବରଙ୍ଗପୁର)"),
        LocationInfo("Malkangiri", "ମାଲକାନଗିରି", "Malkangiri (ମାଲକାନଗିରି)"),
        LocationInfo("Berhampur", "ବ୍ରହ୍ମପୁର", "Berhampur (ବ୍ରହ୍ମପୁର)"),
        LocationInfo("Paralakhemundi", "ପାରଳାଖେମୁଣ୍ଡି", "Paralakhemundi (ପାରଳାଖେମୁଣ୍ଡି)")
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
        "ରାୟଗଡ଼" to "Rayagada",
        "puri" to "Puri",
        "ପୁରୀ" to "Puri",
        "balasore" to "Balasore",
        "baleshwar" to "Balasore",
        "ବାଲେଶ୍ୱର" to "Balasore",
        "bhadrak" to "Bhadrak",
        "ଭଦ୍ରକ" to "Bhadrak",
        "jajpur" to "Jajpur",
        "ଯାଜପୁର" to "Jajpur",
        "kendrapara" to "Kendrapara",
        "kendrapada" to "Kendrapara",
        "କେନ୍ଦ୍ରାପଡ଼ା" to "Kendrapara",
        "jagatsinghpur" to "Jagatsinghpur",
        "ଜଗତସିଂହପୁର" to "Jagatsinghpur",
        "angul" to "Angul",
        "ଅନୁଗୋଳ" to "Angul",
        "dhenkanal" to "Dhenkanal",
        "ଢେଙ୍କାନାଳ" to "Dhenkanal",
        "keonjhar" to "Keonjhar",
        "kendujhar" to "Keonjhar",
        "କେନ୍ଦୁଝର" to "Keonjhar",
        "baripada" to "Baripada",
        "mayurbhanj" to "Baripada",
        "ବାରିପଦା" to "Baripada",
        "rourkela" to "Rourkela",
        "ରାଉରକେଲା" to "Rourkela",
        "sundargarh" to "Sundargarh",
        "ସୁନ୍ଦରଗଡ଼" to "Sundargarh",
        "jharsuguda" to "Jharsuguda",
        "ଝାରସୁଗୁଡ଼ା" to "Jharsuguda",
        "deogarh" to "Deogarh",
        "ଦେବଗଡ଼" to "Deogarh",
        "sonepur" to "Sonepur",
        "subarnapur" to "Sonepur",
        "ସୁବର୍ଣ୍ଣପୁର" to "Sonepur",
        "boudh" to "Boudh",
        "ବୌଦ୍ଧ" to "Boudh",
        "phulbani" to "Phulbani",
        "kandhamal" to "Phulbani",
        "ଫୁଲବାଣୀ" to "Phulbani",
        "koraput" to "Koraput",
        "କୋରାପୁଟ" to "Koraput",
        "jeypore" to "Jeypore",
        "ଜୟପୁର" to "Jeypore",
        "nabarangpur" to "Nabarangpur",
        "ନବରଙ୍ଗପୁର" to "Nabarangpur",
        "malkangiri" to "Malkangiri",
        "ମାଲକାନଗିରି" to "Malkangiri",
        "berhampur" to "Berhampur",
        "brahmapur" to "Berhampur",
        "ganjam" to "Berhampur",
        "ବ୍ରହ୍ମପୁର" to "Berhampur",
        "paralakhemundi" to "Paralakhemundi",
        "gajapati" to "Paralakhemundi",
        "ପାରଳାଖେମୁଣ୍ଡି" to "Paralakhemundi"
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

    private val DISTRICT_KEYS = setOf(
        "kalahandi", "କଳାହାଣ୍ଡି",
        "khordha", "khurda", "ଖୋର୍ଦ୍ଧା",
        "cuttack", "କଟକ",
        "sambalpur", "ସମ୍ବଲପୁର",
        "bargarh", "ବରଗଡ଼",
        "bolangir", "balangir", "ବଲାଙ୍ଗୀର",
        "subarnapur", "sonepur", "ସୁବର୍ଣ୍ଣପୁର", "ସୋନପୁର",
        "boudh", "ବୌଦ୍ଧ",
        "kandhamal", "phulbani", "କନ୍ଧମାଳ", "ଫୁଲବାଣୀ",
        "koraput", "କୋରାପୁଟ",
        "nabarangpur", "ନବରଙ୍ଗପୁର",
        "malkangiri", "ମାଲକାନଗିରି",
        "rayagada", "ରାୟଗଡ଼",
        "ganjam", "ଗଞ୍ଜାମ",
        "gajapati", "ଗଜପତି",
        "puri", "ପୁରୀ",
        "balasore", "baleshwar", "ବାଲେଶ୍ୱର",
        "bhadrak", "ଭଦ୍ରକ",
        "jajpur", "ଯାଜପୁର",
        "kendrapara", "କେନ୍ଦ୍ରାପଡ଼ା",
        "jagatsinghpur", "ଜଗତସିଂହପୁର",
        "angul", "ଅନୁଗୋଳ",
        "dhenkanal", "ଢେଙ୍କାନାଳ",
        "keonjhar", "କେନ୍ଦୁଝର",
        "mayurbhanj", "ମୟୂରଭଞ୍ଜ",
        "sundargarh", "ସୁନ୍ଦରଗଡ଼",
        "jharsuguda", "ଝାରସୁଗୁଡ଼ା",
        "deogarh", "ଦେବଗଡ଼",
        "nuapada", "ନୂଆପଡ଼ା"
    )

    /**
     * Finds any known Odisha location mentioned in raw text.
     * Prioritizes specific towns, villages, and tahasils first before broader district names.
     */
    fun findLocationInText(text: String): String? {
        val lower = text.lowercase(Locale.ROOT)
        // 1. Check specific localities first
        val townKeys = LOCATION_ALIASES.keys
            .filter { it !in DISTRICT_KEYS }
            .sortedByDescending { it.length }
        for (key in townKeys) {
            if (lower.contains(key) || text.contains(key)) {
                return LOCATION_ALIASES[key]
            }
        }

        // 2. Fall back to broader district names if no specific locality found
        val districtKeys = DISTRICT_KEYS.sortedByDescending { it.length }
        for (key in districtKeys) {
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
     * Extracts village, mouza, tahasil, or district using regex.
     */
    private fun extractVillageOrTahasilRegex(text: String): String? {
        val regex = "(?:(?:village|vill|mouza|tahasil|tehsil|district|dist|ମୌଜା|ତହସିଲ|ଗ୍ରାମ|ଜିଲ୍ଲା|ଠିକଣା)[:\\s\\-]+|(?:at[/\\-\\s]+po[:\\s\\-]+)|(?:at[:\\-\\s]+))([A-Za-z\\u0B00-\\u0B7F]+)".toRegex(RegexOption.IGNORE_CASE)
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

        // Build sorted list of LocationGroupData:
        // All locations are treated equally; groups with documents are listed first by count, then alphabetically.
        // "Unassigned / General" is placed at the end.
        val allLocationKeys = (groups.keys + locationPairs.keys).distinct().sortedWith(
            Comparator { a, b ->
                when {
                    a == "Unassigned / General" && b != "Unassigned / General" -> 1
                    b == "Unassigned / General" && a != "Unassigned / General" -> -1
                    else -> {
                        val docsA = (groups[a]?.size ?: 0) + (locationPairs[a]?.size ?: 0)
                        val docsB = (groups[b]?.size ?: 0) + (locationPairs[b]?.size ?: 0)
                        if (docsA != docsB) {
                            docsB.compareTo(docsA)
                        } else {
                            a.compareTo(b, ignoreCase = true)
                        }
                    }
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
