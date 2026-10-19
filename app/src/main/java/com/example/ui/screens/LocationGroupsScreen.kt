package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.PataDatabaseHelper
import com.example.engine.LocationExtractor
import com.example.engine.LocationGroupData
import com.example.export.ExportManager
import com.example.export.ExportResult
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.PersonRecord
import com.example.ui.theme.EarthOchre
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.DocumentThumbnailView
import com.example.viewmodel.PataPairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationGroupsScreen(
    viewModel: PataPairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToAiPairs: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val documents by viewModel.documents.collectAsState()
    val matchPairs by viewModel.matchPairs.collectAsState()
    val persons by viewModel.persons.collectAsState()
    val isMatchingRunning by viewModel.isMatchingRunning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var viewingDocument by remember { mutableStateOf<DocumentRecord?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }
    var exportingTitle by remember { mutableStateOf("") }

    // Group documents and pairs by location dynamically
    val locationGroups = remember(documents, matchPairs) {
        LocationExtractor.groupDocumentsAndPairs(documents, matchPairs)
    }

    var expandedGroupKey by remember(locationGroups) {
        mutableStateOf<String?>(locationGroups.firstOrNull { it.totalDocuments > 0 }?.locationKey)
    }

    val filteredGroups = remember(locationGroups, searchQuery) {
        if (searchQuery.isBlank()) {
            locationGroups
        } else {
            val q = searchQuery.trim().lowercase()
            locationGroups.filter {
                it.locationKey.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.odiaName.contains(q)
            }
        }
    }

    val totalGroupedDocs = remember(locationGroups) {
        locationGroups.sumOf { it.totalDocuments }
    }
    val totalGroupedPairs = remember(locationGroups) {
        locationGroups.sumOf { it.matchedCount }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFFFCC80), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Location Groups", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Pattas & Aadhaars sorted by location", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFE0B2))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_from_locations")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isExportingPdf && locationGroups.any { it.totalDocuments > 0 }) {
                                isExportingPdf = true
                                exportingTitle = "All Locations Dossier"
                                coroutineScope.launch {
                                    val res = ExportManager.exportAllLocationsPdf(
                                        context = context,
                                        locationGroups = locationGroups,
                                        dbHelper = PataDatabaseHelper.getInstance(context)
                                    )
                                    exportResult = res
                                    isExportingPdf = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_export_all_locations_pdf")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Compile Images into PDF", tint = Color.White)
                    }
                    IconButton(
                        onClick = { viewModel.loadDataFromDatabase() },
                        modifier = Modifier.testTag("btn_refresh_locations")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFC77700), // Earth Ochre for distinct location domain
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Location Header Banner
            item {
                LocationHeaderBanner(
                    totalLocations = locationGroups.size,
                    totalDocs = totalGroupedDocs,
                    totalPairs = totalGroupedPairs,
                    onNavigateToAiPairs = onNavigateToAiPairs,
                    onCompileAllPdf = {
                        if (!isExportingPdf) {
                            isExportingPdf = true
                            exportingTitle = "All Locations Dossier"
                            coroutineScope.launch {
                                val res = ExportManager.exportAllLocationsPdf(
                                    context = context,
                                    locationGroups = locationGroups,
                                    dbHelper = PataDatabaseHelper.getInstance(context)
                                )
                                exportResult = res
                                isExportingPdf = false
                            }
                        }
                    }
                )
            }

            // 2. Search Field
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_search_location"),
                    placeholder = { Text("Filter locations (e.g. mouza, tahasil, town...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // 3. Location Group Cards or Empty
            if (filteredGroups.isEmpty()) {
                item {
                    LocationEmptyCard(
                        onSeedData = {
                            viewModel.seedSampleCollection { }
                        }
                    )
                }
            } else {
                items(filteredGroups, key = { it.locationKey }) { group ->
                    val isExpanded = expandedGroupKey == group.locationKey

                    LocationGroupCard(
                        group = group,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedGroupKey = if (isExpanded) null else group.locationKey
                        },
                        onRunSmartMatchInLocation = {
                            viewModel.runSmartLocationMatching(group.locationKey)
                        },
                        onConfirmPair = { pairId ->
                            viewModel.verifyMatchPair(pairId)
                        },
                        onRejectPair = { pairId ->
                            viewModel.rejectMatchPair(pairId)
                        },
                        onViewDocument = { doc ->
                            viewingDocument = doc
                        },
                        onExportPdf = {
                            if (!isExportingPdf) {
                                isExportingPdf = true
                                exportingTitle = group.displayName
                                coroutineScope.launch {
                                    val res = ExportManager.exportLocationDossierPdf(
                                        context = context,
                                        locationGroup = group,
                                        dbHelper = PataDatabaseHelper.getInstance(context)
                                    )
                                    exportResult = res
                                    isExportingPdf = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Document Preview Dialog
    viewingDocument?.let { doc ->
        DocumentPreviewDialog(
            document = doc,
            onDismiss = { viewingDocument = null }
        )
    }

    // PDF Exporting Loading Dialog
    if (isExportingPdf) {
        AlertDialog(
            onDismissRequest = { /* cannot dismiss while exporting */ },
            title = { Text("Compiling Location PDF...") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFC77700))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Compiling location-sorted document images and matching dossiers for $exportingTitle...")
                }
            },
            confirmButton = {}
        )
    }

    // PDF Export Result Dialog
    exportResult?.let { res ->
        AlertDialog(
            onDismissRequest = { exportResult = null },
            icon = {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = null,
                    tint = if (res.isSuccess) Color(0xFF1E5E3A) else MaterialTheme.colorScheme.error
                )
            },
            title = { Text(if (res.isSuccess) "PDF Dossier Generated!" else "Export Failed") },
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
                            exportResult = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E3A)),
                        modifier = Modifier.testTag("btn_share_location_pdf")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share / Open PDF")
                    }
                } else {
                    Button(onClick = { exportResult = null }) {
                        Text("OK")
                    }
                }
            },
            dismissButton = {
                if (res.isSuccess && res.shareUri != null) {
                    TextButton(onClick = { exportResult = null }) {
                        Text("Dismiss")
                    }
                }
            }
        )
    }
}

@Composable
fun LocationHeaderBanner(
    totalLocations: Int,
    totalDocs: Int,
    totalPairs: Int,
    onNavigateToAiPairs: () -> Unit,
    onCompileAllPdf: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_location_banner"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFC77700)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sorted by Location", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.25f)
                ) {
                    Text(
                        "$totalLocations Groups",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "All Patta (ROR) and Aadhaar documents are grouped by their village, tahasil, or town. Cross-corroboration matches records inside their home location.",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(label = "Locations", value = "$totalLocations", color = Color.White, modifier = Modifier.weight(1f))
                MetricPill(label = "Total Documents", value = "$totalDocs", color = Color(0xFFFFE0B2), modifier = Modifier.weight(1f))
                MetricPill(label = "Matched Pairs", value = "$totalPairs", color = Color(0xFFC8E6C9), modifier = Modifier.weight(1f))
            }

            // Dedicated PDF compilation button
            Button(
                onClick = onCompileAllPdf,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8D4004)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_compile_all_locations_pdf")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compile Location Images into PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Quick button to AI Pairs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigateToAiPairs() },
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Switch to Name-Based AI Matched Pairs", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("View →", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LocationGroupCard(
    group: LocationGroupData,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRunSmartMatchInLocation: () -> Unit,
    onConfirmPair: (String) -> Unit,
    onRejectPair: (String) -> Unit,
    onViewDocument: (DocumentRecord) -> Unit,
    onExportPdf: () -> Unit = {}
) {
    var selectedSubTabIndex by remember { mutableIntStateOf(0) }

    val hasPairs = group.matchedCount > 0
    val isProminent = group.matchedCount > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_location_group_${group.locationKey}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isProminent) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (isProminent) 1.5.dp else 1.dp,
            if (isProminent) Color(0xFFFFA000) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isProminent) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Location Title & Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isProminent) Color(0xFFFF8F00) else Color(0xFF6D4C41)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                group.locationKey,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (group.odiaName.isNotBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    group.odiaName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Text(
                            "${group.pataDocuments.size} Pattas  •  ${group.aadhaarDocuments.size} Aadhaars  •  ${group.matchedCount} Pairs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExportPdf,
                        modifier = Modifier.testTag("btn_export_group_pdf_${group.locationKey}")
                    ) {
                        Icon(
                            Icons.Default.PictureAsPdf,
                            contentDescription = "Export ${group.locationKey} PDF",
                            tint = Color(0xFFC77700),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = onToggleExpand) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }
                }
            }

            // Stats Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LocationBadge(label = "${group.pataDocuments.size} Pattas", color = ForestGreen, modifier = Modifier.weight(1f))
                LocationBadge(label = "${group.aadhaarDocuments.size} Aadhaars", color = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                LocationBadge(label = "${group.matchedCount} Pairs", color = if (hasPairs) StatusGreen else Color.Gray, modifier = Modifier.weight(1f))
            }

            // Expandable Content
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sub-tab selectors
                    val subTabs = listOf(
                        "AI Pairs (${group.matchedCount})",
                        "Pattas (${group.pataDocuments.size})",
                        "Aadhaars (${group.aadhaarDocuments.size})"
                    )

                    TabRow(
                        selectedTabIndex = selectedSubTabIndex,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = Color(0xFFC77700)
                    ) {
                        subTabs.forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedSubTabIndex == idx,
                                onClick = { selectedSubTabIndex = idx },
                                text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedSubTabIndex == idx) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }

                    when (selectedSubTabIndex) {
                        0 -> {
                            // Matched pairs in this location
                            if (group.matchedPairs.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("No AI pairs linked in ${group.locationKey} yet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    Button(
                                        onClick = onRunSmartMatchInLocation,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC77700)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Smart Match in ${group.locationKey}")
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.matchedPairs.forEach { pair ->
                                        LocationPairMiniCard(
                                            pair = pair,
                                            onConfirm = { onConfirmPair(pair.id) },
                                            onReject = { onRejectPair(pair.id) }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Patta Land Records in this location
                            if (group.pataDocuments.isEmpty()) {
                                Text("No Patta documents for this location.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.pataDocuments.forEach { doc ->
                                        DocumentMiniCard(
                                            doc = doc,
                                            isPatta = true,
                                            onView = { onViewDocument(doc) }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Aadhaar Cards in this location
                            if (group.aadhaarDocuments.isEmpty()) {
                                Text("No Aadhaar documents for this location.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(12.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    group.aadhaarDocuments.forEach { doc ->
                                        DocumentMiniCard(
                                            doc = doc,
                                            isPatta = false,
                                            onView = { onViewDocument(doc) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationBadge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LocationPairMiniCard(
    pair: MatchPairRecord,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    val isVerified = pair.status == MatchStatus.VERIFIED
    val confidencePercent = (pair.matchConfidence * 100).toInt()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isVerified) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (isVerified) StatusGreen.copy(alpha = 0.5f) else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pair.landNameOriginal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Default.Link, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        pair.aadhaarName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1565C0)
                    )
                }

                Text(
                    "Confidence: $confidencePercent%  •  Translit: ${pair.landNameTransliterated}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isVerified) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Verified", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DocumentMiniCard(
    doc: DocumentRecord,
    isPatta: Boolean,
    onView: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onView() }
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DocumentThumbnailView(
                doc = doc,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    doc.originalFilename,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isPatta && doc.khataNumber != null) {
                    Text(
                        "Khata: ${doc.khataNumber} | Plot: ${doc.plotNumber ?: "—"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!isPatta && doc.aadhaarNumberMasked != null) {
                    Text(
                        doc.aadhaarNumberMasked,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("View", fontSize = 12.sp, color = ForestGreen, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun LocationEmptyCard(onSeedData: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFC77700), modifier = Modifier.size(32.dp))
            }

            Text("No Locations Detected Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Extracted Odisha mouzas, tahasils, and villages will automatically appear here when documents are imported and scanned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(onClick = onSeedData) {
                Text("Seed Verification Test Records")
            }
        }
    }
}
