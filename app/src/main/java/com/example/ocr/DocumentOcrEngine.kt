package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.model.ImportModelResult
import com.example.model.OcrModelStatus
import com.example.model.OcrResult
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

object DocumentOcrEngine {

    const val ENGINE_MLKIT = "Google ML Kit (Latin)"
    const val ENGINE_TESSERACT = "Tesseract 4 (Odia + English)"
    const val ENGINE_SYNTHETIC = "Synthetic Test Document Engine"

    private const val MIN_MODEL_SIZE_BYTES = 1_000_000L // 1 MB minimum for traineddata

    fun getTessdataDir(context: Context): File {
        val dir = File(context.filesDir, "tesseract/tessdata")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelFile(context: Context, modelName: String): File {
        return File(getTessdataDir(context), modelName)
    }

    fun isOdiaModelInstalled(context: Context): Boolean {
        val file = getModelFile(context, "ori.traineddata")
        if (file.exists() && file.length() >= MIN_MODEL_SIZE_BYTES) {
            return true
        }
        // Optional fallback: check if assets happen to contain it
        return tryCopyFromAssetsFallback(context, "ori.traineddata")
    }

    fun isEnglishModelInstalled(context: Context): Boolean {
        val file = getModelFile(context, "eng.traineddata")
        if (file.exists() && file.length() >= MIN_MODEL_SIZE_BYTES) {
            return true
        }
        return tryCopyFromAssetsFallback(context, "eng.traineddata")
    }

    private fun tryCopyFromAssetsFallback(context: Context, modelName: String): Boolean {
        return try {
            val assetsList = context.assets.list("tessdata") ?: emptyArray()
            if (assetsList.contains(modelName)) {
                val target = getModelFile(context, modelName)
                context.assets.open("tessdata/$modelName").use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                target.exists() && target.length() >= MIN_MODEL_SIZE_BYTES
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getModelStatus(context: Context): OcrModelStatus {
        val tessDir = getTessdataDir(context)
        val odiaFile = File(tessDir, "ori.traineddata")
        val engFile = File(tessDir, "eng.traineddata")

        val odiaInstalled = odiaFile.exists() && odiaFile.length() >= MIN_MODEL_SIZE_BYTES
        val engInstalled = engFile.exists() && engFile.length() >= MIN_MODEL_SIZE_BYTES

        return OcrModelStatus(
            isOdiaInstalled = odiaInstalled,
            odiaSizeMb = if (odiaInstalled) odiaFile.length() / (1024f * 1024f) else 0f,
            isEnglishInstalled = engInstalled,
            englishSizeMb = if (engInstalled) engFile.length() / (1024f * 1024f) else 0f
        )
    }

    fun importModelFiles(context: Context, uris: List<Uri>): ImportModelResult {
        var importedCount = 0
        val messages = mutableListOf<String>()
        val tessDir = getTessdataDir(context)

        for (uri in uris) {
            val originalFilename = queryFilename(context, uri)
            val lower = originalFilename.lowercase()

            val isOri = lower == "ori.traineddata" || lower.endsWith("/ori.traineddata") || lower.contains("ori.traineddata")
            val isEng = lower == "eng.traineddata" || lower.endsWith("/eng.traineddata") || lower.contains("eng.traineddata")

            if (!isOri && !isEng) {
                messages.add("Ignored '$originalFilename': Only 'ori.traineddata' or 'eng.traineddata' are accepted.")
                continue
            }

            val targetFilename = if (isOri) "ori.traineddata" else "eng.traineddata"
            val targetFile = File(tessDir, targetFilename)
            val tempFile = File(context.cacheDir, "import_tmp_$targetFilename")

            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val size = tempFile.length()
                if (size < MIN_MODEL_SIZE_BYTES) {
                    val sizeKb = size / 1024
                    messages.add("Rejected '$targetFilename': File size is only ${sizeKb} KB (minimum 1 MB required).")
                    tempFile.delete()
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    val sizeMb = "%.2f".format(targetFile.length() / (1024f * 1024f))
                    messages.add("Successfully installed '$targetFilename' ($sizeMb MB).")
                    importedCount++
                }
            } catch (e: Exception) {
                messages.add("Failed to copy '$targetFilename': ${e.localizedMessage}")
            }
        }

        return ImportModelResult(
            isSuccess = importedCount > 0,
            messages = messages
        )
    }

    private fun queryFilename(context: Context, uri: Uri): String {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToNext()) {
                    name = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "unknown"
    }

    suspend fun processImageUri(
        context: Context,
        imageUri: Uri,
        userRotationDegrees: Int = 0,
        syntheticText: String? = null
    ): OcrResult = withContext(Dispatchers.IO) {
        try {
            // 1. Synthetic test document bypass
            if (syntheticText != null) {
                return@withContext OcrResult(
                    fullText = syntheticText,
                    engineName = ENGINE_SYNTHETIC,
                    languageUsed = "ori+eng",
                    confidence = 0.95f,
                    error = null,
                    isSuccessful = true
                )
            }

            // 2. Decode Bitmap and apply EXIF orientation
            val inputStream: InputStream? = try {
                context.contentResolver.openInputStream(imageUri)
            } catch (e: Exception) {
                null
            }

            if (inputStream == null) {
                return@withContext OcrResult(
                    fullText = "",
                    engineName = ENGINE_MLKIT,
                    languageUsed = "none",
                    confidence = 0f,
                    error = "Failed to open image URI: $imageUri",
                    isSuccessful = false
                )
            }

            val rawBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (rawBitmap == null || rawBitmap.width <= 0 || rawBitmap.height <= 0) {
                return@withContext OcrResult(
                    fullText = "",
                    engineName = ENGINE_MLKIT,
                    languageUsed = "none",
                    confidence = 0f,
                    error = "Corrupted or unreadable image",
                    isSuccessful = false
                )
            }

            // EXIF orientation
            val exifOrientation = try {
                val exifStream = context.contentResolver.openInputStream(imageUri)
                if (exifStream != null) {
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    exifStream.close()
                    orientation
                } else ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val totalRotation = (getExifRotationDegrees(exifOrientation) + userRotationDegrees) % 360
            val bitmap = if (totalRotation != 0) applyRotation(rawBitmap, totalRotation.toFloat()) else rawBitmap

            // 3. Run Google ML Kit (Latin) First
            // Aadhaar and English documents must not depend on Tesseract!
            val mlKitResultText = runMlKitLatin(bitmap)
            val hasAadhaarSignal = isAadhaarContent(mlKitResultText)

            if (hasAadhaarSignal) {
                // Aadhaar detected! Finish cleanly with ML Kit without requiring Tesseract
                return@withContext OcrResult(
                    fullText = mlKitResultText,
                    engineName = ENGINE_MLKIT,
                    languageUsed = "Latin / English",
                    confidence = 0.95f,
                    error = null,
                    isSuccessful = true,
                    isWaitingForModel = false
                )
            }

            // 4. English documents must not depend on Tesseract:
            // Check if ML Kit found English land keywords or significant English text
            val isEnglish = isEnglishDocument(mlKitResultText)
            if (isEnglish) {
                return@withContext OcrResult(
                    fullText = mlKitResultText,
                    engineName = ENGINE_MLKIT,
                    languageUsed = "English",
                    confidence = 0.90f,
                    error = null,
                    isSuccessful = true,
                    isWaitingForModel = false
                )
            }

            // 5. Check if Tesseract Odia Model (ori.traineddata) is installed
            val isOdiaInstalled = isOdiaModelInstalled(context)

            if (!isOdiaInstalled) {
                // Odia land records stay "Waiting for OCR model" when model is missing.
                // Do NOT mark as FAILED_OCR! Keep document PENDING.
                return@withContext OcrResult(
                    fullText = mlKitResultText, // pass any partial latin text found
                    engineName = "Tesseract (ori.traineddata missing)",
                    languageUsed = "none",
                    confidence = 0f,
                    error = null,
                    isSuccessful = true,
                    isWaitingForModel = true
                )
            }

            // 6. Run Tesseract with Odia (+ English if installed)
            val tesseractText = runTesseractOdia(context, bitmap)
            val combinedText = if (mlKitResultText.isNotBlank() && !tesseractText.contains(mlKitResultText.take(20))) {
                "$tesseractText\n$mlKitResultText"
            } else {
                tesseractText
            }

            OcrResult(
                fullText = combinedText,
                engineName = ENGINE_TESSERACT,
                languageUsed = if (isEnglishModelInstalled(context)) "ori+eng" else "ori",
                confidence = 0.85f,
                error = null,
                isSuccessful = true,
                isWaitingForModel = false
            )
        } catch (e: Exception) {
            OcrResult(
                fullText = "",
                engineName = ENGINE_MLKIT,
                languageUsed = "none",
                confidence = 0f,
                error = e.localizedMessage ?: "OCR processing error",
                isSuccessful = false,
                isWaitingForModel = false
            )
        }
    }

    private fun runMlKitLatin(bitmap: Bitmap): String {
        return try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            val task = recognizer.process(image)
            val result = Tasks.await(task, 15, TimeUnit.SECONDS)
            result.text ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun runTesseractOdia(context: Context, bitmap: Bitmap): String {
        var tessBaseAPI: TessBaseAPI? = null
        return try {
            tessBaseAPI = TessBaseAPI()
            val parentDir = File(context.filesDir, "tesseract")
            val lang = if (isEnglishModelInstalled(context)) "ori+eng" else "ori"
            val success = tessBaseAPI.init(parentDir.absolutePath, lang)
            if (!success) {
                return ""
            }
            tessBaseAPI.setImage(bitmap)
            tessBaseAPI.utF8Text ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            try {
                tessBaseAPI?.recycle()
            } catch (e: Exception) {
                // Ignored
            }
        }
    }

    private fun isAadhaarContent(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val hasUidai = lower.contains("uidai") || lower.contains("unique identification") ||
                       lower.contains("government of india") || lower.contains("aadhaar") ||
                       lower.contains("enrollment no") || lower.contains("help@uidai")
        val hasAadhaarNumber = "\\b\\d{4}[\\s-]\\d{4}[\\s-]\\d{4}\\b".toRegex().containsMatchIn(text) ||
                               "\\b[Xx]{4}[\\s-][Xx]{4}[\\s-]\\d{4}\\b".toRegex().containsMatchIn(text)
        return hasUidai || hasAadhaarNumber
    }

    private fun isEnglishDocument(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        val englishLandKeywords = listOf(
            "record of rights", "khatiyan", "khata", "plot", "tahasil",
            "mouza", "rayat", "pattadar", "bhulekh", "odisha", "district",
            "kisam", "rakba", "government of odisha", "revenue", "department",
            "settlement", "tenant", "area", "acres", "decimals", "chaka"
        )
        val matchCount = englishLandKeywords.count { lower.contains(it) }
        if (matchCount >= 2 || lower.contains("record of rights")) {
            return true
        }

        // Substantial recognizable English words (at least 6 distinct words with 3+ alphabetic chars)
        val words = text.split(Regex("[^a-zA-Z]+")).filter { it.length >= 3 }
        return words.size >= 8
    }

    fun applyRotation(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun getExifRotationDegrees(orientation: Int): Int {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    fun redactSensitiveIdentifiers(text: String): String {
        val aadhaarRegex = "\\b(\\d{4})[\\s-](\\d{4})[\\s-](\\d{4})\\b".toRegex()
        return aadhaarRegex.replace(text) { match ->
            val last4 = match.groupValues[3]
            "XXXX-XXXX-$last4"
        }
    }
}
