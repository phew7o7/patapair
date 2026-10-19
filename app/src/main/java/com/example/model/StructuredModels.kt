package com.example.model

/**
 * Structured data models for Aadhaar documents, Odisha land-record documents (Patta/Khatiyan),
 * extracted people, source PDF pages, multi-signal candidate matches, and AI interpretation.
 */

data class SourcePage(
    val documentId: String,
    val uri: String,
    val pageNumber: Int,
    val totalPages: Int,
    val originalFilename: String,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val orientation: Int = 0
)

data class AadhaarRecord(
    val documentId: String,
    val sourcePage: SourcePage,
    val rawOcrText: String,
    val cardholderName: String?,
    val maskedAadhaarNumber: String?,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val careOfOrFather: String? = null,
    val address: String? = null,
    val confidence: Float = 0.85f,
    val isManuallyCorrected: Boolean = false,
    val correctedName: String? = null
)

data class LandRecordDocument(
    val documentId: String,
    val sourcePage: SourcePage,
    val rawOcrText: String,
    val khataNumber: String? = null,
    val plotNumber: String? = null,
    val tahasil: String? = null,
    val mouza: String? = null,
    val district: String? = null,
    val kisam: String? = null,
    val totalArea: String? = null,
    val confidence: Float = 0.85f,
    val isManuallyCorrected: Boolean = false
)

data class ExtractedPerson(
    val id: String,
    val documentId: String,
    val originalName: String,
    val script: ScriptType,
    val transliteratedName: String,
    val normalizedName: String,
    val confidence: Float = 0.85f,
    val role: String = "Rayat / Pattadar",
    val fathersOrSpouseName: String? = null,
    val shareDetails: String? = null,
    val sourcePage: SourcePage? = null,
    val isManuallyCorrected: Boolean = false,
    val correctedName: String? = null
)

data class MatchSignal(
    val signalName: String,
    val scoreContribution: Float,
    val isSupporting: Boolean,
    val description: String,
    val weight: Float = 1.0f
)

data class CandidateMatch(
    val id: String,
    val aadhaarDocId: String,
    val landDocId: String,
    val aadhaarPersonId: String,
    val landPersonId: String,
    val aadhaarName: String,
    val landNameOriginal: String,
    val landNameTransliterated: String,
    val matchConfidence: Float,
    val signals: List<MatchSignal>,
    val supportingEvidence: List<String>,
    val conflictingEvidence: List<String>,
    val explanation: String,
    val status: MatchStatus = MatchStatus.SUGGESTED,
    val isManual: Boolean = false,
    val personCode: String? = null,
    val isMultipleCandidateForSamePerson: Boolean = false,
    val aadhaarSource: SourcePage? = null,
    val landSource: SourcePage? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiAvailabilityStatus(
    val isAvailable: Boolean,
    val modelName: String,
    val supportsOdia: Boolean,
    val reason: String,
    val isCloudOptIn: Boolean = false,
    val privacyNotice: String = "100% On-Device Processing. No documents or sensitive identity numbers are sent to external clouds."
)

data class AiInterpretationResult(
    val isSuccess: Boolean,
    val correctedOcrText: String? = null,
    val detectedNames: List<String> = emptyList(),
    val fatherOrSpouseName: String? = null,
    val suggestedTransliteration: String? = null,
    val explanation: String? = null,
    val isSupportedBySourceText: Boolean = true,
    val error: String? = null
)

data class MatchEvaluationResult(
    val confidence: Float,
    val supportingSignals: List<String>,
    val conflictingSignals: List<String>,
    val explanation: String,
    val isAutoApprovable: Boolean = false // Never auto-approve
)

fun MatchPairRecord.getSupportingEvidence(): List<String> {
    val lines = matchExplanation.lines()
    val supportingLine = lines.find { it.startsWith("Supporting:") || it.startsWith("Supporting Signals:") }
    return if (supportingLine != null) {
        val content = supportingLine.substringAfter(":").trim()
        content.split(";").map { it.trim() }.filter { it.isNotBlank() }
    } else {
        listOf("Phonetic cross-script alignment (${(matchConfidence * 100).toInt()}%)")
    }
}

fun MatchPairRecord.getConflictingEvidence(): List<String> {
    val lines = matchExplanation.lines()
    val conflictingLine = lines.find {
        it.startsWith("Caution / Conflicting:") ||
        it.startsWith("Cautionary / Conflicting Signals:") ||
        it.startsWith("Cautionary:")
    }
    return if (conflictingLine != null) {
        val content = conflictingLine.substringAfter(":").trim()
        content.split(";").map { it.trim() }.filter { it.isNotBlank() }
    } else emptyList()
}

fun MatchPairRecord.getLocationStatusNote(): String? {
    val lines = matchExplanation.lines()
    return lines.find { it.contains("Location: Unknown", ignoreCase = true) || it.contains("Location: Unavailable", ignoreCase = true) }
}

data class MatchingDiagnostics(
    val eligibleAadhaarCount: Int = 0,
    val eligibleLandCount: Int = 0,
    val missingNameAadhaarCount: Int = 0,
    val missingNameLandCount: Int = 0,
    val totalCandidatesEvaluated: Int = 0,
    val candidatesRejectedByFilters: Int = 0,
    val candidatesSuggested: Int = 0,
    val candidatesNeedsReview: Int = 0,
    val candidatesVerified: Int = 0,
    val isEvaluatedCountEstimated: Boolean = false
)

