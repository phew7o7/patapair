package com.example.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.db.PataDatabaseHelper
import com.example.engine.LocationExtractor
import com.example.engine.LocationGroupData
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.util.DocumentImageLoader
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
                canvas.drawText("Ranking Score: ${(pair.matchConfidence * 100).toInt()}% (Heuristic ranking aid)", 60f, 140f, subTitlePaint)

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

    suspend fun exportLocationDossierPdf(
        context: Context,
        locationGroup: LocationGroupData,
        dbHelper: PataDatabaseHelper
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanKey = locationGroup.locationKey.replace("[^A-Za-z0-9_]".toRegex(), "_")
            val pdfFile = File(exportDir, "patapair_location_${cleanKey}_${System.currentTimeMillis()}.pdf")

            val pdfDocument = PdfDocument()
            var pageIndex = 1

            val docMap = dbHelper.getAllDocuments().associateBy { it.id }

            // 1. Cover / Summary Page for this Location
            val coverPageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
            val coverPage = pdfDocument.startPage(coverPageInfo)
            val coverCanvas = coverPage.canvas

            val headerPaint = Paint().apply {
                color = Color.rgb(27, 94, 32)
                style = Paint.Style.FILL
            }
            coverCanvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 120f, headerPaint)

            val titleWhitePaint = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isFakeBoldText = true
            }
            val subtitleWhitePaint = Paint().apply {
                color = Color.rgb(200, 230, 201)
                textSize = 12f
            }
            coverCanvas.drawText("PataPair — Location Document Dossier", 40f, 55f, titleWhitePaint)
            coverCanvas.drawText("Compiled Location: ${locationGroup.displayName}", 40f, 85f, subtitleWhitePaint)

            val sectionTitlePaint = Paint().apply {
                color = Color.rgb(30, 30, 30)
                textSize = 14f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                color = Color.rgb(50, 50, 50)
                textSize = 11f
            }

            coverCanvas.drawText("Location Summary Statistics", 40f, 160f, sectionTitlePaint)

            // Statistics Box
            val boxPaint = Paint().apply {
                color = Color.rgb(245, 247, 245)
                style = Paint.Style.FILL
            }
            val boxBorder = Paint().apply {
                color = Color.rgb(200, 215, 200)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            coverCanvas.drawRect(40f, 180f, 555f, 310f, boxPaint)
            coverCanvas.drawRect(40f, 180f, 555f, 310f, boxBorder)

            coverCanvas.drawText("Location Name: ${locationGroup.locationKey} (${locationGroup.odiaName})", 60f, 210f, textPaint)
            coverCanvas.drawText("Total Documents Compiled: ${locationGroup.totalDocuments}", 60f, 235f, textPaint)
            coverCanvas.drawText("Patta (Land Record) Pages: ${locationGroup.pataDocuments.size}", 60f, 260f, textPaint)
            coverCanvas.drawText("Aadhaar Cards: ${locationGroup.aadhaarDocuments.size}", 60f, 285f, textPaint)
            coverCanvas.drawText("AI Matched Pairs Formed: ${locationGroup.matchedCount}", 320f, 260f, textPaint)

            coverCanvas.drawText("Overview & Index of Records:", 40f, 350f, sectionTitlePaint)
            var indexY = 380f
            if (locationGroup.matchedPairs.isNotEmpty()) {
                coverCanvas.drawText("• ${locationGroup.matchedPairs.size} Matched Pairs (Side-by-Side Images & Details)", 50f, indexY, textPaint)
                indexY += 22f
            }
            if (locationGroup.pataDocuments.isNotEmpty()) {
                coverCanvas.drawText("• ${locationGroup.pataDocuments.size} Patta Land Records in this Location", 50f, indexY, textPaint)
                indexY += 22f
            }
            if (locationGroup.aadhaarDocuments.isNotEmpty()) {
                coverCanvas.drawText("• ${locationGroup.aadhaarDocuments.size} Aadhaar Cards in this Location", 50f, indexY, textPaint)
                indexY += 22f
            }

            pdfDocument.finishPage(coverPage)
            pageIndex++

            // 2. Matched Pairs Pages in this Location
            for (pair in locationGroup.matchedPairs) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                // Banner
                val bannerPaint = Paint().apply {
                    color = Color.rgb(240, 246, 240)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 60f, bannerPaint)

                val pairHeaderPaint = Paint().apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 13f
                    isFakeBoldText = true
                }
                canvas.drawText("${locationGroup.locationKey} • AI Matched Pair (${(pair.matchConfidence * 100).toInt()}% Match)", 40f, 35f, pairHeaderPaint)

                val landDoc = docMap[pair.landDocId]
                val aadhaarDoc = docMap[pair.aadhaarDocId]

                // Draw Patta box on the left (40f to 285f)
                val landMeta = listOf(
                    "Odia Name: ${pair.landNameOriginal}",
                    "Translit: ${pair.landNameTransliterated}",
                    "Khata: ${landDoc?.khataNumber ?: "N/A"}",
                    "Plot: ${landDoc?.plotNumber ?: "N/A"}",
                    "Tahasil: ${landDoc?.tahasil ?: locationGroup.locationKey}",
                    "Mouza: ${landDoc?.mouza ?: locationGroup.locationKey}"
                )
                drawDocumentBox(
                    context = context,
                    canvas = canvas,
                    doc = landDoc,
                    left = 40f,
                    top = 80f,
                    width = 245f,
                    height = 540f,
                    title = "Patta: ${pair.landNameTransliterated}",
                    subtitle = landDoc?.originalFilename ?: "Land Record",
                    metaLines = landMeta
                )

                // Draw Aadhaar box on the right (310f to 555f)
                val aadhaarMeta = listOf(
                    "Cardholder: ${pair.aadhaarName}",
                    "UID: ${aadhaarDoc?.aadhaarNumberMasked ?: "XXXX-XXXX-XXXX"}",
                    "Location: ${locationGroup.locationKey}",
                    "Status: ${pair.status.name}",
                    "Score: ${(pair.matchConfidence * 100).toInt()}%"
                )
                drawDocumentBox(
                    context = context,
                    canvas = canvas,
                    doc = aadhaarDoc,
                    left = 310f,
                    top = 80f,
                    width = 245f,
                    height = 540f,
                    title = "Aadhaar: ${pair.aadhaarName}",
                    subtitle = aadhaarDoc?.originalFilename ?: "Aadhaar Card",
                    metaLines = aadhaarMeta
                )

                // Bottom Explanation Box
                val bottomBox = Paint().apply {
                    color = Color.rgb(250, 250, 250)
                    style = Paint.Style.FILL
                }
                canvas.drawRect(40f, 640f, 555f, 790f, bottomBox)
                canvas.drawRect(40f, 640f, 555f, 790f, boxBorder)

                val noteTitlePaint = Paint().apply {
                    color = Color.rgb(27, 94, 32)
                    textSize = 10.5f
                    isFakeBoldText = true
                }
                canvas.drawText("AI Cross-Script Matching Details:", 55f, 665f, noteTitlePaint)
                canvas.drawText(pair.matchExplanation.take(85), 55f, 685f, textPaint)
                if (pair.matchExplanation.length > 85) {
                    canvas.drawText(pair.matchExplanation.substring(85).take(85), 55f, 700f, textPaint)
                }
                canvas.drawText("Location Corroboration: Shared administrative block (${locationGroup.locationKey})", 55f, 725f, textPaint)
                canvas.drawText("Status: ${pair.status.name}  |  Person Code: ${pair.personCode ?: "Auto-Assigned"}", 55f, 745f, textPaint)

                pdfDocument.finishPage(page)
                pageIndex++
            }

            // 3. Document Pages for Individual Documents in this Location
            val remainingPattas = locationGroup.pataDocuments.filter { doc ->
                locationGroup.matchedPairs.none { it.landDocId == doc.id }
            }
            val remainingAadhaars = locationGroup.aadhaarDocuments.filter { doc ->
                locationGroup.matchedPairs.none { it.aadhaarDocId == doc.id }
            }

            val remainingDocs = remainingPattas + remainingAadhaars
            for (chunk in remainingDocs.chunked(2)) {
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                canvas.drawText("${locationGroup.locationKey} • Additional Location Documents", 40f, 40f, sectionTitlePaint)

                val doc1 = chunk[0]
                val meta1 = listOf(
                    "Filename: ${doc1.originalFilename}",
                    "Type: ${doc1.docType}",
                    "Khata: ${doc1.khataNumber ?: "N/A"}",
                    "Plot: ${doc1.plotNumber ?: "N/A"}",
                    "Location: ${locationGroup.locationKey}"
                )
                drawDocumentBox(
                    context = context,
                    canvas = canvas,
                    doc = doc1,
                    left = 40f,
                    top = 65f,
                    width = 245f,
                    height = 540f,
                    title = doc1.docType.name,
                    subtitle = doc1.originalFilename,
                    metaLines = meta1
                )

                if (chunk.size > 1) {
                    val doc2 = chunk[1]
                    val meta2 = listOf(
                        "Filename: ${doc2.originalFilename}",
                        "Type: ${doc2.docType}",
                        "Khata: ${doc2.khataNumber ?: "N/A"}",
                        "Plot: ${doc2.plotNumber ?: "N/A"}",
                        "Location: ${locationGroup.locationKey}"
                    )
                    drawDocumentBox(
                        context = context,
                        canvas = canvas,
                        doc = doc2,
                        left = 310f,
                        top = 65f,
                        width = 245f,
                        height = 540f,
                        title = doc2.docType.name,
                        subtitle = doc2.originalFilename,
                        metaLines = meta2
                    )
                }

                pdfDocument.finishPage(page)
                pageIndex++
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
                summary = "Exported ${pageIndex - 1} pages for ${locationGroup.displayName} into PDF: ${pdfFile.name}"
            )
        } catch (e: Exception) {
            ExportResult(
                isSuccess = false,
                shareUri = null,
                file = null,
                summary = "Failed to export location PDF: ${e.localizedMessage}",
                errorMessage = e.localizedMessage
            )
        }
    }

    suspend fun exportAllLocationsPdf(
        context: Context,
        locationGroups: List<LocationGroupData>,
        dbHelper: PataDatabaseHelper
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val validGroups = locationGroups.filter { it.totalDocuments > 0 }
            if (validGroups.isEmpty()) {
                return@withContext ExportResult(
                    isSuccess = false,
                    shareUri = null,
                    file = null,
                    summary = "No documents found to export location PDF.",
                    errorMessage = "There are no documents categorized into locations to export."
                )
            }

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val pdfFile = File(exportDir, "patapair_all_locations_dossier_${System.currentTimeMillis()}.pdf")

            val pdfDocument = PdfDocument()
            var pageIndex = 1
            val docMap = dbHelper.getAllDocuments().associateBy { it.id }

            // Consolidated Cover Page
            val coverPageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
            val coverPage = pdfDocument.startPage(coverPageInfo)
            val coverCanvas = coverPage.canvas

            val headerPaint = Paint().apply {
                color = Color.rgb(27, 94, 32)
                style = Paint.Style.FILL
            }
            coverCanvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 130f, headerPaint)

            val titleWhitePaint = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isFakeBoldText = true
            }
            val subtitleWhitePaint = Paint().apply {
                color = Color.rgb(200, 230, 201)
                textSize = 12f
            }
            coverCanvas.drawText("PataPair — Consolidated Location Dossier", 40f, 55f, titleWhitePaint)
            coverCanvas.drawText("All Odisha Land Records & Aadhaar Cards Sorted by Location", 40f, 85f, subtitleWhitePaint)
            coverCanvas.drawText("${validGroups.size} Location Groups • ${validGroups.sumOf { it.totalDocuments }} Total Documents", 40f, 105f, subtitleWhitePaint)

            val sectionTitlePaint = Paint().apply {
                color = Color.rgb(30, 30, 30)
                textSize = 14f
                isFakeBoldText = true
            }
            val textPaint = Paint().apply {
                color = Color.rgb(50, 50, 50)
                textSize = 11f
            }

            coverCanvas.drawText("Location Index & Table of Contents", 40f, 170f, sectionTitlePaint)

            var tocY = 205f
            for (group in validGroups) {
                if (tocY > 780f) break
                val pinPaint = Paint().apply {
                    color = Color.rgb(199, 119, 0)
                    textSize = 12f
                    isFakeBoldText = true
                }
                coverCanvas.drawText("• ${group.displayName}", 50f, tocY, pinPaint)
                coverCanvas.drawText("${group.pataDocuments.size} Pattas  |  ${group.aadhaarDocuments.size} Aadhaars  |  ${group.matchedCount} Matched Pairs", 260f, tocY, textPaint)
                tocY += 24f
            }

            pdfDocument.finishPage(coverPage)
            pageIndex++

            // Render each location group
            for (group in validGroups) {
                // 1. Section Title Page / Divider for this Location Group
                val dividerPageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                val dividerPage = pdfDocument.startPage(dividerPageInfo)
                val dividerCanvas = dividerPage.canvas

                val ochrePaint = Paint().apply {
                    color = Color.rgb(199, 119, 0)
                    style = Paint.Style.FILL
                }
                dividerCanvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 110f, ochrePaint)
                dividerCanvas.drawText("LOCATION GROUP: ${group.locationKey.uppercase()}", 40f, 50f, titleWhitePaint)
                dividerCanvas.drawText("${group.displayName} • ${group.totalDocuments} Documents (${group.pataDocuments.size} Pattas, ${group.aadhaarDocuments.size} Aadhaars)", 40f, 75f, subtitleWhitePaint)
                dividerCanvas.drawText("${group.matchedCount} AI-Matched Pairs Corroborated in this Administrative Block", 40f, 95f, subtitleWhitePaint)

                // Location Summary in Divider
                dividerCanvas.drawText("Location Index & Content Breakdown:", 40f, 150f, sectionTitlePaint)
                var secY = 180f
                dividerCanvas.drawText("• Location Identifier: ${group.locationKey} (${group.odiaName})", 50f, secY, textPaint)
                secY += 24f
                dividerCanvas.drawText("• Patta (Land Records): ${group.pataDocuments.size} documents", 50f, secY, textPaint)
                secY += 24f
                dividerCanvas.drawText("• Aadhaar Cards: ${group.aadhaarDocuments.size} documents", 50f, secY, textPaint)
                secY += 24f
                dividerCanvas.drawText("• AI Matched Pairs Formed: ${group.matchedCount} pairs", 50f, secY, textPaint)
                secY += 34f

                if (group.matchedPairs.isNotEmpty()) {
                    dividerCanvas.drawText("Matched Pairs Overview in ${group.locationKey}:", 40f, secY, sectionTitlePaint)
                    secY += 24f
                    for (pair in group.matchedPairs) {
                        if (secY > 780f) break
                        dividerCanvas.drawText("✓ ${pair.aadhaarName}  ⟷  ${pair.landNameTransliterated} (${pair.landNameOriginal})  [${(pair.matchConfidence * 100).toInt()}%]", 50f, secY, textPaint)
                        secY += 20f
                    }
                } else {
                    dividerCanvas.drawText("No verified pairs formed yet in this location. Document images shown on following pages.", 40f, secY, textPaint)
                }

                pdfDocument.finishPage(dividerPage)
                pageIndex++

                // 2. Render Full Side-by-Side Pages for every Matched Pair in this Location
                for (pair in group.matchedPairs) {
                    val pairPageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                    val pairPage = pdfDocument.startPage(pairPageInfo)
                    val canvas = pairPage.canvas

                    // Banner
                    val bannerPaint = Paint().apply {
                        color = Color.rgb(240, 246, 240)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, A4_WIDTH_PT.toFloat(), 60f, bannerPaint)

                    val pairHeaderPaint = Paint().apply {
                        color = Color.rgb(27, 94, 32)
                        textSize = 13f
                        isFakeBoldText = true
                    }
                    canvas.drawText("${group.locationKey} • AI Matched Pair (${(pair.matchConfidence * 100).toInt()}% Confidence)", 40f, 35f, pairHeaderPaint)

                    val landDoc = docMap[pair.landDocId]
                    val aadhaarDoc = docMap[pair.aadhaarDocId]

                    val landMeta = listOf(
                        "Odia Name: ${pair.landNameOriginal}",
                        "Translit: ${pair.landNameTransliterated}",
                        "Khata: ${landDoc?.khataNumber ?: "N/A"}",
                        "Plot: ${landDoc?.plotNumber ?: "N/A"}",
                        "Tahasil: ${landDoc?.tahasil ?: group.locationKey}",
                        "Mouza: ${landDoc?.mouza ?: group.locationKey}"
                    )
                    drawDocumentBox(
                        context = context,
                        canvas = canvas,
                        doc = landDoc,
                        left = 40f,
                        top = 80f,
                        width = 245f,
                        height = 540f,
                        title = "Patta: ${pair.landNameTransliterated}",
                        subtitle = landDoc?.originalFilename ?: "Land Record",
                        metaLines = landMeta
                    )

                    val aadhaarMeta = listOf(
                        "Cardholder: ${pair.aadhaarName}",
                        "UID: ${aadhaarDoc?.aadhaarNumberMasked ?: "XXXX-XXXX-XXXX"}",
                        "Location: ${group.locationKey}",
                        "Status: ${pair.status.name}",
                        "Score: ${(pair.matchConfidence * 100).toInt()}%"
                    )
                    drawDocumentBox(
                        context = context,
                        canvas = canvas,
                        doc = aadhaarDoc,
                        left = 310f,
                        top = 80f,
                        width = 245f,
                        height = 540f,
                        title = "Aadhaar: ${pair.aadhaarName}",
                        subtitle = aadhaarDoc?.originalFilename ?: "Aadhaar Card",
                        metaLines = aadhaarMeta
                    )

                    // Bottom Explanation Box
                    val bottomBox = Paint().apply {
                        color = Color.rgb(250, 250, 250)
                        style = Paint.Style.FILL
                    }
                    val boxBorder = Paint().apply {
                        color = Color.rgb(200, 215, 200)
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    canvas.drawRect(40f, 640f, 555f, 790f, bottomBox)
                    canvas.drawRect(40f, 640f, 555f, 790f, boxBorder)

                    val noteTitlePaint = Paint().apply {
                        color = Color.rgb(27, 94, 32)
                        textSize = 10.5f
                        isFakeBoldText = true
                    }
                    canvas.drawText("AI Cross-Script Matching Details:", 55f, 665f, noteTitlePaint)
                    canvas.drawText(pair.matchExplanation.take(85), 55f, 685f, textPaint)
                    if (pair.matchExplanation.length > 85) {
                        canvas.drawText(pair.matchExplanation.substring(85).take(85), 55f, 700f, textPaint)
                    }
                    canvas.drawText("Location Corroboration: Shared administrative block (${group.locationKey})", 55f, 725f, textPaint)
                    canvas.drawText("Status: ${pair.status.name}  |  Person Code: ${pair.personCode ?: "Auto-Assigned"}", 55f, 745f, textPaint)

                    pdfDocument.finishPage(pairPage)
                    pageIndex++
                }

                // 3. Render Remaining Standalone Document Pages in this Location
                val remainingPattas = group.pataDocuments.filter { doc ->
                    group.matchedPairs.none { it.landDocId == doc.id }
                }
                val remainingAadhaars = group.aadhaarDocuments.filter { doc ->
                    group.matchedPairs.none { it.aadhaarDocId == doc.id }
                }
                val remainingDocs = remainingPattas + remainingAadhaars

                for (chunk in remainingDocs.chunked(2)) {
                    val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawText("${group.locationKey} • Location Documents & Images", 40f, 40f, sectionTitlePaint)

                    val doc1 = chunk[0]
                    val meta1 = listOf(
                        "Filename: ${doc1.originalFilename}",
                        "Type: ${doc1.docType}",
                        "Khata: ${doc1.khataNumber ?: "N/A"}",
                        "Plot: ${doc1.plotNumber ?: "N/A"}",
                        "Location: ${group.locationKey}"
                    )
                    drawDocumentBox(
                        context = context,
                        canvas = canvas,
                        doc = doc1,
                        left = 40f,
                        top = 65f,
                        width = 245f,
                        height = 540f,
                        title = doc1.docType.name,
                        subtitle = doc1.originalFilename,
                        metaLines = meta1
                    )

                    if (chunk.size > 1) {
                        val doc2 = chunk[1]
                        val meta2 = listOf(
                            "Filename: ${doc2.originalFilename}",
                            "Type: ${doc2.docType}",
                            "Khata: ${doc2.khataNumber ?: "N/A"}",
                            "Plot: ${doc2.plotNumber ?: "N/A"}",
                            "Location: ${group.locationKey}"
                        )
                        drawDocumentBox(
                            context = context,
                            canvas = canvas,
                            doc = doc2,
                            left = 310f,
                            top = 65f,
                            width = 245f,
                            height = 540f,
                            title = doc2.docType.name,
                            subtitle = doc2.originalFilename,
                            metaLines = meta2
                        )
                    }

                    pdfDocument.finishPage(page)
                    pageIndex++
                }
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
                summary = "Exported consolidated Location PDF (${pageIndex - 1} pages) across ${validGroups.size} locations: ${pdfFile.name}"
            )
        } catch (e: Exception) {
            ExportResult(
                isSuccess = false,
                shareUri = null,
                file = null,
                summary = "Failed to export all locations PDF: ${e.localizedMessage}",
                errorMessage = e.localizedMessage
            )
        }
    }

    private suspend fun drawDocumentBox(
        context: Context,
        canvas: Canvas,
        doc: DocumentRecord?,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        title: String,
        subtitle: String,
        metaLines: List<String>
    ) {
        val borderPaint = Paint().apply {
            color = Color.rgb(200, 215, 200)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val bgPaint = Paint().apply {
            color = Color.rgb(248, 250, 248)
            style = Paint.Style.FILL
        }
        canvas.drawRect(left, top, left + width, top + height, bgPaint)
        canvas.drawRect(left, top, left + width, top + height, borderPaint)

        val titlePaint = Paint().apply {
            color = Color.rgb(27, 94, 32)
            textSize = 10.5f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.rgb(50, 50, 50)
            textSize = 9f
        }

        canvas.drawText(title.take(30), left + 8f, top + 16f, titlePaint)
        if (subtitle.isNotBlank()) {
            val subPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 8.5f
            }
            canvas.drawText(subtitle.take(35), left + 8f, top + 28f, subPaint)
        }

        // Draw document image / thumbnail with high resolution
        val imgTop = top + 34f
        val imgHeight = if (height >= 450f) 250f else if (height >= 280f) 140f else 80f
        val imgWidth = width - 16f

        if (doc != null) {
            val bitmap = DocumentImageLoader.loadThumbnail(context, doc, maxWidth = 480, maxHeight = 600)
            if (bitmap != null) {
                val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                val dstRect = RectF(left + 8f, imgTop, left + 8f + imgWidth, imgTop + imgHeight)
                canvas.drawBitmap(bitmap, srcRect, dstRect, null)
            }
        }

        // Draw metadata lines below image
        var curY = imgTop + imgHeight + 14f
        for (line in metaLines) {
            if (curY < top + height - 4f) {
                canvas.drawText(line.take(45), left + 8f, curY, textPaint)
                curY += 12f
            }
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
