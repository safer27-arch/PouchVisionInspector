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

    val LABELS = listOf(LABEL_NORMAL, LABEL_WARNING, LABEL_LIMIT, LABEL_NG)

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
        val labeledAt: String
    ) {
        val isLabeled: Boolean get() = trueLabel.isNotBlank()
        val isMismatch: Boolean
            get() = isLabeled && normalizeLabel(aiJudgment) != normalizeLabel(trueLabel)
    }

    data class TypeStats(
        val inspectionType: String,
        val total: Int,
        val labeled: Int,
        val unlabeled: Int,
        val mismatch: Int,
        val matched: Int,
        val accuracyPercent: Double,
        val normal: Int,
        val warning: Int,
        val limit: Int,
        val ng: Int
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun syncFromInspectionHistory(context: Context): Int {
        if (!isEnabled(context)) return 0

        val existing = loadMutableJson(context)
        val knownIds = mutableSetOf<Long>()
        for (i in 0 until existing.length()) {
            existing.optJSONObject(i)?.optLong("sourceId", 0L)
                ?.takeIf { it > 0L }?.let { knownIds.add(it) }
        }

        var added = 0
        InspectionHistoryStore.load(context)
            .filter {
                !it.inspectionType.equals("TOTAL SESSION", true) &&
                    it.imagePath.isNotBlank() && it.id > 0L &&
                    !knownIds.contains(it.id)
            }
            .sortedBy { it.id }
            .forEach { source ->
                val copiedImage = copyTrainingImage(
                    context, source.imagePath, source.id, source.inspectionType
                )
                if (copiedImage.isBlank()) return@forEach

                existing.put(JSONObject().apply {
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
                })
                knownIds.add(source.id)
                added++
            }

        if (added > 0) saveJson(context, existing)
        return added
    }

    fun load(context: Context): List<TrainingRecord> {
        val array = loadMutableJson(context)
        val result = mutableListOf<TrainingRecord>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            result.add(
                TrainingRecord(
                    item.optLong("sourceId", 0L),
                    item.optString("dateTime", ""),
                    item.optString("model", ""),
                    item.optString("line", ""),
                    item.optString("inspectionType", ""),
                    item.optDouble("score", 0.0),
                    item.optString("aiJudgment", ""),
                    item.optInt("sensitivity", 0),
                    item.optString("details", ""),
                    item.optString("imagePath", ""),
                    item.optString("trueLabel", ""),
                    item.optString("note", ""),
                    item.optString("labeledAt", "")
                )
            )
        }
        return result.sortedByDescending { it.sourceId }
    }

    fun setLabel(context: Context, sourceId: Long, label: String, note: String): Boolean {
        if (label !in LABELS) return false
        val array = loadMutableJson(context)
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        var updated = false
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optLong("sourceId", 0L) == sourceId) {
                item.put("trueLabel", label)
                item.put("note", note.trim())
                item.put("labeledAt", now)
                updated = true
                break
            }
        }
        if (updated) saveJson(context, array)
        return updated
    }

    fun clearLabel(context: Context, sourceId: Long): Boolean {
        val array = loadMutableJson(context)
        var updated = false
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optLong("sourceId", 0L) == sourceId) {
                item.put("trueLabel", "")
                item.put("note", "")
                item.put("labeledAt", "")
                updated = true
                break
            }
        }
        if (updated) saveJson(context, array)
        return updated
    }

    fun countByLabel(context: Context, inspectionType: String? = null): Map<String, Int> {
        val records = load(context).filter {
            inspectionType == null || it.inspectionType.equals(inspectionType, true)
        }
        return linkedMapOf(
            LABEL_NORMAL to records.count { it.trueLabel == LABEL_NORMAL },
            LABEL_WARNING to records.count { it.trueLabel == LABEL_WARNING },
            LABEL_LIMIT to records.count { it.trueLabel == LABEL_LIMIT },
            LABEL_NG to records.count { it.trueLabel == LABEL_NG }
        )
    }

    fun statsByType(context: Context): List<TypeStats> {
        val order = listOf("BOTTOM CORNER", "SEAL", "FORMING", "TAB", "DISASSEMBLY")
        val records = load(context)
        return order.map { type ->
            val rows = records.filter { it.inspectionType.equals(type, true) }
            val labeledRows = rows.filter { it.isLabeled }
            val mismatch = labeledRows.count { it.isMismatch }
            val matched = labeledRows.size - mismatch
            TypeStats(
                type, rows.size, labeledRows.size, rows.size - labeledRows.size,
                mismatch, matched,
                if (labeledRows.isEmpty()) 0.0 else matched * 100.0 / labeledRows.size,
                labeledRows.count { it.trueLabel == LABEL_NORMAL },
                labeledRows.count { it.trueLabel == LABEL_WARNING },
                labeledRows.count { it.trueLabel == LABEL_LIMIT },
                labeledRows.count { it.trueLabel == LABEL_NG }
            )
        }
    }

    fun exportZip(context: Context, outputStream: java.io.OutputStream) {
        val records = load(context)
        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("labels.csv"))
            zip.write(buildCsv(records).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val guide = """
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
                실제 NG 샘플이 충분해지기 전에는 Threshold를 임의 확정하지 마십시오.
            """.trimIndent()
            zip.putNextEntry(ZipEntry("README.txt"))
            zip.write(guide.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            records.forEach { r ->
                val file = File(r.imagePath)
                if (!file.exists() || !file.isFile) return@forEach
                val safeType = r.inspectionType.replace(" ", "_").replace("/", "_").replace("\\", "_")
                val labelFolder = r.trueLabel.ifBlank { "UNLABELED" }.replace("/", "_")
                zip.putNextEntry(ZipEntry("images/$safeType/$labelFolder/${file.name}"))
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        zip.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun buildCsv(records: List<TrainingRecord>): String = buildString {
        append(listOf(
            "Source_ID","DateTime","Model","Line","Inspection_Type","Score",
            "AI_Judgment","True_Label","Mismatch","Sensitivity","Image_File","Note","Details"
        ).joinToString(",") { csv(it) })
        append("\r\n")
        records.sortedBy { it.sourceId }.forEach { r ->
            append(listOf(
                r.sourceId.toString(), r.dateTime, r.model, r.line, r.inspectionType,
                String.format(Locale.US, "%.3f", r.score), r.aiJudgment, r.trueLabel,
                if (r.isMismatch) "1" else "0", r.sensitivity.toString(),
                File(r.imagePath).name, r.note, r.details
            ).joinToString(",") { csv(it) })
            append("\r\n")
        }
    }

    private fun copyTrainingImage(
        context: Context, sourcePath: String, sourceId: Long, inspectionType: String
    ): String = try {
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile) return ""
        val dir = File(context.filesDir, IMAGE_DIR)
        if (!dir.exists() && !dir.mkdirs()) return ""
        val safeType = inspectionType.lowercase(Locale.US)
            .replace(" ", "_").replace("/", "_").replace("\\", "_")
        val target = File(dir, "${sourceId}_${safeType}.jpg")
        if (!target.exists()) {
            source.inputStream().use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        target.absolutePath
    } catch (_: Exception) { "" }

    private fun loadMutableJson(context: Context): JSONArray {
        val text = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDS, "[]") ?: "[]"
        return try { JSONArray(text) } catch (_: Exception) { JSONArray() }
    }

    private fun saveJson(context: Context, array: JSONArray) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECORDS, array.toString()).commit()
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\""

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
