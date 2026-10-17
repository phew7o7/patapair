package com.example.engine

import kotlin.math.max
import kotlin.math.min

data class NameMatchResult(
    val score: Float,
    val explanation: String,
    val isAutoApprovable: Boolean
)

object MatcherEngine {

    const val AUTO_APPROVE_THRESHOLD = 0.90f
    const val SUGGEST_THRESHOLD = 0.65f

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

        // 2. Token permutation check (e.g., "Omkar Rath" vs "Rath Omkar")
        if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            return NameMatchResult(
                score = 0.95f,
                explanation = "Exact token match with different word order ('$normA' <-> '$normL')",
                isAutoApprovable = true
            )
        }

        // Check surnames: If both have multi-word names, ensure surnames don't conflict
        if (tokensA.size >= 2 && tokensL.size >= 2) {
            val surnameA = tokensA.last()
            val surnameL = tokensL.last()
            val surnameDist = levenshteinSimilarity(surnameA, surnameL)

            // If surnames are clearly different (similarity < 0.70), heavily penalize
            if (surnameDist < 0.70f) {
                // Check if surname was moved to the front
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

        // Prefix scale
        var prefix = 0
        for (i in 0 until min(4, min(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return jaro + prefix * 0.1f * (1.0f - jaro)
    }
}
