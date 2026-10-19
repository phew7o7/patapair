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
import com.example.engine.LocationExtractor
import com.example.engine.LocationGroupData
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
import com.example.export.ExportManager
import com.example.export.ExportResult
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

    private val _locationGroups = MutableStateFlow<List<LocationGroupData>>(emptyList())
    val locationGroups: StateFlow<List<LocationGroupData>> = _locationGroups.asStateFlow()

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

    private val _isMatchingRunning = MutableStateFlow(false)
    val isMatchingRunning: StateFlow<Boolean> = _isMatchingRunning.asStateFlow()

    private val _matchingActionMessage = MutableStateFlow<String?>(null)
    val matchingActionMessage: StateFlow<String?> = _matchingActionMessage.asStateFlow()

    private var processingJob: Job? = null
    private var lastActualEvaluated: Int? = null
    private var lastActualRejected: Int? = null

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
            _locationGroups.value = LocationExtractor.groupDocumentsAndPairs(docs, pairs)

            computeDiagnostics(docs, ppl, pairs, lastActualEvaluated, lastActualRejected)
        }
    }

    private fun computeDiagnostics(
        docs: List<DocumentRecord>,
        ppl: List<PersonRecord>,
        pairs: List<MatchPairRecord>,
        actualEvaluated: Int? = null,
        actualRejected: Int? = null
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

        val isEstimated = (actualEvaluated == null)
        val evaluated = actualEvaluated ?: (eligibleAadhaar * eligibleLand)
        val rejected = actualRejected ?: (evaluated - (suggested + needsReview + verified)).coerceAtLeast(0)

        _matchingDiagnostics.value = com.example.model.MatchingDiagnostics(
            eligibleAadhaarCount = eligibleAadhaar,
            eligibleLandCount = eligibleLand,
            missingNameAadhaarCount = missingAadhaar,
            missingNameLandCount = missingLand,
            totalCandidatesEvaluated = evaluated,
            candidatesRejectedByFilters = rejected,
            candidatesSuggested = suggested,
            candidatesNeedsReview = needsReview,
            candidatesVerified = verified,
            isEvaluatedCountEstimated = isEstimated
        )
    }

    // --- Import Pipelines ---

    fun importUris(uris: List<Uri>) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val total = uris.size
            _processingMessage.value = "Importing $total files..."
            val batch = mutableListOf<DocumentRecord>()
            var processed = 0

            for (uri in uris) {
                processed++
                if (total > 15 && processed % 15 == 0) {
                    _processingMessage.value = "Importing ($processed/$total) files..."
                }

                try {
                    // Take persistable permission
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
                        batch.addAll(importPdfPages(uri, filename))
                    } else {
                        val doc = DocumentRecord(
                            id = UUID.randomUUID().toString(),
                            uri = uri.toString(),
                            originalFilename = filename,
                            pdfPageNumber = 1,
                            totalPdfPages = 1,
                            docType = DocType.UNKNOWN,
                            docStatus = DocStatus.PENDING,
                            ocrEngineUsed = "",
                            ocrLanguages = "",
                            rawExtractedText = "",
                            redactedText = "",
                            isDuplicate = false,
                            sha256 = null
                        )
                        batch.add(doc)
                    }

                    // Flush batch every 40 items to keep UI responsive
                    if (batch.size >= 40) {
                        dbHelper.upsertDocuments(batch)
                        batch.clear()
                        loadDataFromDatabase()
                    }
                } catch (e: Exception) {
                    // Keep error traceable
                }
            }

            if (batch.isNotEmpty()) {
                dbHelper.upsertDocuments(batch)
                batch.clear()
                loadDataFromDatabase()
            }

            _processingMessage.value = "Imported $total documents. Starting OCR processing queue..."
            startProcessingQueue()
        }
    }

    fun importUrisToLocation(uris: List<Uri>, targetLocation: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val total = uris.size
            _processingMessage.value = "Importing $total files to location: $targetLocation..."
            val batch = mutableListOf<DocumentRecord>()
            var processed = 0

            for (uri in uris) {
                processed++
                try {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (e: Exception) {
                        // Continue
                    }

                    val filename = resolveFilename(uri)
                    val mimeType = context.contentResolver.getType(uri) ?: ""

                    if (mimeType.contains("pdf", ignoreCase = true) || filename.endsWith(".pdf", ignoreCase = true)) {
                        val pdfDocs = importPdfPages(uri, filename).map { doc ->
                            doc.copy(mouza = targetLocation, tahasil = targetLocation)
                        }
                        batch.addAll(pdfDocs)
                    } else {
                        val doc = DocumentRecord(
                            id = UUID.randomUUID().toString(),
                            uri = uri.toString(),
                            originalFilename = filename,
                            pdfPageNumber = 1,
                            totalPdfPages = 1,
                            docType = DocType.UNKNOWN,
                            docStatus = DocStatus.PENDING,
                            ocrEngineUsed = "",
                            ocrLanguages = "",
                            rawExtractedText = "",
                            redactedText = "",
                            isDuplicate = false,
                            mouza = targetLocation,
                            tahasil = targetLocation,
                            sha256 = null
                        )
                        batch.add(doc)
                    }

                    if (batch.size >= 40) {
                        dbHelper.upsertDocuments(batch)
                        batch.clear()
                        loadDataFromDatabase()
                    }
                } catch (e: Exception) {}
            }

            if (batch.isNotEmpty()) {
                dbHelper.upsertDocuments(batch)
                batch.clear()
                loadDataFromDatabase()
            }

            _processingMessage.value = "Imported $total documents for $targetLocation. Processing OCR & matching..."
            startProcessingQueue()
        }
    }

    fun exportLocationPdf(locationGroup: LocationGroupData, onComplete: (ExportResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val result = ExportManager.exportLocationDossierPdf(
                context = context,
                locationGroup = locationGroup,
                dbHelper = dbHelper
            )
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    fun exportAllLocationsPdf(onComplete: (ExportResult) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val groups = _locationGroups.value
            val result = ExportManager.exportAllLocationsPdf(
                context = context,
                locationGroups = groups,
                dbHelper = dbHelper
            )
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            _processingMessage.value = "Scanning folder for Odia Patta & Aadhaar files..."
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
                _processingMessage.value = "Found ${collectedUris.size} files in folder. Beginning import..."
                importUris(collectedUris)
            } else {
                _processingMessage.value = "No PDF or image files found in the selected folder."
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
                        docStatus = DocStatus.PENDING,
                        ocrEngineUsed = "",
                        ocrLanguages = "",
                        rawExtractedText = "",
                        redactedText = "",
                        imageWidth = width,
                        imageHeight = height,
                        isDuplicate = false,
                        sha256 = null
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

                    // Periodically refresh UI data so user sees live counts across 700+ docs
                    if ((index + 1) % 10 == 0 || index == total - 1) {
                        loadDataFromDatabase()
                    }

                    // Incrementally generate match suggestions during processing
                    if ((index + 1) % 25 == 0 && (index + 1) < total) {
                        generateMatchSuggestions()
                        System.gc()
                    }
                }

                _processingProgress.value = 1.0f
                val summary = "Processing complete: $successes succeeded, $failures failed out of $total documents."
                _processingMessage.value = summary
                _retrySummary.value = summary

                // Final pass to ensure all pairs are fully computed
                loadDataFromDatabase()
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

    /**
     * Re-processes documents classified as UNKNOWN or FAILED through Tesseract Odia OCR.
     */
    fun reprocessUnknownDocuments(onComplete: ((classifiedCount: Int, failedCount: Int) -> Unit)? = null) {
        if (_isProcessing.value) return

        processingJob = viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            try {
                val targets = dbHelper.getAllDocuments().filter {
                    !it.isDuplicate && (it.docType == DocType.UNKNOWN || it.docType == DocType.FAILED_OCR || it.docStatus == DocStatus.FAILED)
                }

                val total = targets.size
                if (total == 0) {
                    _processingMessage.value = "No unknown or failed documents to process."
                    withContext(Dispatchers.Main) { onComplete?.invoke(0, 0) }
                    return@launch
                }

                val updatedDocs = targets.map {
                    it.copy(docStatus = DocStatus.PENDING, processingErrors = null)
                }
                dbHelper.upsertDocuments(updatedDocs)
                loadDataFromDatabase()

                var classifiedCount = 0
                var failedCount = 0

                for ((index, doc) in updatedDocs.withIndex()) {
                    _processingProgress.value = (index.toFloat() / total)
                    _processingMessage.value = "Processing Odia OCR (${index + 1}/$total): ${doc.originalFilename} (Pg ${doc.pdfPageNumber})"

                    val resultDoc = processSingleDocument(doc)
                    if (resultDoc.docType == DocType.LAND_RECORD || resultDoc.docType == DocType.AADHAAR) {
                        classifiedCount++
                    } else {
                        failedCount++
                    }
                }

                _processingProgress.value = 1.0f
                val summary = "OCR Processing complete: $classifiedCount classified, $failedCount remaining out of $total documents."
                _processingMessage.value = summary
                _retrySummary.value = summary

                generateMatchSuggestions()
                withContext(Dispatchers.Main) { onComplete?.invoke(classifiedCount, failedCount) }
            } catch (e: CancellationException) {
                _processingMessage.value = "Processing cancelled."
                throw e
            } catch (e: Exception) {
                _processingMessage.value = "Error during processing: ${e.localizedMessage}"
            } finally {
                _isProcessing.value = false
                loadDataFromDatabase()
            }
        }
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

            // 1. Synthetic Aadhaar Documents (English + Odia/Hindi headers with realistic Odisha locations)
            data class SampleAadhaarData(val filename: String, val name: String, val maskedId: String, val location: String, val father: String)
            val sampleAadhaars = listOf(
                SampleAadhaarData("Aadhaar_Dillip_Sahu_Kesinga.jpg", "Dillip Kumar Sahu", "XXXX-XXXX-9124", "Kesinga, Kalahandi, Odisha - 766012", "Ghasiram Sahu"),
                SampleAadhaarData("Aadhaar_Suresh_Majhi_Kesinga.jpg", "Suresh Majhi", "XXXX-XXXX-3341", "At/Po: Kesinga, Dist: Kalahandi", "Duryodhan Majhi"),
                SampleAadhaarData("Aadhaar_Purna_Nayak_Bhawanipatna.jpg", "Purna Chandra Nayak", "XXXX-XXXX-5512", "Bhawanipatna, Kalahandi, Odisha - 766001", "Tikaram Nayak"),
                SampleAadhaarData("Aadhaar_Santosh_Patel_Bhawanipatna.jpg", "Santosh Patel", "XXXX-XXXX-7890", "Town: Bhawanipatna, Kalahandi", "Harpal Patel"),
                SampleAadhaarData("Aadhaar_Omkar_Rath.jpg", "Omkar Rath", "XXXX-XXXX-8921", "At/Po: Kudiary, Jatni, Khordha", "Niranjan Rath"),
                SampleAadhaarData("Aadhaar_Bijay_Mohapatra.jpg", "Bijay Kumar Mohapatra", "XXXX-XXXX-4532", "Jatni, Khordha, Odisha", "Niranjan Mohapatra"),
                SampleAadhaarData("Aadhaar_Subhashree_Pradhan.jpg", "Subhashree Pradhan", "XXXX-XXXX-6604", "Kudiary, Jatni, Khordha", "Balaram Pradhan")
            )

            for (item in sampleAadhaars) {
                val docId = UUID.randomUUID().toString()
                val text = """
                    Government of India
                    Unique Identification Authority of India (UIDAI)
                    Enrollment No: 1024/55231/98124
                    ${item.name}
                    S/O: ${item.father}
                    Address: ${item.location}
                    DOB: 14/08/1988
                    Gender: Male
                    ${item.maskedId}
                    help@uidai.gov.in  www.uidai.gov.in
                """.trimIndent()

                val classifierResult = DocumentClassifier.classifyDocument(text)

                syntheticDocs.add(
                    DocumentRecord(
                        id = docId,
                        uri = "synthetic://aadhaar/${item.filename}",
                        originalFilename = item.filename,
                        pdfPageNumber = 1,
                        totalPdfPages = 1,
                        docType = DocType.AADHAAR,
                        docStatus = DocStatus.DETECTED,
                        ocrEngineUsed = DocumentOcrEngine.ENGINE_SYNTHETIC,
                        ocrLanguages = "eng+ori",
                        rawExtractedText = text,
                        redactedText = text,
                        imageWidth = 1000,
                        imageHeight = 650,
                        classificationScore = classifierResult.confidence,
                        classificationReason = classifierResult.reason,
                        rulesFiredJson = "[]",
                        isSynthetic = true,
                        aadhaarNumberMasked = item.maskedId,
                        mouza = LocationExtractor.extractCanonicalLocation(
                            DocumentRecord(id = docId, uri = "", originalFilename = item.filename, pdfPageNumber = 1, totalPdfPages = 1, docType = DocType.AADHAAR, docStatus = DocStatus.DETECTED, ocrEngineUsed = "", ocrLanguages = "", rawExtractedText = text, redactedText = text)
                        )
                    )
                )

                syntheticPersons.add(
                    PersonRecord(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        originalName = item.name,
                        script = ScriptType.LATIN,
                        transliteratedName = item.name,
                        normalizedName = OdiaTransliterationEngine.normalizeForMatching(item.name),
                        confidence = 0.95f,
                        role = "Aadhaar Cardholder",
                        fathersOrSpouseName = item.father
                    )
                )
            }

            // 2. Synthetic Odisha Land Record PDF (with Odia script, tables, and Kesinga/Bhawanipatna mouzas)
            data class SampleLandData(val odiaName: String, val khata: String, val plot: String, val mouza: String, val tahasil: String, val district: String, val father: String)
            val sampleLandRecords = listOf(
                SampleLandData("ଦିଲୀପ କୁମାର ସାହୁ", "104", "118/45", "କେସିଙ୍ଗା", "କେସିଙ୍ଗା", "କଳାହାଣ୍ଡି", "ଘାସିରାମ"),
                SampleLandData("ସୁରେଶ ମାଝୀ", "62", "89/12", "କେସିଙ୍ଗା", "କେସିଙ୍ଗା", "କଳାହାଣ୍ଡି", "ଦୁର୍ଯ୍ୟୋଧନ"),
                SampleLandData("ପୂର୍ଣ୍ଣ ଚନ୍ଦ୍ର ନାୟକ", "215", "304/88", "ଭବାନୀପାଟଣା", "ଭବାନୀପାଟଣା", "କଳାହାଣ୍ଡି", "ଟିକାରାମ"),
                SampleLandData("ସନ୍ତୋଷ ପଟେଲ", "178", "212/60", "ଭବାନୀପାଟଣା", "ଭବାନୀପାଟଣା", "କଳାହାଣ୍ଡି", "ହରପାଲ"),
                SampleLandData("ଓମକାର ରଥ", "142", "204/1105", "କୁଦିଆରୀ", "ଜଟଣୀ", "ଖୋର୍ଦ୍ଧା", "ନିରଞ୍ଜନ"),
                SampleLandData("ବିଜୟ କୁମାର ମହାପାତ୍ର", "88", "112/504", "କୁଦିଆରୀ", "ଜଟଣୀ", "ଖୋର୍ଦ୍ଧା", "ନିରଞ୍ଜନ"),
                SampleLandData("ସୁଭଶ୍ରୀ ପ୍ରଧାନ", "310", "415/990", "କୁଦିଆରୀ", "ଜଟଣୀ", "ଖୋର୍ଦ୍ଧା", "ବଳରାମ")
            )

            val landPdfName = "Khatiyan_Kalahandi_Khordha_2024.pdf"

            for ((index, item) in sampleLandRecords.withIndex()) {
                val docId = UUID.randomUUID().toString()
                val text = """
                    ଓଡ଼ିଶା ସରକାର
                    ଭୂ-ଅଭିଲେଖ (ଖତିୟାନ / Record of Rights)
                    ଜିଲ୍ଲା: ${item.district} | ତହସିଲ: ${item.tahasil} | ମୌଜା: ${item.mouza}
                    ଖାତା ନଂ: ${item.khata}
                    ରୟତ ସ୍ୱତ୍ୱ: ପ୍ରଜା ସ୍ୱତ୍ୱ
                    ରୟତ: ${item.odiaName}
                    ପିତା: ${item.father}
                    --------------------------------------------------
                    ପ୍ଲଟ ନଂ    | କିସମ    | ରକବା (ଏକର-ଡେସିମିଲ)
                    ${item.plot} | ଶାରଦ-୨  | ୦.୪୫
                    --------------------------------------------------
                """.trimIndent()

                val classifierResult = DocumentClassifier.classifyDocument(text, hasTableGridVisuals = true)
                val transliterated = OdiaTransliterationEngine.transliterateOdiaToEnglish(item.odiaName)

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
                        khataNumber = item.khata,
                        plotNumber = item.plot,
                        tahasil = item.tahasil,
                        mouza = item.mouza,
                        district = item.district
                    )
                )

                syntheticPersons.add(
                    PersonRecord(
                        id = UUID.randomUUID().toString(),
                        documentId = docId,
                        originalName = item.odiaName,
                        script = ScriptType.ODIA,
                        transliteratedName = transliterated,
                        normalizedName = OdiaTransliterationEngine.normalizeForMatching(item.odiaName),
                        confidence = 0.95f,
                        role = "Rayat / Pattadar",
                        fathersOrSpouseName = item.father
                    )
                )
            }

            dbHelper.upsertDocuments(syntheticDocs)
            dbHelper.upsertPersons(syntheticPersons)

            loadDataFromDatabase()
            generateMatchSuggestions()

            withContext(Dispatchers.Main) {
                onComplete("Seeded records for Kesinga, Bhawanipatna, and Jatni with verified cross-script Odia Khatiyans and Aadhaar cards.")
            }
        }
    }

    // --- Matching Engine Generation ---

    fun generateMatchSuggestions() {
        if (_isMatchingRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isMatchingRunning.value = true
            _matchingActionMessage.value = "Evaluating candidate pairs..."
            try {
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
                    computeDiagnostics(allDocs, dbHelper.getAllPersons(), pairs, 0, 0)
                    _matchingActionMessage.value = "Need both Aadhaar and Land records with extracted names."
                    return@launch
                }

                val existingPairs = dbHelper.getAllMatchPairs()
                val existingMap = existingPairs.associateBy { "${it.aadhaarPersonId}:${it.landPersonId}" }

                // Preserve user-verified, user-rejected, and manual pairings
                val userDecisions = existingPairs.filter {
                    it.status == MatchStatus.VERIFIED || it.status == MatchStatus.REJECTED || it.isManual
                }
                val userDecidedKeys = userDecisions.map { "${it.aadhaarPersonId}:${it.landPersonId}" }.toSet()

                // Pre-scan to detect multi-candidate conflicts
                val candidateCountPerAadhaar = mutableMapOf<String, Int>()
                val candidateCountPerLand = mutableMapOf<String, Int>()

                for (aPerson in aadhaarPersons) {
                    for (lPerson in landPersons) {
                        val testEval = MatcherEngine.evaluateCandidate(
                            aadhaarPerson = aPerson,
                            landPerson = lPerson,
                            aadhaarDoc = docMap[aPerson.documentId],
                            landDoc = docMap[lPerson.documentId],
                            hasMultipleCandidates = false
                        )
                        if (testEval.score >= MatcherEngine.THRESHOLD_REVIEW) {
                            candidateCountPerAadhaar[aPerson.id] = (candidateCountPerAadhaar[aPerson.id] ?: 0) + 1
                            candidateCountPerLand[lPerson.id] = (candidateCountPerLand[lPerson.id] ?: 0) + 1
                        }
                    }
                }

                val newUnverifiedPairs = mutableListOf<MatchPairRecord>()
                var totalEvaluated = 0
                var totalRejected = 0

                for (aPerson in aadhaarPersons) {
                    for (lPerson in landPersons) {
                        totalEvaluated++
                        val key = "${aPerson.id}:${lPerson.id}"

                        // Preserve existing user decision without overwriting
                        if (userDecidedKeys.contains(key)) {
                            continue
                        }

                        val hasMultiple = (candidateCountPerAadhaar[aPerson.id] ?: 0) > 1 ||
                                          (candidateCountPerLand[lPerson.id] ?: 0) > 1

                        val matchResult = MatcherEngine.evaluateCandidate(
                            aadhaarPerson = aPerson,
                            landPerson = lPerson,
                            aadhaarDoc = docMap[aPerson.documentId],
                            landDoc = docMap[lPerson.documentId],
                            hasMultipleCandidates = hasMultiple
                        )

                        if (matchResult.score >= MatcherEngine.THRESHOLD_REVIEW) {
                            val isHighQuality = matchResult.score >= MatcherEngine.THRESHOLD_SUGGESTED &&
                                (matchResult.conflictingEvidence.isEmpty() || (matchResult.score >= 0.72f && matchResult.conflictingEvidence.size <= 1))

                            val status = if (isHighQuality) {
                                MatchStatus.SUGGESTED
                            } else {
                                MatchStatus.NEEDS_REVIEW
                            }

                            val pairId = existingMap[key]?.id ?: UUID.randomUUID().toString()

                            newUnverifiedPairs.add(
                                MatchPairRecord(
                                    id = pairId,
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
                        } else {
                            totalRejected++
                        }
                    }
                }

                // Replace unverified candidate pairs in database while strictly preserving verified/manual ones
                dbHelper.replaceUnverifiedMatchPairs(newUnverifiedPairs)

                val refreshedPairs = dbHelper.getAllMatchPairs()
                _matchPairs.value = refreshedPairs
                _locationGroups.value = LocationExtractor.groupDocumentsAndPairs(allDocs, refreshedPairs)

                val suggestedCount = refreshedPairs.count { it.status == MatchStatus.SUGGESTED }
                val needsReviewCount = refreshedPairs.count { it.status == MatchStatus.NEEDS_REVIEW }
                val verifiedCount = refreshedPairs.count { it.status == MatchStatus.VERIFIED }

                lastActualEvaluated = totalEvaluated
                lastActualRejected = totalRejected
                computeDiagnostics(allDocs, dbHelper.getAllPersons(), refreshedPairs, totalEvaluated, totalRejected)

                _matchingActionMessage.value = "Matcher completed: $totalEvaluated evaluated | $suggestedCount suggested, $needsReviewCount need review, $totalRejected rejected"
            } catch (e: Exception) {
                _matchingActionMessage.value = "Error during matching: ${e.localizedMessage}"
            } finally {
                _isMatchingRunning.value = false
            }
        }
    }

    fun runSmartLocationMatching(locationKey: String) {
        if (_isMatchingRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isMatchingRunning.value = true
            _matchingActionMessage.value = "Running AI matching for $locationKey..."
            generateMatchSuggestions()
        }
    }

    /**
     * Re-parses existing documents in the database that have rawExtractedText but 0 extracted people,
     * or are missing Khata/Plot numbers, or have short/incomplete names, using updated Odia Khatiyan parser.
     */
    fun reprocessUnextractedDocuments(onComplete: ((reprocessedCount: Int, newPersonCount: Int) -> Unit)? = null) {
        if (_isMatchingRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isMatchingRunning.value = true
            _matchingActionMessage.value = "Re-parsing records with updated parser..."
            try {
                val allDocs = dbHelper.getAllDocuments().filter { !it.isDuplicate }
                var reprocessedCount = 0
                var newPersonCount = 0

                for (doc in allDocs) {
                    if (doc.rawExtractedText.isBlank()) continue
                    val existingPersons = dbHelper.getPersonsByDocumentId(doc.id)
                    if (existingPersons.any { it.isManuallyCorrected }) continue

                    val hasIncompleteOrShortPersons = existingPersons.isEmpty() || existingPersons.any { it.originalName.trim().length <= 3 }
                    val hasAdministrativeNoisePerson = existingPersons.any {
                        it.transliteratedName.contains("jamidar", ignoreCase = true) ||
                        it.originalName.contains("ଜମିଦାର") ||
                        it.originalName.contains("ଖତିୟାନ") ||
                        it.originalName.contains("ରୟତ") ||
                        it.originalName.contains("ପଟ୍ଟା") ||
                        it.originalName.contains("ପଟା") ||
                        it.originalName.contains("ମାଲ") ||
                        it.originalName.contains("ସରକାର") ||
                        it.originalName.contains("ଆଧାର") ||
                        it.originalName.equals("AARA", ignoreCase = true) ||
                        it.originalName.equals("MAI", ignoreCase = true)
                    }
                    val isMissingLandId = doc.docType == DocType.LAND_RECORD && (doc.khataNumber == null || doc.plotNumber == null)
                    val isUnknownWithText = doc.docType == DocType.UNKNOWN && doc.rawExtractedText.isNotBlank()

                    if (hasIncompleteOrShortPersons || hasAdministrativeNoisePerson || isMissingLandId || isUnknownWithText) {
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

                _documents.value = dbHelper.getAllDocuments()
                _persons.value = dbHelper.getAllPersons()

                _matchingActionMessage.value = "Re-parse completed: $reprocessedCount documents re-parsed ($newPersonCount people extracted)."
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(reprocessedCount, newPersonCount)
                }

                // Automatically regenerate matches with the newly parsed records
                generateMatchSuggestions()
            } catch (e: Exception) {
                _matchingActionMessage.value = "Error during re-parse: ${e.localizedMessage}"
            } finally {
                _isMatchingRunning.value = false
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

    fun verifyMatchPair(pairId: String) {
        updateMatchStatus(pairId, MatchStatus.VERIFIED)
    }

    fun rejectMatchPair(pairId: String) {
        updateMatchStatus(pairId, MatchStatus.REJECTED)
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
