package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import com.example.util.DocumentThumbnailView
import com.example.util.FullscreenDocumentDialog
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.PersonRecord
import com.example.model.getConflictingEvidence
import com.example.model.getSupportingEvidence
import com.example.model.getLocationStatusNote
import com.example.viewmodel.PataPairViewModel

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ManualReviewScreen(
    viewModel: PataPairViewModel,
    onNavigateBack: () -> Unit
) {
    val matchPairs by viewModel.matchPairs.collectAsState()
    val documents by viewModel.documents.collectAsState()
    val persons by viewModel.persons.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val diagnostics by viewModel.matchingDiagnostics.collectAsState()

    val suggestedPairs = matchPairs.filter { it.status == MatchStatus.SUGGESTED }
    val needsReviewPairs = matchPairs.filter { it.status == MatchStatus.NEEDS_REVIEW }
    val verifiedPairs = matchPairs.filter { it.status == MatchStatus.VERIFIED }
    val allActiveCandidates = matchPairs.filter { it.status != MatchStatus.REJECTED }

    var selectedTabIndex by remember(suggestedPairs.size, needsReviewPairs.size, verifiedPairs.size) {
        mutableIntStateOf(
            if (suggestedPairs.isNotEmpty()) 0
            else if (needsReviewPairs.isNotEmpty()) 1
            else if (verifiedPairs.isNotEmpty()) 2
            else 0
        )
    }
    var editingPerson by remember { mutableStateOf<PersonRecord?>(null) }
    var editNameValue by remember { mutableStateOf("") }

    var showManualPairDialog by remember { mutableStateOf(false) }
    var alreadyVerifiedWarningPair by remember { mutableStateOf<PairData?>(null) }

    val currentList = when (selectedTabIndex) {
        0 -> suggestedPairs
        1 -> needsReviewPairs
        2 -> verifiedPairs
        3 -> allActiveCandidates
        else -> matchPairs
    }

    val docMap = remember(documents) { documents.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cross-Script Pair Verification", style = MaterialTheme.typography.titleLarge)
                        Text("Aadhaar (English) & Khatiyan (Odia)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC8E6C9))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_from_review")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.reprocessUnextractedDocuments() },
                        modifier = Modifier.testTag("btn_rescan_from_review")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Re-scan & Match", tint = Color.White)
                    }
                    Button(
                        onClick = { showManualPairDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(end = 8.dp).testTag("btn_open_manual_pair_modal")
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Manual Pair", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // AI-Assisted Status Banner
            Surface(
                color = Color(0xFFF1F5F9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Color(0xFF0F766E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (aiStatus.isAvailable) "AI Multi-Signal Interpretation Active" else "Rulebook Engine Active (100% On-Device)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F766E)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF0369A1),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Private & Local",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF0369A1)
                        )
                    }
                }
            }

            // Matching Pipeline Diagnostic Summary Card (Responsive Layout)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pipeline Diagnostics",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.reprocessUnextractedDocuments() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 28.dp)
                                    .testTag("btn_reparse_records")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Re-parse", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.generateMatchSuggestions() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 28.dp)
                                    .testTag("btn_rerun_matcher")
                            ) {
                                Text("Re-run Matcher", fontSize = 11.sp)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "• Eligible Records: ${diagnostics.eligibleAadhaarCount} Aadhaar (${diagnostics.missingNameAadhaarCount} missing names) | ${diagnostics.eligibleLandCount} Land Records (${diagnostics.missingNameLandCount} missing names)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val evalText = if (diagnostics.isEvaluatedCountEstimated) {
                            "• Candidate Evaluation: ${diagnostics.totalCandidatesEvaluated} pairs evaluated (estimated matrix: ${diagnostics.eligibleAadhaarCount} × ${diagnostics.eligibleLandCount}) | ${diagnostics.candidatesRejectedByFilters} rejected by filters"
                        } else {
                            "• Candidate Evaluation: ${diagnostics.totalCandidatesEvaluated} pairs evaluated (actual run) | ${diagnostics.candidatesRejectedByFilters} rejected by filters"
                        }
                        Text(
                            text = evalText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Candidate Triage: ${diagnostics.candidatesSuggested} suggested, ${diagnostics.candidatesNeedsReview} need review, ${diagnostics.candidatesVerified} verified by user",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Suggested (${suggestedPairs.size})", fontSize = 11.sp, fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Needs Review (${needsReviewPairs.size})", fontSize = 11.sp, fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 },
                    text = { Text("Verified (${verifiedPairs.size})", fontSize = 11.sp, fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = { selectedTabIndex = 3 },
                    text = { Text("All Active (${allActiveCandidates.size})", fontSize = 11.sp, fontWeight = if (selectedTabIndex == 3) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            if (currentList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when (selectedTabIndex) {
                                0 -> if (needsReviewPairs.isNotEmpty()) {
                                    "0 pairs meet auto-suggested confidence, but you have ${needsReviewPairs.size} candidates in 'Needs Review'!"
                                } else {
                                    "No suggested pairs currently awaiting review."
                                }
                                1 -> "No pairs requiring manual review."
                                2 -> "No verified pairs yet. Approve suggestions or create manual pairs."
                                else -> "No active candidate pairs found in database."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        if (selectedTabIndex == 0 && needsReviewPairs.isNotEmpty()) {
                            Button(
                                onClick = { selectedTabIndex = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("View Review Candidates (${needsReviewPairs.size})")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.reprocessUnextractedDocuments() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E5E3A))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("⚡ Re-scan & Match All Odia Documents")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .testTag("pair_review_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentList, key = { it.id }) { pair ->
                        val aadhaarDoc = docMap[pair.aadhaarDocId]
                        val landDoc = docMap[pair.landDocId]

                        MatchPairCard(
                            pair = pair,
                            aadhaarDoc = aadhaarDoc,
                            landDoc = landDoc,
                            onApprove = { viewModel.updateMatchStatus(pair.id, MatchStatus.VERIFIED) },
                            onReject = { viewModel.updateMatchStatus(pair.id, MatchStatus.REJECTED) },
                            onEditLandName = {
                                val p = persons.find { it.id == pair.landPersonId }
                                if (p != null) {
                                    editingPerson = p
                                    editNameValue = p.originalName
                                }
                            },
                            onEditAadhaarName = {
                                val p = persons.find { it.id == pair.aadhaarPersonId }
                                if (p != null) {
                                    editingPerson = p
                                    editNameValue = p.originalName
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Person Name Edit Dialog (allows correcting both Land and Aadhaar names)
    editingPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { editingPerson = null },
            title = { Text("Correct Person Name in Record") },
            text = {
                Column {
                    Text("Original: ${person.originalName} (${person.script.name})", style = MaterialTheme.typography.bodySmall)
                    Text("Role: ${person.role}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (person.fathersOrSpouseName != null) {
                        Text("Father / Spouse: ${person.fathersOrSpouseName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNameValue,
                        onValueChange = { editNameValue = it },
                        label = { Text("Correct Name") },
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_correction")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.manuallyCorrectPersonName(person.id, editNameValue)
                        editingPerson = null
                    },
                    modifier = Modifier.testTag("btn_save_name_correction")
                ) {
                    Text("Save Correction")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingPerson = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Manual Pair Dialog
    if (showManualPairDialog) {
        ManualPairPickerModal(
            documents = documents,
            persons = persons,
            onDismiss = { showManualPairDialog = false },
            onPairConfirmed = { aDocId, lDocId, aPersonId, lPersonId ->
                val existingVerified = matchPairs.any {
                    it.status == MatchStatus.VERIFIED && (it.aadhaarDocId == aDocId || it.landDocId == lDocId)
                }

                if (existingVerified) {
                    alreadyVerifiedWarningPair = PairData(aDocId, lDocId, aPersonId, lPersonId)
                } else {
                    viewModel.manualPair(aDocId, lDocId, aPersonId, lPersonId)
                    showManualPairDialog = false
                }
            }
        )
    }

    // Warning confirmation dialog if already verified
    alreadyVerifiedWarningPair?.let { data ->
        AlertDialog(
            onDismissRequest = { alreadyVerifiedWarningPair = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Document Already Verified") },
            text = { Text("One or both selected documents are already part of an approved verified pair. Do you want to override and create this new pairing?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.manualPair(data.aDocId, data.lDocId, data.aPersonId, data.lPersonId)
                        alreadyVerifiedWarningPair = null
                        showManualPairDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Confirm Re-Pair")
                }
            },
            dismissButton = {
                TextButton(onClick = { alreadyVerifiedWarningPair = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class PairData(
    val aDocId: String,
    val lDocId: String,
    val aPersonId: String,
    val lPersonId: String
)

@Composable
fun ManualPairPickerModal(
    documents: List<DocumentRecord>,
    persons: List<PersonRecord>,
    onDismiss: () -> Unit,
    onPairConfirmed: (aDocId: String, lDocId: String, aPersonId: String, lPersonId: String) -> Unit
) {
    val docMap = remember(documents) { documents.associateBy { it.id } }
    val aadhaarPersons = remember(persons, docMap) {
        persons.filter { p -> docMap[p.documentId]?.docType == DocType.AADHAAR && docMap[p.documentId]?.isDuplicate != true }
    }
    val landPersons = remember(persons, docMap) {
        persons.filter { p -> docMap[p.documentId]?.docType == DocType.LAND_RECORD && docMap[p.documentId]?.isDuplicate != true }
    }

    var selectedAadhaarPersonId by remember { mutableStateOf(aadhaarPersons.firstOrNull()?.id ?: "") }
    var selectedLandPersonId by remember { mutableStateOf(landPersons.firstOrNull()?.id ?: "") }

    val selectedAadhaarPerson = aadhaarPersons.find { it.id == selectedAadhaarPersonId }
    val selectedLandPerson = landPersons.find { it.id == selectedLandPersonId }

    val selectedAadhaarDoc = selectedAadhaarPerson?.let { docMap[it.documentId] }
    val selectedLandDoc = selectedLandPerson?.let { docMap[it.documentId] }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Manual Pairing") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Select an Aadhaar record and a Land Record person to manually link.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))

                // Select Aadhaar
                Text("1. Aadhaar Cardholder", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp)
                ) {
                    items(aadhaarPersons) { person ->
                        val isSelected = person.id == selectedAadhaarPersonId
                        val doc = docMap[person.documentId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedAadhaarPersonId = person.id }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    person.originalName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "${doc?.originalFilename ?: "Aadhaar"} • ${doc?.aadhaarNumberMasked ?: "ID: XXXX"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Select Land Record
                Text("2. Land Record (Khatiyan / Patta)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(6.dp)
                ) {
                    items(landPersons) { person ->
                        val isSelected = person.id == selectedLandPersonId
                        val doc = docMap[person.documentId]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { selectedLandPersonId = person.id }
                                .padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    person.originalName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                Text(
                                    "Translit: ${person.transliteratedName} • ${doc?.originalFilename ?: "Land Record"} (Pg ${doc?.pdfPageNumber ?: 1})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (doc?.khataNumber != null) {
                                    Text(
                                        "Khata: ${doc.khataNumber}, Plot: ${doc.plotNumber ?: "N/A"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (aadhaarPersons.isNotEmpty() && landPersons.isNotEmpty() && selectedAadhaarPerson != null && selectedLandPerson != null && selectedAadhaarDoc != null && selectedLandDoc != null) {
                Button(
                    onClick = {
                        onPairConfirmed(
                            selectedAadhaarDoc.id,
                            selectedLandDoc.id,
                            selectedAadhaarPerson.id,
                            selectedLandPerson.id
                        )
                    },
                    modifier = Modifier.testTag("btn_confirm_manual_pair"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Pair Selected Records")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MatchPairCard(
    pair: MatchPairRecord,
    aadhaarDoc: DocumentRecord?,
    landDoc: DocumentRecord?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onEditLandName: () -> Unit,
    onEditAadhaarName: () -> Unit = {}
) {
    val supportingEvidence = remember(pair.matchExplanation) { pair.getSupportingEvidence() }
    val conflictingEvidence = remember(pair.matchExplanation) { pair.getConflictingEvidence() }
    var previewingDoc by remember { mutableStateOf<DocumentRecord?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("match_card_${pair.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Explicit Status Badge and Confidence
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = when (pair.status) {
                        MatchStatus.VERIFIED -> Color(0xFFD1FAE5)
                        MatchStatus.SUGGESTED -> Color(0xFFE0F2FE)
                        MatchStatus.NEEDS_REVIEW -> Color(0xFFFEF3C7)
                        MatchStatus.REJECTED -> Color(0xFFFEE2E2)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pair.status == MatchStatus.VERIFIED) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when (pair.status) {
                                MatchStatus.VERIFIED -> "${pair.personCode ?: "Verified"} • VERIFIED BY USER"
                                MatchStatus.SUGGESTED -> "SUGGESTED CANDIDATE • Score ${(pair.matchConfidence * 100).toInt()}%"
                                MatchStatus.NEEDS_REVIEW -> "NEEDS REVIEW • Score ${(pair.matchConfidence * 100).toInt()}%"
                                MatchStatus.REJECTED -> "REJECTED"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when (pair.status) {
                                MatchStatus.VERIFIED -> Color(0xFF047857)
                                MatchStatus.SUGGESTED -> Color(0xFF0369A1)
                                MatchStatus.NEEDS_REVIEW -> Color(0xFFB45309)
                                MatchStatus.REJECTED -> Color(0xFFB91C1C)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = if (pair.isManual) "Manual Link" else "Multi-Signal Analysis",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Two-Pane Side by Side with Visual Document Thumbnails & Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Aadhaar Document Details & Image Preview
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Aadhaar Card", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onEditAadhaarName, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = Color(0xFF2563EB), modifier = Modifier.size(13.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Visual Document Preview (PDF or Image)
                        DocumentThumbnailView(
                            doc = aadhaarDoc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp),
                            label = "Aadhaar Image",
                            onEnlargeRequested = { previewingDoc = aadhaarDoc }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(pair.aadhaarName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(aadhaarDoc?.originalFilename ?: "Aadhaar Document", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (aadhaarDoc?.aadhaarNumberMasked != null) {
                            Text(aadhaarDoc.aadhaarNumberMasked, style = MaterialTheme.typography.labelSmall, color = Color(0xFF2563EB), fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Land Record Document Details (With Exact PDF Page & Image Preview)
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF1E5E3A), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Land Record", style = MaterialTheme.typography.labelMedium, color = Color(0xFF1E5E3A), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = onEditLandName, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name", tint = Color(0xFF1E5E3A), modifier = Modifier.size(13.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Visual Document Preview (PDF page or Image)
                        DocumentThumbnailView(
                            doc = landDoc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp),
                            label = "Patta Pg ${landDoc?.pdfPageNumber ?: 1}",
                            onEnlargeRequested = { previewingDoc = landDoc }
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(pair.landNameOriginal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text("Translit: ${pair.landNameTransliterated}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E5E3A))
                        val pageInfo = if (landDoc != null) " (Page ${landDoc.pdfPageNumber} of ${landDoc.totalPdfPages})" else ""
                        Text("${landDoc?.originalFilename ?: "Land Record"}$pageInfo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (landDoc?.khataNumber != null) {
                            Text("Khata: ${landDoc.khataNumber}, Plot: ${landDoc.plotNumber ?: "N/A"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (landDoc?.mouza != null) {
                            Text("Mouza: ${landDoc.mouza}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Signal Evidence Breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Signal & Evidence Evaluation", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(6.dp))

                    if (supportingEvidence.isNotEmpty()) {
                        for (item in supportingEvidence) {
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(item, fontSize = 11.sp, color = Color(0xFF14532D))
                            }
                        }
                    }

                    if (conflictingEvidence.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        for (item in conflictingEvidence) {
                            Row(
                                modifier = Modifier.padding(vertical = 1.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(item, fontSize = 11.sp, color = Color(0xFF78350F), fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    val locNote = remember(pair.matchExplanation) { pair.getLocationStatusNote() }
                    if (locNote != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            modifier = Modifier.padding(vertical = 1.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(locNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (supportingEvidence.isEmpty() && conflictingEvidence.isEmpty() && locNote == null) {
                        Text(pair.matchExplanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = DividerDefaults.color)

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.testTag("btn_reject_${pair.id}")
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reject", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pair.status == MatchStatus.VERIFIED) Color(0xFF15803D) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("btn_approve_${pair.id}")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (pair.status == MatchStatus.VERIFIED) "Verified ✓" else "Approve Pair", fontSize = 12.sp)
                }
            }
        }
    }

    if (previewingDoc != null) {
        FullscreenDocumentDialog(
            doc = previewingDoc!!,
            onDismiss = { previewingDoc = null }
        )
    }
}
