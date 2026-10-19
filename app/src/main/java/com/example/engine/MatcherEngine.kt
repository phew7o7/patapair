package com.example.engine

import com.example.model.DocumentRecord
import com.example.model.MatchSignal
import com.example.model.PersonRecord
import kotlin.math.max
import kotlin.math.min

data class NameMatchResult(
    val score: Float,
    val explanation: String,
    val isAutoApprovable: Boolean = false
)

data class MultiSignalMatchResult(
    val score: Float,
    val explanation: String,
    val supportingEvidence: List<String>,
    val conflictingEvidence: List<String>,
    val signals: List<MatchSignal>,
    val isAutoApprovable: Boolean = false // Matches MUST NEVER be auto-approved
)

object MatcherEngine {

    // Configurable experimental weighting (65% name, 35% location)
    const val WEIGHT_NAME = 0.65f
    const val WEIGHT_LOCATION = 0.35f

    const val THRESHOLD_SUGGESTED = 0.65f
    const val THRESHOLD_REVIEW = 0.40f

    /**
     * Extracts village or mouza name from raw text if present.
     */
    fun extractVillageFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = "(?:(?:village|vill|mouza|ମୌଜା|ଗ୍ରାମ)[:\\s\\-]+|(?:at[/\\-\\s]+po[:\\s\\-]+)|(?:at[:\\-\\s]+))([A-Za-z\\u0B00-\\u0B7F]+)".toRegex(RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Extracts father or spouse name from raw text if present.
     */
    fun extractFatherFromText(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val odiaFatherMatch = "(?:ପିତା|ସ୍ୱାମୀ)[:\\s\\-]+([A-Za-z\\u0B00-\\u0B7F\\s]+)".toRegex().find(text)
        val engFatherMatch = "(?:s/o|d/o|w/o|c/o|father)[:\\s\\-]+([A-Za-z\\s]+)".toRegex(RegexOption.IGNORE_CASE).find(text)
        return odiaFatherMatch?.groupValues?.getOrNull(1)?.lines()?.firstOrNull()?.trim()
            ?: engFatherMatch?.groupValues?.getOrNull(1)?.lines()?.firstOrNull()?.trim()
    }

    /**
     * Normalizes a village or mouza name for comparison across English and Odia scripts.
     */
    fun normalizeLocation(loc: String?): String {
        if (loc.isNullOrBlank()) return ""
        var s = loc.lowercase(java.util.Locale.ROOT)
            .replace("[^a-z\\u0B00-\\u0B7F\\s]".toRegex(), " ")
            .trim()
        if (OdiaTransliterationEngine.detectScript(s) == com.example.model.ScriptType.ODIA) {
            s = OdiaTransliterationEngine.transliterateOdiaToEnglish(s).lowercase(java.util.Locale.ROOT)
        }
        return s.removePrefix("at ")
            .removePrefix("vill ")
            .removePrefix("village ")
            .removePrefix("mouza ")
            .trim()
    }

    /**
     * Conservative composite string similarity.
     * Combines Levenshtein edit distance and Jaro-Winkler with strict length-ratio guards.
     * Prevents short strings whose characters happen to be a sparse subsequence of a much longer
     * string (e.g. "aara" vs "jamidarangk") from generating high similarity false positives.
     */
    fun safeSimilarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val len1 = s1.length
        val len2 = s2.length
        val minLen = min(len1, len2)
        val maxLen = max(len1, len2)
        val lengthRatio = minLen.toFloat() / maxLen.toFloat()

        val levSim = levenshteinSimilarity(s1, s2)
        val jw = jaroWinkler(s1, s2)

        // Asymmetric length trap: If one string is much shorter than the other
        // and character edit distance is poor (e.g. "aara" vs "jamidarangk"),
        // pure Jaro-Winkler gives an artificial ~0.79 because the short string's characters
        // appear in the longer string. We heavily penalize when lengths diverge or edit distance is poor.
        if (lengthRatio < 0.60f && levSim < 0.55f) {
            return (levSim * lengthRatio).coerceIn(0.0f, 1.0f)
        }

        // Conservative composite similarity:
        // Weighted blend of Levenshtein (edit distance) and Jaro-Winkler, scaled by length agreement.
        val composite = (jw * 0.45f) + (levSim * 0.55f)
        val scale = Math.sqrt(lengthRatio.toDouble()).toFloat()
        return (composite * scale).coerceIn(0.0f, 1.0f)
    }

    /**
     * Simplified person matching using ONLY person names (Aadhaar & Land Record)
     * and Village/Mouza location when available in the source records.
     *
     * Weighting: 70% name similarity, 30% location similarity when both fields available.
     * When location is unavailable, name similarity alone is used and marked incomplete.
     * Matches are NEVER auto-approved.
     */
    fun evaluateCandidate(
        aadhaarPerson: PersonRecord,
        landPerson: PersonRecord,
        aadhaarDoc: DocumentRecord?,
        landDoc: DocumentRecord?,
        hasMultipleCandidates: Boolean = false
    ): MultiSignalMatchResult {
        val signals = mutableListOf<MatchSignal>()
        val supporting = mutableListOf<String>()
        val conflicting = mutableListOf<String>()

        val rawA = aadhaarPerson.originalName
        val rawL = if (landPerson.transliteratedName.isNotBlank()) landPerson.transliteratedName else landPerson.originalName

        val normA = OdiaTransliterationEngine.normalizeForMatching(rawA)
        val normL = OdiaTransliterationEngine.normalizeForMatching(rawL)

        if (normA.isBlank() || normL.isBlank()) {
            return MultiSignalMatchResult(
                score = 0.0f,
                explanation = "Empty name in one or both records",
                supportingEvidence = emptyList(),
                conflictingEvidence = listOf("Empty name input"),
                signals = emptyList(),
                isAutoApprovable = false
            )
        }

        val tokensA = normA.split(" ").filter { it.isNotBlank() }
        val tokensL = normL.split(" ").filter { it.isNotBlank() }

        // Short or incomplete name fragment check (single-token <= 4 chars or overall length <= 4)
        val isShortA = (tokensA.size == 1 && tokensA[0].length <= 4) || normA.length <= 4
        val isShortL = (tokensL.size == 1 && tokensL[0].length <= 4) || normL.length <= 4
        val isSingleTokenA = tokensA.size == 1
        val isSingleTokenL = tokensL.size == 1
        val isShortOrIncomplete = isShortA || isShortL

        var rawNameSim: Float

        // --- 1. Overall Name Comparison across Odia and English ---
        if (normA == normL) {
            rawNameSim = 1.0f
            signals.add(MatchSignal("EXACT_NAME_MATCH", 1.0f, true, "Exact cross-script phonetic match ('$normA' == '$normL')"))
            supporting.add("Exact cross-script phonetic match: '$normA' == '$normL'")
        } else if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            rawNameSim = 0.95f
            signals.add(MatchSignal("TOKEN_PERMUTATION", 0.95f, true, "Exact token match with inverted word order ('$normA' <-> '$normL')"))
            supporting.add("Complete token match with inverted word order: '$normA' <-> '$normL'")
        } else {
            val safeScore = safeSimilarity(normA, normL)
            var matchedTokensScore = 0f
            val usedL = BooleanArray(tokensL.size)

            for (tA in tokensA) {
                var bestTokSim = 0f
                var bestLIdx = -1
                for (j in tokensL.indices) {
                    if (!usedL[j]) {
                        val sim = safeSimilarity(tA, tokensL[j])
                        if (sim > bestTokSim) {
                            bestTokSim = sim
                            bestLIdx = j
                        }
                    }
                }
                if (bestTokSim >= 0.72f && bestLIdx != -1) {
                    matchedTokensScore += bestTokSim
                    usedL[bestLIdx] = true
                }
            }

            val tokenCoverage = if (tokensA.isNotEmpty() && tokensL.isNotEmpty()) {
                (2.0f * matchedTokensScore) / (tokensA.size + tokensL.size)
            } else 0f

            val computedSim = (safeScore * 0.45f) + (tokenCoverage * 0.55f)
            rawNameSim = computedSim
            signals.add(MatchSignal("PHONETIC_SIMILARITY", computedSim, computedSim >= 0.70f, "Phonetic similarity: ${(computedSim * 100).toInt()}%"))

            if (computedSim >= 0.70f) {
                supporting.add("Cross-script name similarity: ${(computedSim * 100).toInt()}% between '$normA' and '$normL'")
            } else {
                conflicting.add("Weak name similarity: ${(computedSim * 100).toInt()}%")
            }
        }

        // --- 2. Separate Given-Name and Surname Analysis ---
        // Surnames and given names can ONLY be separated when BOTH records have >= 2 tokens.
        // Do NOT apply surname logic to single-token names!
        if (tokensA.size >= 2 && tokensL.size >= 2) {
            val surnameA = tokensA.last()
            val surnameL = tokensL.last()
            val surnameSim = safeSimilarity(surnameA, surnameL)

            if (surnameSim < 0.70f) {
                // Check if surname was moved to front in one of the documents
                val frontMatch = safeSimilarity(tokensA.first(), tokensL.last()) >= 0.80f ||
                                 safeSimilarity(tokensA.last(), tokensL.first()) >= 0.80f
                if (!frontMatch) {
                    rawNameSim -= 0.40f
                    signals.add(MatchSignal("SURNAME_MISMATCH", -0.40f, false, "Conflicting surnames: '$surnameA' vs '$surnameL'"))
                    conflicting.add("Surname conflict: '$surnameA' (Aadhaar) vs '$surnameL' (Land Record). Do not collapse distinct people.")
                } else {
                    signals.add(MatchSignal("SURNAME_TRANSPOSITION", 0.10f, true, "Surname moved to front: '$surnameA' <-> '$surnameL'"))
                    supporting.add("Surname transposition identified: '$surnameA' <-> '$surnameL'")
                }
            } else if (surnameSim >= 0.80f) {
                // Surnames match; verify given names
                val givenA = tokensA.dropLast(1).joinToString(" ")
                val givenL = tokensL.dropLast(1).joinToString(" ")

                val givenTokensA = givenA.split(" ").filter { it.isNotBlank() }
                val givenTokensL = givenL.split(" ").filter { it.isNotBlank() }
                val primaryGivenMatch = if (givenTokensA.isNotEmpty() && givenTokensL.isNotEmpty()) {
                    safeSimilarity(givenTokensA.first(), givenTokensL.first()) >= 0.80f
                } else false

                val givenSim = if (primaryGivenMatch) 0.85f else safeSimilarity(givenA, givenL)
                val givenLev = if (primaryGivenMatch) 0.85f else levenshteinSimilarity(givenA, givenL)

                // Distinct given names sharing a surname (e.g. Ramesh Mohapatra vs Suresh Mohapatra)
                if (!primaryGivenMatch && (givenSim < 0.75f || givenLev < 0.70f || (givenA.first() != givenL.first() && levenshteinDistance(givenA, givenL) >= 2))) {
                    rawNameSim -= 0.45f
                    signals.add(MatchSignal("GIVEN_NAME_MISMATCH", -0.45f, false, "Given names diverge: '$givenA' vs '$givenL' despite shared surname"))
                    conflicting.add("Different given names ('$givenA' vs '$givenL') despite shared surname '$surnameA'. Surnames alone must never establish identity.")
                } else if (primaryGivenMatch || givenSim >= 0.80f) {
                    rawNameSim = (rawNameSim + 0.12f).coerceAtMost(1.0f)
                    signals.add(MatchSignal("NAME_SURNAME_CONCORDANT", 0.15f, true, "Concordant given name and surname"))
                    supporting.add("Verified concordant given name ('$givenA') and surname ('$surnameA')")
                } else {
                    conflicting.add("Uncertain given name similarity ('$givenA' vs '$givenL', ${(givenSim * 100).toInt()}%). Requires manual review.")
                }
            }
        } else if (isSingleTokenA || isSingleTokenL) {
            // Single-token name handling: intelligent overlap matching
            if (isSingleTokenA && isSingleTokenL) {
                val sim = safeSimilarity(tokensA[0], tokensL[0])
                if (sim >= 0.80f) {
                    rawNameSim = (rawNameSim + 0.15f).coerceAtMost(0.95f)
                    signals.add(MatchSignal("EXACT_SINGLE_TOKEN_MATCH", 0.20f, true, "Single-token name match ('$normA')"))
                    supporting.add("Single-token name match: '$normA' == '$normL'")
                }
            } else {
                // One has 1 token and other has >= 2 tokens (e.g. 'Suresh' vs 'Suresh Sahu')
                val singleTok = if (isSingleTokenA) tokensA[0] else tokensL[0]
                val multiTokens = if (isSingleTokenA) tokensL else tokensA
                var bestOverlap = 0f
                for (mt in multiTokens) {
                    val s = safeSimilarity(singleTok, mt)
                    if (s > bestOverlap) bestOverlap = s
                }
                if (bestOverlap >= 0.80f) {
                    rawNameSim = max(rawNameSim, (bestOverlap * 0.85f))
                    signals.add(MatchSignal("NAME_TOKEN_SUBSET_MATCH", 0.15f, true, "Single-token matches word in full name ('$singleTok')"))
                    supporting.add("Single-token '$singleTok' aligns with token in full name (${(bestOverlap * 100).toInt()}%)")
                } else if (!isShortOrIncomplete) {
                    signals.add(MatchSignal("SINGLE_TOKEN_PARTIAL", 0.0f, false, "Single-token record"))
                }
            }
        }

        // Short or incomplete name penalty: single <=4 letter fragments are weak evidence
        if (isShortOrIncomplete) {
            rawNameSim = (rawNameSim * 0.40f).coerceAtMost(0.35f)
            signals.add(MatchSignal("SHORT_NAME_WEAK_EVIDENCE", -0.30f, false, "Short or incomplete name fragment"))
            conflicting.add("Short or incomplete name fragment ('$normA' vs '$normL'); weak discriminative evidence.")
        }

        val finalNameScore = rawNameSim.coerceIn(0.0f, 1.0f)

        // --- 3. Location (Village / Mouza / Tahasil / Town) Matching ---
        val canonA = aadhaarDoc?.let { LocationExtractor.extractCanonicalLocation(it) } ?: ""
        val canonL = landDoc?.let { LocationExtractor.extractCanonicalLocation(it) } ?: ""

        val rawLocA = aadhaarDoc?.mouza ?: extractVillageFromText(aadhaarDoc?.rawExtractedText)
        val rawLocL = landDoc?.mouza ?: extractVillageFromText(landDoc?.rawExtractedText)

        val locA = normalizeLocation(rawLocA)
        val locL = normalizeLocation(rawLocL)

        val hasCanonMatch = canonA.isNotBlank() && canonA != "Unassigned / General" &&
                           canonL.isNotBlank() && canonL != "Unassigned / General" &&
                           canonA.equals(canonL, ignoreCase = true)

        val hasBothLocations = locA.isNotBlank() && locL.isNotBlank()
        var locScore = 0.0f
        var hasLocationConflict = false

        if (hasCanonMatch) {
            locScore = 1.0f
            val locLabel = rawLocA ?: canonA
            supporting.add("Village / Mouza corroborated: '$locLabel' (both documents share same location)")
            signals.add(MatchSignal("LOCATION_MATCH", 0.35f, true, "Same location group: $canonA"))
        } else if (hasBothLocations) {
            val locSim = safeSimilarity(locA, locL)

            if (locA == locL || locSim >= 0.82f) {
                locScore = locSim.coerceAtLeast(0.90f)
                supporting.add("Village / Mouza corroborated: '$rawLocA' <-> '$rawLocL' (${(locSim * 100).toInt()}%)")
                signals.add(MatchSignal("LOCATION_MATCH", 0.30f * locScore, true, "Corroborated village: '$rawLocA'"))
            } else if (locSim >= 0.68f) {
                locScore = locSim
                supporting.add("Plausible village spelling variant: '$rawLocA' <-> '$rawLocL' (${(locSim * 100).toInt()}%)")
                signals.add(MatchSignal("LOCATION_VARIANT", 0.20f * locScore, true, "Village spelling variant"))
            } else {
                hasLocationConflict = true
                conflicting.add("Conflicting location: Village/Mouza '$rawLocA' (Aadhaar) vs '$rawLocL' (Land Record).")
                signals.add(MatchSignal("LOCATION_CONFLICT", -0.20f, false, "Conflicting village: '$rawLocA' vs '$rawLocL'"))
                locScore = 0.0f
            }
        } else {
            // Location is missing in one or both records: mark unknown, NOT supporting and NOT conflicting
            signals.add(MatchSignal("LOCATION_UNAVAILABLE", 0.0f, false, "Village/Mouza unrecorded in one or both records (evidence incomplete)"))
        }

        // --- 4. Transparent Ranking Calculation ---
        // Ranking scores are heuristics for ordering candidates, not probabilities of identity.
        val finalScore = if (hasBothLocations) {
            val combined = (finalNameScore * WEIGHT_NAME) + (locScore * WEIGHT_LOCATION)
            if (hasLocationConflict) {
                (combined - 0.20f).coerceAtLeast(0.0f)
            } else {
                combined
            }
        } else {
            // Location unavailable: use name similarity alone and mark evidence incomplete
            finalNameScore
        }.coerceIn(0.0f, 1.0f)

        // Multiple Candidate Conflict Warning
        if (hasMultipleCandidates) {
            conflicting.add("Multiple potential candidate pairings detected across records.")
            signals.add(MatchSignal("MULTI_CANDIDATE_FLAG", 0.0f, false, "Candidate has multiple potential pairings"))
        }

        val explanationLines = mutableListOf<String>()
        explanationLines.add("Ranking Score: ${(finalScore * 100).toInt()}% (Heuristic ranking aid, not identity probability)")
        if (hasBothLocations) {
            val nameContrib = (finalNameScore * WEIGHT_NAME * 100).toInt()
            val locContrib = (locScore * WEIGHT_LOCATION * 100).toInt()
            explanationLines.add("Weights: 70% Name ($nameContrib%), 30% Location ($locContrib%)")
        } else {
            explanationLines.add("Weights: 100% Name (${(finalNameScore * 100).toInt()}%), Location: Unknown/Unavailable in source")
        }
        if (supporting.isNotEmpty()) {
            explanationLines.add("Supporting Signals: " + supporting.joinToString("; "))
        }
        if (conflicting.isNotEmpty()) {
            explanationLines.add("Cautionary / Conflicting Signals: " + conflicting.joinToString("; "))
        }
        val fullExplanation = explanationLines.joinToString("\n")

        return MultiSignalMatchResult(
            score = finalScore,
            explanation = fullExplanation,
            supportingEvidence = supporting,
            conflictingEvidence = conflicting,
            signals = signals,
            isAutoApprovable = false // Matches MUST NEVER be auto-approved
        )
    }

    fun compareNames(aadhaarName: String, landRecordName: String): NameMatchResult {
        val normA = OdiaTransliterationEngine.normalizeForMatching(aadhaarName)
        val normL = OdiaTransliterationEngine.normalizeForMatching(landRecordName)

        if (normA.isBlank() || normL.isBlank()) {
            return NameMatchResult(0f, "Empty name input", false)
        }

        val tokensA = normA.split(" ").filter { it.isNotBlank() }
        val tokensL = normL.split(" ").filter { it.isNotBlank() }

        // Short or incomplete name fragment check (single token <= 4 chars or length <= 4)
        val isShortA = (tokensA.size == 1 && tokensA[0].length <= 4) || normA.length <= 4
        val isShortL = (tokensL.size == 1 && tokensL[0].length <= 4) || normL.length <= 4
        if (isShortA || isShortL) {
            val sim = safeSimilarity(normA, normL)
            val penalized = (sim * 0.40f).coerceAtMost(0.35f)
            return NameMatchResult(
                score = penalized,
                explanation = "Short or incomplete name fragment ('$normA' vs '$normL'); weak discriminative evidence",
                isAutoApprovable = false
            )
        }

        // 1. Exact match after transliteration and phonetic normalization
        if (normA == normL) {
            return NameMatchResult(
                score = 1.0f,
                explanation = "Exact cross-script phonetic match ('$normA' == '$normL')",
                isAutoApprovable = false // Matches are ranking aids, never auto-approved
            )
        }

        // 2. Token permutation check
        if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            return NameMatchResult(
                score = 0.95f,
                explanation = "Exact token match with different word order ('$normA' <-> '$normL')",
                isAutoApprovable = false
            )
        }

        // 3. Separate Given-Name and Surname Analysis (when BOTH have >= 2 tokens)
        if (tokensA.size >= 2 && tokensL.size >= 2) {
            val surnameA = tokensA.last()
            val surnameL = tokensL.last()
            val surnameSim = safeSimilarity(surnameA, surnameL)

            // If surnames are clearly different (similarity < 0.70), heavily penalize
            if (surnameSim < 0.70f) {
                val frontMatch = safeSimilarity(tokensA.first(), tokensL.last()) >= 0.80f ||
                                 safeSimilarity(tokensA.last(), tokensL.first()) >= 0.80f
                if (!frontMatch) {
                    val overallSim = safeSimilarity(normA, normL)
                    val penalized = min(overallSim * 0.4f, 0.45f)
                    return NameMatchResult(
                        score = penalized,
                        explanation = "Surname mismatch ('$surnameA' vs '$surnameL'), below threshold",
                        isAutoApprovable = false
                    )
                }
            } else if (surnameSim >= 0.80f) {
                // Surnames match; ensure given names are not completely divergent
                val givenA = tokensA.dropLast(1).joinToString(" ")
                val givenL = tokensL.dropLast(1).joinToString(" ")

                val givenTokensA = givenA.split(" ").filter { it.isNotBlank() }
                val givenTokensL = givenL.split(" ").filter { it.isNotBlank() }
                val primaryGivenMatch = if (givenTokensA.isNotEmpty() && givenTokensL.isNotEmpty()) {
                    safeSimilarity(givenTokensA.first(), givenTokensL.first()) >= 0.80f
                } else false

                val givenSim = if (primaryGivenMatch) 0.85f else safeSimilarity(givenA, givenL)
                val givenLev = if (primaryGivenMatch) 0.85f else levenshteinSimilarity(givenA, givenL)

                if (!primaryGivenMatch && (givenSim < 0.75f || givenLev < 0.70f || (givenA.first() != givenL.first() && levenshteinDistance(givenA, givenL) >= 2))) {
                    val penalized = min(0.45f, (givenSim * 0.4f) + 0.10f)
                    return NameMatchResult(
                        score = penalized,
                        explanation = "Matching surname ('$surnameA') but weak/divergent given name ('$givenA' vs '$givenL') - Requires review",
                        isAutoApprovable = false
                    )
                }
            }
        }

        // 4. Safe Composite Similarity & Token Overlap
        val safeScore = safeSimilarity(normA, normL)
        var matchedTokensScore = 0f
        val usedL = BooleanArray(tokensL.size)

        for (tA in tokensA) {
            var bestTokSim = 0f
            var bestLIdx = -1
            for (j in tokensL.indices) {
                if (!usedL[j]) {
                    val sim = safeSimilarity(tA, tokensL[j])
                    if (sim > bestTokSim) {
                        bestTokSim = sim
                        bestLIdx = j
                    }
                }
            }
            if (bestTokSim >= 0.72f && bestLIdx != -1) {
                matchedTokensScore += bestTokSim
                usedL[bestLIdx] = true
            }
        }

        val tokenCoverage = (2.0f * matchedTokensScore) / (tokensA.size + tokensL.size)
        var combinedScore = (safeScore * 0.45f) + (tokenCoverage * 0.55f)

        // Single-token name cap if one is single-token and the other is multi-token
        if (tokensA.size == 1 || tokensL.size == 1) {
            combinedScore = combinedScore.coerceAtMost(0.55f)
        }

        val explanation = when {
            combinedScore >= 0.85f -> "Strong phonetic & token alignment (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL'"
            combinedScore >= 0.65f -> "Moderate similarity (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL' - Requires manual review"
            else -> "Low similarity (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL'"
        }

        return NameMatchResult(
            score = combinedScore,
            explanation = explanation,
            isAutoApprovable = false
        )
    }

    fun levenshteinSimilarity(s1: String, s2: String): Float {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun jaroWinkler(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f

        val matchDistance = max(s1.length, s2.length) / 2 - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0f

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toFloat()
        val jaro = (m / s1.length + m / s2.length + (m - transpositions / 2f) / m) / 3f

        var prefix = 0
        for (i in 0 until min(4, min(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        // Conservative prefix scaling: avoid rewarding shared prefixes (e.g. "Rama-", "Sure-")
        // so heavily that distinct given names become false matches.
        val prefixScale = if (jaro >= 0.75f && prefix > 0) 0.04f else 0.0f
        return (jaro + prefix * prefixScale * (1.0f - jaro)).coerceIn(0.0f, 1.0f)
    }
}
