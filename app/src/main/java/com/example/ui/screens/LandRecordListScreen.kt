package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.PersonRecord
import com.example.ui.components.DiagnosticDialog
import com.example.viewmodel.PataPairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandRecordListScreen(
    viewModel: PataPairViewModel,
    onNavigateBack: () -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val persons by viewModel.persons.collectAsState()

    val landRecords = documents.filter { it.docType == DocType.LAND_RECORD && !it.isDuplicate }
    val personsByDoc = persons.groupBy { it.documentId }

    var selectedDocumentForDiagnostic by remember { mutableStateOf<DocumentRecord?>(null) }
    var editingPerson by remember { mutableStateOf<PersonRecord?>(null) }
    var editNameValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Odisha Land Records (Patta / Khatiyan)", style = MaterialTheme.typography.titleLarge)
                        Text("${landRecords.size} records detected & indexed", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC8E6C9))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_from_land_records")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .padding(14.dp)
        ) {
            if (landRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No Land Records Detected Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Import Odisha Khatiyan PDFs or scan land records from Dashboard to populate this view.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("land_records_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(landRecords, key = { it.id }) { doc ->
                        val docPersons = personsByDoc[doc.id] ?: emptyList()
                        LandRecordCard(
                            document = doc,
                            persons = docPersons,
                            onCardClick = { selectedDocumentForDiagnostic = doc },
                            onEditPerson = { p ->
                                editingPerson = p
                                editNameValue = p.originalName
                            }
                        )
                    }
                }
            }
        }
    }

    // Diagnostic Trace Dialog
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

    // Person Name Manual Correction Dialog
    editingPerson?.let { person ->
        AlertDialog(
            onDismissRequest = { editingPerson = null },
            title = { Text("Correct Landholder / Rayat Name") },
            text = {
                Column {
                    Text("Current Extracted: ${person.originalName} (${person.script.name})", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editNameValue,
                        onValueChange = { editNameValue = it },
                        label = { Text("Correct Name (Odia or English)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_edit_person_name")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.manuallyCorrectPersonName(person.id, editNameValue)
                        editingPerson = null
                    },
                    modifier = Modifier.testTag("btn_save_person_name")
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
}

@Composable
fun LandRecordCard(
    document: DocumentRecord,
    persons: List<PersonRecord>,
    onCardClick: () -> Unit,
    onEditPerson: (PersonRecord) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("land_doc_${document.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableChart, contentDescription = null, tint = Color(0xFF1E5E3A), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = document.originalFilename,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = Color(0xFFD4EFDF),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Page ${document.pdfPageNumber} of ${document.totalPdfPages}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E5E3A),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Khata & Plot tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Khata No: ${document.khataNumber ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Plot No: ${document.plotNumber ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                if (document.tahasil != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Tahasil: ${document.tahasil}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Extracted People Section
            Text(
                text = "Associated Landholders / Rayats (${persons.size}):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E5E3A)
            )

            if (persons.isEmpty()) {
                Text(
                    text = "No person names extracted yet from this record page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for (p in persons) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF1E5E3A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(p.originalName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Transliteration: ${p.transliteratedName}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1E5E3A))
                            }
                        }
                        IconButton(onClick = { onEditPerson(p) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Name", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
