package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException
import java.io.IOException

sealed class PdfRenderResult {
    data class Success(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val pageNumber: Int,
        val totalPages: Int,
        val scaleUsed: Float
    ) : PdfRenderResult()

    data class Failure(
        val stage: Stage,
        val detailedMessage: String,
        val pageNumber: Int? = null,
        val totalPages: Int? = null,
        val cause: Throwable? = null
    ) : PdfRenderResult() {
        val formattedError: String
            get() = "${stage.description}: $detailedMessage"
    }

    enum class Stage(val description: String) {
        URI_PERMISSION("URI permission failure"),
        PDF_OPEN("PDF open failure"),
        INVALID_PAGE("Invalid page number"),
        BITMAP_ALLOCATION("Bitmap allocation failure"),
        RENDERING("Rendering failure")
    }
}

/**
 * Handles multi-page PDF rendering to Bitmaps with stage-specific error diagnostics,
 * zero-based page index conversion, memory pressure handling, and proper resource disposal.
 */
object PdfPageRenderer {

    // Pluggable provider for unit tests and headless environments
    interface PdfRendererProvider {
        fun open(pfd: ParcelFileDescriptor): PdfPageAccessor
    }

    interface PdfPageAccessor : AutoCloseable {
        val pageCount: Int
        fun openPage(zeroBasedIndex: Int): PdfPageSession
    }

    interface PdfPageSession : AutoCloseable {
        val width: Int
        val height: Int
        fun render(destination: Bitmap, mode: Int)
    }

    private val defaultPlatformProvider = object : PdfRendererProvider {
        override fun open(pfd: ParcelFileDescriptor): PdfPageAccessor {
            val renderer = PdfRenderer(pfd)
            return object : PdfPageAccessor {
                override val pageCount: Int get() = renderer.pageCount
                override fun openPage(zeroBasedIndex: Int): PdfPageSession {
                    val page = renderer.openPage(zeroBasedIndex)
                    return object : PdfPageSession {
                        override val width: Int get() = page.width
                        override val height: Int get() = page.height
                        override fun render(destination: Bitmap, mode: Int) {
                            page.render(destination, null, null, mode)
                        }
                        override fun close() = page.close()
                    }
                }
                override fun close() = renderer.close()
            }
        }
    }

    var provider: PdfRendererProvider = defaultPlatformProvider

    interface FileDescriptorProvider {
        fun openFileDescriptor(context: Context, uriString: String, mode: String): ParcelFileDescriptor?
    }

    private val defaultFdProvider = object : FileDescriptorProvider {
        override fun openFileDescriptor(context: Context, uriString: String, mode: String): ParcelFileDescriptor? {
            val uri = Uri.parse(uriString)
            return context.contentResolver.openFileDescriptor(uri, mode)
        }
    }

    var fdProvider: FileDescriptorProvider = defaultFdProvider

    interface BitmapAllocator {
        fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap
    }

    private val defaultBitmapAllocator = object : BitmapAllocator {
        override fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
            return Bitmap.createBitmap(width, height, config)
        }
    }

    var bitmapAllocator: BitmapAllocator = defaultBitmapAllocator

    fun resetProvider() {
        provider = defaultPlatformProvider
        fdProvider = defaultFdProvider
        bitmapAllocator = defaultBitmapAllocator
    }

    fun renderPage(
        context: Context,
        uri: Uri,
        pageNumber: Int,
        userRotationDegrees: Int = 0
    ): PdfRenderResult = renderPage(context, uri.toString(), pageNumber, userRotationDegrees)

    /**
     * Renders a specific 1-based page from a PDF URI into an ARGB_8888 Bitmap.
     *
     * @param context Application context
     * @param uriString URI string to the PDF file
     * @param pageNumber 1-based page number (1 to totalPages)
     * @param userRotationDegrees Additional user-requested rotation in degrees
     */
    fun renderPage(
        context: Context,
        uriString: String,
        pageNumber: Int,
        userRotationDegrees: Int = 0
    ): PdfRenderResult {
        // Stage 1: File descriptor access & URI permission check
        val pfd = try {
            fdProvider.openFileDescriptor(context, uriString, "r")
        } catch (e: SecurityException) {
            return PdfRenderResult.Failure(
                stage = PdfRenderResult.Stage.URI_PERMISSION,
                detailedMessage = "Permission denied opening URI '$uriString': ${e.localizedMessage}",
                pageNumber = pageNumber,
                cause = e
            )
        } catch (e: FileNotFoundException) {
            return PdfRenderResult.Failure(
                stage = PdfRenderResult.Stage.PDF_OPEN,
                detailedMessage = "File not found for URI '$uriString': ${e.localizedMessage}",
                pageNumber = pageNumber,
                cause = e
            )
        } catch (e: Exception) {
            return PdfRenderResult.Failure(
                stage = PdfRenderResult.Stage.PDF_OPEN,
                detailedMessage = "Failed to open file descriptor for '$uriString': ${e.localizedMessage}",
                pageNumber = pageNumber,
                cause = e
            )
        }

        if (pfd == null) {
            return PdfRenderResult.Failure(
                stage = PdfRenderResult.Stage.PDF_OPEN,
                detailedMessage = "Null file descriptor returned for URI '$uriString'",
                pageNumber = pageNumber
            )
        }

        var accessor: PdfPageAccessor? = null
        var pageSession: PdfPageSession? = null

        try {
            // Stage 2: Initialize PdfRenderer
            accessor = try {
                provider.open(pfd)
            } catch (e: SecurityException) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.PDF_OPEN,
                    detailedMessage = "PDF is password-protected or restricted: ${e.localizedMessage}",
                    pageNumber = pageNumber,
                    cause = e
                )
            } catch (e: Exception) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.PDF_OPEN,
                    detailedMessage = "Failed to initialize PdfRenderer: ${e.localizedMessage}",
                    pageNumber = pageNumber,
                    cause = e
                )
            }

            val totalPages = accessor.pageCount
            if (totalPages <= 0) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.PDF_OPEN,
                    detailedMessage = "PDF contains zero pages",
                    pageNumber = pageNumber,
                    totalPages = 0
                )
            }

            // Stage 3: Zero-based page index validation
            // Android PdfRenderer uses zero-based indices (0 until totalPages).
            // Input pageNumber is 1-based (1..totalPages).
            val zeroBasedIndex = pageNumber - 1
            if (pageNumber < 1 || zeroBasedIndex >= totalPages) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.INVALID_PAGE,
                    detailedMessage = "Requested page $pageNumber is out of valid range (1..$totalPages)",
                    pageNumber = pageNumber,
                    totalPages = totalPages
                )
            }

            // Stage 4: Open requested page session
            pageSession = try {
                accessor.openPage(zeroBasedIndex)
            } catch (e: Exception) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.RENDERING,
                    detailedMessage = "Failed to open PDF page $pageNumber: ${e.localizedMessage}",
                    pageNumber = pageNumber,
                    totalPages = totalPages,
                    cause = e
                )
            }

            val ptWidth = pageSession.width
            val ptHeight = pageSession.height
            if (ptWidth <= 0 || ptHeight <= 0) {
                return PdfRenderResult.Failure(
                    stage = PdfRenderResult.Stage.RENDERING,
                    detailedMessage = "Invalid PDF page dimensions (${ptWidth}x${ptHeight} pt)",
                    pageNumber = pageNumber,
                    totalPages = totalPages
                )
            }

            // Stage 5: Resolution selection and graceful memory pressure handling
            // 72 pt = 1 inch. Scale 2.0 = 144 DPI (ideal for OCR without memory bloat).
            // For standard A4 (595x842 pt), scale 2.0 produces ~1190x1684 px (~8MB ARGB_8888).
            val maxPt = maxOf(ptWidth, ptHeight)
            val baseScale = when {
                maxPt <= 850 -> 2.0f
                maxPt <= 1200 -> 1.75f
                maxPt <= 1600 -> 1.5f
                maxPt <= 2400 -> 1.0f
                else -> (2400f / maxPt).coerceAtLeast(0.5f)
            }

            // Candidate resolution scales from highest to lowest if memory pressure occurs
            val candidateScales = listOf(baseScale, 1.5f, 1.0f, 0.75f, 0.5f)
                .distinct()
                .filter { it <= baseScale }

            var renderedBitmap: Bitmap? = null
            var chosenScale = baseScale
            var lastError: Throwable? = null

            for (scale in candidateScales) {
                val targetW = (ptWidth * scale).toInt().coerceAtLeast(1)
                val targetH = (ptHeight * scale).toInt().coerceAtLeast(1)

                try {
                    val bitmap = bitmapAllocator.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                    // Composite over opaque white background so transparent text layers are legible
                    try {
                        bitmap.eraseColor(Color.WHITE)
                    } catch (ignored: Throwable) {}
                    pageSession.render(bitmap, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    renderedBitmap = bitmap
                    chosenScale = scale
                    break
                } catch (oom: OutOfMemoryError) {
                    lastError = oom
                    System.gc()
                } catch (e: Exception) {
                    lastError = e
                    break
                }
            }

            if (renderedBitmap == null) {
                val isOom = lastError is OutOfMemoryError
                return PdfRenderResult.Failure(
                    stage = if (isOom) PdfRenderResult.Stage.BITMAP_ALLOCATION else PdfRenderResult.Stage.RENDERING,
                    detailedMessage = if (isOom) {
                        "Out of memory allocating bitmap for page $pageNumber (${ptWidth}x${ptHeight} pt)"
                    } else {
                        "Rendering failed on page $pageNumber: ${lastError?.localizedMessage ?: "Unknown error"}"
                    },
                    pageNumber = pageNumber,
                    totalPages = totalPages,
                    cause = lastError
                )
            }

            // Apply optional user rotation if specified
            val finalBitmap = if (userRotationDegrees % 360 != 0) {
                val rotated = DocumentOcrEngine.applyRotation(renderedBitmap, userRotationDegrees.toFloat())
                if (rotated !== renderedBitmap) {
                    renderedBitmap.recycle()
                }
                rotated
            } else {
                renderedBitmap
            }

            val width = try { finalBitmap.width } catch (e: Throwable) { (ptWidth * chosenScale).toInt() }
            val height = try { finalBitmap.height } catch (e: Throwable) { (ptHeight * chosenScale).toInt() }

            return PdfRenderResult.Success(
                bitmap = finalBitmap,
                width = width,
                height = height,
                pageNumber = pageNumber,
                totalPages = totalPages,
                scaleUsed = chosenScale
            )

        } finally {
            try { pageSession?.close() } catch (ignored: Throwable) {}
            try { accessor?.close() } catch (ignored: Throwable) {}
            try { pfd.close() } catch (ignored: Throwable) {}
        }
    }
}
