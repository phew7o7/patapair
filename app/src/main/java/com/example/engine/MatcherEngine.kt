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

    const val AUTO_APPROVE_THRESHOLD = 0.90f
    const val SUGGEST_THRESHOLD = 0.65f

    /**
     * Evaluates a candidate pairing across multiple independent signals:
     * - Name phonetic cross-script similarity
     * - Surname consistency (prevents collapsing distinct people)
     * - Father / Guardian name alignment
     * - Mouza / Village location match
     * - Multi-candidate conflict detection
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

        val normA = aadhaarPerson.normalizedName
        val normL = landPerson.normalizedName

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

        var baseScore = 0.0f

        // Signal 1: Name cross-script match
        if (normA == normL) {
            baseScore += 0.85f
            signals.add(MatchSignal("EXACT_NAME_MATCH", 0.85f, true, "Exact cross-script phonetic match ('$normA' == '$normL')"))
            supporting.add("Exact cross-script phonetic match: '$normA' == '$normL'")
        } else if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            baseScore += 0.80f
            signals.add(MatchSignal("TOKEN_PERMUTATION", 0.80f, true, "Exact token match with inverted word order ('$normA' <-> '$normL')"))
            supporting.add("Complete token match with inverted word order: '$normA' <-> '$normL'")
        } else {
            val jwScore = jaroWinkler(normA, normL)
            var matchedTokensScore = 0f
            val usedL = BooleanArray(tokensL.size)

            for (tA in tokensA) {
                var bestTokSim = 0f
                var bestLIdx = -1
                for (j in tokensL.indices) {
                    if (!usedL[j]) {
                        val sim = jaroWinkler(tA, tokensL[j])
                        if (sim > bestTokSim) {
                            bestTokSim = sim
                            bestLIdx = j
                        }
                    }
                }
                if (bestTokSim >= 0.75f && bestLIdx != -1) {
                    matchedTokensScore += bestTokSim
                    usedL[bestLIdx] = true
                }
            }

            val tokenCoverage = if (tokensA.isNotEmpty() && tokensL.isNotEmpty()) {
                (2.0f * matchedTokensScore) / (tokensA.size + tokensL.size)
            } else 0f

            val nameSim = (jwScore * 0.4f) + (tokenCoverage * 0.6f)
            val contribution = nameSim * 0.75f
            baseScore += contribution
            signals.add(MatchSignal("PHONETIC_SIMILARITY", contribution, nameSim >= 0.70f, "Phonetic & token similarity: ${(nameSim * 100).toInt()}%"))

            if (nameSim >= 0.70f) {
                supporting.add("Cross-script name similarity: ${(nameSim * 100).toInt()}% between '$normA' and '$normL'")
            } else {
                conflicting.add("Weak name similarity: ${(nameSim * 100).toInt()}%")
            }
        }

        // Signal 2: Surname Consistency (MANDATORY: Surnames must never be removed or ignored)
        if (tokensA.size >= 2 && tokensL.size >= 2) {
            val surnameA = tokensA.last()
            val surnameL = tokensL.last()
            val surnameDist = levenshteinSimilarity(surnameA, surnameL)

            if (surnameDist >= 0.80f) {
                baseScore += 0.20f
                signals.add(MatchSignal("SURNAME_MATCH", 0.20f, true, "Matching family surname: '$surnameA'"))
                supporting.add("Verified matching surname: '$surnameA'")
            } else {
                // Check if surname was moved to front in one of the documents
                val frontMatch = levenshteinSimilarity(tokensA.first(), tokensL.last()) >= 0.80f ||
                                 levenshteinSimilarity(tokensA.last(), tokensL.first()) >= 0.80f
                if (frontMatch) {
                    baseScore += 0.15f
                    signals.add(MatchSignal("SURNAME_TRANSPOSITION", 0.15f, true, "Surname moved to front: '$surnameA' <-> '$surnameL'"))
                    supporting.add("Surname transposition identified: '$surnameA' <-> '$surnameL'")
                } else {
                    // Severe penalty: distinct surnames mean distinct individuals!
                    baseScore -= 0.35f
                    signals.add(MatchSignal("SURNAME_MISMATCH", -0.35f, false, "Conflicting surnames: '$surnameA' vs '$surnameL'"))
                    conflicting.add("Surname conflict: '$surnameA' (Aadhaar) vs '$surnameL' (Land Record). Do not collapse distinct people.")
                }
            }
        }

        // Signal 3: Father / Spouse / Guardian Corroboration
        val fatherA = aadhaarPerson.fathersOrSpouseName
        val fatherL = landPerson.fathersOrSpouseName
        if (!fatherA.isNullOrBlank() && !fatherL.isNullOrBlank()) {
            val normFA = OdiaTransliterationEngine.normalizeForMatching(fatherA)
            val normFL = OdiaTransliterationEngine.normalizeForMatching(fatherL)
            val fSim = jaroWinkler(normFA, normFL)

            if (fSim >= 0.80f || normFA == normFL) {
                baseScore += 0.15f
                signals.add(MatchSignal("FATHER_NAME_MATCH", 0.15f, true, "Father / Guardian corroborated: '$fatherA'"))
                supporting.add("Father / Guardian match: '$fatherA' <-> '$fatherL'")
            } else {
                baseScore -= 0.15f
                signals.add(MatchSignal("FATHER_NAME_MISMATCH", -0.15f, false, "Father / Guardian discrepancy: '$fatherA' vs '$fatherL'"))
                conflicting.add("Discrepant father/guardian: '$fatherA' vs '$fatherL'")
            }
        }

        // Signal 4: Mouza / Location Corroboration
        val mouza = landDoc?.mouza
        val aadhaarRaw = aadhaarDoc?.rawExtractedText
        if (!mouza.isNullOrBlank() && !aadhaarRaw.isNullOrBlank()) {
            if (aadhaarRaw.contains(mouza, ignoreCase = true)) {
                baseScore += 0.08f
                signals.add(MatchSignal("MOUZA_LOCATION_MATCH", 0.08f, true, "Village/Mouza match: '$mouza'"))
                supporting.add("Village / Mouza corroboration: '$mouza'")
            }
        }

        // Signal 5: Multiple Candidate Conflict Warning
        if (hasMultipleCandidates) {
            conflicting.add("Multiple candidate matches detected across records. Verify Khata/Plot carefully.")
            signals.add(MatchSignal("MULTI_CANDIDATE_FLAG", 0.0f, false, "Candidate has multiple potential pairings"))
        }

        val finalScore = baseScore.coerceIn(0.0f, 1.0f)

        val explanationLines = mutableListOf<String>()
        if (supporting.isNotEmpty()) {
            explanationLines.add("Supporting: " + supporting.joinToString("; "))
        }
        if (conflicting.isNotEmpty()) {
            explanationLines.add("Caution / Conflicting: " + conflicting.joinToString("; "))
        }
        val fullExplanation = explanationLines.joinToString("\n")

        return MultiSignalMatchResult(
            score = finalScore,
            explanation = fullExplanation,
            supportingEvidence = supporting,
            conflictingEvidence = conflicting,
            signals = signals,
            isAutoApprovable = false // Strict requirement: Human must approve
        )
    }

    fun compareNames(aadhaarName: String, landRecordName: String): NameMatchResult {
        val normA = OdiaTransliterationEngine.normalizeForMatching(aadhaarName)
        val normL = OdiaTransliterationEngine.normalizeForMatching(landRecordName)

        if (normA.isBlank() || normL.isBlank()) {
            return NameMatchResult(0f, "Empty name input", false)
        }

        // 1. Exact match after transliteration and phonetic normalization
        if (normA == normL) {
            return NameMatchResult(
                score = 1.0f,
                explanation = "Exact cross-script phonetic match ('$normA' == '$normL')",
                isAutoApprovable = true
            )
        }

        val tokensA = normA.split(" ").filter { it.isNotBlank() }
        val tokensL = normL.split(" ").filter { it.isNotBlank() }

        // 2. Token permutation check
        if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            return NameMatchResult(
                score = 0.95f,
                explanation = "Exact token match with different word order ('$normA' <-> '$normL')",
                isAutoApprovable = false
            )
        }

        // Check surnames: If both have multi-word names, ensure surnames don't conflict
        if (tokensA.size >= 2 && tokensL.size >= 2) {
            val surnameA = tokensA.last()
            val surnameL = tokensL.last()
            val surnameDist = levenshteinSimilarity(surnameA, surnameL)

            // If surnames are clearly different (similarity < 0.70), heavily penalize
            if (surnameDist < 0.70f) {
                val frontMatch = levenshteinSimilarity(tokensA.first(), tokensL.last()) >= 0.80f ||
                                 levenshteinSimilarity(tokensA.last(), tokensL.first()) >= 0.80f
                if (!frontMatch) {
                    val overallSim = jaroWinkler(normA, normL)
                    val penalized = min(overallSim * 0.5f, 0.55f)
                    return NameMatchResult(
                        score = penalized,
                        explanation = "Surname mismatch ('$surnameA' vs '$surnameL'), below threshold",
                        isAutoApprovable = false
                    )
                }
            }
        }

        // 3. Jaro-Winkler Similarity
        val jwScore = jaroWinkler(normA, normL)

        // 4. Token Overlap & Fuzzy Token Match
        var matchedTokensScore = 0f
        val usedL = BooleanArray(tokensL.size)

        for (tA in tokensA) {
            var bestTokSim = 0f
            var bestLIdx = -1
            for (j in tokensL.indices) {
                if (!usedL[j]) {
                    val sim = jaroWinkler(tA, tokensL[j])
                    if (sim > bestTokSim) {
                        bestTokSim = sim
                        bestLIdx = j
                    }
                }
            }
            if (bestTokSim >= 0.75f && bestLIdx != -1) {
                matchedTokensScore += bestTokSim
                usedL[bestLIdx] = true
            }
        }

        val tokenCoverage = (2.0f * matchedTokensScore) / (tokensA.size + tokensL.size)
        val combinedScore = (jwScore * 0.4f) + (tokenCoverage * 0.6f)

        val explanation = when {
            combinedScore >= 0.90f -> "Strong phonetic & token similarity (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL'"
            combinedScore >= 0.70f -> "Moderate similarity (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL' - Requires manual review"
            else -> "Low similarity (${(combinedScore * 100).toInt()}%) between '$normA' and '$normL'"
        }

        return NameMatchResult(
            score = combinedScore,
            explanation = explanation,
            isAutoApprovable = combinedScore >= AUTO_APPROVE_THRESHOLD
        )
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val dist = levenshteinDistance(s1, s2)
        return 1.0f - (dist.toFloat() / maxLen)
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
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

        return jaro + prefix * 0.1f * (1.0f - jaro)
    }
}
