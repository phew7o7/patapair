package com.example.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.DocStatus
import com.example.model.DocType
import com.example.model.DocumentRecord
import com.example.model.MatchPairRecord
import com.example.model.MatchStatus
import com.example.model.PersonRecord
import com.example.model.ScriptType

class PataDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "patapair_offline.db"
        const val DATABASE_VERSION = 2

        const val TABLE_DOCUMENTS = "documents"
        const val TABLE_PERSONS = "persons"
        const val TABLE_MATCH_PAIRS = "match_pairs"

        @Volatile
        private var instance: PataDatabaseHelper? = null

        fun getInstance(context: Context): PataDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: PataDatabaseHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DOCUMENTS (
                id TEXT PRIMARY KEY,
                uri TEXT NOT NULL,
                original_filename TEXT NOT NULL,
                pdf_page_number INTEGER DEFAULT 1,
                total_pdf_pages INTEGER DEFAULT 1,
                doc_type TEXT NOT NULL,
                doc_status TEXT NOT NULL,
                ocr_engine_used TEXT,
                ocr_languages TEXT,
                raw_extracted_text TEXT,
                redacted_text TEXT,
                image_width INTEGER DEFAULT 0,
                image_height INTEGER DEFAULT 0,
                image_orientation INTEGER DEFAULT 0,
                user_rotation INTEGER DEFAULT 0,
                classification_score REAL DEFAULT 0.0,
                classification_reason TEXT,
                rules_fired_json TEXT,
                processing_errors TEXT,
                is_manually_corrected INTEGER DEFAULT 0,
                is_duplicate INTEGER DEFAULT 0,
                is_synthetic INTEGER DEFAULT 0,
                sha256 TEXT,
                khata_number TEXT,
                plot_number TEXT,
                tahasil TEXT,
                mouza TEXT,
                district TEXT,
                aadhaar_number_masked TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PERSONS (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                original_name TEXT NOT NULL,
                script TEXT NOT NULL,
                transliterated_name TEXT NOT NULL,
                normalized_name TEXT NOT NULL,
                confidence REAL DEFAULT 0.8,
                is_manually_corrected INTEGER DEFAULT 0,
                role TEXT,
                fathers_or_spouse_name TEXT,
                share_details TEXT,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY (document_id) REFERENCES $TABLE_DOCUMENTS (id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_MATCH_PAIRS (
                id TEXT PRIMARY KEY,
                aadhaar_doc_id TEXT NOT NULL,
                land_doc_id TEXT NOT NULL,
                aadhaar_person_id TEXT NOT NULL,
                land_person_id TEXT NOT NULL,
                aadhaar_name TEXT NOT NULL,
                land_name_original TEXT NOT NULL,
                land_name_transliterated TEXT NOT NULL,
                match_confidence REAL NOT NULL,
                match_explanation TEXT,
                status TEXT NOT NULL,
                is_manual INTEGER DEFAULT 0,
                person_code TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_doc_type ON $TABLE_DOCUMENTS(doc_type)")
        db.execSQL("CREATE INDEX idx_doc_sha256 ON $TABLE_DOCUMENTS(sha256)")
        db.execSQL("CREATE INDEX idx_persons_doc ON $TABLE_PERSONS(document_id)")
        db.execSQL("CREATE INDEX idx_pairs_status ON $TABLE_MATCH_PAIRS(status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MATCH_PAIRS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PERSONS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_DOCUMENTS")
        onCreate(db)
    }

    // --- Document Operations ---

    fun upsertDocument(doc: DocumentRecord) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", doc.id)
            put("uri", doc.uri)
            put("original_filename", doc.originalFilename)
            put("pdf_page_number", doc.pdfPageNumber)
            put("total_pdf_pages", doc.totalPdfPages)
            put("doc_type", doc.docType.name)
            put("doc_status", doc.docStatus.name)
            put("ocr_engine_used", doc.ocrEngineUsed)
            put("ocr_languages", doc.ocrLanguages)
            put("raw_extracted_text", doc.rawExtractedText)
            put("redacted_text", doc.redactedText)
            put("image_width", doc.imageWidth)
            put("image_height", doc.imageHeight)
            put("image_orientation", doc.imageOrientation)
            put("user_rotation", doc.userRotation)
            put("classification_score", doc.classificationScore)
            put("classification_reason", doc.classificationReason)
            put("rules_fired_json", doc.rulesFiredJson)
            put("processing_errors", doc.processingErrors)
            put("is_manually_corrected", if (doc.isManuallyCorrected) 1 else 0)
            put("is_duplicate", if (doc.isDuplicate) 1 else 0)
            put("is_synthetic", if (doc.isSynthetic) 1 else 0)
            put("sha256", doc.sha256)
            put("khata_number", doc.khataNumber)
            put("plot_number", doc.plotNumber)
            put("tahasil", doc.tahasil)
            put("mouza", doc.mouza)
            put("district", doc.district)
            put("aadhaar_number_masked", doc.aadhaarNumberMasked)
            put("timestamp", doc.timestamp)
        }
        db.insertWithOnConflict(TABLE_DOCUMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun upsertDocuments(docs: List<DocumentRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (doc in docs) {
                val values = ContentValues().apply {
                    put("id", doc.id)
                    put("uri", doc.uri)
                    put("original_filename", doc.originalFilename)
                    put("pdf_page_number", doc.pdfPageNumber)
                    put("total_pdf_pages", doc.totalPdfPages)
                    put("doc_type", doc.docType.name)
                    put("doc_status", doc.docStatus.name)
                    put("ocr_engine_used", doc.ocrEngineUsed)
                    put("ocr_languages", doc.ocrLanguages)
                    put("raw_extracted_text", doc.rawExtractedText)
                    put("redacted_text", doc.redactedText)
                    put("image_width", doc.imageWidth)
                    put("image_height", doc.imageHeight)
                    put("image_orientation", doc.imageOrientation)
                    put("user_rotation", doc.userRotation)
                    put("classification_score", doc.classificationScore)
                    put("classification_reason", doc.classificationReason)
                    put("rules_fired_json", doc.rulesFiredJson)
                    put("processing_errors", doc.processingErrors)
                    put("is_manually_corrected", if (doc.isManuallyCorrected) 1 else 0)
                    put("is_duplicate", if (doc.isDuplicate) 1 else 0)
                    put("is_synthetic", if (doc.isSynthetic) 1 else 0)
                    put("sha256", doc.sha256)
                    put("khata_number", doc.khataNumber)
                    put("plot_number", doc.plotNumber)
                    put("tahasil", doc.tahasil)
                    put("mouza", doc.mouza)
                    put("district", doc.district)
                    put("aadhaar_number_masked", doc.aadhaarNumberMasked)
                    put("timestamp", doc.timestamp)
                }
                db.insertWithOnConflict(TABLE_DOCUMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllDocuments(): List<DocumentRecord> {
        val list = mutableListOf<DocumentRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_DOCUMENTS, null, null, null, null, null, "timestamp DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToDocument(it))
            }
        }
        return list
    }

    fun getDocumentById(id: String): DocumentRecord? {
        val db = readableDatabase
        val cursor = db.query(TABLE_DOCUMENTS, null, "id = ?", arrayOf(id), null, null, null)
        cursor.use {
            if (it.moveToNext()) {
                return cursorToDocument(it)
            }
        }
        return null
    }

    fun findDocumentBySha256(sha256: String): DocumentRecord? {
        val db = readableDatabase
        val cursor = db.query(TABLE_DOCUMENTS, null, "sha256 = ? AND is_duplicate = 0", arrayOf(sha256), null, null, null)
        cursor.use {
            if (it.moveToNext()) {
                return cursorToDocument(it)
            }
        }
        return null
    }

    fun updateDocumentManualClassification(id: String, newType: DocType) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("doc_type", newType.name)
            put("is_manually_corrected", 1)
            put("classification_reason", "Manually classified as ${newType.name}")
        }
        db.update(TABLE_DOCUMENTS, values, "id = ?", arrayOf(id))
    }

    fun updateDocumentRotation(id: String, rotationDegrees: Int) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("user_rotation", rotationDegrees)
        }
        db.update(TABLE_DOCUMENTS, values, "id = ?", arrayOf(id))
    }

    fun deleteDocument(id: String) {
        val db = writableDatabase
        db.delete(TABLE_DOCUMENTS, "id = ?", arrayOf(id))
        db.delete(TABLE_PERSONS, "document_id = ?", arrayOf(id))
        db.delete(TABLE_MATCH_PAIRS, "aadhaar_doc_id = ? OR land_doc_id = ?", arrayOf(id, id))
    }

    // --- Person Operations ---

    fun upsertPersons(persons: List<PersonRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (p in persons) {
                // If manually corrected before, preserve manual correction
                val existingCursor = db.query(TABLE_PERSONS, arrayOf("is_manually_corrected", "original_name", "transliterated_name", "normalized_name"), "id = ?", arrayOf(p.id), null, null, null)
                var wasManuallyCorrected = false
                var preservedOriginal = p.originalName
                var preservedTranslit = p.transliteratedName
                var preservedNormalized = p.normalizedName

                existingCursor.use {
                    if (it.moveToNext()) {
                        wasManuallyCorrected = it.getInt(0) == 1
                        if (wasManuallyCorrected) {
                            preservedOriginal = it.getString(1)
                            preservedTranslit = it.getString(2)
                            preservedNormalized = it.getString(3)
                        }
                    }
                }

                val values = ContentValues().apply {
                    put("id", p.id)
                    put("document_id", p.documentId)
                    put("original_name", if (wasManuallyCorrected) preservedOriginal else p.originalName)
                    put("script", p.script.name)
                    put("transliterated_name", if (wasManuallyCorrected) preservedTranslit else p.transliteratedName)
                    put("normalized_name", if (wasManuallyCorrected) preservedNormalized else p.normalizedName)
                    put("confidence", p.confidence)
                    put("is_manually_corrected", if (wasManuallyCorrected || p.isManuallyCorrected) 1 else 0)
                    put("role", p.role)
                    put("fathers_or_spouse_name", p.fathersOrSpouseName)
                    put("share_details", p.shareDetails)
                    put("timestamp", p.timestamp)
                }
                db.insertWithOnConflict(TABLE_PERSONS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deletePersonsByDocumentId(docId: String) {
        val db = writableDatabase
        db.delete(TABLE_PERSONS, "document_id = ?", arrayOf(docId))
    }

    fun getPersonsByDocumentId(docId: String): List<PersonRecord> {
        val list = mutableListOf<PersonRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PERSONS, null, "document_id = ?", arrayOf(docId), null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPerson(it))
            }
        }
        return list
    }

    fun getAllPersons(): List<PersonRecord> {
        val list = mutableListOf<PersonRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PERSONS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToPerson(it))
            }
        }
        return list
    }

    fun updatePersonManualName(personId: String, newName: String, transliterated: String, normalized: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("original_name", newName)
            put("transliterated_name", transliterated)
            put("normalized_name", normalized)
            put("is_manually_corrected", 1)
        }
        db.update(TABLE_PERSONS, values, "id = ?", arrayOf(personId))
    }

    // --- Match Pair Operations ---

    fun upsertMatchPairs(pairs: List<MatchPairRecord>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (pair in pairs) {
                val values = ContentValues().apply {
                    put("id", pair.id)
                    put("aadhaar_doc_id", pair.aadhaarDocId)
                    put("land_doc_id", pair.landDocId)
                    put("aadhaar_person_id", pair.aadhaarPersonId)
                    put("land_person_id", pair.landPersonId)
                    put("aadhaar_name", pair.aadhaarName)
                    put("land_name_original", pair.landNameOriginal)
                    put("land_name_transliterated", pair.landNameTransliterated)
                    put("match_confidence", pair.matchConfidence)
                    put("match_explanation", pair.matchExplanation)
                    put("status", pair.status.name)
                    put("is_manual", if (pair.isManual) 1 else 0)
                    put("person_code", pair.personCode)
                    put("timestamp", pair.timestamp)
                }
                db.insertWithOnConflict(TABLE_MATCH_PAIRS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllMatchPairs(): List<MatchPairRecord> {
        val list = mutableListOf<MatchPairRecord>()
        val db = readableDatabase
        val cursor = db.query(TABLE_MATCH_PAIRS, null, null, null, null, null, "match_confidence DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(cursorToMatchPair(it))
            }
        }
        return list
    }

    fun updateMatchPairStatus(pairId: String, newStatus: MatchStatus) {
        val db = writableDatabase
        var personCode: String? = null

        if (newStatus == MatchStatus.VERIFIED) {
            // Assign sequential Person_001 code if not already assigned
            val cursor = db.query(TABLE_MATCH_PAIRS, arrayOf("person_code"), "id = ?", arrayOf(pairId), null, null, null)
            cursor.use {
                if (it.moveToNext()) {
                    personCode = it.getString(0)
                }
            }

            if (personCode == null) {
                val maxCursor = db.rawQuery("SELECT person_code FROM $TABLE_MATCH_PAIRS WHERE person_code IS NOT NULL", null)
                var nextNum = 1
                maxCursor.use {
                    while (it.moveToNext()) {
                        val code = it.getString(0)
                        if (code.startsWith("Person_")) {
                            val num = code.removePrefix("Person_").toIntOrNull() ?: 0
                            if (num >= nextNum) nextNum = num + 1
                        }
                    }
                }
                personCode = "Person_%03d".format(nextNum)
            }
        }

        val values = ContentValues().apply {
            put("status", newStatus.name)
            if (personCode != null) {
                put("person_code", personCode)
            }
        }
        db.update(TABLE_MATCH_PAIRS, values, "id = ?", arrayOf(pairId))
    }

    fun clearAllData() {
        val db = writableDatabase
        db.delete(TABLE_MATCH_PAIRS, null, null)
        db.delete(TABLE_PERSONS, null, null)
        db.delete(TABLE_DOCUMENTS, null, null)
    }

    // --- Helpers ---

    private fun cursorToDocument(cursor: Cursor): DocumentRecord {
        return DocumentRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            uri = cursor.getString(cursor.getColumnIndexOrThrow("uri")),
            originalFilename = cursor.getString(cursor.getColumnIndexOrThrow("original_filename")),
            pdfPageNumber = cursor.getInt(cursor.getColumnIndexOrThrow("pdf_page_number")),
            totalPdfPages = cursor.getInt(cursor.getColumnIndexOrThrow("total_pdf_pages")),
            docType = DocType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("doc_type"))),
            docStatus = DocStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("doc_status"))),
            ocrEngineUsed = cursor.getString(cursor.getColumnIndexOrThrow("ocr_engine_used")) ?: "",
            ocrLanguages = cursor.getString(cursor.getColumnIndexOrThrow("ocr_languages")) ?: "",
            rawExtractedText = cursor.getString(cursor.getColumnIndexOrThrow("raw_extracted_text")) ?: "",
            redactedText = cursor.getString(cursor.getColumnIndexOrThrow("redacted_text")) ?: "",
            imageWidth = cursor.getInt(cursor.getColumnIndexOrThrow("image_width")),
            imageHeight = cursor.getInt(cursor.getColumnIndexOrThrow("image_height")),
            imageOrientation = cursor.getInt(cursor.getColumnIndexOrThrow("image_orientation")),
            userRotation = cursor.getInt(cursor.getColumnIndexOrThrow("user_rotation")),
            classificationScore = cursor.getFloat(cursor.getColumnIndexOrThrow("classification_score")),
            classificationReason = cursor.getString(cursor.getColumnIndexOrThrow("classification_reason")) ?: "",
            rulesFiredJson = cursor.getString(cursor.getColumnIndexOrThrow("rules_fired_json")) ?: "[]",
            processingErrors = cursor.getString(cursor.getColumnIndexOrThrow("processing_errors")),
            isManuallyCorrected = cursor.getInt(cursor.getColumnIndexOrThrow("is_manually_corrected")) == 1,
            isDuplicate = cursor.getInt(cursor.getColumnIndexOrThrow("is_duplicate")) == 1,
            isSynthetic = cursor.getInt(cursor.getColumnIndexOrThrow("is_synthetic")) == 1,
            sha256 = cursor.getString(cursor.getColumnIndexOrThrow("sha256")),
            khataNumber = cursor.getString(cursor.getColumnIndexOrThrow("khata_number")),
            plotNumber = cursor.getString(cursor.getColumnIndexOrThrow("plot_number")),
            tahasil = cursor.getString(cursor.getColumnIndexOrThrow("tahasil")),
            mouza = cursor.getString(cursor.getColumnIndexOrThrow("mouza")),
            district = cursor.getString(cursor.getColumnIndexOrThrow("district")),
            aadhaarNumberMasked = cursor.getString(cursor.getColumnIndexOrThrow("aadhaar_number_masked")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
        )
    }

    private fun cursorToPerson(cursor: Cursor): PersonRecord {
        return PersonRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            documentId = cursor.getString(cursor.getColumnIndexOrThrow("document_id")),
            originalName = cursor.getString(cursor.getColumnIndexOrThrow("original_name")),
            script = ScriptType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("script"))),
            transliteratedName = cursor.getString(cursor.getColumnIndexOrThrow("transliterated_name")),
            normalizedName = cursor.getString(cursor.getColumnIndexOrThrow("normalized_name")),
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow("confidence")),
            isManuallyCorrected = cursor.getInt(cursor.getColumnIndexOrThrow("is_manually_corrected")) == 1,
            role = cursor.getString(cursor.getColumnIndexOrThrow("role")) ?: "Rayat / Pattadar",
            fathersOrSpouseName = cursor.getString(cursor.getColumnIndexOrThrow("fathers_or_spouse_name")),
            shareDetails = cursor.getString(cursor.getColumnIndexOrThrow("share_details")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
        )
    }

    private fun cursorToMatchPair(cursor: Cursor): MatchPairRecord {
        return MatchPairRecord(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            aadhaarDocId = cursor.getString(cursor.getColumnIndexOrThrow("aadhaar_doc_id")),
            landDocId = cursor.getString(cursor.getColumnIndexOrThrow("land_doc_id")),
            aadhaarPersonId = cursor.getString(cursor.getColumnIndexOrThrow("aadhaar_person_id")),
            landPersonId = cursor.getString(cursor.getColumnIndexOrThrow("land_person_id")),
            aadhaarName = cursor.getString(cursor.getColumnIndexOrThrow("aadhaar_name")),
            landNameOriginal = cursor.getString(cursor.getColumnIndexOrThrow("land_name_original")),
            landNameTransliterated = cursor.getString(cursor.getColumnIndexOrThrow("land_name_transliterated")),
            matchConfidence = cursor.getFloat(cursor.getColumnIndexOrThrow("match_confidence")),
            matchExplanation = cursor.getString(cursor.getColumnIndexOrThrow("match_explanation")) ?: "",
            status = MatchStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
            isManual = cursor.getInt(cursor.getColumnIndexOrThrow("is_manual")) == 1,
            personCode = cursor.getString(cursor.getColumnIndexOrThrow("person_code")),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"))
        )
    }
}
