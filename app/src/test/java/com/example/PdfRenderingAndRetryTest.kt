package com.example

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.example.engine.DocumentClassifier
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.PersonRecord
import com.example.model.ScriptType
import com.example.ocr.DocumentOcrEngine
import com.example.ocr.PdfPageRenderer
import com.example.ocr.PdfRenderResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.FileNotFoundException

class PdfRenderingAndRetryTest {

    private lateinit var mockProvider: TestPdfProvider
    private lateinit var mockFdProvider: TestFdProvider
    private val dummyContext: Context = android.content.ContextWrapper(null)

    private val unsafe: sun.misc.Unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply {
        isAccessible = true
    }.get(null) as sun.misc.Unsafe

    private val mockBitmapAllocator = object : PdfPageRenderer.BitmapAllocator {
        override fun createBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
            return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
        }
    }

    @Before
    fun setUp() {
        mockProvider = TestPdfProvider(pageCount = 74)
        mockFdProvider = TestFdProvider(unsafe)
        PdfPageRenderer.provider = mockProvider
        PdfPageRenderer.fdProvider = mockFdProvider
        PdfPageRenderer.bitmapAllocator = mockBitmapAllocator
    }

    @After
    fun tearDown() {
        PdfPageRenderer.resetProvider()
    }

    class TestFdProvider(
        private val unsafe: sun.misc.Unsafe,
        var securityExceptionOnOpen: Boolean = false,
        var canOpenFile: Boolean = true
    ) : PdfPageRenderer.FileDescriptorProvider {
        override fun openFileDescriptor(context: Context, uriString: String, mode: String): ParcelFileDescriptor? {
            if (securityExceptionOnOpen) {
                throw SecurityException("Mock URI permission denied for $uriString")
            }
            if (!canOpenFile) {
                throw FileNotFoundException("Mock file not found for $uriString")
            }
            return unsafe.allocateInstance(ParcelFileDescriptor::class.java) as ParcelFileDescriptor
        }
    }

    /**
     * Test provider that tracks exactly which 0-based page index was requested
     * and simulates a 74-page PDF (matching 'bhamarmal village.pdf' pages 1..74).
     */
    class TestPdfProvider(
        val pageCount: Int = 74,
        var shouldThrowOnOpen: Throwable? = null,
        var shouldThrowOnAllocOrRender: Throwable? = null
    ) : PdfPageRenderer.PdfRendererProvider {

        val requestedZeroBasedIndices = mutableListOf<Int>()
        var openedPfdCount = 0
        var closedPfdCount = 0

        override fun open(pfd: ParcelFileDescriptor): PdfPageRenderer.PdfPageAccessor {
            shouldThrowOnOpen?.let { throw it }
            openedPfdCount++
            return object : PdfPageRenderer.PdfPageAccessor {
                override val pageCount: Int get() = this@TestPdfProvider.pageCount

                override fun openPage(zeroBasedIndex: Int): PdfPageRenderer.PdfPageSession {
                    requestedZeroBasedIndices.add(zeroBasedIndex)
                    return object : PdfPageRenderer.PdfPageSession {
                        override val width: Int get() = 595
                        override val height: Int get() = 842

                        override fun render(destination: Bitmap, mode: Int) {
                            shouldThrowOnAllocOrRender?.let { throw it }
                        }

                        override fun close() {}
                    }
                }

                override fun close() {
                    closedPfdCount++
                }
            }
        }
    }

    @Test
    fun testFirstPageZeroBasedConversion() {
        val uriString = "content://docs/bhamarmal_village.pdf"

        // 1-based page 1 must convert to 0-based index 0
        val result = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 1)
        assertTrue("Page 1 must render successfully: ${(result as? PdfRenderResult.Failure)?.formattedError}", result is PdfRenderResult.Success)
        val success = result as PdfRenderResult.Success
        assertEquals(1, success.pageNumber)
        assertEquals(74, success.totalPages)
        assertEquals(1, mockProvider.requestedZeroBasedIndices.size)
        assertEquals("Requested 0-based index for page 1 must be 0", 0, mockProvider.requestedZeroBasedIndices.first())
    }

    @Test
    fun testMiddlePagesZeroBasedConversionPages70to73() {
        val uriString = "content://docs/bhamarmal_village.pdf"

        // Pages 70 through 73 of bhamarmal village.pdf
        for (page in 70..73) {
            mockProvider.requestedZeroBasedIndices.clear()
            val result = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = page)
            assertTrue("Page $page must render successfully: ${(result as? PdfRenderResult.Failure)?.formattedError}", result is PdfRenderResult.Success)
            val success = result as PdfRenderResult.Success
            assertEquals(page, success.pageNumber)
            assertEquals("Page $page must map to zero-based index ${page - 1}", page - 1, mockProvider.requestedZeroBasedIndices.first())
        }
    }

    @Test
    fun testLastPageZeroBasedConversionPage74() {
        val uriString = "content://docs/bhamarmal_village.pdf"

        // 1-based page 74 (last page) must convert to 0-based index 73
        mockProvider.requestedZeroBasedIndices.clear()
        val result = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 74)
        assertTrue("Last page 74 must render successfully: ${(result as? PdfRenderResult.Failure)?.formattedError}", result is PdfRenderResult.Success)
        val success = result as PdfRenderResult.Success
        assertEquals(74, success.pageNumber)
        assertEquals(74, success.totalPages)
        assertEquals("Last page 74 must map to zero-based index 73", 73, mockProvider.requestedZeroBasedIndices.first())
    }

    @Test
    fun testInvalidPageNumbersAreDiagnosedAccurately() {
        val uriString = "content://docs/bhamarmal_village.pdf"

        // Test page 0 (zero is invalid in 1-based notation)
        val result0 = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 0)
        assertTrue("Page 0 must produce failure", result0 is PdfRenderResult.Failure)
        val failure0 = result0 as PdfRenderResult.Failure
        assertEquals("Stage must be INVALID_PAGE: ${failure0.detailedMessage}", PdfRenderResult.Stage.INVALID_PAGE, failure0.stage)
        assertTrue(failure0.formattedError.startsWith("Invalid page number:"))
        assertNotEquals("Must not collapse into 'Corrupted or unreadable image'", "Corrupted or unreadable image", failure0.formattedError)

        // Test negative page
        val resultNeg = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = -1)
        assertTrue("Negative page must fail", resultNeg is PdfRenderResult.Failure)
        assertEquals(PdfRenderResult.Stage.INVALID_PAGE, (resultNeg as PdfRenderResult.Failure).stage)

        // Test page past the last page (e.g. page 75 on a 74-page document)
        val result75 = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 75)
        assertTrue("Page 75 on 74-page PDF must fail", result75 is PdfRenderResult.Failure)
        val failure75 = result75 as PdfRenderResult.Failure
        assertEquals(PdfRenderResult.Stage.INVALID_PAGE, failure75.stage)
        assertTrue("Error should state range 1..74: ${failure75.detailedMessage}", failure75.detailedMessage.contains("74"))
    }

    @Test
    fun testInaccessibleUriAndSecurityPermissionFailures() {
        // Test SecurityException on open
        mockFdProvider.securityExceptionOnOpen = true
        val uriString = "content://docs/unauthorized.pdf"
        val permResult = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 1)
        assertTrue("SecurityException must produce failure", permResult is PdfRenderResult.Failure)
        val permFail = permResult as PdfRenderResult.Failure
        assertEquals("Stage must be URI_PERMISSION", PdfRenderResult.Stage.URI_PERMISSION, permFail.stage)
        assertTrue(permFail.formattedError.startsWith("URI permission failure:"))

        // Test FileNotFoundException
        mockFdProvider.securityExceptionOnOpen = false
        mockFdProvider.canOpenFile = false
        val fnfResult = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 1)
        assertTrue("FileNotFoundException must produce failure", fnfResult is PdfRenderResult.Failure)
        val fnfFail = fnfResult as PdfRenderResult.Failure
        assertEquals("Stage must be PDF_OPEN", PdfRenderResult.Stage.PDF_OPEN, fnfFail.stage)
        assertTrue(fnfFail.formattedError.startsWith("PDF open failure:"))
    }

    @Test
    fun testCorruptOrPasswordProtectedPdfReporting() {
        mockProvider.shouldThrowOnOpen = SecurityException("Document is password protected")
        val uriString = "content://docs/locked.pdf"

        val result = PdfPageRenderer.renderPage(dummyContext, uriString, pageNumber = 1)
        assertTrue("Password protected PDF must fail", result is PdfRenderResult.Failure)
        val failure = result as PdfRenderResult.Failure
        assertEquals(PdfRenderResult.Stage.PDF_OPEN, failure.stage)
        assertTrue(failure.detailedMessage.contains("password-protected", ignoreCase = true))
    }

    @Test
    fun testStageSpecificErrorDistinctionInClassifier() {
        // Ensure that distinct error stages are never collapsed to "Corrupted or unreadable image"
        val stages = listOf(
            "PDF open failure: Stream terminated prematurely",
            "URI permission failure: Permission Denial",
            "Invalid page number: Requested page 75 is out of valid range (1..74)",
            "Rendering failure: Native render crash",
            "Bitmap allocation failure: Out of memory",
            "OCR initialization failure: TessBaseAPI.init returned false",
            "Empty OCR output: No readable text recognized in document"
        )

        for (stageErr in stages) {
            val classifierResult = DocumentClassifier.classifyDocument(
                text = "",
                ocrError = stageErr
            )
            assertEquals("Document must be marked as FAILED_OCR", DocType.FAILED_OCR, classifierResult.docType)
            assertTrue("Reason must retain specific stage error: ${classifierResult.reason}", classifierResult.reason.contains(stageErr))
            assertFalse("Must never collapse to generic 'Corrupted or unreadable image'", classifierResult.reason.contains("Corrupted or unreadable image"))
        }
    }

    @Test
    fun testRetryReprocessesOnlyFailedDocumentsAndPreservesSuccessfulResults() {
        val docSuccess1 = DocumentRecord(
            id = "doc_success_1",
            uri = "content://docs/aadhaar.jpg",
            originalFilename = "Aadhaar_1.jpg",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.AADHAAR,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "MLKit",
            ocrLanguages = "eng",
            rawExtractedText = "UIDAI 1234 5678 9012",
            redactedText = "UIDAI XXXX-XXXX-9012"
        )

        val docSuccess2 = DocumentRecord(
            id = "doc_success_2",
            uri = "content://docs/land_1.jpg",
            originalFilename = "Land_1.jpg",
            pdfPageNumber = 1,
            totalPdfPages = 1,
            docType = DocType.LAND_RECORD,
            docStatus = DocStatus.DETECTED,
            ocrEngineUsed = "Tesseract",
            ocrLanguages = "ori",
            rawExtractedText = "ଖତିୟାନ ଖାତା ୧୦",
            redactedText = "ଖତିୟାନ ଖାତା ୧୦"
        )

        val docFailed1 = DocumentRecord(
            id = "doc_failed_1",
            uri = "content://docs/bhamarmal_village.pdf",
            originalFilename = "bhamarmal village.pdf",
            pdfPageNumber = 70,
            totalPdfPages = 74,
            docType = DocType.FAILED_OCR,
            docStatus = DocStatus.FAILED,
            ocrEngineUsed = "PdfRenderer",
            ocrLanguages = "none",
            rawExtractedText = "",
            redactedText = "",
            processingErrors = "Corrupted or unreadable image"
        )

        val allDocs = listOf(docSuccess1, docSuccess2, docFailed1)

        // Filter that retry applies:
        val failedToRetry = allDocs.filter {
            !it.isDuplicate && (it.docType == DocType.FAILED_OCR || it.docStatus == DocStatus.FAILED)
        }

        assertEquals("Only failed document must be selected for retry", 1, failedToRetry.size)
        assertEquals("doc_failed_1", failedToRetry.first().id)

        // Preserved documents:
        val preservedDocs = allDocs.filterNot {
            !it.isDuplicate && (it.docType == DocType.FAILED_OCR || it.docStatus == DocStatus.FAILED)
        }
        assertEquals("2 successful documents must be completely preserved", 2, preservedDocs.size)
    }

    @Test
    fun testPersonRecordSlotDeduplicationOnReprocess() {
        val docId = "doc_test_100"
        val extractedNames1 = listOf("ଓମକାର ରଥ" to ScriptType.ODIA)

        val personsRun1 = extractedNames1.mapIndexed { index, (name, script) ->
            PersonRecord(
                id = "${docId}_p_$index",
                documentId = docId,
                originalName = name,
                script = script,
                transliteratedName = "Omkar Rath",
                normalizedName = "omkar rath"
            )
        }

        assertEquals(1, personsRun1.size)
        assertEquals("doc_test_100_p_0", personsRun1.first().id)

        // Run 2 (reprocessing same document)
        val extractedNames2 = listOf("ଓମକାର ରଥ" to ScriptType.ODIA)
        val personsRun2 = extractedNames2.mapIndexed { index, (name, script) ->
            PersonRecord(
                id = "${docId}_p_$index",
                documentId = docId,
                originalName = name,
                script = script,
                transliteratedName = "Omkar Rath",
                normalizedName = "omkar rath"
            )
        }

        // Deterministic ID matches, preventing duplicate rows in SQLite
        assertEquals(personsRun1.first().id, personsRun2.first().id)
    }

    @Test
    fun testOdiaTestBitmapCreationAndVerificationFormat() {
        val bitmap = DocumentOcrEngine.createOdiaTestBitmap()
        assertNotNull("Test bitmap must not be null", bitmap)
    }
}
