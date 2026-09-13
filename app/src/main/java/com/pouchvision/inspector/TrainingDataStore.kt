package com.pouchvision.inspector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TrainingDataStore {

    private const val PREF_NAME = "pouch_training_data"
    private const val KEY_ENABLED = "training_enabled"
    private const val KEY_RECORDS = "training_records_json"
    private const val IMAGE_DIR = "training_images"

    const val LABEL_NORMAL = "정상"
    const val LABEL_WARNING = "주의"
    const val LABEL_LIMIT = "한계정상"
    const val LABEL_NG = "불량"

    // Bottom Corner Ground Truth: 실제 물리 주름 개수
    const val WRINKLE_COUNT_UNKNOWN = -1
    const val WRINKLE_COUNT_0 = 0
    const val WRINKLE_COUNT_1 = 1
    const val WRINKLE_COUNT_2 = 2
    const val WRINKLE_COUNT_3_PLUS = 3

    val LABELS = listOf(
        LABEL_NORMAL,
        LABEL_WARNING,
        LABEL_LIMIT,
        LABEL_NG
    )

    data class TrainingRecord(
        val sourceId: Long,
        val dateTime: String,
        val model: String,
        val line: String,
        val inspectionType: String,
        val score: Double,
        val aiJudgment: String,
        val sensitivity: Int,
        val details: String,
        val imagePath: String,
        val trueLabel: String,
        val note: String,
        val labeledAt: String,
        val wrinkleCountGt: Int = WRINKLE_COUNT_UNKNOWN
    ) {
        val isBottomCorner: Boolean
            get() = inspectionType.equals("BOTTOM CORNER", ignoreCase = true)

        val hasWrinkleCountGroundTruth: Boolean
            get() = isBottomCorner && wrinkleCountGt != WRINKLE_COUNT_UNKNOWN

        val wrinkleCountText: String
            get() = TrainingDataStore.wrinkleCountText(wrinkleCountGt)

        val isLabeled: Boolean
            get() = if (isBottomCorner) {
                hasWrinkleCountGroundTruth || trueLabel.isNotBlank()
            } else {
                trueLabel.isNotBlank()
            }

        val isMismatch: Boolean
            get() = isLabeled &&
                normalizeLabel(aiJudgment) != normalizeLabel(trueLabel)
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        ).getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /**
     * 현재 InspectionHistoryStore 이력 중 아직 학습 저장소에 없는
     * 이미지 기록을 가져옵니다.
     *
     * 학습 이미지는 별도 폴더로 복사하므로 일반 History가 오래되어
     * 삭제되더라도 라벨 학습 데이터는 유지됩니다.
     */
    fun syncFromInspectionHistory(
        context: Context
    ): Int {
        if (!isEnabled(context)) {
            return 0
        }

        val existing = loadMutableJson(context)
        val knownIds = mutableSetOf<Long>()

        for (i in 0 until existing.length()) {
            existing.optJSONObject(i)
                ?.optLong("sourceId", 0L)
                ?.takeIf { it > 0L }
                ?.let { knownIds.add(it) }
        }

        var added = 0

        InspectionHistoryStore.load(context)
            .filter {
                !it.inspectionType.equals(
                    "TOTAL SESSION",
                    ignoreCase = true
                ) &&
                    it.imagePath.isNotBlank() &&
                    it.id > 0L &&
                    !knownIds.contains(it.id)
            }
            .sortedBy { it.id }
            .forEach { source ->

                val copiedImage =
                    copyTrainingImage(
                        context = context,
                        sourcePath = source.imagePath,
                        sourceId = source.id,
                        inspectionType = source.inspectionType
                    )

                if (copiedImage.isBlank()) {
                    return@forEach
                }

                val item = JSONObject().apply {
                    put("sourceId", source.id)
                    put("dateTime", source.dateTime)
                    put("model", source.model)
                    put("line", source.line)
                    put("inspectionType", source.inspectionType)
                    put("score", source.score)
                    put("aiJudgment", source.judgment)
                    put("sensitivity", source.sensitivity)
                    put("details", source.details)
                    put("imagePath", copiedImage)
                    put("trueLabel", "")
                    put("note", "")
                    put("labeledAt", "")
                    put("wrinkleCountGt", WRINKLE_COUNT_UNKNOWN)
                }

                existing.put(item)
                knownIds.add(source.id)
                added++
            }

        if (added > 0) {
            saveJson(context, existing)
        }

        return added
    }

    fun load(context: Context): List<TrainingRecord> {
        val array = loadMutableJson(context)
        val result = mutableListOf<TrainingRecord>()

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            result.add(
                TrainingRecord(
                    sourceId = item.optLong("sourceId", 0L),
                    dateTime = item.optString("dateTime", ""),
                    model = item.optString("model", ""),
                    line = item.optString("line", ""),
                    inspectionType = item.optString("inspectionType", ""),
                    score = item.optDouble("score", 0.0),
                    aiJudgment = item.optString("aiJudgment", ""),
                    sensitivity = item.optInt("sensitivity", 0),
                    details = item.optString("details", ""),
                    imagePath = item.optString("imagePath", ""),
                    trueLabel = item.optString("trueLabel", ""),
                    note = item.optString("note", ""),
                    labeledAt = item.optString("labeledAt", ""),
                    wrinkleCountGt = item.optInt(
                        "wrinkleCountGt",
                        WRINKLE_COUNT_UNKNOWN
                    )
                )
            )
        }

        return result.sortedByDescending { it.sourceId }
    }

    fun setLabel(
        context: Context,
        sourceId: Long,
        label: String,
        note: String
    ): Boolean {
        if (label !in LABELS) {
            return false
        }

        val array = loadMutableJson(context)
        val nowText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        var updated = false

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            if (item.optLong("sourceId", 0L) == sourceId) {
                item.put("trueLabel", label)
                item.put("note", note.trim())
                item.put("labeledAt", nowText)
                updated = true
                break
            }
        }

        if (updated) {
            saveJson(context, array)
        }

        return updated
    }

    fun clearLabel(
        context: Context,
        sourceId: Long
    ): Boolean {
        val array = loadMutableJson(context)
        var updated = false

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            if (item.optLong("sourceId", 0L) == sourceId) {
                item.put("trueLabel", "")
                item.put("note", "")
                item.put("labeledAt", "")
                item.put("wrinkleCountGt", WRINKLE_COUNT_UNKNOWN)
                updated = true
                break
            }
        }

        if (updated) {
            saveJson(context, array)
        }

        return updated
    }

    fun isBottomCorner(inspectionType: String): Boolean {
        return inspectionType.equals(
            "BOTTOM CORNER",
            ignoreCase = true
        )
    }

    fun hasWrinkleCountGroundTruth(record: TrainingRecord): Boolean {
        return record.hasWrinkleCountGroundTruth
    }

    fun wrinkleCountText(count: Int): String {
        return when (count) {
            WRINKLE_COUNT_0 -> "0개"
            WRINKLE_COUNT_1 -> "1개"
            WRINKLE_COUNT_2 -> "2개"
            WRINKLE_COUNT_3_PLUS -> "3개 이상"
            else -> "미지정"
        }
    }

    fun labelFromWrinkleCount(count: Int): String {
        return when (count) {
            WRINKLE_COUNT_0,
            WRINKLE_COUNT_1 -> LABEL_NORMAL
            WRINKLE_COUNT_2 -> LABEL_LIMIT
            WRINKLE_COUNT_3_PLUS -> LABEL_NG
            else -> ""
        }
    }

    fun setWrinkleCountGroundTruth(
        context: Context,
        sourceId: Long,
        countCode: Int
    ): Boolean {
        return setWrinkleCountGroundTruth(
            context = context,
            sourceId = sourceId,
            countCode = countCode,
            note = ""
        )
    }

    fun setWrinkleCountGroundTruth(
        context: Context,
        sourceId: Long,
        countCode: Int,
        note: String
    ): Boolean {
        if (
            countCode !in listOf(
                WRINKLE_COUNT_0,
                WRINKLE_COUNT_1,
                WRINKLE_COUNT_2,
                WRINKLE_COUNT_3_PLUS
            )
        ) {
            return false
        }

        val array = loadMutableJson(context)
        val nowText = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        var updated = false

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            if (item.optLong("sourceId", 0L) == sourceId) {
                val type = item.optString("inspectionType", "")
                if (!isBottomCorner(type)) {
                    return false
                }

                item.put("wrinkleCountGt", countCode)
                item.put("trueLabel", labelFromWrinkleCount(countCode))
                item.put("note", note.trim())
                item.put("labeledAt", nowText)

                updated = true
                break
            }
        }

        if (updated) {
            saveJson(context, array)
        }

        return updated
    }

    fun setWrinkleCountGroundTruth(
        context: Context,
        record: TrainingRecord,
        countCode: Int
    ): Boolean {
        return setWrinkleCountGroundTruth(
            context = context,
            sourceId = record.sourceId,
            countCode = countCode,
            note = record.note
        )
    }

    fun setWrinkleCountGroundTruth(
        context: Context,
        record: TrainingRecord,
        countCode: Int,
        note: String
    ): Boolean {
        return setWrinkleCountGroundTruth(
            context = context,
            sourceId = record.sourceId,
            countCode = countCode,
            note = note
        )
    }

    fun clearWrinkleCountGroundTruth(
        context: Context,
        sourceId: Long
    ): Boolean {
        val array = loadMutableJson(context)
        var updated = false

        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            if (item.optLong("sourceId", 0L) == sourceId) {
                item.put("wrinkleCountGt", WRINKLE_COUNT_UNKNOWN)
                item.put("trueLabel", "")
                item.put("note", "")
                item.put("labeledAt", "")
                updated = true
                break
            }
        }

        if (updated) {
            saveJson(context, array)
        }

        return updated
    }

    fun countByWrinkleGroundTruth(
        context: Context,
        inspectionType: String? = "BOTTOM CORNER"
    ): Map<Int, Int> {
        val records =
            load(context)
                .filter {
                    inspectionType == null ||
                        it.inspectionType.equals(
                            inspectionType,
                            ignoreCase = true
                        )
                }

        return linkedMapOf(
            WRINKLE_COUNT_0 to records.count {
                it.wrinkleCountGt == WRINKLE_COUNT_0
            },
            WRINKLE_COUNT_1 to records.count {
                it.wrinkleCountGt == WRINKLE_COUNT_1
            },
            WRINKLE_COUNT_2 to records.count {
                it.wrinkleCountGt == WRINKLE_COUNT_2
            },
            WRINKLE_COUNT_3_PLUS to records.count {
                it.wrinkleCountGt == WRINKLE_COUNT_3_PLUS
            }
        )
    }

    fun countByLabel(
        context: Context,
        inspectionType: String? = null
    ): Map<String, Int> {
        val records =
            load(context)
                .filter {
                    inspectionType == null ||
                        it.inspectionType.equals(
                            inspectionType,
                            ignoreCase = true
                        )
                }

        return linkedMapOf(
            LABEL_NORMAL to records.count { it.trueLabel == LABEL_NORMAL },
            LABEL_WARNING to records.count { it.trueLabel == LABEL_WARNING },
            LABEL_LIMIT to records.count { it.trueLabel == LABEL_LIMIT },
            LABEL_NG to records.count { it.trueLabel == LABEL_NG }
        )
    }

    fun exportZip(
        context: Context,
        outputStream: java.io.OutputStream
    ) {
        val records = load(context)

        ZipOutputStream(outputStream).use { zip ->
            val csvText = buildCsv(records)

            zip.putNextEntry(
                ZipEntry("labels.csv")
            )
            zip.write(
                csvText.toByteArray(
                    Charsets.UTF_8
                )
            )
            zip.closeEntry()

            val guide =
                """
                PouchVisionInspector Training Dataset

                labels.csv
                - AI_Judgment : 현재 앱 판정
                - True_Label  : 작업자가 확정한 정답
                - Mismatch    : AI 판정과 정답이 다른 경우 1
                - Image_File  : images 폴더의 학습 이미지

                권장 라벨
                정상 / 주의 / 한계정상 / 불량

                주의:
                라벨이 비어 있는 데이터는 아직 정답 확정 전입니다.
                """.trimIndent()

            zip.putNextEntry(
                ZipEntry("README.txt")
            )
            zip.write(
                guide.toByteArray(
                    Charsets.UTF_8
                )
            )
            zip.closeEntry()

            records.forEach { record ->
                val file = File(record.imagePath)
                if (!file.exists() || !file.isFile) {
                    return@forEach
                }

                val safeType =
                    record.inspectionType
                        .replace(" ", "_")
                        .replace("/", "_")
                        .replace("\\", "_")

                val labelFolder =
                    record.trueLabel
                        .ifBlank { "UNLABELED" }
                        .replace("/", "_")

                val entryName =
                    "images/$safeType/$labelFolder/" +
                        file.name

                zip.putNextEntry(
                    ZipEntry(entryName)
                )

                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        zip.write(buffer, 0, read)
                    }
                }

                zip.closeEntry()
            }
        }
    }

    private fun buildCsv(
        records: List<TrainingRecord>
    ): String {
        return buildString {
            append(
                listOf(
                    "Source_ID",
                    "DateTime",
                    "Model",
                    "Line",
                    "Inspection_Type",
                    "Score",
                    "AI_Judgment",
                    "True_Label",
                    "Wrinkle_Count_GT",
                    "Mismatch",
                    "Sensitivity",
                    "Image_File",
                    "Note",
                    "Details"
                ).joinToString(",") {
                    csv(it)
                }
            )
            append("\r\n")

            records.sortedBy { it.sourceId }
                .forEach { r ->

                    val imageName =
                        File(r.imagePath).name

                    append(
                        listOf(
                            r.sourceId.toString(),
                            r.dateTime,
                            r.model,
                            r.line,
                            r.inspectionType,
                            String.format(
                                Locale.US,
                                "%.3f",
                                r.score
                            ),
                            r.aiJudgment,
                            r.trueLabel,
                            if (r.isBottomCorner) r.wrinkleCountText else "",
                            if (r.isMismatch) "1" else "0",
                            r.sensitivity.toString(),
                            imageName,
                            r.note,
                            r.details
                        ).joinToString(",") {
                            csv(it)
                        }
                    )
                    append("\r\n")
                }
        }
    }

    private fun copyTrainingImage(
        context: Context,
        sourcePath: String,
        sourceId: Long,
        inspectionType: String
    ): String {
        return try {
            val source = File(sourcePath)
            if (!source.exists() || !source.isFile) {
                return ""
            }

            val dir = File(
                context.filesDir,
                IMAGE_DIR
            )

            if (!dir.exists() && !dir.mkdirs()) {
                return ""
            }

            val safeType =
                inspectionType
                    .lowercase(Locale.US)
                    .replace(" ", "_")
                    .replace("/", "_")
                    .replace("\\", "_")

            val target = File(
                dir,
                "${sourceId}_${safeType}.jpg"
            )

            if (!target.exists()) {
                source.inputStream().use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            target.absolutePath

        } catch (_: Exception) {
            ""
        }
    }

    private fun loadMutableJson(
        context: Context
    ): JSONArray {
        val text =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            ).getString(
                KEY_RECORDS,
                "[]"
            ) ?: "[]"

        return try {
            JSONArray(text)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun saveJson(
        context: Context,
        array: JSONArray
    ) {
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(
                KEY_RECORDS,
                array.toString()
            )
            .commit()
    }

    private fun csv(value: String): String {
        return "\"" +
            value
                .replace("\"", "\"\"")
                .replace("\r", " ")
                .replace("\n", " ") +
            "\""
    }

    fun normalizeLabel(value: String): String {
        val v = value.trim()

        return when {
            v.contains("불량") -> LABEL_NG
            v.contains("한계") -> LABEL_LIMIT
            v.contains("주의") -> LABEL_WARNING
            v.contains("정상") -> LABEL_NORMAL
            else -> v
        }
    }
}
