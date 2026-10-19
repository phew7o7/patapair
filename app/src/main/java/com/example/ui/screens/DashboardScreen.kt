package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.PataDatabaseHelper
import com.example.engine.LocationExtractor
import com.example.export.ExportManager
import com.example.export.ExportResult
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchStatus
import com.example.ui.components.DiagnosticDialog
import com.example.util.DocumentThumbnailView
import com.example.viewmodel.PataPairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: PataPairViewModel,
    onNavigateToAiPairs: () -> Unit = {},
    onNavigateToLocationGroups: () -> Unit = {},
    onNavigateToLandRecords: () -> Unit,
    onNavigateToManualReview: () -> Unit,
    onNavigateToExport: () -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val matchPairs by viewModel.matchPairs.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val progress by viewModel.processingProgress.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()
    val ocrModelStatus by viewModel.ocrModelStatus.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val retrySummary by viewModel.retrySummary.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedDocumentForDiagnostic by remember { mutableStateOf<DocumentRecord?>(null) }
    var showSeedConfirmDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showModelImportDialog by remember { mutableStateOf(false) }
    var modelImportMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isCompilingLocationPdf by remember { mutableStateOf(false) }
    var locationPdfResult by remember { mutableStateOf<ExportResult?>(null) }

    // Multi-file picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // Folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        treeUri?.let { viewModel.importFolder(it) }
    }

    // PDF picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    // OCR Model picker
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importOcrModels(uris) { result ->
                modelImportMessages = result.messages
                showModelImportDialog = true
            }
        }
    }

    // Honest counts derived directly from SQLite Single Source of Truth
    val totalCount = documents.size
    val waitingForModelCount = documents.count { it.classificationReason == "Waiting for OCR model" && !it.isDuplicate }
    val landRecordCount = documents.count { it.docType == DocType.LAND_RECORD && !it.isDuplicate }
    val aadhaarCount = documents.count { it.docType == DocType.AADHAAR && !it.isDuplicate }
    val unknownCount = documents.count { it.docType == DocType.UNKNOWN && it.classificationReason != "Waiting for OCR model" && !it.isDuplicate }
    val failedCount = documents.count { it.docType == DocType.FAILED_OCR && !it.isDuplicate }
    val duplicateCount = documents.count { it.isDuplicate }
    val verifiedPairsCount = matchPairs.count { it.status == MatchStatus.VERIFIED }
    val suggestedPairsCount = matchPairs.count { it.status == MatchStatus.SUGGESTED || it.status == MatchStatus.NEEDS_REVIEW }

    val filterCategories = mutableListOf("All ($totalCount)").apply {
        if (waitingForModelCount > 0) add("Waiting for Model ($waitingForModelCount)")
        add("Land Records ($landRecordCount)")
        add("Aadhaar ($aadhaarCount)")
        add("Unknown ($unknownCount)")
        add("Failed OCR ($failedCount)")
        add("Duplicates ($duplicateCount)")
    }

    val selectedCategoryTitle = filterCategories.getOrElse(selectedFilterIndex) { "All" }.substringBefore(" (")
    val filteredDocs = when (selectedCategoryTitle) {
        "Waiting for Model" -> documents.filter { it.classificationReason == "Waiting for OCR model" && !it.isDuplicate }
        "Land Records" -> documents.filter { it.docType == DocType.LAND_RECORD && !it.isDuplicate }
        "Aadhaar" -> documents.filter { it.docType == DocType.AADHAAR && !it.isDuplicate }
        "Unknown" -> documents.filter { it.docType == DocType.UNKNOWN && it.classificationReason != "Waiting for OCR model" && !it.isDuplicate }
        "Failed OCR" -> documents.filter { it.docType == DocType.FAILED_OCR && !it.isDuplicate }
        "Duplicates" -> documents.filter { it.isDuplicate }
        else -> documents
    }

    val finalDocs = remember(filteredDocs, searchQuery) {
        if (searchQuery.isBlank()) {
            filteredDocs
        } else {
            val q = searchQuery.trim().lowercase()
            filteredDocs.filter { doc ->
                doc.originalFilename.lowercase().contains(q) ||
                (doc.mouza?.lowercase()?.contains(q) == true) ||
                (doc.tahasil?.lowercase()?.contains(q) == true) ||
                (doc.district?.lowercase()?.contains(q) == true) ||
                (doc.rawExtractedText?.lowercase()?.contains(q) == true) ||
                LocationExtractor.extractCanonicalLocation(doc).lowercase().contains(q)
            }
        }
    }

    val topLocationsText = remember(documents) {
        val extracted = documents.mapNotNull { doc ->
            LocationExtractor.extractCanonicalLocation(doc).takeIf { it != "Unassigned / General" }
        }.distinct()
        if (extracted.isNotEmpty()) {
            extracted.take(3).joinToString(", ") + if (extracted.size > 3) "..." else ""
        } else {
            "Sorted by village / tahasil"
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "PataPair — Land & Aadhaar System",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Odisha Khatiyan & Aadhaar Offline Matching",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFC8E6C9)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(
                        onClick = { showSeedConfirmDialog = true },
                        modifier = Modifier.testTag("btn_seed_sample_data")
                    ) {
                        Icon(Icons.Default.Science, contentDescription = "Seed Synthetic Data", tint = Color.White)
                    }
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.testTag("btn_clear_data")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Collection", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("document_queue_list"),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Stats Overview Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Land Records",
                        count = landRecordCount.toString(),
                        subtitle = "Patta / Khatiyan",
                        color = Color(0xFF1E5E3A),
                        modifier = Modifier.weight(1f).clickable { onNavigateToLandRecords() }
                    )
                    StatCard(
                        title = "Aadhaar Cards",
                        count = aadhaarCount.toString(),
                        subtitle = "Verified UIDs",
                        color = Color(0xFF1B4F72),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Verified Pairs",
                        count = verifiedPairsCount.toString(),
                        subtitle = "$suggestedPairsCount In Review",
                        color = Color(0xFFC77700),
                        modifier = Modifier.weight(1f).clickable { onNavigateToManualReview() }
                    )
                }
            }

            // Feature Hero Navigation Cards (AI Pairs & Location Groups)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // AI Pairs Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigateToAiPairs() }
                            .testTag("hero_card_ai_pairs"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI Pairs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                "$suggestedPairsCount matches found",
                                color = Color(0xFFC8E6C9),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "See pairs AI made →",
                                color = Color(0xFFFFD54F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Location Groups Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onNavigateToLocationGroups() }
                            .testTag("hero_card_location_groups"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFC77700)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("By Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                topLocationsText,
                                color = Color(0xFFFFE0B2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Explore groups →",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Secondary Quick Navigation Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToLandRecords,
                        modifier = Modifier.weight(1f).testTag("nav_land_records")
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pata View", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToManualReview,
                        modifier = Modifier.weight(1f).testTag("nav_manual_review")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Review", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToExport,
                        modifier = Modifier.weight(1f).testTag("nav_export")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", fontSize = 12.sp)
                    }
                }
            }

            // OCR Model Status Card
            item {
                if (!ocrModelStatus.isOdiaInstalled) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_ocr_model_warning"),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF8E1)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Odia OCR model not installed",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF78350F)
                                    )
                                    Text(
                                        text = "Odia land records will wait until 'ori.traineddata' is installed. English & Aadhaar documents process immediately.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Status badges for ori and eng
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (ocrModelStatus.isOdiaInstalled) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (ocrModelStatus.isOdiaInstalled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = null,
                                            tint = if (ocrModelStatus.isOdiaInstalled) Color(0xFF16A34A) else Color(0xFFDC2626),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (ocrModelStatus.isOdiaInstalled) "ori: ${"%.1f".format(ocrModelStatus.odiaSizeMb)}MB" else "ori.traineddata: Missing",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (ocrModelStatus.isOdiaInstalled) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                    }
                                }

                                Surface(
                                    color = if (ocrModelStatus.isEnglishInstalled) Color(0xFFDCFCE7) else Color(0xFFF3F4F6),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (ocrModelStatus.isEnglishInstalled) Icons.Default.CheckCircle else Icons.Default.Info,
                                            contentDescription = null,
                                            tint = if (ocrModelStatus.isEnglishInstalled) Color(0xFF16A34A) else Color(0xFF6B7280),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (ocrModelStatus.isEnglishInstalled) "eng: ${"%.1f".format(ocrModelStatus.englishSizeMb)}MB" else "eng: Optional",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (ocrModelStatus.isEnglishInstalled) Color(0xFF15803D) else Color(0xFF4B5563)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    modelPickerLauncher.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_import_ocr_models"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import OCR Models (ori.traineddata)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_ocr_model_installed"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Odia OCR Model Installed (${"%.1f".format(ocrModelStatus.odiaSizeMb)} MB)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1B5E20)
                                    )
                                }

                                TextButton(
                                    onClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Update Models", fontSize = 11.sp, color = Color(0xFF1B5E20))
                                }
                            }

                            if (ocrModelStatus.isOdiaVerified) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "✓ Tesseract verified with ori.traineddata: \"${ocrModelStatus.odiaSampleOutput}\"",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF15803D),
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (ocrModelStatus.odiaVerificationMessage.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "⚠ Verification: ${ocrModelStatus.odiaVerificationMessage}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFB45309),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // AI Interpretation & Privacy Status Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_ai_engine_status"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = Color(0xFF0F766E),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "AI Engine: ${aiStatus.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F766E)
                                )
                            }
                            Text(
                                text = if (aiStatus.isAvailable) "Active with Odia verification fallback" else "Offline Rulebook Active • 100% On-Device & Private",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF475569)
                            )
                        }

                        Surface(
                            color = Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Zero Cloud Leakage",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Compact Document Ingestion Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_document_ingestion"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isProcessing) "Processing Ingestion Queue" else "Document Ingestion",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Upload Khatiyan records or Aadhaar cards",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isProcessing) {
                                    TextButton(
                                        onClick = { viewModel.reprocessUnextractedDocuments() },
                                        modifier = Modifier.testTag("btn_reprocess_unextracted"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("⚡ Match All", fontSize = 11.sp, color = Color(0xFF1E5E3A), fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (unknownCount > 0 && !isProcessing) {
                                    TextButton(
                                        onClick = { viewModel.reprocessUnknownDocuments() },
                                        modifier = Modifier.testTag("btn_reprocess_unknown"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Re-scan ($unknownCount)", fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (failedCount > 0 && !isProcessing) {
                                    TextButton(
                                        onClick = { viewModel.retryFailedDocuments() },
                                        modifier = Modifier.testTag("btn_retry_failed"),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Retry ($failedCount)", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Three balanced, compact buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    filePickerLauncher.launch(arrayOf("image/*", "application/pdf"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_files"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Files", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    folderPickerLauncher.launch(null)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_folder"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import Folder", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_import_pdf"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import PDF", fontSize = 11.sp, maxLines = 1)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Dedicated option to compile location wise sorted images into a PDF
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    if (!isCompilingLocationPdf) {
                                        isCompilingLocationPdf = true
                                        coroutineScope.launch {
                                            val groups = LocationExtractor.groupDocumentsAndPairs(documents, matchPairs)
                                            val res = ExportManager.exportAllLocationsPdf(
                                                context = context,
                                                locationGroups = groups,
                                                dbHelper = PataDatabaseHelper.getInstance(context)
                                            )
                                            locationPdfResult = res
                                            isCompilingLocationPdf = false
                                        }
                                    }
                                }
                                .testTag("btn_compile_location_images_pdf"),
                            color = Color(0xFFC77700).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFC77700).copy(alpha = 0.45f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFC77700)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Compile Location Images to PDF",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF8D4004)
                                        )
                                        Text(
                                            text = "Compile location-wise sorted document images into an official dossier PDF",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFC77700))
                            }
                        }

                        if (isProcessing) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = processingMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.pauseProcessing() },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }

            // Retry summary card
            retrySummary?.let { summary ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_retry_summary"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearRetrySummary() },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = "Dismiss",
                                    tint = Color(0xFF0369A1),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section Header & Search + Filter Chips (Everything below Document Ingestion)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Document Ingestion Queue",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${finalDocs.size} / $totalCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "Tap card to inspect OCR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search field for instant query
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_documents"),
                        placeholder = { Text("Search by filename, location, khata, plot...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Modern M3 FilterChips in a LazyRow: Smooth scrolling, zero gesture conflict with vertical list
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(filterCategories.size) { index ->
                            val title = filterCategories[index]
                            val isSelected = selectedFilterIndex == index
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilterIndex = index },
                                label = {
                                    Text(
                                        title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    when {
                                        title.startsWith("All") -> Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                                        title.startsWith("Land Records") -> Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF1E5E3A))
                                        title.startsWith("Aadhaar") -> Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF1565C0))
                                        title.startsWith("Waiting") -> Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFB45309))
                                        title.startsWith("Unknown") -> Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD97706))
                                        title.startsWith("Failed") -> Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                        else -> null
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            // Document Queue List or Empty State
            if (finalDocs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("card_empty_queue"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (totalCount == 0) Icons.Default.Folder else Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = if (totalCount == 0) "No documents in collection yet."
                                else "No documents matching \"$selectedCategoryTitle\"${if (searchQuery.isNotBlank()) " with query '$searchQuery'" else ""}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (totalCount == 0) "Import Khatiyans (Pattas) and Aadhaar cards, or seed synthetic verification records to see cross-script pairing and location sorting in action."
                                else "Try clearing your search query or selecting 'All' in the filter chips above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            if (totalCount == 0) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Button(
                                        onClick = { showSeedConfirmDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E3A)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Seed Demo Records", fontSize = 12.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { filePickerLauncher.launch(arrayOf("image/*", "application/pdf")) },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import Files", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                items(finalDocs, key = { it.id }) { doc ->
                    DocumentItemCard(
                        document = doc,
                        onClick = { selectedDocumentForDiagnostic = doc }
                    )
                }
            }
        }
    }

    // Diagnostic Modal Dialog
    selectedDocumentForDiagnostic?.let { doc ->
        DiagnosticDialog(
            document = doc,
            onDismiss = { selectedDocumentForDiagnostic = null },
            onReclassify = { newType ->
                viewModel.setManualClassification(doc.id, newType)
                selectedDocumentForDiagnostic = null
            },
            onRotate = {
                viewModel.rotateDocument(doc.id, 90)
                selectedDocumentForDiagnostic = null
            },
            onReprocess = {
                viewModel.reprocessDocument(doc.id)
                selectedDocumentForDiagnostic = null
            }
        )
    }

    // Seed Sample Collection Confirmation Dialog (Debug/Verification)
    if (showSeedConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSeedConfirmDialog = false },
            icon = { Icon(Icons.Default.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Seed Synthetic Verification Data") },
            text = {
                Text("This will inject verified synthetic test documents (5 Aadhaar cards and 5 Odisha Land Record Khatiyan pages with authentic Odia script). Existing data is NOT deleted, and synthetic items are tagged 'Synthetic demo data'.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.seedSampleCollection { /* completed */ }
                        showSeedConfirmDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_seed")
                ) {
                    Text("Seed Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSeedConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Collection Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear All Document Records?") },
            text = { Text("Are you sure you want to delete all stored documents, extracted persons, and match pairs from the local database?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showClearConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // OCR Model Import Results Dialog
    if (showModelImportDialog) {
        AlertDialog(
            onDismissRequest = { showModelImportDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("OCR Model Import Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (modelImportMessages.isEmpty()) {
                        Text("No valid files were imported.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        for (msg in modelImportMessages) {
                            Text(msg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showModelImportDialog = false },
                    modifier = Modifier.testTag("btn_close_import_result")
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Compiling Location-Wise PDF Loading Dialog
    if (isCompilingLocationPdf) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Compiling Location PDF Dossier...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFC77700))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Compiling all location-sorted document images and cross-matched pairs into an official PDF dossier...")
                }
            },
            confirmButton = {}
        )
    }

    // Location PDF Export Result Dialog
    locationPdfResult?.let { res ->
        AlertDialog(
            onDismissRequest = { locationPdfResult = null },
            icon = {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = if (res.isSuccess) Color(0xFF1E5E3A) else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (res.isSuccess) "Location PDF Generated!" else "PDF Generation Failed") },
            text = {
                Text(res.summary, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                if (res.isSuccess && res.shareUri != null) {
                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, res.shareUri)
                                type = "application/pdf"
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Location PDF"))
                            locationPdfResult = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E3A)),
                        modifier = Modifier.testTag("btn_share_location_pdf_from_home")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share / Open PDF")
                    }
                } else {
                    Button(onClick = { locationPdfResult = null }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (res.isSuccess && res.shareUri != null) {
                    TextButton(onClick = { locationPdfResult = null }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(count, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
fun DocumentItemCard(
    document: DocumentRecord,
    onClick: () -> Unit
) {
    val location = remember(document) {
        LocationExtractor.extractCanonicalLocation(document)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("doc_card_${document.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DocumentThumbnailView(
                doc = document,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = document.originalFilename,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (document.totalPdfPages > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Pg ${document.pdfPageNumber}/${document.totalPdfPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Location badge if extracted from document
                if (location != "Unassigned / General") {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFC77700).copy(alpha = 0.12f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFC77700), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = location,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF8D4004)
                            )
                        }
                    }
                }

                val detailLine = when {
                    document.khataNumber != null || document.plotNumber != null -> {
                        "Khata #${document.khataNumber ?: "—"} • Plot #${document.plotNumber ?: "—"}"
                    }
                    document.classificationReason == "Waiting for OCR model" -> "Waiting for OCR model (ori.traineddata)"
                    document.classificationReason.isNotEmpty() -> document.classificationReason
                    else -> "Awaiting classification"
                }

                Text(
                    text = detailLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (document.classificationReason == "Waiting for OCR model") Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (document.classificationReason == "Waiting for OCR model") FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (document.processingErrors != null) {
                    Text(
                        text = "Error: ${document.processingErrors}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = when {
                        document.classificationReason == "Waiting for OCR model" -> Color(0xFFFEF3C7)
                        document.docType == DocType.LAND_RECORD -> Color(0xFFD4EFDF)
                        document.docType == DocType.AADHAAR -> Color(0xFFD6EAF8)
                        document.docType == DocType.FAILED_OCR -> Color(0xFFFADBD8)
                        document.docType == DocType.UNSUPPORTED_SCRIPT -> Color(0xFFFCF3CF)
                        else -> Color(0xFFE5E7EB)
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = when {
                            document.classificationReason == "Waiting for OCR model" -> "WAITING"
                            document.docType == DocType.LAND_RECORD -> "LAND RECORD"
                            document.docType == DocType.AADHAAR -> "AADHAAR"
                            document.docType == DocType.FAILED_OCR -> "FAILED OCR"
                            document.docType == DocType.UNSUPPORTED_SCRIPT -> "UNSUPPORTED"
                            else -> "UNKNOWN"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            document.classificationReason == "Waiting for OCR model" -> Color(0xFF92400E)
                            document.docType == DocType.LAND_RECORD -> Color(0xFF1E5E3A)
                            document.docType == DocType.AADHAAR -> Color(0xFF1B4F72)
                            document.docType == DocType.FAILED_OCR -> Color(0xFF78281F)
                            document.docType == DocType.UNSUPPORTED_SCRIPT -> Color(0xFF7D6608)
                            else -> Color(0xFF374151)
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (document.isDuplicate) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Duplicate", style = MaterialTheme.typography.labelSmall, color = Color(0xFF78281F))
                }

                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Inspect",
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
