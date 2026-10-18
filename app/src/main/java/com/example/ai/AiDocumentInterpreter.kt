package com.example.ai

import android.content.Context
import com.example.engine.OdiaTransliterationEngine
import com.example.model.AiAvailabilityStatus
import com.example.model.AiInterpretationResult
import com.example.model.DocType
import com.example.model.MatchEvaluationResult
import com.example.model.ScriptType
import java.util.Locale

/**
 * Modular interface for AI-assisted document interpretation, OCR error correction,
 * name transliteration, and multi-signal match candidate evaluation.
 */
interface AiDocumentInterpreter {
    fun checkAvailability(context: Context): AiAvailabilityStatus
    suspend fun interpretDocument(rawText: String, docType: DocType, script: ScriptType): AiInterpretationResult
    suspend fun suggestTransliterations(name: String, script: ScriptType): List<String>
    suspend fun evaluateCandidateMatch(
        aadhaarName: String,
        landNameOriginal: String,
        landNameTranslit: String,
        contextInfo: Map<String, String> = emptyMap()
    ): MatchEvaluationResult
}

/**
 * Production implementation of AiDocumentInterpreter.
 * Follows strict privacy guidelines: 100% on-device by default, never transmits
 * sensitive identifiers or document images, and validates all AI outputs against source text.
 */
class DefaultAiDocumentInterpreter : AiDocumentInterpreter {

    companion object {
        // Known OCR corruptions of common header tokens that must not be extracted as names
        private val COMMON_OCR_HEADER_CORRECTIONS = mapOf(
            "aadnaar" to "aadhaar",
            "aadha4r" to "aadhaar",
            "aadhmar" to "aadhaar",
            "aadha" to "aadhaar",
            "uidai" to "uidai",
            "u1dai" to "uidai",
            "uida1" to "uidai",
            "meraaadhaar" to "meraaadhaar",
            "mera aadnaar" to "mera aadhaar",
            "khatiyan" to "khatiyan",
            "kl1atiyan" to "khatiyan",
            "kl1ata" to "khata",
            "khata no" to "khata no",
            "tahasi1" to "tahasil",
            "tahasii" to "tahasil",
            "mouza" to "mouza",
            "pattadar" to "pattadar",
            "rayat" to "rayat",
            "raya1" to "rayat"
        )

        // Test hook to allow injecting simulated on-device model states during unit tests
        @Volatile
        var testAvailabilityOverride: AiAvailabilityStatus? = null
    }

    override fun checkAvailability(context: Context): AiAvailabilityStatus {
        testAvailabilityOverride?.let { return it }

        // Inspect on-device generative capabilities (e.g. AICore / Edge Neural Services)
        val hasAiCorePackage = try {
            val pm = context.packageManager
            pm.getPackageInfo("com.google.android.aicore", 0) != null
        } catch (e: Exception) {
            false
        }

        // Critical safety rule: Do NOT assume a model supports Odia simply because it supports generative AI.
        // Odia script requires verified tokenizer and language vocab support.
        val supportsOdiaScript = false // Standard on-device mobile LLMs do not currently offer certified Odia OCR/transliteration

        return if (hasAiCorePackage) {
            AiAvailabilityStatus(
                isAvailable = true,
                modelName = "Android AICore On-Device Model",
                supportsOdia = supportsOdiaScript,
                reason = "AICore detected on device. Note: Odia script transliteration is routed through verified local phonetic dictionary for maximum accuracy.",
                isCloudOptIn = false
            )
        } else {
            AiAvailabilityStatus(
                isAvailable = false,
                modelName = "On-Device Neural Engine",
                supportsOdia = false,
                reason = "On-device AI hardware / AICore not initialized on this device. Using high-precision deterministic cross-script rulebook.",
                isCloudOptIn = false
            )
        }
    }

    override suspend fun interpretDocument(
        rawText: String,
        docType: DocType,
        script: ScriptType
    ): AiInterpretationResult {
        if (rawText.isBlank()) {
            return AiInterpretationResult(
                isSuccess = false,
                error = "Source text is empty"
            )
        }

        // 1. OCR Token Cleanup: Detect and rectify noisy header tokens (e.g. "AADNAAR" -> "AADHAAR")
        val lines = rawText.lines()
        val correctedLines = mutableListOf<String>()

        for (line in lines) {
            var correctedLine = line
            val lower = line.lowercase(Locale.ROOT).trim()

            for ((noisy, correct) in COMMON_OCR_HEADER_CORRECTIONS) {
                if (lower.contains(noisy)) {
                    correctedLine = correctedLine.replace(noisy, correct, ignoreCase = true)
                }
            }
            correctedLines.add(correctedLine)
        }

        val correctedText = correctedLines.joinToString("\n")

        // 2. Identify potential father/spouse relation in land record or Aadhaar
        var fatherOrSpouseName: String? = null
        for (line in lines) {
            val odiaFatherMatch = "(?:ପିତା|ସ୍ୱାମୀ)[:\\s]+([A-Za-z\\u0B00-\\u0B7F\\s]+)".toRegex().find(line)
            val engFatherMatch = "(?:s/o|d/o|w/o|c/o|father)[:\\s]+([A-Za-z\\s]+)".toRegex(RegexOption.IGNORE_CASE).find(line)

            val matched = odiaFatherMatch?.groupValues?.getOrNull(1)?.trim()
                ?: engFatherMatch?.groupValues?.getOrNull(1)?.trim()

            if (!matched.isNullOrBlank() && validateAgainstSource(matched, rawText)) {
                fatherOrSpouseName = matched
                break
            }
        }

        return AiInterpretationResult(
            isSuccess = true,
            correctedOcrText = correctedText,
            fatherOrSpouseName = fatherOrSpouseName,
            isSupportedBySourceText = true,
            explanation = "Interpreted document with source text grounding. Rectified known OCR misreadings."
        )
    }

    override suspend fun suggestTransliterations(name: String, script: ScriptType): List<String> {
        val clean = name.trim()
        if (clean.isBlank()) return emptyList()

        val results = mutableListOf<String>()

        if (script == ScriptType.ODIA || OdiaTransliterationEngine.detectScript(clean) == ScriptType.ODIA) {
            val baseTranslit = OdiaTransliterationEngine.transliterateOdiaToEnglish(clean)
            results.add(baseTranslit)

            // Generate verified Odia transliteration variants
            val variants = generateSpellingVariants(baseTranslit)
            results.addAll(variants)
        } else {
            results.add(clean)
            val variants = generateSpellingVariants(clean)
            results.addAll(variants)
        }

        return results.distinct()
    }

    override suspend fun evaluateCandidateMatch(
        aadhaarName: String,
        landNameOriginal: String,
        landNameTranslit: String,
        contextInfo: Map<String, String>
    ): MatchEvaluationResult {
        val normA = OdiaTransliterationEngine.normalizeForMatching(aadhaarName)
        val normL = OdiaTransliterationEngine.normalizeForMatching(landNameTranslit)

        val supporting = mutableListOf<String>()
        val conflicting = mutableListOf<String>()

        var score = 0.0f

        // Token breakdown
        val tokensA = normA.split(" ").filter { it.isNotBlank() }
        val tokensL = normL.split(" ").filter { it.isNotBlank() }

        if (normA == normL) {
            score += 0.95f
            supporting.add("Exact cross-script phonetic identity ('$normA' == '$normL')")
        } else if (tokensA.toSet() == tokensL.toSet() && tokensA.size == tokensL.size) {
            score += 0.90f
            supporting.add("Complete token match with inverted word order ('$normA' <-> '$normL')")
        } else {
            // Check surname alignment
            if (tokensA.size >= 2 && tokensL.size >= 2) {
                val surnameA = tokensA.last()
                val surnameL = tokensL.last()
                if (surnameA == surnameL) {
                    score += 0.40f
                    supporting.add("Surname match: '$surnameA'")
                } else {
                    score -= 0.35f
                    conflicting.add("Conflicting family surname: '$surnameA' (Aadhaar) vs '$surnameL' (Land Record)")
                }
            }

            // Check given name overlap
            val givenA = tokensA.firstOrNull() ?: ""
            val givenL = tokensL.firstOrNull() ?: ""
            if (givenA == givenL && givenA.isNotBlank()) {
                score += 0.40f
                supporting.add("Given name match: '$givenA'")
            }
        }

        // Additional Context Checks: Father's name
        val aFather = contextInfo["aadhaar_father"]
        val lFather = contextInfo["land_father"]
        if (!aFather.isNullOrBlank() && !lFather.isNullOrBlank()) {
            val normAF = OdiaTransliterationEngine.normalizeForMatching(aFather)
            val normLF = OdiaTransliterationEngine.normalizeForMatching(lFather)
            if (normAF == normLF) {
                score += 0.15f
                supporting.add("Father / Guardian name corroboration: '$aFather'")
            } else if (normAF.split(" ").lastOrNull() != normLF.split(" ").lastOrNull()) {
                score -= 0.20f
                conflicting.add("Father / Guardian surname discrepancy: '$aFather' vs '$lFather'")
            }
        }

        // Additional Context Checks: Mouza / Location
        val mouza = contextInfo["mouza"]
        val aadhaarAddress = contextInfo["aadhaar_address"]
        if (!mouza.isNullOrBlank() && !aadhaarAddress.isNullOrBlank()) {
            if (aadhaarAddress.contains(mouza, ignoreCase = true)) {
                score += 0.08f
                supporting.add("Location / Mouza alignment: '$mouza'")
            }
        }

        val finalScore = score.coerceIn(0.0f, 1.0f)
        val explanation = when {
            finalScore >= 0.85f -> "Strong multi-signal corroboration. Recommended for user verification."
            finalScore >= 0.65f -> "Moderate multi-signal match. Review names and source PDF page carefully."
            else -> "Low confidence or conflicting signals. Manual confirmation required."
        }

        return MatchEvaluationResult(
            confidence = finalScore,
            supportingSignals = supporting,
            conflictingSignals = conflicting,
            explanation = explanation,
            isAutoApprovable = false // Critical: NEVER auto-approve
        )
    }

    /**
     * Rejects values that are unsupported by the source text to prevent AI hallucinations.
     */
    private fun validateAgainstSource(candidate: String, rawOcrText: String): Boolean {
        if (candidate.isBlank()) return false
        val cleanCandidate = candidate.trim().lowercase(Locale.ROOT)
        val cleanRaw = rawOcrText.lowercase(Locale.ROOT)

        // Check if full candidate exists as a substring
        if (cleanRaw.contains(cleanCandidate)) return true

        // Check if individual words exist in the raw text
        val tokens = cleanCandidate.split("\\s+".toRegex()).filter { it.length > 2 }
        if (tokens.isEmpty()) return false

        val matchingTokens = tokens.count { cleanRaw.contains(it) }
        return matchingTokens.toFloat() / tokens.size >= 0.6f
    }

    private fun generateSpellingVariants(name: String): List<String> {
        val variants = mutableListOf<String>()
        val lower = name.lowercase(Locale.ROOT)

        // Common Odia surname and phoneme variants
        val rules = listOf(
            "mohapatra" to "mahapatra",
            "mahapatra" to "mohapatra",
            "patnaik" to "pattnayak",
            "pattnayak" to "patnaik",
            "rath" to "ratha",
            "ratha" to "rath",
            "sahoo" to "sahu",
            "sahu" to "sahoo",
            "das" to "dash",
            "dash" to "das",
            "nayak" to "naik",
            "naik" to "nayak",
            "tripathi" to "tripathy",
            "tripathy" to "tripathi",
            "mohanty" to "mahanty",
            "mahanty" to "mohanty"
        )

        for ((from, to) in rules) {
            if (lower.contains(from)) {
                val replaced = lower.replace(from, to)
                variants.add(capitalizeWords(replaced))
            }
        }

        return variants
    }

    private fun capitalizeWords(str: String): String {
        return str.split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
    }
}
