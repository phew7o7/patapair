package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DocType
import com.example.model.DocumentRecord
import org.json.JSONArray

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiagnosticDialog(
    document: DocumentRecord,
    onDismiss: () -> Unit,
    onReclassify: (DocType) -> Unit,
    onRotate: () -> Unit,
    onReprocess: () -> Unit
) {
    var showReclassifyMenu by remember { mutableStateOf(false) }

    val rulesList = remember(document.rulesFiredJson) {
        val list = mutableListOf<Triple<String, Float, String>>()
        try {
            val arr = JSONArray(document.rulesFiredJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val rule = obj.optString("rule", "RULE")
                val score = obj.optDouble("score", 0.0).toFloat()
                val desc = obj.optString("description", "")
                list.add(Triple(rule, score, desc))
            }
        } catch (e: Exception) {
            // Ignored
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_document_diagnostic"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Document Diagnostic Trace",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${document.originalFilename} (Page ${document.pdfPageNumber} of ${document.totalPdfPages})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Cancel, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Status & DocType Badges
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = when (document.docType) {
                            DocType.LAND_RECORD -> Color(0xFFD4EFDF)
                            DocType.AADHAAR -> Color(0xFFD6EAF8)
                            DocType.FAILED_OCR -> Color(0xFFFADBD8)
                            DocType.UNSUPPORTED_SCRIPT -> Color(0xFFFCF3CF)
                            DocType.UNKNOWN -> Color(0xFFE5E7EB)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = document.docType.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (document.docType) {
                                DocType.LAND_RECORD -> Color(0xFF1E5E3A)
                                DocType.AADHAAR -> Color(0xFF1B4F72)
                                DocType.FAILED_OCR -> Color(0xFF78281F)
                                DocType.UNSUPPORTED_SCRIPT -> Color(0xFF7D6608)
                                DocType.UNKNOWN -> Color(0xFF374151)
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Status: ${document.docStatus.name}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (document.isDuplicate) {
                        Surface(
                            color = Color(0xFFFADBD8),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "DUPLICATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF78281F),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (document.isSynthetic) {
                        Surface(
                            color = Color(0xFFEDE9FE),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Synthetic demo data",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF5B21B6),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Diagnostic Details Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("1. Pipeline & OCR Metadata", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Engine: ${document.ocrEngineUsed.ifEmpty { "Not Run" }}", style = MaterialTheme.typography.bodySmall)
                        Text("• Languages Configured: ${document.ocrLanguages.ifEmpty { "N/A" }}", style = MaterialTheme.typography.bodySmall)
                        Text("• Dimensions: ${document.imageWidth} x ${document.imageHeight} px", style = MaterialTheme.typography.bodySmall)
                        Text("• Orientation / Rotation: EXIF ${document.imageOrientation}° | User Rotation: ${document.userRotation}°", style = MaterialTheme.typography.bodySmall)
                        if (document.sha256 != null) {
                            Text("• SHA-256: ${document.sha256.take(16)}...", style = MaterialTheme.typography.bodySmall)
                        }
                        if (document.processingErrors != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Error: ${document.processingErrors}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Classification Analysis Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("2. Classification Scores & Rule Audit", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("• Reason: ${document.classificationReason.ifEmpty { "Pending classification" }}", style = MaterialTheme.typography.bodySmall)
                        Text("• Classification Score: ${document.classificationScore.toInt()} pts (Heuristic rule sum; threshold: 35 for Land, 50 for Aadhaar)", style = MaterialTheme.typography.bodySmall)

                        if (document.docType == DocType.LAND_RECORD) {
                            val khataDisplay = document.khataNumber ?: "Not identified in OCR text"
                            val plotDisplay = document.plotNumber ?: "Not identified in OCR text"
                            Text("• Extracted Land IDs: Khata: $khataDisplay | Plot: $plotDisplay", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        } else if (document.khataNumber != null || document.plotNumber != null) {
                            Text("• Extracted Land IDs: Khata ${document.khataNumber ?: "N/A"} | Plot ${document.plotNumber ?: "N/A"}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        if (document.aadhaarNumberMasked != null) {
                            Text("• Aadhaar ID: ${document.aadhaarNumberMasked}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }

                        if (rulesList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Fired Rules:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                            for ((rule, score, desc) in rulesList) {
                                Text("  - [$rule] (+${score.toInt()} pts) $desc", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Extracted Text (Redacted for privacy)
                Text("3. Extracted Text (Sensitive Identifiers Redacted)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF263238))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (document.redactedText.isNotBlank()) document.redactedText else "(No text extracted or OCR failed)",
                        color = Color(0xFFECEFF1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DividerDefaults.color)

                // Actions: Rotate, Reprocess, Manual Reclassification (Responsive Layout)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRotate,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_rotate_doc"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate 90°", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onReprocess,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_reprocess_doc"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reprocess OCR", fontSize = 12.sp)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showReclassifyMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_reclassify_doc"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual Reclassification", fontSize = 12.sp)
                        }

                        DropdownMenu(
                            expanded = showReclassifyMenu,
                            onDismissRequest = { showReclassifyMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Land Record (Patta/Khatiyan)") },
                                onClick = {
                                    showReclassifyMenu = false
                                    onReclassify(DocType.LAND_RECORD)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Aadhaar Card") },
                                onClick = {
                                    showReclassifyMenu = false
                                    onReclassify(DocType.AADHAAR)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Unknown Document") },
                                onClick = {
                                    showReclassifyMenu = false
                                    onReclassify(DocType.UNKNOWN)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
