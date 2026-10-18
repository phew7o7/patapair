package com.example.model

enum class DocType {
    LAND_RECORD,
    AADHAAR,
    UNKNOWN,
    FAILED_OCR,
    UNSUPPORTED_SCRIPT
}

enum class DocStatus {
    PENDING,
    PROCESSING,
    DETECTED,
    REVIEW_NEEDED,
    NEEDS_REVIEW,
    PAIRED,
    REJECTED,
    FAILED
}

enum class ScriptType {
    ODIA,
    LATIN,
    MIXED,
    UNKNOWN
}

enum class MatchStatus {
    SUGGESTED,
    NEEDS_REVIEW,
    VERIFIED,
    REJECTED
}

data class DocumentRecord(
    val id: String,
    val uri: String,
    val originalFilename: String,
    val pdfPageNumber: Int = 1,
    val totalPdfPages: Int = 1,
    val docType: DocType,
    val docStatus: DocStatus,
    val ocrEngineUsed: String,
    val ocrLanguages: String,
    val rawExtractedText: String,
    val redactedText: String,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val imageOrientation: Int = 0,
    val userRotation: Int = 0,
    val classificationScore: Float = 0f,
    val classificationReason: String = "",
    val rulesFiredJson: String = "[]",
    val processingErrors: String? = null,
    val isManuallyCorrected: Boolean = false,
    val isDuplicate: Boolean = false,
    val isSynthetic: Boolean = false,
    val sha256: String? = null,
    val khataNumber: String? = null,
    val plotNumber: String? = null,
    val tahasil: String? = null,
    val mouza: String? = null,
    val district: String? = null,
    val aadhaarNumberMasked: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class PersonRecord(
    val id: String,
    val documentId: String,
    val originalName: String,
    val script: ScriptType,
    val transliteratedName: String,
    val normalizedName: String,
    val confidence: Float = 0.8f,
    val isManuallyCorrected: Boolean = false,
    val role: String = "Rayat / Pattadar",
    val fathersOrSpouseName: String? = null,
    val shareDetails: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class MatchPairRecord(
    val id: String,
    val aadhaarDocId: String,
    val landDocId: String,
    val aadhaarPersonId: String,
    val landPersonId: String,
    val aadhaarName: String,
    val landNameOriginal: String,
    val landNameTransliterated: String,
    val matchConfidence: Float,
    val matchExplanation: String,
    val status: MatchStatus = MatchStatus.SUGGESTED,
    val isManual: Boolean = false,
    val personCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class OcrResult(
    val fullText: String,
    val engineName: String,
    val languageUsed: String,
    val confidence: Float,
    val error: String? = null,
    val isSuccessful: Boolean = error == null,
    val isWaitingForModel: Boolean = false,
    val renderedWidth: Int = 0,
    val renderedHeight: Int = 0
)

data class OcrModelStatus(
    val isOdiaInstalled: Boolean = false,
    val odiaSizeMb: Float = 0f,
    val isEnglishInstalled: Boolean = false,
    val englishSizeMb: Float = 0f,
    val isOdiaVerified: Boolean = false,
    val odiaVerificationMessage: String = "",
    val odiaSampleOutput: String = ""
) {
    val isReadyForOdia: Boolean get() = isOdiaInstalled
}

data class OdiaModelVerificationResult(
    val isVerified: Boolean,
    val sampleOcrText: String = "",
    val message: String = ""
)

data class ImportModelResult(
    val isSuccess: Boolean,
    val messages: List<String>
)

data class ClassificationRuleResult(
    val ruleName: String,
    val scoreContribution: Float,
    val description: String
)

data class ExtractedPersonDraft(
    val name: String,
    val script: ScriptType,
    val fatherOrSpouseName: String? = null,
    val role: String = "Rayat / Pattadar",
    val shareDetails: String? = null
)

data class DocumentClassifierResult(
    val docType: DocType,
    val confidence: Float,
    val reason: String,
    val rulesFired: List<ClassificationRuleResult>,
    val extractedPersons: List<Pair<String, ScriptType>>,
    val khataNumber: String? = null,
    val plotNumber: String? = null,
    val tahasil: String? = null,
    val mouza: String? = null,
    val aadhaarMasked: String? = null,
    val extractedPersonDetails: List<ExtractedPersonDraft> = emptyList()
)
