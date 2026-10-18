package com.example.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiDocumentInterpreter
import com.example.ai.DefaultAiDocumentInterpreter
import com.example.db.PataDatabaseHelper
import com.example.engine.DocumentClassifier
import com.example.engine.MatcherEngine
import com.example.engine.OdiaTransliterationEngine
import com.example.model.AiAvailabilityStatus
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.ImportModelResult
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.OcrModelStatus
import com.example.model.PersonRecord
import com.example.model.ScriptType
import com.example.ocr.DocumentOcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID

class PataPairViewModel(application: Application) : AndroidViewModel(application) {

    private val dbHelper = PataDatabaseHelper.getInstance(application)
    val aiInterpreter: AiDocumentInterpreter = DefaultAiDocumentInterpreter()

    private val _documents = MutableStateFlow<List<DocumentRecord>>(emptyList())
    val documents: StateFlow<List<DocumentRecord>> = _documents.asStateFlow()

    private val _persons = MutableStateFlow<List<PersonRecord>>(emptyList())
    val persons: StateFlow<List<PersonRecord>> = _persons.asStateFlow()

    private val _matchPairs = MutableStateFlow<List<MatchPairRecord>>(emptyList())
    val matchPairs: StateFlow<List<MatchPairRecord>> = _matchPairs.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress.asStateFlow()

    private val _processingMessage = MutableStateFlow("")
    val processingMessage: StateFlow<String> = _processingMessage.asStateFlow()

    private val _ocrModelStatus = MutableStateFlow(OcrModelStatus())
    val ocrModelStatus: StateFlow<OcrModelStatus> = _ocrModelStatus.asStateFlow()

    private val _aiStatus = MutableStateFlow(
        aiInterpreter.checkAvailability(application)
    )
    val aiStatus: StateFlow<AiAvailabilityStatus> = _aiStatus.asStateFlow()

    private val _retrySummary = MutableStateFlow<String?>(null)
    val retrySummary: StateFlow<String?> = _retrySummary.asStateFlow()

    private val _matchingDiagnostics = MutableStateFlow(com.example.model.MatchingDiagnostics())
    val matchingDiagnostics: StateFlow<com.example.model.MatchingDiagnostics> = _matchingDiagnostics.asStateFlow()

    private var processingJob: Job? = null

    init {
        refreshModelStatus()
        refreshAiStatus()
        loadDataFromDatabase()
    }

    fun refreshAiStatus() {
        val context = getApplication<Application>()
        _aiStatus.value = aiInterpreter.checkAvailability(context)
    }

    fun refreshModelStatus() {
        val context = getApplication<Application>()
        _ocrModelStatus.value = DocumentOcrEngine.getModelStatus(context)
    }

    fun loadDataFromDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = dbHelper.getAllDocuments()
            val ppl = dbHelper.getAllPersons()
            val pairs = dbHelper.getAllMatchPairs()

            _documents.value = docs
            _persons.value = ppl
            _matchPairs.value = pairs

            computeDiagnostics(docs, ppl, pairs)

            // Auto-trigger matching engine if candidate documents exist but pairs have not yet been generated
            if (pairs.isEmpty() && docs.isNotEmpty()) {
                val hasAadhaar = docs.any { it.docType == DocType.AADHAAR }
                val hasLand = docs.any { it.docType == DocType.LAND_RECORD }
                if (hasAadhaar && hasLand) {
                    generateMatchSuggestions()
                }
            }
        }
    }

    private fun computeDiagnostics(
        docs: List<DocumentRecord>,
        ppl: List<PersonRecord>,
        pairs: List<MatchPairRecord>
    ) {
        val personDocIds = ppl.map { it.documentId }.toSet()
        val aadhaarDocs = docs.filter { !it.isDuplicate && it.docType == DocType.AADHAAR }
        val landDocs = docs.filter { !it.isDuplicate && it.docType == DocType.LAND_RECORD }

        val eligibleAadhaar = aadhaarDocs.count { personDocIds.contains(it.id) }
        val missingAadhaar = aadhaarDocs.size - eligibleAadhaar
        val eligibleLand = landDocs.count { personDocIds.contains(it.id) }
        val missingLand = landDocs.size - eligibleLand

        val suggested = pairs.count { it.status == MatchStatus.SUGGESTED }
        val needsReview = pairs.count { it.status == MatchStatus.NEEDS_REVIEW }
        val verified = pairs.count { it.status == MatchStatus.VERIFIED }

        val evaluated = eligibleAadhaar * eligibleLand
        val rejected = (evaluated - (suggested + needsReview + verified)).coerceAtLeast(0)

        _matchingDiagnostics.value = com.example.model.MatchingDiagnostics(
            eligibleAadhaarCount = eligibleAadhaar,
            eligibleLandCount = eligibleLand,
            missingNameAadhaarCount = missingAadhaar,
            missingNameLandCount = missingLand,
            totalCandidatesEvaluated = evaluated,
            candidatesRejectedByFilters = rejected,
            candidatesSuggested = suggested,
            candidatesNeedsReview = needsReview,
            candidatesVerified = verified
        )
    }

    // --- Import Pipelines ---

    fun importUris(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val newDocs = mutableListOf<DocumentRecord>()

            for (uri in uris) {
                try {
                    // 1. Take persistable permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // In some virtual environments this throws SecurityException; gracefully continue
                    }

                    val filename = resolveFilename(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: ""

                    if (mimeType.contains("pdf", ignoreCase = true) || filename.endsWith(".pdf", ignoreCase = true)) {
                        // Multi-page PDF handling via PdfRenderer
                        newDocs.addAll(importPdfPages(uri, filename))
                    } else {
                        // Standard Image
                        val sha256 = calculateSha256(uri)
                        val isDuplicate = sha256 != null && dbHelper.findDocumentBySha256(sha256) != null

                        val doc = DocumentRecord(
                            id = UUID.randomUUID().toString(),
                            uri = uri.toString(),
                            originalFilename = filename,
                            pdfPageNumber = 1,
                            totalPdfPages = 1,
                            docType = DocType.UNKNOWN,
                            docStatus = if (isDuplicate) DocStatus.REVIEW_NEEDED else DocStatus.PENDING,
                            ocrEngineUsed = "",
                            ocrLanguages = "",
                            rawExtractedText = "",
                            redactedText = "",
                            isDuplicate = isDuplicate,
                            sha256 = sha256
                        )
                        newDocs.add(doc)
                    }
                } catch (e: Exception) {
                    // Keep error traceable
                }
            }

            if (newDocs.isNotEmpty()) {
                dbHelper.upsertDocuments(newDocs)
                loadDataFromDatabase()
                startProcessingQueue()
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Continue
            }

            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@launch
            val collectedUris = mutableListOf<Uri>()

            fun scanDir(dir: DocumentFile) {
                for (file in dir.listFiles()) {
                    if (file.isDirectory) {
                        scanDir(file)
                    } else if (file.isFile) {
                        val name = file.name ?: ""
                        val mime = file.type ?: ""
                        if (mime.startsWith("image/") || mime == "application/pdf" ||
                            name.endsWith(".pdf", ignoreCase = true) ||
                            name.endsWith(".jpg", ignoreCase = true) ||
                            name.endsWith(".jpeg", ignoreCase = true) ||
                            name.endsWith(".png", ignoreCase = true)) {
                            collectedUris.add(file.uri)
                        }
                    }
                }
            }

            scanDir(rootDoc)
            if (collectedUris.isNotEmpty()) {
                importUris(collectedUris)
            }
        }
    }

    private suspend fun importPdfPages(uri: Uri, filename: String): List<DocumentRecord> = withContext(Dispatchers.IO) {
        val context = getApplication<Application>()
        val pageDocs = mutableListOf<DocumentRecord>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                val sha256 = calculateSha256(uri)
                val isDuplicate = sha256 != null && dbHelper.findDocumentBySha256(sha256) != null

                for (pageIndex in 0 until pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = page.width
                    val height = page.height
                    page.close()

                    val doc = DocumentRecord(
                        id = UUID.randomUUID().toString(),
                        uri = uri.toString(),
                        originalFilename = filename,
                        pdfPageNumber = pageIndex + 1,
                        totalPdfPages = pageCount,
                        docType = DocType.UNKNOWN,
                        docStatus = if (isDuplicate) DocStatus.REVIEW_NEEDED else DocStatus.PENDING,
                        ocrEngineUsed = "",
                        ocrLanguages = "",
                        rawExtractedText = "",
                        redactedText = "",
                        imageWidth = width,
                        imageHeight = height,
                        isDuplicate = isDuplicate,
                        sha256 = if (pageIndex == 0) sha256 else null
                    )
                    pageDocs.add(doc)
                }
            }
        } catch (e: Exception) {
            pageDocs.add(
                DocumentRecord(
                    id = UUID.randomUUID().toString(),
                    uri = uri.toString(),
                    originalFilename = filename,
                    pdfPageNumber = 1,
                    totalPdfPages = 1,
                    docType = DocType.FAILED_OCR,
                    docStatus = DocStatus.FAILED,
                    ocrEngineUsed = "PdfRenderer",
                    ocrLanguages = "none",
                    rawExtractedText = "",
                    redactedText = "",
                    processingErrors = "Failed to render PDF: ${e.localizedMessage}"
                )
            )
        } finally {
            renderer?.close()
            pfd?.close()
        }
        pageDocs
    }

    // --- OCR Model Import Flow ---

    fun importOcrModels(uris: List<Uri>, onComplete: (ImportModelResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val result = DocumentOcrEngine.importModelFiles(context, uris)
            refreshModelStatus()

            // If model is now installed, automatically trigger queue to process waiting documents
            if (DocumentOcrEngine.isOdiaModelInstalled(context)) {
                startProcessingQueue()
            }

            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    // --- Processing Queue ---

    fun startProcessingQueue() {
        if (_isProcessing.value) return

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val pendingDocs = dbHelper.getAllDocuments().filter {
                    !it.isDuplicate && (it.docStatus == DocStatus.PENDING || it.docStatus == DocStatus.PROCESSING || it.classificationReason == "Waiting for OCR model")
                }

                val total = pendingDocs.size
                if (total == 0) return@launch

                var successes = 0
                var failures = 0

                for ((index, doc) in pendingDocs.withIndex()) {
                    _processingProgress.value = (index.toFloat() / total)
                    _processingMessage.value = "Processing (${index + 1}/$total): ${doc.originalFilename} (Pg ${doc.pdfPageNumber})"

                    val resultDoc = processSingleDocument(doc)
                    if (resultDoc.docType != DocType.FAILED_OCR && resultDoc.docStatus != DocStatus.FAILED) {
                        successes++
                    } else {
                        failures++
                    }
                }

                _processingProgress.value = 1.0f
                val summary = "Processing complete: $successes succeeded, $failures failed out of $total documents."
                _processingMessage.value = summary
                _retrySummary.value = summary

                // Generate automatic suggestions after processing batch
                generateMatchSuggestions()
            } catch (e: CancellationException) {
                _processingMessage.value = "Processing paused/cancelled."
                throw e
            } catch (e: Exception) {
                _processingMessage.value = "Error during processing: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
                loadDataFromDatabase()
            }
        }
    }

    fun pauseProcessing() {
        processingJob?.cancel()
        processingJob = null
        _isProcessing.value = false
        _processingMessage.value = "Paused"
    }

    /**
     * Reprocesses ONLY failed documents, preserving all successful classifications and verified pairs,
     * avoiding duplicate person records, and reporting exact success/failure numbers upon completion.
     */
    fun retryFailedDocuments(onComplete: ((successCount: Int, failureCount: Int) -> Unit)? = null) {
        if (_isProcessing.value) return

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val failedDocs = dbHelper.getAllDocuments().filter {
                    !it.isDuplicate && (it.docType == DocType.FAILED_OCR || it.docStatus == DocStatus.FAILED)
                }

                val total = failedDocs.size
                if (total == 0) {
                    _processingMessage.value = "No failed documents to retry."
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(0, 0)
                    }
                    return@launch
                }

                // Reset only these failed documents to PENDING
                val updatedDocs = failedDocs.map {
                    it.copy(docStatus = DocStatus.PENDING, processingErrors = null)
                }
                dbHelper.upsertDocuments(updatedDocs)
                loadDataFromDatabase()

                var successes = 0
                var failures = 0

                for ((index, doc) in updatedDocs.withIndex()) {
                    _processingProgress.value = (index.toFloat() / total)
                    _processingMessage.value = "Retrying (${index + 1}/$total): ${doc.originalFilename} (Pg ${doc.pdfPageNumber})"

                    val resultDoc = processSingleDocument(doc)
                    if (resultDoc.docType != DocType.FAILED_OCR && resultDoc.docStatus != DocStatus.FAILED) {
                        successes++
                    } else {
                        failures++
                    }
                }

                _processingProgress.value = 1.0f
                val summary = "Retry complete: $successes succeeded, $failures failed out of $total documents."
                _processingMessage.value = summary
                _retrySummary.value = summary

                generateMatchSuggestions()

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(successes, failures)
                }
            } catch (e: CancellationException) {
                _processingMessage.value = "Retry paused/cancelled."
                throw e
            } catch (e: Exception) {
                _processingMessage.value = "Error during retry: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
                loadDataFromDatabase()
            }
        }
    }

    fun clearRetrySummary() {
        _retrySummary.value = null
    }

    private suspend fun processSingleDocument(doc: DocumentRecord): DocumentRecord {
        val context = getApplication<Application>()

        // Run OCR Engine (renders PDF pages via PdfPageRenderer; never passes PDF URI to BitmapFactory)
        val ocrResult = DocumentOcrEngine.processDocument(
            context = context,
            doc = doc
        )

        val rawText = ocrResult.fullText
        val redactedText = DocumentOcrEngine.redactSensitiveIdentifiers(rawText)

        // Run AI Interpretation on OCR text to rectify known OCR tokens (e.g. AADNAAR -> Aadhaar header)
        val aiInterpretation = aiInterpreter.interpretDocument(
            rawText = rawText,
            docType = doc.docType,
            script = OdiaTransliterationEngine.detectScript(rawText)
        )
        val cleanedText = aiInterpretation.correctedOcrText ?: rawText

        // Run Classifier on cleaned text
        val classifierResult = DocumentClassifier.classifyDocument(
            text = cleanedText,
            ocrError = ocrResult.error,
            hasTableGridVisuals = false,
            isWaitingForModel = ocrResult.isWaitingForModel
        )

        val rulesJson = JSONArray().apply {
            for (rule in classifierResult.rulesFired) {
                put(JSONObject().apply {
                    put("rule", rule.ruleName)
                    put("score", rule.scoreContribution)
                    put("description", rule.description)
                })
            }
        }.toString()

        val updatedDoc = doc.copy(
            docType = classifierResult.docType,
            docStatus = when {
                ocrResult.isWaitingForModel -> DocStatus.PENDING
                classifierResult.docType == DocType.FAILED_OCR -> DocStatus.FAILED
                else -> DocStatus.DETECTED
            },
            ocrEngineUsed = ocrResult.engineName,
            ocrLanguages = ocrResult.languageUsed,
            rawExtractedText = rawText,
            redactedText = redactedText,
            imageWidth = if (ocrResult.renderedWidth > 0) ocrResult.renderedWidth else doc.imageWidth,
            imageHeight = if (ocrResult.renderedHeight > 0) ocrResult.renderedHeight else doc.imageHeight,
            classificationScore = classifierResult.confidence,
            classificationReason = if (ocrResult.isWaitingForModel) "Waiting for OCR model" else classifierResult.reason,
            rulesFiredJson = rulesJson,
            processingErrors = if (ocrResult.isWaitingForModel) null else ocrResult.error,
            khataNumber = classifierResult.khataNumber,
            plotNumber = classifierResult.plotNumber,
            tahasil = classifierResult.tahasil,
            mouza = classifierResult.mouza,
            aadhaarNumberMasked = classifierResult.aadhaarMasked
        )
        dbHelper.upsertDocument(updatedDoc)

        // Prevent duplicate person entries on reprocess/retry:
        val existingPersons = dbHelper.getPersonsByDocumentId(updatedDoc.id)
        val hasManualCorrections = existingPersons.any { it.isManuallyCorrected }
        if (!hasManualCorrections) {
            dbHelper.deletePersonsByDocumentId(updatedDoc.id)
        }

        // Store extracted person records only if not waiting for model
        if (!ocrResult.isWaitingForModel && classifierResult.extractedPersons.isNotEmpty()) {
            val detailsMap = classifierResult.extractedPersonDetails.associateBy { it.name }

            val personsList = classifierResult.extractedPersons.mapIndexed { index, (name, script) ->
                val draft = detailsMap[name]
                val transliterated = if (script == ScriptType.ODIA) {
                    OdiaTransliterationEngine.transliterateOdiaToEnglish(name)
                } else name

                val normalized = OdiaTransliterationEngine.normalizeForMatching(name)

                // Deterministic slot ID to prevent orphaned duplicate records
                val personId = "${updatedDoc.id}_p_$index"

                PersonRecord(
                    id = personId,
                    documentId = updatedDoc.id,
                    originalName = name,
                    script = script,
                    transliteratedName = transliterated,
                    normalizedName = normalized,
                    confidence = 0.85f,
                    role = draft?.role ?: (if (updatedDoc.docType == DocType.LAND_RECORD) "Rayat / Pattadar" else "Aadhaar Cardholder"),
                    fathersOrSpouseName = draft?.fatherOrSpouseName ?: aiInterpretation.fatherOrSpouseName
                )
            }
            dbHelper.upsertPersons(personsList)
        }

        return updatedDoc
    }

    // --- Synthetic Collection (Debug / Verification) ---

    fun seedSampleCollection(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val syntheticDocs = mutableListOf<DocumentRecord>()
            val syntheticPersons = mutableListOf<PersonRecord>()

            // 1. Synthetic Aadhaar Documents (English + Hindi/Odia headers)
            val sampleAadhaars = listOf(
                Triple("Aadhaar_Omkar_Rath.jpg", "Omkar Rath", "XXXX-XXXX-8921"),
                Triple("Aadhaar_Bijay_Mohapatra.jpg", "Bijay Kumar Mohapatra", "XXXX-XXXX-4532"),
                Triple("Aadhaar_Satyabrata_Patnaik.jpg", "Satyabrata Patnaik", "XXXX-XXXX-7711"),
                Triple("Aadhaar_Manas_Behera.jpg", "Manas Ranjan Behera", "XXXX-XXXX-2190"),
                Triple("Aadhaar_Subhashree_Pradhan.jpg", "Subhashree Pradhan", "XXXX-XXXX-6604")
            )

            for ((filename, name, maskedId) in sampleAadhaars) {
                val docId = UUID.randomUUID().toString()
                val text = """
                    Government of India
                    Unique Identification Authority of India (UIDAI)
                    Enrollment No: 1024/55231/98124
                    $name
                    DOB: 14/08/1988
                    Gender: Male
                    $maskedId
                    help@uidai.gov.in  www.uidai.gov.in
                """.trimIndent()

                val classifierResult = DocumentClassifier.classifyDocument(text)

                syntheticDocs.add(
                    DocumentRecord(
                        id = docId,
                        uri = "synthetic://aadhaar/$filename",
                        originalFilename = filename,
                        pdfPageNumber = 1,
                        totalPdfPages = 1,
                        docType = DocType.AADHAAR,
                        docStatus = DocStatus.DETECTED,
                        ocrEngineUsed = DocumentOcrEngine.ENGINE_SYNTHETIC,
                        ocrLanguages = "eng+hin",
                        rawExtractedText = text,
                        redactedText = text,
                        imageWidth = 1000,
                        imageHeight = 650,
                        classificationScore = classifierResult.confidence,
                        classificationReason = classifierResult.reason,
                        rulesFiredJson = "[]",
                        isSynthetic = true,
                        aadhaarNumberMasked = maskedId
                    )
                )

                syntheticPersons.add(
                    PersonRecord(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        originalName = name,
                        script = ScriptType.LATIN,
                        transliteratedName = name,
                        normalizedName = OdiaTransliterationEngine.normalizeForMatching(name),
                        confidence = 0.95f,
                        role = "Aadhaar Cardholder"
                    )
                )
            }

            // 2. Synthetic Odisha Land Record PDF (with Odia script and table structure)
            val landPdfName = "Khatiyan_Khordha_Mouza_2024.pdf"
            val sampleLandRecords = listOf(
                Triple("ଓମକାର ରଥ", "142", "204/1105"),           // Omkar Rath
                Triple("ବିଜୟ କୁମାର ମହାପାତ୍ର", "88", "112/504"),   // Bijay Kumar Mohapatra
                Triple("ସତ୍ୟବ୍ରତ ପଟ୍ଟନାୟକ", "205", "309/881"),    // Satyabrata Patnaik
                Triple("ମାନସ ରଞ୍ଜନ ବେହେରା", "56", "78/12"),       // Manas Ranjan Behera
                Triple("ସୁଭଶ୍ରୀ ପ୍ରଧାନ", "310", "415/990")        // Subhashree Pradhan
            )

            for ((index, item) in sampleLandRecords.withIndex()) {
                val (odiaName, khata, plot) = item
                val docId = UUID.randomUUID().toString()
                val text = """
                    ଓଡ଼ିଶା ସରକାର
                    ଭୂ-ଅଭିଲେଖ (ଖତିୟାନ / Record of Rights)
                    ଜିଲ୍ଲା: ଖୋର୍ଦ୍ଧା | ତହସିଲ: ଜଟଣୀ | ମୌଜା: କୁଦିଆରୀ
                    ଖାତା ନଂ: $khata
                    ରୟତ ସ୍ୱତ୍ୱ: ପ୍ରଜା ସ୍ୱତ୍ୱ
                    ରୟତ: $odiaName
                    ପିତା: ନିରଞ୍ଜନ
                    --------------------------------------------------
                    ପ୍ଲଟ ନଂ    | କିସମ    | ରକବା (ଏକର-ଡେସିମିଲ)
                    $plot | ଶାରଦ-୨  | ୦.୪୫
                    --------------------------------------------------
                """.trimIndent()

                val classifierResult = DocumentClassifier.classifyDocument(text, hasTableGridVisuals = true)
                val transliterated = OdiaTransliterationEngine.transliterateOdiaToEnglish(odiaName)

                syntheticDocs.add(
                    DocumentRecord(
                        id = docId,
                        uri = "synthetic://land_records/$landPdfName#page=${index + 1}",
                        originalFilename = landPdfName,
                        pdfPageNumber = index + 1,
                        totalPdfPages = sampleLandRecords.size,
                        docType = DocType.LAND_RECORD,
                        docStatus = DocStatus.DETECTED,
                        ocrEngineUsed = DocumentOcrEngine.ENGINE_SYNTHETIC,
                        ocrLanguages = "ori",
                        rawExtractedText = text,
                        redactedText = text,
                        imageWidth = 1200,
                        imageHeight = 1700,
                        classificationScore = classifierResult.confidence,
                        classificationReason = classifierResult.reason,
                        rulesFiredJson = "[]",
                        isSynthetic = true,
                        khataNumber = khata,
                        plotNumber = plot,
                        tahasil = "ଜଟଣୀ (Jatni)",
                        mouza = "କୁଦିଆରୀ (Kudiary)",
                        district = "ଖୋର୍ଦ୍ଧା (Khordha)"
                    )
                )

                syntheticPersons.add(
                    PersonRecord(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        originalName = odiaName,
                        script = ScriptType.ODIA,
                        transliteratedName = transliterated,
                        normalizedName = OdiaTransliterationEngine.normalizeForMatching(odiaName),
                        confidence = 0.95f,
                        role = "Rayat / Pattadar"
                    )
                )
            }

            dbHelper.upsertDocuments(syntheticDocs)
            dbHelper.upsertPersons(syntheticPersons)

            loadDataFromDatabase()
            generateMatchSuggestions()

            withContext(Dispatchers.Main) {
                onComplete("Seeded 5 synthetic Aadhaar cards and 5 Odisha Khatiyan land records with verifiable Odia script.")
            }
        }
    }

    // --- Matching Engine Generation ---

    fun generateMatchSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            val allDocs = dbHelper.getAllDocuments().filter { !it.isDuplicate }
            val docMap = allDocs.associateBy { it.id }

            val aadhaarPersons = dbHelper.getAllPersons().filter { p ->
                val d = docMap[p.documentId]
                d != null && d.docType == DocType.AADHAAR
            }

            val landPersons = dbHelper.getAllPersons().filter { p ->
                val d = docMap[p.documentId]
                d != null && d.docType == DocType.LAND_RECORD
            }

            // Only generate match suggestions when candidates exist on BOTH sides
            if (aadhaarPersons.isEmpty() || landPersons.isEmpty()) {
                val pairs = dbHelper.getAllMatchPairs()
                computeDiagnostics(allDocs, dbHelper.getAllPersons(), pairs)
                return@launch
            }

            val existingPairs = dbHelper.getAllMatchPairs()
            val existingPairKeys = existingPairs.map { "${it.aadhaarPersonId}:${it.landPersonId}" }.toSet()

            // Pre-scan to detect multi-candidate conflicts
            val candidateCountPerAadhaar = mutableMapOf<String, Int>()
            val candidateCountPerLand = mutableMapOf<String, Int>()

            for (aPerson in aadhaarPersons) {
                for (lPerson in landPersons) {
                    val key = "${aPerson.id}:${lPerson.id}"
                    if (existingPairKeys.contains(key)) continue

                    val testEval = MatcherEngine.evaluateCandidate(
                        aadhaarPerson = aPerson,
                        landPerson = lPerson,
                        aadhaarDoc = docMap[aPerson.documentId],
                        landDoc = docMap[lPerson.documentId],
                        hasMultipleCandidates = false
                    )

                    if (testEval.score >= 0.60f) {
                        candidateCountPerAadhaar[aPerson.id] = (candidateCountPerAadhaar[aPerson.id] ?: 0) + 1
                        candidateCountPerLand[lPerson.id] = (candidateCountPerLand[lPerson.id] ?: 0) + 1
                    }
                }
            }

            val newPairs = mutableListOf<MatchPairRecord>()

            for (aPerson in aadhaarPersons) {
                for (lPerson in landPersons) {
                    val key = "${aPerson.id}:${lPerson.id}"
                    if (existingPairKeys.contains(key)) continue

                    val hasMultiple = (candidateCountPerAadhaar[aPerson.id] ?: 0) > 1 ||
                                      (candidateCountPerLand[lPerson.id] ?: 0) > 1

                    val matchResult = MatcherEngine.evaluateCandidate(
                        aadhaarPerson = aPerson,
                        landPerson = lPerson,
                        aadhaarDoc = docMap[aPerson.documentId],
                        landDoc = docMap[lPerson.documentId],
                        hasMultipleCandidates = hasMultiple
                    )

                    if (matchResult.score >= 0.60f) {
                        val status = if (matchResult.score >= 0.85f && matchResult.conflictingEvidence.isEmpty() && !hasMultiple) {
                            MatchStatus.SUGGESTED
                        } else {
                            MatchStatus.NEEDS_REVIEW
                        }

                        newPairs.add(
                            MatchPairRecord(
                                id = UUID.randomUUID().toString(),
                                aadhaarDocId = aPerson.documentId,
                                landDocId = lPerson.documentId,
                                aadhaarPersonId = aPerson.id,
                                landPersonId = lPerson.id,
                                aadhaarName = aPerson.originalName,
                                landNameOriginal = lPerson.originalName,
                                landNameTransliterated = lPerson.transliteratedName,
                                matchConfidence = matchResult.score,
                                matchExplanation = matchResult.explanation,
                                status = status,
                                isManual = false
                            )
                        )
                    }
                }
            }

            if (newPairs.isNotEmpty()) {
                dbHelper.upsertMatchPairs(newPairs)
            }
            loadDataFromDatabase()
        }
    }

    /**
     * Re-parses existing documents in the database that have rawExtractedText but 0 extracted people,
     * or are missing Khata/Plot numbers, using updated Odia numeral converters and Khatiyan parser.
     */
    fun reprocessUnextractedDocuments(onComplete: ((reprocessedCount: Int, newPersonCount: Int) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val allDocs = dbHelper.getAllDocuments().filter { !it.isDuplicate }
            var reprocessedCount = 0
            var newPersonCount = 0

            for (doc in allDocs) {
                if (doc.rawExtractedText.isBlank()) continue
                val existingPersons = dbHelper.getPersonsByDocumentId(doc.id)
                if (existingPersons.any { it.isManuallyCorrected }) continue

                val isMissingPersons = existingPersons.isEmpty()
                val isMissingLandId = doc.docType == DocType.LAND_RECORD && (doc.khataNumber == null || doc.plotNumber == null)

                if (isMissingPersons || isMissingLandId) {
                    val classifierResult = DocumentClassifier.classifyDocument(
                        text = doc.rawExtractedText,
                        ocrError = doc.processingErrors,
                        hasTableGridVisuals = false
                    )

                    val updatedDoc = doc.copy(
                        docType = if (doc.isManuallyCorrected) doc.docType else classifierResult.docType,
                        classificationScore = classifierResult.confidence,
                        khataNumber = classifierResult.khataNumber ?: doc.khataNumber,
                        plotNumber = classifierResult.plotNumber ?: doc.plotNumber,
                        tahasil = classifierResult.tahasil ?: doc.tahasil,
                        mouza = classifierResult.mouza ?: doc.mouza,
                        aadhaarNumberMasked = classifierResult.aadhaarMasked ?: doc.aadhaarNumberMasked
                    )
                    dbHelper.upsertDocument(updatedDoc)

                    if (classifierResult.extractedPersons.isNotEmpty()) {
                        dbHelper.deletePersonsByDocumentId(doc.id)
                        val detailsMap = classifierResult.extractedPersonDetails.associateBy { it.name }
                        val personsList = classifierResult.extractedPersons.mapIndexed { index, (name, script) ->
                            val draft = detailsMap[name]
                            val transliterated = if (script == ScriptType.ODIA) {
                                OdiaTransliterationEngine.transliterateOdiaToEnglish(name)
                            } else name
                            val normalized = OdiaTransliterationEngine.normalizeForMatching(name)

                            PersonRecord(
                                id = "${updatedDoc.id}_p_$index",
                                documentId = updatedDoc.id,
                                originalName = name,
                                script = script,
                                transliteratedName = transliterated,
                                normalizedName = normalized,
                                confidence = 0.85f,
                                role = draft?.role ?: (if (updatedDoc.docType == DocType.LAND_RECORD) "Rayat / Pattadar" else "Aadhaar Cardholder"),
                                fathersOrSpouseName = draft?.fatherOrSpouseName
                            )
                        }
                        dbHelper.upsertPersons(personsList)
                        newPersonCount += personsList.size
                    }
                    reprocessedCount++
                }
            }

            generateMatchSuggestions()
            loadDataFromDatabase()

            withContext(Dispatchers.Main) {
                onComplete?.invoke(reprocessedCount, newPersonCount)
            }
        }
    }

    // --- Manual Review & Corrections ---

    fun setManualClassification(docId: String, newType: DocType) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.updateDocumentManualClassification(docId, newType)
            generateMatchSuggestions()
            loadDataFromDatabase()
        }
    }

    fun rotateDocument(docId: String, additionalDegrees: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = dbHelper.getDocumentById(docId) ?: return@launch
            val newRotation = (doc.userRotation + additionalDegrees) % 360
            dbHelper.updateDocumentRotation(docId, newRotation)
            loadDataFromDatabase()
        }
    }

    fun manuallyCorrectPersonName(personId: String, correctedName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val script = OdiaTransliterationEngine.detectScript(correctedName)
            val transliterated = if (script == ScriptType.ODIA) {
                OdiaTransliterationEngine.transliterateOdiaToEnglish(correctedName)
            } else correctedName
            val normalized = OdiaTransliterationEngine.normalizeForMatching(correctedName)

            dbHelper.updatePersonManualName(personId, correctedName, transliterated, normalized)
            generateMatchSuggestions()
            loadDataFromDatabase()
        }
    }

    fun manualPair(aadhaarDocId: String, landDocId: String, aadhaarPersonId: String, landPersonId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val aadhaarPerson = dbHelper.getAllPersons().firstOrNull { it.id == aadhaarPersonId } ?: return@launch
            val landPerson = dbHelper.getAllPersons().firstOrNull { it.id == landPersonId } ?: return@launch

            val match = MatcherEngine.compareNames(aadhaarPerson.normalizedName, landPerson.normalizedName)
            val pairId = UUID.randomUUID().toString()

            val pair = MatchPairRecord(
                id = pairId,
                aadhaarDocId = aadhaarDocId,
                landDocId = landDocId,
                aadhaarPersonId = aadhaarPersonId,
                landPersonId = landPersonId,
                aadhaarName = aadhaarPerson.originalName,
                landNameOriginal = landPerson.originalName,
                landNameTransliterated = landPerson.transliteratedName,
                matchConfidence = match.score,
                matchExplanation = "Manually paired. ${match.explanation}",
                status = MatchStatus.VERIFIED,
                isManual = true
            )
            dbHelper.upsertMatchPairs(listOf(pair))
            dbHelper.updateMatchPairStatus(pairId, MatchStatus.VERIFIED)
            loadDataFromDatabase()
        }
    }

    fun updateMatchStatus(pairId: String, newStatus: MatchStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.updateMatchPairStatus(pairId, newStatus)
            loadDataFromDatabase()
        }
    }

    fun reprocessDocument(docId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = dbHelper.getDocumentById(docId) ?: return@launch
            processSingleDocument(doc)
            generateMatchSuggestions()
            loadDataFromDatabase()
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            dbHelper.clearAllData()
            loadDataFromDatabase()
        }
    }

    // --- Helpers ---

    private fun resolveFilename(uri: Uri): String {
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document_${System.currentTimeMillis()}"
    }

    private fun calculateSha256(uri: Uri): String? {
        val context = getApplication<Application>()
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
}
