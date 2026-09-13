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

    private const val PREF_NAME =
        "pouch_training_data"

    private const val KEY_ENABLED =
        "training_enabled"

    private const val KEY_RECORDS =
        "training_records_json"

    private const val IMAGE_DIR =
        "training_images"

    /*
     * =========================================================
     * 공통 정답 라벨
     * =========================================================
     */
    const val LABEL_NORMAL = "정상"
    const val LABEL_WARNING = "주의"
    const val LABEL_LIMIT = "한계정상"
    const val LABEL_NG = "불량"

    val LABELS =
        listOf(
            LABEL_NORMAL,
            LABEL_WARNING,
            LABEL_LIMIT,
            LABEL_NG
        )

    /*
     * =========================================================
     * Bottom Corner 주름 개수 Ground Truth
     * =========================================================
     *
     * -1 : 아직 미지정
     *  0 : 주름 0개
     *  1 : 주름 1개
     *  2 : 주름 2개
     *  3 : 주름 3개 이상
     *
     * 사용자가 확정한 Master 기준
     *
     * 0개 -> 정상
     * 1개 -> 정상
     * 2개 -> 한계정상
     * 3개 이상 -> 불량
     */
    const val WRINKLE_COUNT_UNSET = -1
    const val WRINKLE_COUNT_0 = 0
    const val WRINKLE_COUNT_1 = 1
    const val WRINKLE_COUNT_2 = 2
    const val WRINKLE_COUNT_3_PLUS = 3

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

        /*
         * Bottom Corner 전용 정답 주름 개수
         *
         * -1 = 미지정
         * 0  = 0개
         * 1  = 1개
         * 2  = 2개
         * 3  = 3개 이상
         */
        val wrinkleCountGt: Int =
            WRINKLE_COUNT_UNSET
    ) {

        val isLabeled: Boolean
            get() =
                trueLabel.isNotBlank()

        val isMismatch: Boolean
            get() =
                isLabeled &&
                    normalizeLabel(
                        aiJudgment
                    ) !=
                    normalizeLabel(
                        trueLabel
                    )

        val isBottomCorner: Boolean
            get() =
                inspectionType.equals(
                    "BOTTOM CORNER",
                    ignoreCase = true
                )

        val hasWrinkleCountGroundTruth: Boolean
            get() =
                isBottomCorner &&
                    wrinkleCountGt >=
                    WRINKLE_COUNT_0

        val wrinkleCountText: String
            get() =
                wrinkleCountText(
                    wrinkleCountGt
                )
    }

    /*
     * =========================================================
     * 학습 데이터 수집 ON / OFF
     * =========================================================
     */

    fun isEnabled(
        context: Context
    ): Boolean {

        return context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_ENABLED,
                true
            )
    }

    fun setEnabled(
        context: Context,
        enabled: Boolean
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_ENABLED,
                enabled
            )
            .apply()
    }

    /*
     * =========================================================
     * 현재 검사 이력 -> 학습 데이터
     * =========================================================
     *
     * 검사 결과 이미지를 학습용 폴더에 별도 복사합니다.
     *
     * 일반 검사 History가 나중에 삭제되어도
     * Training 데이터는 별도로 유지됩니다.
     */

    fun syncFromInspectionHistory(
        context: Context
    ): Int {

        if (
            !isEnabled(
                context
            )
        ) {
            return 0
        }

        val existing =
            loadMutableJson(
                context
            )

        val knownIds =
            mutableSetOf<Long>()

        for (
            i in 0 until existing.length()
        ) {

            existing
                .optJSONObject(i)
                ?.optLong(
                    "sourceId",
                    0L
                )
                ?.takeIf {
                    it > 0L
                }
                ?.let {
                    knownIds.add(
                        it
                    )
                }
        }

        var added = 0

        InspectionHistoryStore
            .load(
                context
            )
            .filter {

                !it.inspectionType.equals(
                    "TOTAL SESSION",
                    ignoreCase = true
                ) &&

                    it.imagePath.isNotBlank() &&

                    it.id > 0L &&

                    !knownIds.contains(
                        it.id
                    )
            }
            .sortedBy {
                it.id
            }
            .forEach { source ->

                val copiedImage =
                    copyTrainingImage(
                        context =
                            context,

                        sourcePath =
                            source.imagePath,

                        sourceId =
                            source.id,

                        inspectionType =
                            source.inspectionType
                    )

                if (
                    copiedImage.isBlank()
                ) {
                    return@forEach
                }

                val item =
                    JSONObject()
                        .apply {

                            put(
                                "sourceId",
                                source.id
                            )

                            put(
                                "dateTime",
                                source.dateTime
                            )

                            put(
                                "model",
                                source.model
                            )

                            put(
                                "line",
                                source.line
                            )

                            put(
                                "inspectionType",
                                source.inspectionType
                            )

                            put(
                                "score",
                                source.score
                            )

                            put(
                                "aiJudgment",
                                source.judgment
                            )

                            put(
                                "sensitivity",
                                source.sensitivity
                            )

                            put(
                                "details",
                                source.details
                            )

                            put(
                                "imagePath",
                                copiedImage
                            )

                            put(
                                "trueLabel",
                                ""
                            )

                            put(
                                "note",
                                ""
                            )

                            put(
                                "labeledAt",
                                ""
                            )

                            /*
                             * 새 항목
                             *
                             * 기존 데이터와 호환되도록
                             * 기본값은 -1(미지정)입니다.
                             */
                            put(
                                "wrinkleCountGt",
                                WRINKLE_COUNT_UNSET
                            )
                        }

                existing.put(
                    item
                )

                knownIds.add(
                    source.id
                )

                added++
            }

        if (
            added > 0
        ) {

            saveJson(
                context,
                existing
            )
        }

        return added
    }

    /*
     * =========================================================
     * 학습 데이터 불러오기
     * =========================================================
     */

    fun load(
        context: Context
    ): List<TrainingRecord> {

        val array =
            loadMutableJson(
                context
            )

        val result =
            mutableListOf<TrainingRecord>()

        for (
            i in 0 until array.length()
        ) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            result.add(
                TrainingRecord(

                    sourceId =
                        item.optLong(
                            "sourceId",
                            0L
                        ),

                    dateTime =
                        item.optString(
                            "dateTime",
                            ""
                        ),

                    model =
                        item.optString(
                            "model",
                            ""
                        ),

                    line =
                        item.optString(
                            "line",
                            ""
                        ),

                    inspectionType =
                        item.optString(
                            "inspectionType",
                            ""
                        ),

                    score =
                        item.optDouble(
                            "score",
                            0.0
                        ),

                    aiJudgment =
                        item.optString(
                            "aiJudgment",
                            ""
                        ),

                    sensitivity =
                        item.optInt(
                            "sensitivity",
                            0
                        ),

                    details =
                        item.optString(
                            "details",
                            ""
                        ),

                    imagePath =
                        item.optString(
                            "imagePath",
                            ""
                        ),

                    trueLabel =
                        item.optString(
                            "trueLabel",
                            ""
                        ),

                    note =
                        item.optString(
                            "note",
                            ""
                        ),

                    labeledAt =
                        item.optString(
                            "labeledAt",
                            ""
                        ),

                    /*
                     * 예전 25개 데이터에는 이 Key가 없습니다.
                     *
                     * optInt 기본값 -1을 사용하므로
                     * 기존 데이터는 그대로 살아 있고
                     * 주름 개수만 미지정 상태가 됩니다.
                     */
                    wrinkleCountGt =
                        item.optInt(
                            "wrinkleCountGt",
                            WRINKLE_COUNT_UNSET
                        )
                )
            )
        }

        return result
            .sortedByDescending {
                it.sourceId
            }
    }

    /*
     * =========================================================
     * 기존 공통 정답 라벨 지정
     * =========================================================
     *
     * SEAL / FORMING / TAB / DISASSEMBLY 등에서는
     * 기존 방식 그대로 사용합니다.
     */

    fun setLabel(
        context: Context,
        sourceId: Long,
        label: String,
        note: String
    ): Boolean {

        if (
            label !in LABELS
        ) {
            return false
        }

        val array =
            loadMutableJson(
                context
            )

        val nowText =
            currentTimeText()

        var updated =
            false

        for (
            i in 0 until array.length()
        ) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            if (
                item.optLong(
                    "sourceId",
                    0L
                ) == sourceId
            ) {

                item.put(
                    "trueLabel",
                    label
                )

                item.put(
                    "note",
                    note.trim()
                )

                item.put(
                    "labeledAt",
                    nowText
                )

                updated =
                    true

                break
            }
        }

        if (
            updated
        ) {

            saveJson(
                context,
                array
            )
        }

        return updated
    }

    /*
     * =========================================================
     * Bottom Corner 주름 개수 Ground Truth 지정
     * =========================================================
     *
     * countCode
     *
     * 0 = 주름 0개
     * 1 = 주름 1개
     * 2 = 주름 2개
     * 3 = 주름 3개 이상
     *
     * 개수 지정과 동시에 최종 정답도 자동 지정됩니다.
     *
     * 0 -> 정상
     * 1 -> 정상
     * 2 -> 한계정상
     * 3+ -> 불량
     */

    fun setWrinkleCountGroundTruth(
        context: Context,
        sourceId: Long,
        countCode: Int,
        note: String
    ): Boolean {

        if (
            countCode !in
            WRINKLE_COUNT_0..
                WRINKLE_COUNT_3_PLUS
        ) {
            return false
        }

        val array =
            loadMutableJson(
                context
            )

        val nowText =
            currentTimeText()

        var updated =
            false

        for (
            i in 0 until array.length()
        ) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            if (
                item.optLong(
                    "sourceId",
                    0L
                ) != sourceId
            ) {
                continue
            }

            val inspectionType =
                item.optString(
                    "inspectionType",
                    ""
                )

            /*
             * 이 기능은 Bottom Corner에서만 사용합니다.
             */
            if (
                !inspectionType.equals(
                    "BOTTOM CORNER",
                    ignoreCase = true
                )
            ) {
                return false
            }

            val label =
                labelFromWrinkleCount(
                    countCode
                )

            item.put(
                "wrinkleCountGt",
                countCode
            )

            item.put(
                "trueLabel",
                label
            )

            item.put(
                "note",
                note.trim()
            )

            item.put(
                "labeledAt",
                nowText
            )

            updated =
                true

            break
        }

        if (
            updated
        ) {

            saveJson(
                context,
                array
            )
        }

        return updated
    }

    /*
     * =========================================================
     * 정답 라벨 / 주름 개수 지우기
     * =========================================================
     */

    fun clearLabel(
        context: Context,
        sourceId: Long
    ): Boolean {

        val array =
            loadMutableJson(
                context
            )

        var updated =
            false

        for (
            i in 0 until array.length()
        ) {

            val item =
                array.optJSONObject(i)
                    ?: continue

            if (
                item.optLong(
                    "sourceId",
                    0L
                ) == sourceId
            ) {

                item.put(
                    "trueLabel",
                    ""
                )

                item.put(
                    "wrinkleCountGt",
                    WRINKLE_COUNT_UNSET
                )

                item.put(
                    "note",
                    ""
                )

                item.put(
                    "labeledAt",
                    ""
                )

                updated =
                    true

                break
            }
        }

        if (
            updated
        ) {

            saveJson(
                context,
                array
            )
        }

        return updated
    }

    /*
     * =========================================================
     * 라벨별 개수
     * =========================================================
     */

    fun countByLabel(
        context: Context,
        inspectionType: String? = null
    ): Map<String, Int> {

        val records =
            load(
                context
            )
                .filter {

                    inspectionType == null ||

                        it.inspectionType.equals(
                            inspectionType,
                            ignoreCase = true
                        )
                }

        return linkedMapOf(

            LABEL_NORMAL to
                records.count {
                    it.trueLabel ==
                        LABEL_NORMAL
                },

            LABEL_WARNING to
                records.count {
                    it.trueLabel ==
                        LABEL_WARNING
                },

            LABEL_LIMIT to
                records.count {
                    it.trueLabel ==
                        LABEL_LIMIT
                },

            LABEL_NG to
                records.count {
                    it.trueLabel ==
                        LABEL_NG
                }
        )
    }

    /*
     * =========================================================
     * Bottom Corner Ground Truth 개수별 통계
     * =========================================================
     */

    fun countByWrinkleGroundTruth(
        context: Context
    ): Map<Int, Int> {

        val records =
            load(
                context
            )
                .filter {

                    it.isBottomCorner &&
                        it.hasWrinkleCountGroundTruth
                }

        return linkedMapOf(

            WRINKLE_COUNT_0 to
                records.count {
                    it.wrinkleCountGt ==
                        WRINKLE_COUNT_0
                },

            WRINKLE_COUNT_1 to
                records.count {
                    it.wrinkleCountGt ==
                        WRINKLE_COUNT_1
                },

            WRINKLE_COUNT_2 to
                records.count {
                    it.wrinkleCountGt ==
                        WRINKLE_COUNT_2
                },

            WRINKLE_COUNT_3_PLUS to
                records.count {
                    it.wrinkleCountGt ==
                        WRINKLE_COUNT_3_PLUS
                }
        )
    }

    /*
     * =========================================================
     * ZIP Export
     * =========================================================
     */

    fun exportZip(
        context: Context,
        outputStream: java.io.OutputStream
    ) {

        val records =
            load(
                context
            )

        ZipOutputStream(
            outputStream
        ).use { zip ->

            /*
             * labels.csv
             */
            val csvText =
                buildCsv(
                    records
                )

            zip.putNextEntry(
                ZipEntry(
                    "labels.csv"
                )
            )

            zip.write(
                csvText.toByteArray(
                    Charsets.UTF_8
                )
            )

            zip.closeEntry()

            /*
             * README
             */
            val guide =
                """
                PouchVisionInspector Training Dataset

                labels.csv

                - AI_Judgment       : 현재 앱 판정
                - True_Label        : 작업자가 확정한 최종 정답
                - Mismatch          : AI 판정과 정답이 다른 경우 1
                - Wrinkle_Count_GT  : Bottom Corner 실제 주름 개수 정답
                                      -1 = 미지정
                                       0 = 0개
                                       1 = 1개
                                       2 = 2개
                                       3 = 3개 이상
                - Image_File        : images 폴더의 학습 이미지

                Bottom Corner Master 기준

                주름 0개     = 정상
                주름 1개     = 정상
                주름 2개     = 한계정상
                주름 3개 이상 = 불량

                AI 보조 분석 예정 인자

                - 주름 개수
                - 최장 주름 길이
                - 전체 주름 길이
                - 음영 / 대비
                - 주름 강도
                - 주름 위치
                - 주름 연속성

                공통 권장 라벨

                정상 / 주의 / 한계정상 / 불량

                주의:

                True_Label이 비어 있으면
                아직 작업자 정답이 확정되지 않은 데이터입니다.

                Bottom Corner에서 Wrinkle_Count_GT = -1이면
                실제 주름 개수 Ground Truth가 아직 입력되지 않은 데이터입니다.
                """.trimIndent()

            zip.putNextEntry(
                ZipEntry(
                    "README.txt"
                )
            )

            zip.write(
                guide.toByteArray(
                    Charsets.UTF_8
                )
            )

            zip.closeEntry()

            /*
             * 이미지 저장
             */
            records.forEach { record ->

                val file =
                    File(
                        record.imagePath
                    )

                if (
                    !file.exists() ||
                    !file.isFile
                ) {
                    return@forEach
                }

                val safeType =
                    record.inspectionType
                        .replace(
                            " ",
                            "_"
                        )
                        .replace(
                            "/",
                            "_"
                        )
                        .replace(
                            "\\",
                            "_"
                        )

                val labelFolder =
                    record.trueLabel
                        .ifBlank {
                            "UNLABELED"
                        }
                        .replace(
                            "/",
                            "_"
                        )

                val entryName =
                    "images/" +
                        "$safeType/" +
                        "$labelFolder/" +
                        file.name

                zip.putNextEntry(
                    ZipEntry(
                        entryName
                    )
                )

                FileInputStream(
                    file
                ).use { input ->

                    val buffer =
                        ByteArray(
                            8192
                        )

                    while (
                        true
                    ) {

                        val read =
                            input.read(
                                buffer
                            )

                        if (
                            read <= 0
                        ) {
                            break
                        }

                        zip.write(
                            buffer,
                            0,
                            read
                        )
                    }
                }

                zip.closeEntry()
            }
        }
    }

    /*
     * =========================================================
     * CSV 생성
     * =========================================================
     */

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
                    "Mismatch",

                    /*
                     * 새 항목
                     */
                    "Wrinkle_Count_GT",

                    "Sensitivity",
                    "Image_File",
                    "Note",
                    "Details"
                )
                    .joinToString(
                        ","
                    ) {
                        csv(
                            it
                        )
                    }
            )

            append(
                "\r\n"
            )

            records
                .sortedBy {
                    it.sourceId
                }
                .forEach { r ->

                    val imageName =
                        File(
                            r.imagePath
                        ).name

                    append(
                        listOf(

                            r.sourceId
                                .toString(),

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

                            if (
                                r.isMismatch
                            ) {
                                "1"
                            } else {
                                "0"
                            },

                            /*
                             * Bottom Corner가 아니거나
                             * 아직 개수 미지정이면 -1
                             */
                            r.wrinkleCountGt
                                .toString(),

                            r.sensitivity
                                .toString(),

                            imageName,

                            r.note,

                            r.details
                        )
                            .joinToString(
                                ","
                            ) {
                                csv(
                                    it
                                )
                            }
                    )

                    append(
                        "\r\n"
                    )
                }
        }
    }

    /*
     * =========================================================
     * 학습 이미지 복사
     * =========================================================
     */

    private fun copyTrainingImage(
        context: Context,
        sourcePath: String,
        sourceId: Long,
        inspectionType: String
    ): String {

        return try {

            val source =
                File(
                    sourcePath
                )

            if (
                !source.exists() ||
                !source.isFile
            ) {
                return ""
            }

            val dir =
                File(
                    context.filesDir,
                    IMAGE_DIR
                )

            if (
                !dir.exists() &&
                !dir.mkdirs()
            ) {
                return ""
            }

            val safeType =
                inspectionType
                    .lowercase(
                        Locale.US
                    )
                    .replace(
                        " ",
                        "_"
                    )
                    .replace(
                        "/",
                        "_"
                    )
                    .replace(
                        "\\",
                        "_"
                    )

            val target =
                File(
                    dir,
                    "${sourceId}_${safeType}.jpg"
                )

            if (
                !target.exists()
            ) {

                source
                    .inputStream()
                    .use { input ->

                        FileOutputStream(
                            target
                        ).use { output ->

                            input.copyTo(
                                output
                            )
                        }
                    }
            }

            target.absolutePath

        } catch (
            _: Exception
        ) {

            ""
        }
    }

    /*
     * =========================================================
     * JSON 불러오기
     * =========================================================
     */

    private fun loadMutableJson(
        context: Context
    ): JSONArray {

        val text =
            context
                .getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    KEY_RECORDS,
                    "[]"
                )
                ?: "[]"

        return try {

            JSONArray(
                text
            )

        } catch (
            _: Exception
        ) {

            JSONArray()
        }
    }

    /*
     * =========================================================
     * JSON 저장
     * =========================================================
     */

    private fun saveJson(
        context: Context,
        array: JSONArray
    ) {

        context
            .getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_RECORDS,
                array.toString()
            )
            .commit()
    }

    /*
     * =========================================================
     * CSV 안전 처리
     * =========================================================
     */

    private fun csv(
        value: String
    ): String {

        return "\"" +

            value
                .replace(
                    "\"",
                    "\"\""
                )
                .replace(
                    "\r",
                    " "
                )
                .replace(
                    "\n",
                    " "
                ) +

            "\""
    }

    /*
     * =========================================================
     * 판정 문자열 정규화
     * =========================================================
     */

    fun normalizeLabel(
        value: String
    ): String {

        val v =
            value.trim()

        return when {

            v.contains(
                "불량"
            ) ->
                LABEL_NG

            v.contains(
                "한계"
            ) ->
                LABEL_LIMIT

            v.contains(
                "주의"
            ) ->
                LABEL_WARNING

            v.contains(
                "정상"
            ) ->
                LABEL_NORMAL

            else ->
                v
        }
    }

    /*
     * =========================================================
     * 주름 개수 -> Master 판정
     * =========================================================
     */

    fun labelFromWrinkleCount(
        countCode: Int
    ): String {

        return when (
            countCode
        ) {

            WRINKLE_COUNT_0 ->
                LABEL_NORMAL

            WRINKLE_COUNT_1 ->
                LABEL_NORMAL

            WRINKLE_COUNT_2 ->
                LABEL_LIMIT

            WRINKLE_COUNT_3_PLUS ->
                LABEL_NG

            else ->
                ""
        }
    }

    /*
     * =========================================================
     * 주름 개수 표시 문자열
     * =========================================================
     */

    fun wrinkleCountText(
        countCode: Int
    ): String {

        return when (
            countCode
        ) {

            WRINKLE_COUNT_0 ->
                "0개"

            WRINKLE_COUNT_1 ->
                "1개"

            WRINKLE_COUNT_2 ->
                "2개"

            WRINKLE_COUNT_3_PLUS ->
                "3개 이상"

            else ->
                "미지정"
        }
    }

    /*
     * =========================================================
     * 현재 시간
     * =========================================================
     */

    private fun currentTimeText(): String {

        return SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.getDefault()
        ).format(
            Date()
        )
    }
}
