package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.LocationExtractor
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.util.DocumentThumbnailView
import com.example.viewmodel.PataPairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPairsScreen(
    viewModel: PataPairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLocations: () -> Unit
) {
    val matchPairs by viewModel.matchPairs.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val isMatchingRunning by viewModel.isMatchingRunning.collectAsState()
    val matchingMessage by viewModel.matchingActionMessage.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var viewingDocument by remember { mutableStateOf<DocumentRecord?>(null) }
    var showExplanationPair by remember { mutableStateOf<MatchPairRecord?>(null) }

    val docMap = remember(documents) { documents.associateBy { it.id } }

    // Categorized pairs
    val activePairs = remember(matchPairs) {
        matchPairs.filter { it.status != MatchStatus.REJECTED }
    }
    val highConfidencePairs = remember(activePairs) {
        activePairs.filter { it.matchConfidence >= 0.80f }
    }
    val goodMatchesPairs = remember(activePairs) {
        activePairs.filter { it.matchConfidence in 0.65f..0.799f }
    }
    val needsReviewPairs = remember(activePairs) {
        activePairs.filter { it.status == MatchStatus.NEEDS_REVIEW || it.matchConfidence < 0.65f }
    }
    val verifiedPairs = remember(activePairs) {
        activePairs.filter { it.status == MatchStatus.VERIFIED }
    }

    val tabPairs = when (selectedFilterIndex) {
        0 -> activePairs
        1 -> highConfidencePairs
        2 -> goodMatchesPairs
        3 -> needsReviewPairs
        4 -> verifiedPairs
        else -> activePairs
    }

    val filteredList = remember(tabPairs, searchQuery) {
        if (searchQuery.isBlank()) {
            tabPairs
        } else {
            val q = searchQuery.trim().lowercase()
            tabPairs.filter { pair ->
                pair.aadhaarName.lowercase().contains(q) ||
                pair.landNameOriginal.lowercase().contains(q) ||
                pair.landNameTransliterated.lowercase().contains(q) ||
                (pair.personCode?.lowercase()?.contains(q) == true) ||
                (docMap[pair.landDocId]?.let { LocationExtractor.extractCanonicalLocation(it).lowercase().contains(q) } == true) ||
                (docMap[pair.aadhaarDocId]?.let { LocationExtractor.extractCanonicalLocation(it).lowercase().contains(q) } == true)
            }
        }
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
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("AI Matched Pairs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${activePairs.size} matches discovered by AI engine", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC8E6C9))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_from_ai_pairs")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.generateMatchSuggestions() },
                        enabled = !isMatchingRunning,
                        modifier = Modifier.testTag("btn_rerun_ai_scanner")
                    ) {
                        if (isMatchingRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-scan & Pair", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ForestGreen,
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
            // 1. AI Summary Banner Card
            item {
                AiPairsSummaryBanner(
                    totalPairs = activePairs.size,
                    highConfidenceCount = highConfidencePairs.size,
                    goodMatchCount = goodMatchesPairs.size,
                    verifiedCount = verifiedPairs.size,
                    isScanning = isMatchingRunning,
                    matchingMessage = matchingMessage,
                    onNavigateToLocations = onNavigateToLocations
                )
            }

            // 2. Search & Filter Bar
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_ai_pairs"),
                        placeholder = { Text("Search by person name, location, Khata...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Scrollable Filter Tabs
                    val tabs = listOf(
                        "All Pairs (${activePairs.size})",
                        "High Conf >80% (${highConfidencePairs.size})",
                        "Good 65-80% (${goodMatchesPairs.size})",
                        "Needs Review (${needsReviewPairs.size})",
                        "Verified (${verifiedPairs.size})"
                    )

                    ScrollableTabRow(
                        selectedTabIndex = selectedFilterIndex,
                        edgePadding = 0.dp,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = ForestGreen
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedFilterIndex == index,
                                onClick = { selectedFilterIndex = index },
                                text = {
                                    Text(
                                        title,
                                        fontWeight = if (selectedFilterIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. Pairs List or Empty State
            if (filteredList.isEmpty()) {
                item {
                    AiPairsEmptyState(
                        isScanning = isMatchingRunning,
                        hasAnyDocs = documents.isNotEmpty(),
                        onRunScanner = { viewModel.generateMatchSuggestions() },
                        onSeedData = {
                            viewModel.seedSampleCollection { }
                        }
                    )
                }
            } else {
                items(filteredList, key = { it.id }) { pair ->
                    val landDoc = docMap[pair.landDocId]
                    val aadhaarDoc = docMap[pair.aadhaarDocId]

                    AiPairCard(
                        pair = pair,
                        landDoc = landDoc,
                        aadhaarDoc = aadhaarDoc,
                        onConfirmPair = {
                            viewModel.verifyMatchPair(pair.id)
                        },
                        onRejectPair = {
                            viewModel.rejectMatchPair(pair.id)
                        },
                        onViewDocument = { doc ->
                            viewingDocument = doc
                        },
                        onViewExplanation = {
                            showExplanationPair = pair
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

    // AI Match Reason Explanation Dialog
    showExplanationPair?.let { pair ->
        AlertDialog(
            onDismissRequest = { showExplanationPair = null },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ForestGreen) },
            title = { Text("AI Match Signals Breakdown", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pair: ${pair.landNameTransliterated} (Patta) ↔ ${pair.aadhaarName} (Aadhaar)",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Confidence Score: ${(pair.matchConfidence * 100).toInt()}%",
                        color = if (pair.matchConfidence >= 0.80f) StatusGreen else StatusAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        pair.matchExplanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showExplanationPair = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AiPairsSummaryBanner(
    totalPairs: Int,
    highConfidenceCount: Int,
    goodMatchCount: Int,
    verifiedCount: Int,
    isScanning: Boolean,
    matchingMessage: String?,
    onNavigateToLocations: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_ai_pairs_summary"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B5E20)
        ),
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
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Match Engine", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isScanning) "Scanning..." else "$totalPairs Pairs Formed",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "AI compares names cross-script (Odia script to English transliteration), validates father/husband details, and corroborates common Odisha locations.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricPill(label = "High Conf (>80%)", value = "$highConfidenceCount", color = Color(0xFF81C784), modifier = Modifier.weight(1f))
                MetricPill(label = "Good Match", value = "$goodMatchCount", color = Color(0xFFFFD54F), modifier = Modifier.weight(1f))
                MetricPill(label = "Verified", value = "$verifiedCount", color = Color(0xFF64B5F6), modifier = Modifier.weight(1f))
            }

            // Quick jump to Location Groups
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onNavigateToLocations() },
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFFF8A65), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View sorted by Location", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Text("Explore →", color = Color(0xFFFFD54F), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isScanning && !matchingMessage.isNullOrBlank()) {
                Text(
                    text = matchingMessage,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun MetricPill(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiPairCard(
    pair: MatchPairRecord,
    landDoc: DocumentRecord?,
    aadhaarDoc: DocumentRecord?,
    onConfirmPair: () -> Unit,
    onRejectPair: () -> Unit,
    onViewDocument: (DocumentRecord) -> Unit,
    onViewExplanation: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val confidencePercent = (pair.matchConfidence * 100).toInt()
    val isVerified = pair.status == MatchStatus.VERIFIED

    val confidenceColor = when {
        isVerified -> StatusGreen
        pair.matchConfidence >= 0.80f -> Color(0xFF2E7D32)
        pair.matchConfidence >= 0.65f -> Color(0xFFE65100)
        else -> StatusAmber
    }

    val landLocation = landDoc?.let { LocationExtractor.extractCanonicalLocation(it) } ?: "Unassigned"
    val aadhaarLocation = aadhaarDoc?.let { LocationExtractor.extractCanonicalLocation(it) } ?: "Unassigned"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_ai_pair_${pair.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isVerified) Color(0xFFF1F8E9) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isVerified) StatusGreen.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Status Badge & Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = confidenceColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isVerified) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = confidenceColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isVerified) "VERIFIED (${confidencePercent}%)" else "$confidencePercent% AI Match",
                                color = confidenceColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (pair.personCode != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                pair.personCode,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                TextButton(onClick = onViewExplanation) {
                    Text("Why AI Matched?", fontSize = 12.sp, color = ForestGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            // Side-by-side comparison box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left: Patta (Land Record)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PATTA (ROR)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ForestGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        pair.landNameOriginal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Translit: ${pair.landNameTransliterated}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (landDoc?.khataNumber != null) {
                        Text(
                            "Khata: ${landDoc.khataNumber} | Plot: ${landDoc.plotNumber ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (landLocation != "Unassigned") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(landLocation, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE65100))
                        }
                    }

                    if (landDoc != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DocumentThumbnailView(
                            doc = landDoc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onViewDocument(landDoc) }
                        )
                    }
                }

                // Middle: Linking Icon
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(confidenceColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = confidenceColor, modifier = Modifier.size(18.dp))
                }

                // Right: Aadhaar Card
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AADHAAR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        pair.aadhaarName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val maskedAadhaar = aadhaarDoc?.aadhaarNumberMasked
                    if (!maskedAadhaar.isNullOrBlank()) {
                        Text(
                            maskedAadhaar,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (aadhaarLocation != "Unassigned") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(aadhaarLocation, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFFE65100))
                        }
                    }

                    if (aadhaarDoc != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        DocumentThumbnailView(
                            doc = aadhaarDoc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onViewDocument(aadhaarDoc) }
                        )
                    }
                }
            }

            // Quick Signals Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (landLocation.equals(aadhaarLocation, ignoreCase = true) && landLocation != "Unassigned") {
                    SignalBadge(label = "Same Location: $landLocation", isPositive = true)
                }
                if (confidencePercent >= 80) {
                    SignalBadge(label = "Strong Phonetic Concordance", isPositive = true)
                }
                if (isVerified) {
                    SignalBadge(label = "Verified Match", isPositive = true)
                }
            }

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isVerified) {
                    OutlinedButton(
                        onClick = onRejectPair,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                        border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_reject_pair_${pair.id}")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Not a Match", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onConfirmPair,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_confirm_pair_${pair.id}")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve & Pair", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verified & Linked", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    TextButton(onClick = onRejectPair) {
                        Text("Unlink", color = StatusRed, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SignalBadge(label: String, isPositive: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPositive) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isPositive) Color(0xFF2E7D32) else Color(0xFFC62828)
        )
    }
}

@Composable
fun AiPairsEmptyState(
    isScanning: Boolean,
    hasAnyDocs: Boolean,
    onRunScanner: () -> Unit,
    onSeedData: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(36.dp))
            }

            Text("No AI Matched Pairs Found Yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "The AI scanner matches Odia Land Records (Patta) with English Aadhaar cards by analyzing person names, kinship relations, and extracted Odisha locations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasAnyDocs) {
                    Button(
                        onClick = onRunScanner,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        enabled = !isScanning,
                        modifier = Modifier.testTag("btn_empty_run_scanner")
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Run Smart AI Scanner")
                    }
                }

                OutlinedButton(
                    onClick = onSeedData,
                    modifier = Modifier.testTag("btn_empty_seed_test_data")
                ) {
                    Text("Seed Verification Test Records")
                }
            }
        }
    }
}

@Composable
fun DocumentPreviewDialog(
    document: DocumentRecord,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(document.originalFilename, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DocumentThumbnailView(
                    doc = document,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Text(
                    "Type: ${document.docType} | Engine: ${document.ocrEngineUsed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!document.rawExtractedText.isNullOrBlank()) {
                    Text(
                        "Extracted Text:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        document.rawExtractedText.take(300) + if (document.rawExtractedText.length > 300) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
