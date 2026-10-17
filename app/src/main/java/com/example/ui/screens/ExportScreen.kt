package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.PataDatabaseHelper
import com.example.export.ExportManager
import com.example.export.ExportResult
import com.example.model.MatchStatus
import com.example.viewmodel.PataPairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: PataPairViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val matchPairs by viewModel.matchPairs.collectAsState()

    val verifiedCount = matchPairs.count { it.status == MatchStatus.VERIFIED }
    val totalPairs = matchPairs.size

    var isExporting by remember { mutableStateOf(false) }
    var exportResult by remember { mutableStateOf<ExportResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Export Audit & Pairing Dossiers", style = MaterialTheme.typography.titleLarge)
                        Text("$verifiedCount Verified • $totalPairs Total Candidates", style = MaterialTheme.typography.labelSmall, color = Color(0xFFC8E6C9))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_from_export")) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Export verified Odisha land record and Aadhaar matches for revenue audit, court compliance, and local archival.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Option 1: PDF Dossier
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Official PDF Dossier (A4)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Multi-page verified certificate report with side-by-side Aadhaar & Khatiyan tables", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val res = ExportManager.exportPdfDossier(
                                    context = context,
                                    dbHelper = PataDatabaseHelper.getInstance(context),
                                    filterVerifiedOnly = true
                                )
                                exportResult = res
                                isExporting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_export_pdf"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isExporting
                    ) {
                        Text("Export Verified PDF ($verifiedCount Records)")
                    }
                }
            }

            // Option 2: CSV Audit Report
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF1565C0), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("CSV Spreadsheet Report (UTF-8)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Full tabular dataset including Odia script, transliterations, Khata & Plot IDs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val res = ExportManager.exportCsvReport(
                                    context = context,
                                    dbHelper = PataDatabaseHelper.getInstance(context),
                                    filterVerifiedOnly = false
                                )
                                exportResult = res
                                isExporting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_export_csv"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !isExporting
                    ) {
                        Text("Export Full CSV Report ($totalPairs Pairs)")
                    }
                }
            }

            // Option 3: Full ZIP Bundle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = Color(0xFF4A148C), modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Complete Archival ZIP Bundle", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Bundled CSV audit report, audit logs, and README", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            coroutineScope.launch {
                                val res = ExportManager.exportZipBundle(
                                    context = context,
                                    dbHelper = PataDatabaseHelper.getInstance(context)
                                )
                                exportResult = res
                                isExporting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("btn_export_zip"),
                        enabled = !isExporting
                    ) {
                        Text("Export Archival ZIP")
                    }
                }
            }

            if (isExporting) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generating export artifact...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Export Result Notification Card
            exportResult?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("card_export_result"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.isSuccess) Color(0xFFD4EFDF) else Color(0xFFFADBD8)
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = if (res.isSuccess) "Export Successful!" else "Export Failed",
                            fontWeight = FontWeight.Bold,
                            color = if (res.isSuccess) Color(0xFF1E5E3A) else Color(0xFF78281F),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = res.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (res.isSuccess) Color(0xFF1E5E3A) else Color(0xFF78281F)
                        )

                        if (res.isSuccess && res.shareUri != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_STREAM, res.shareUri)
                                        type = if (res.file?.name?.endsWith(".pdf") == true) "application/pdf"
                                               else if (res.file?.name?.endsWith(".csv") == true) "text/csv"
                                               else "application/zip"
                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Export Dossier"))
                                },
                                modifier = Modifier.testTag("btn_share_export")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share / Open File")
                            }
                        }
                    }
                }
            }
        }
    }
}
