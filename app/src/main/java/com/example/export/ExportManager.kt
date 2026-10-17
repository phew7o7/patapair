package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.db.PataDatabaseHelper
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ExportResult(
    val isSuccess: Boolean,
    val shareUri: Uri?,
    val file: File?,
    val summary: String,
    val errorMessage: String? = null
)

object ExportManager {

    const val A4_WIDTH_PT = 595
    const val A4_HEIGHT_PT = 842

    suspend fun exportCsvReport(
        context: Context,
        dbHelper: PataDatabaseHelper,
        filterVerifiedOnly: Boolean = false
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val allPairs = dbHelper.getAllMatchPairs()
            val pairsToExport = if (filterVerifiedOnly) {
                allPairs.filter { it.status == MatchStatus.VERIFIED }
            } else {
                allPairs
            }

            if (pairsToExport.isEmpty()) {
                return@withContext ExportResult(
                    isSuccess = false,
                    shareUri = null,
                    file = null,
                    summary = "No matching pairs available to export.",
                    errorMessage = "There are no ${if (filterVerifiedOnly) "verified" else "matched"} document pairs in the system to export."
                )
            }

            val docMap = dbHelper.getAllDocuments().associateBy { it.id }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val csvFile = File(exportDir, "patapair_audit_report_${System.currentTimeMillis()}.csv")

            OutputStreamWriter(FileOutputStream(csvFile), StandardCharsets.UTF_8).use { writer ->
                // Write UTF-8 BOM so Excel opens Odia script correctly
                writer.write("\uFEFF")
                // CSV Header
                writer.write("Person Code,Aadhaar Name,Land Name (Odia),Land Name (Transliterated),Match Score,Status,Khata No,Plot No,Tahasil,Mouza,Aadhaar File,Land Record File,PDF Page\n")

                for (pair in pairsToExport) {
                    val landDoc = docMap[pair.landDocId]
                    val aadhaarDoc = docMap[pair.aadhaarDocId]

                    val row = listOf(
                        escapeCsv(pair.personCode ?: "N/A"),
                        escapeCsv(pair.aadhaarName),
                        escapeCsv(pair.landNameOriginal),
                        escapeCsv(pair.landNameTransliterated),
                        String.format("%.2f%%", pair.matchConfidence * 100),
                        escapeCsv(pair.status.name),
                        escapeCsv(landDoc?.khataNumber ?: "N/A"),
                        escapeCsv(landDoc?.plotNumber ?: "N/A"),
                        escapeCsv(landDoc?.tahasil ?: "N/A"),
                        escapeCsv(landDoc?.mouza ?: "N/A"),
                        escapeCsv(aadhaarDoc?.originalFilename ?: "N/A"),
                        escapeCsv(landDoc?.originalFilename ?: "N/A"),
                        (landDoc?.pdfPageNumber ?: 1).toString()
                    ).joinToString(",")

                    writer.write(row + "\n")
                }
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            ExportResult(
                isSuccess = true,
                shareUri = uri,
                file = csvFile,
                summary = "Exported ${pairsToExport.size} pair records to CSV: ${csvFile.name}"
            )
        } catch (e: Exception) {
            ExportResult(
                isSuccess = false,
                shareUri = null,
                file = null,
                summary = "Failed to export CSV: ${e.localizedMessage}",
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun exportPdfDossier(
        context: Context,
        dbHelper: PataDatabaseHelper,
        filterVerifiedOnly: Boolean = true
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val allPairs = dbHelper.getAllMatchPairs()
            val pairsToExport = if (filterVerifiedOnly) {
                allPairs.filter { it.status == MatchStatus.VERIFIED }
            } else {
                allPairs.filter { it.status != MatchStatus.REJECTED }
            }

            if (pairsToExport.isEmpty()) {
                return@withContext ExportResult(
                    isSuccess = false,
                    shareUri = null,
                    file = null,
                    summary = "No verified pairs available to export.",
                    errorMessage = "Export requires at least one verified pair."
                )
            }

            val docMap = dbHelper.getAllDocuments().associateBy { it.id }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val pdfFile = File(exportDir, "patapair_verified_dossier_${System.currentTimeMillis()}.pdf")

            val pdfDocument = PdfDocument()
            var pageNumber = 1

            for (pair in pairsToExport) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val titlePaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 18f
                    isFakeBoldText = true
                }
                val subTitlePaint = Paint().apply {
                    color = Color.DKGRAY
                    textSize = 12f
                }
                val textPaint = Paint().apply {
                    color = Color.BLACK
                    textSize = 10f
                }
                val greenPaint = Paint().apply {
                    color = Color.rgb(30, 94, 58)
                    textSize = 14f
                    isFakeBoldText = true
                }

                // Header Banner
                canvas.drawRect(40f, 40f, 555f, 90f, Paint().apply {
                    color = Color.rgb(240, 245, 240)
                })
                canvas.drawText("PataPair — Verified Land & Aadhaar Record Dossier", 60f, 70f, titlePaint)

                // Pair ID & Person Code
                val personCodeText = pair.personCode ?: "Person_${pageNumber}"
                canvas.drawText("$personCodeText • Status: ${pair.status.name}", 60f, 120f, greenPaint)
                canvas.drawText("Match Confidence: ${(pair.matchConfidence * 100).toInt()}%", 60f, 140f, subTitlePaint)

                val landDoc = docMap[pair.landDocId]
                val aadhaarDoc = docMap[pair.aadhaarDocId]

                // Box for Aadhaar Info
                canvas.drawRect(40f, 170f, 285f, 450f, Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                })
                canvas.drawText("Aadhaar Card Document", 60f, 200f, textPaint)
                canvas.drawText(aadhaarDoc?.originalFilename ?: "Aadhaar", 60f, 220f, textPaint)
                canvas.drawText("ID: ${aadhaarDoc?.aadhaarNumberMasked ?: "XXXX-XXXX-XXXX"}", 60f, 240f, textPaint)

                // Box for Land Record Info
                canvas.drawRect(310f, 170f, 555f, 450f, Paint().apply {
                    color = Color.LTGRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                })
                canvas.drawText("Land Record Page ${landDoc?.pdfPageNumber ?: 1}", 330f, 200f, textPaint)
                canvas.drawText(landDoc?.originalFilename ?: "Land Record", 330f, 220f, textPaint)
                canvas.drawText("Khata: ${landDoc?.khataNumber ?: "N/A"}, Plot: ${landDoc?.plotNumber ?: "N/A"}", 330f, 240f, textPaint)

                // Details Table Box
                canvas.drawRect(40f, 480f, 555f, 780f, Paint().apply {
                    color = Color.rgb(245, 245, 245)
                })
                canvas.drawText("Match Explanation & Audit Notes:", 60f, 510f, titlePaint)
                canvas.drawText(pair.matchExplanation.take(70), 60f, 535f, textPaint)
                canvas.drawText("Khata No: ${landDoc?.khataNumber ?: "N/A"}  |  Plot No: ${landDoc?.plotNumber ?: "N/A"}", 60f, 560f, textPaint)
                canvas.drawText("Tahasil: ${landDoc?.tahasil ?: "N/A"}  |  Mouza: ${landDoc?.mouza ?: "N/A"}", 60f, 585f, textPaint)

                pdfDocument.finishPage(page)
                pageNumber++
            }

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            ExportResult(
                isSuccess = true,
                shareUri = uri,
                file = pdfFile,
                summary = "Exported ${pairsToExport.size} verified pair pages to PDF: ${pdfFile.name}"
            )
        } catch (e: Exception) {
            ExportResult(
                isSuccess = false,
                shareUri = null,
                file = null,
                summary = "Failed to export PDF: ${e.localizedMessage}",
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun exportZipBundle(
        context: Context,
        dbHelper: PataDatabaseHelper
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val csvResult = exportCsvReport(context, dbHelper, filterVerifiedOnly = false)
            if (!csvResult.isSuccess || csvResult.file == null) {
                return@withContext ExportResult(
                    isSuccess = false,
                    shareUri = null,
                    file = null,
                    summary = "Cannot export ZIP: CSV generation failed (${csvResult.errorMessage})"
                )
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val zipFile = File(exportDir, "patapair_full_bundle_${System.currentTimeMillis()}.zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                // 1. Add CSV report
                val csvEntry = ZipEntry("audit_report.csv")
                zos.putNextEntry(csvEntry)
                csvResult.file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()

                // 2. Add Readme
                val readmeEntry = ZipEntry("README.txt")
                zos.putNextEntry(readmeEntry)
                val readmeText = """
                    PataPair Audit & Verification Export
                    Generated: ${System.currentTimeMillis()}
                    Contains:
                    - audit_report.csv: Complete matched documents, Odia transliteration, Khata/Plot numbers and confidence scores.
                """.trimIndent()
                zos.write(readmeText.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                zipFile
            )

            ExportResult(
                isSuccess = true,
                shareUri = uri,
                file = zipFile,
                summary = "Exported full ZIP audit bundle: ${zipFile.name}"
            )
        } catch (e: Exception) {
            ExportResult(
                isSuccess = false,
                shareUri = null,
                file = null,
                summary = "Failed to export ZIP: ${e.localizedMessage}",
                errorMessage = e.localizedMessage
            )
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
