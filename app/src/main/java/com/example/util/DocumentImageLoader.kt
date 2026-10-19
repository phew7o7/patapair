package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.ocr.DocumentOcrEngine
import com.example.ocr.PdfPageRenderer
import com.example.ocr.PdfRenderResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentImageLoader {

    // 24MB in-memory thumbnail cache
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    suspend fun loadThumbnail(
        context: Context,
        doc: DocumentRecord,
        maxWidth: Int = 360,
        maxHeight: Int = 480
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${doc.id}_thumb_${maxWidth}x${maxHeight}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        if (doc.isSynthetic) {
            val synthetic = createSyntheticBadgeBitmap(doc)
            memoryCache.put(cacheKey, synthetic)
            return@withContext synthetic
        }

        val bitmap = try {
            val uri = Uri.parse(doc.uri)
            val isPdf = DocumentOcrEngine.isPdf(doc, context)

            if (isPdf) {
                when (val result = PdfPageRenderer.renderPage(context, doc.uri, doc.pdfPageNumber, doc.userRotation)) {
                    is PdfRenderResult.Success -> {
                        val rendered = result.bitmap
                        val scaled = scaleDownIfNeeded(rendered, maxWidth, maxHeight)
                        if (scaled !== rendered) {
                            rendered.recycle()
                        }
                        scaled
                    }
                    is PdfRenderResult.Failure -> null
                }
            } else {
                // Decode downsampled bitmap from image URI
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }

                val origW = options.outWidth
                val origH = options.outHeight
                if (origW <= 0 || origH <= 0) return@withContext null

                var sampleSize = 1
                while (origW / (sampleSize * 2) >= maxWidth && origH / (sampleSize * 2) >= maxHeight) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.RGB_565
                }

                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                } ?: return@withContext null

                val totalRot = doc.userRotation % 360
                val rotated = if (totalRot != 0) {
                    DocumentOcrEngine.applyRotation(raw, totalRot.toFloat())
                } else raw

                val scaled = scaleDownIfNeeded(rotated, maxWidth, maxHeight)
                if (scaled !== rotated && rotated !== raw) {
                    rotated.recycle()
                }
                scaled
            }
        } catch (e: Exception) {
            null
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }
        bitmap
    }

    suspend fun loadFullResolution(
        context: Context,
        doc: DocumentRecord
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (doc.isSynthetic) {
            return@withContext createSyntheticBadgeBitmap(doc, 800, 1100)
        }

        try {
            val isPdf = DocumentOcrEngine.isPdf(doc, context)
            if (isPdf) {
                when (val result = PdfPageRenderer.renderPage(context, doc.uri, doc.pdfPageNumber, doc.userRotation)) {
                    is PdfRenderResult.Success -> result.bitmap
                    is PdfRenderResult.Failure -> null
                }
            } else {
                val uri = Uri.parse(doc.uri)
                val raw = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                } ?: return@withContext null

                val totalRot = doc.userRotation % 360
                if (totalRot != 0) {
                    DocumentOcrEngine.applyRotation(raw, totalRot.toFloat())
                } else raw
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (source.width <= maxW && source.height <= maxH) return source
        val ratio = Math.min(maxW.toFloat() / source.width, maxH.toFloat() / source.height)
        val targetW = Math.max(1, (source.width * ratio).toInt())
        val targetH = Math.max(1, (source.height * ratio).toInt())
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun createSyntheticBadgeBitmap(doc: DocumentRecord, w: Int = 300, h: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val isAadhaar = doc.docType == DocType.AADHAAR
        canvas.drawColor(if (isAadhaar) Color.rgb(235, 245, 255) else Color.rgb(235, 250, 240))

        val paint = Paint().apply {
            color = if (isAadhaar) Color.rgb(37, 99, 235) else Color.rgb(30, 94, 58)
            textSize = 24f
            isAntiAlias = true
            isFakeBoldText = true
        }

        val title = if (isAadhaar) "Aadhaar Card" else "Odisha Khatiyan"
        canvas.drawText(title, 20f, 50f, paint)

        val subPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 18f
            isAntiAlias = true
        }

        canvas.drawText(doc.originalFilename, 20f, 90f, subPaint)
        if (doc.totalPdfPages > 1) {
            canvas.drawText("Page ${doc.pdfPageNumber} of ${doc.totalPdfPages}", 20f, 120f, subPaint)
        }
        if (doc.khataNumber != null) {
            canvas.drawText("Khata: ${doc.khataNumber}", 20f, 150f, subPaint)
        }
        if (doc.aadhaarNumberMasked != null) {
            canvas.drawText("UID: ${doc.aadhaarNumberMasked}", 20f, 150f, subPaint)
        }

        return bitmap
    }
}

/**
 * Visual Thumbnail Component for Document Pages (PDF or Image).
 * Allows users to inspect the authentic document image and tap to enlarge.
 */
@Composable
fun DocumentThumbnailView(
    doc: DocumentRecord?,
    modifier: Modifier = Modifier,
    label: String = "",
    onEnlargeRequested: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var bitmap by remember(doc?.id, doc?.pdfPageNumber) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(doc?.id) { mutableStateOf(true) }

    LaunchedEffect(doc?.id, doc?.pdfPageNumber) {
        if (doc == null) {
            bitmap = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        bitmap = DocumentImageLoader.loadThumbnail(context, doc)
        isLoading = false
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFF3F4F6))
            .clickable(enabled = doc != null) {
                onEnlargeRequested?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = doc?.originalFilename ?: "Document page",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Zoom icon badge overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                shape = RoundedCornerShape(4.dp),
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ZoomIn,
                        contentDescription = "Zoom",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                    if (label.isNotBlank()) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 9.sp)
                    }
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (doc != null) "Page ${doc.pdfPageNumber}" else "No Image",
                    fontSize = 10.sp,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        }
    }
}

/**
 * Fullscreen Zoomable Document Inspector Dialog.
 */
@Composable
fun FullscreenDocumentDialog(
    doc: DocumentRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var fullBitmap by remember(doc.id, doc.pdfPageNumber) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(doc.id) { mutableStateOf(true) }

    LaunchedEffect(doc.id, doc.pdfPageNumber) {
        isLoading = true
        fullBitmap = DocumentImageLoader.loadFullResolution(context, doc)
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.originalFilename,
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        val pageStr = if (doc.totalPdfPages > 1) "Page ${doc.pdfPageNumber} of ${doc.totalPdfPages} • " else ""
                        Text(
                            text = "$pageStr${if (doc.docType == DocType.AADHAAR) "Aadhaar Card" else "Land Record (Odia Khatiyan)"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.LightGray
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }

                // Image Viewer Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                    } else if (fullBitmap != null) {
                        Image(
                            bitmap = fullBitmap!!.asImageBitmap(),
                            contentDescription = doc.originalFilename,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Could not render high-resolution document image",
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }
    }
}
