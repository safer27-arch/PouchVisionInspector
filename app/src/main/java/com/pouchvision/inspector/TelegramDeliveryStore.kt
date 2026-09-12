package com.pouchvision.inspector

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/*
 * =============================================================
 * Telegram 전송 이력 저장소
 * =============================================================
 *
 * 목적
 * - Telegram 자동전송 성공 / 실패 이력을 별도로 저장합니다.
 * - 실패한 경우 재전송에 사용할 결과 이미지를 앱 내부에 보관합니다.
 * - 검사 이력 화면에서 나중에 "전송 성공 / 실패"를 확인하고
 *   재전송 버튼을 만들 수 있도록 준비합니다.
 *
 * 중요
 * - 이 파일을 추가하는 것만으로 기존 검사/Telegram 동작은 바뀌지 않습니다.
 * - 다음 단계에서 TelegramSender와 연결합니다.
 * =============================================================
 */

object TelegramDeliveryStore {

    private const val PREF_NAME =
        "telegram_delivery_history_pref"

    private const val KEY_HISTORY =
        "telegram_delivery_history_json"

    private const val MAX_HISTORY_COUNT =
        300

    private const val RETRY_IMAGE_FOLDER =
        "telegram_retry_images"

    private const val MAX_IMAGE_SIDE =
        1280

    private const val JPEG_QUALITY =
        88

    const val STATUS_SUCCESS =
        "SUCCESS"

    const val STATUS_FAILED =
        "FAILED"

    const val STATUS_PENDING =
        "PENDING"

    data class DeliveryRecord(
        val id: Long,
        val dateTime: String,
        val model: String,
        val line: String,
        val inspectionType: String,
        val score: Double,
        val judgment: String,
        val status: String,
        val successCount: Int,
        val failureCount: Int,
        val message: String,
        val imagePath: String,
        val retryCount: Int
    )

    /*
     * =========================================================
     * 신규 전송 시작(PENDING)
     * =========================================================
     */

    fun createDashboardSummaryPending(
        context: Context,
        model: String,
        line: String,
        intervalHours: Int,
        testMode: Boolean
    ): Long {

        return createPending(
            context = context,
            model = model,
            line = line,
            inspectionType = "DASHBOARD SUMMARY",
            score = 0.0,
            judgment =
                if (testMode) {
                    "TEST / ${intervalHours}시간"
                } else {
                    "AUTO / ${intervalHours}시간"
                },
            resultBitmap = null
        )
    }

    fun createPending(
        context: Context,
        model: String,
        line: String,
        inspectionType: String,
        score: Double,
        judgment: String,
        resultBitmap: Bitmap?
    ): Long {

        val id =
            System.currentTimeMillis()

        val imagePath =
            if (
                resultBitmap != null
            ) {
                saveRetryImage(
                    context = context,
                    bitmap = resultBitmap,
                    recordId = id
                )
                    ?: ""
            } else {
                ""
            }

        val record =
            JSONObject().apply {

                put(
                    "id",
                    id
                )

                put(
                    "dateTime",
                    InspectionTimeHelper.nowText()
                )

                put(
                    "model",
                    model
                )

                put(
                    "line",
                    line
                )

                put(
                    "inspectionType",
                    inspectionType
                )

                put(
                    "score",
                    score
                )

                put(
                    "judgment",
                    judgment
                )

                put(
                    "status",
                    STATUS_PENDING
                )

                put(
                    "successCount",
                    0
                )

                put(
                    "failureCount",
                    0
                )

                put(
                    "message",
                    "Telegram 전송 중"
                )

                put(
                    "imagePath",
                    imagePath
                )

                put(
                    "retryCount",
                    0
                )
            }

        prependRecord(
            context = context,
            record = record
        )

        return id
    }

    /*
     * =========================================================
     * 전송 결과 갱신
     * =========================================================
     */

    fun updateResult(
        context: Context,
        recordId: Long,
        success: Boolean,
        successCount: Int,
        failureCount: Int,
        message: String
    ) {

        val array =
            loadJsonArray(
                context
            )

        val newArray =
            JSONArray()

        for (
            i in 0 until
            array.length()
        ) {

            val item =
                array.optJSONObject(
                    i
                )
                    ?: continue

            if (
                item.optLong(
                    "id",
                    0L
                ) ==
                recordId
            ) {

                item.put(
                    "status",
                    if (
                        success
                    ) {
                        STATUS_SUCCESS
                    } else {
                        STATUS_FAILED
                    }
                )

                item.put(
                    "successCount",
                    successCount
                )

                item.put(
                    "failureCount",
                    failureCount
                )

                item.put(
                    "message",
                    message
                )

                /*
                 * 성공하면 재전송용 임시 이미지는 더 이상 필요 없습니다.
                 */
                if (
                    success
                ) {

                    val path =
                        item.optString(
                            "imagePath",
                            ""
                        )

                    deleteRetryImage(
                        context = context,
                        imagePath = path
                    )

                    item.put(
                        "imagePath",
                        ""
                    )
                }
            }

            newArray.put(
                item
            )
        }

        saveJsonArray(
            context = context,
            array = newArray
        )
    }

    /*
     * =========================================================
     * 재전송 횟수 증가
     * =========================================================
     */

    fun markRetryStarted(
        context: Context,
        recordId: Long
    ) {

        val array =
            loadJsonArray(
                context
            )

        val newArray =
            JSONArray()

        for (
            i in 0 until
            array.length()
        ) {

            val item =
                array.optJSONObject(
                    i
                )
                    ?: continue

            if (
                item.optLong(
                    "id",
                    0L
                ) ==
                recordId
            ) {

                item.put(
                    "status",
                    STATUS_PENDING
                )

                item.put(
                    "message",
                    "Telegram 재전송 중"
                )

                item.put(
                    "retryCount",
                    item.optInt(
                        "retryCount",
                        0
                    ) + 1
                )
            }

            newArray.put(
                item
            )
        }

        saveJsonArray(
            context = context,
            array = newArray
        )
    }

    /*
     * =========================================================
     * 전체 이력 읽기
     * =========================================================
     */

    fun load(
        context: Context
    ): List<DeliveryRecord> {

        val array =
            loadJsonArray(
                context
            )

        val result =
            mutableListOf<DeliveryRecord>()

        for (
            i in 0 until
            array.length()
        ) {

            val item =
                array.optJSONObject(
                    i
                )
                    ?: continue

            result.add(
                DeliveryRecord(
                    id =
                        item.optLong(
                            "id",
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

                    judgment =
                        item.optString(
                            "judgment",
                            ""
                        ),

                    status =
                        item.optString(
                            "status",
                            STATUS_FAILED
                        ),

                    successCount =
                        item.optInt(
                            "successCount",
                            0
                        ),

                    failureCount =
                        item.optInt(
                            "failureCount",
                            0
                        ),

                    message =
                        item.optString(
                            "message",
                            ""
                        ),

                    imagePath =
                        item.optString(
                            "imagePath",
                            ""
                        ),

                    retryCount =
                        item.optInt(
                            "retryCount",
                            0
                        )
                )
            )
        }

        return result
    }

    fun find(
        context: Context,
        recordId: Long
    ): DeliveryRecord? {

        return load(
            context
        )
            .firstOrNull {
                it.id ==
                    recordId
            }
    }

    fun getRetryImageFile(
        record: DeliveryRecord
    ): File? {

        if (
            record.imagePath.isBlank()
        ) {
            return null
        }

        val file =
            File(
                record.imagePath
            )

        return if (
            file.exists() &&
            file.isFile
        ) {
            file
        } else {
            null
        }
    }

    /*
     * =========================================================
     * 최근 실패 건수
     * =========================================================
     */

    fun failedCount(
        context: Context
    ): Int {

        return load(
            context
        )
            .count {
                it.status ==
                    STATUS_FAILED
            }
    }

    /*
     * =========================================================
     * 전체 Telegram 전송 이력 삭제
     * =========================================================
     */

    fun clearAll(
        context: Context
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(
                KEY_HISTORY
            )
            .apply()

        try {

            val directory =
                File(
                    context.filesDir,
                    RETRY_IMAGE_FOLDER
                )

            if (
                directory.exists()
            ) {
                directory.deleteRecursively()
            }

        } catch (
            e: Exception
        ) {
            // 삭제 실패가 앱 종료로 이어지지 않도록 합니다.
        }
    }

    /*
     * =========================================================
     * 내부 JSON 처리
     * =========================================================
     */

    private fun prependRecord(
        context: Context,
        record: JSONObject
    ) {

        val oldArray =
            loadJsonArray(
                context
            )

        val newArray =
            JSONArray()

        newArray.put(
            record
        )

        val copyCount =
            minOf(
                oldArray.length(),
                MAX_HISTORY_COUNT - 1
            )

        for (
            i in 0 until
            copyCount
        ) {

            newArray.put(
                oldArray.getJSONObject(
                    i
                )
            )
        }

        /*
         * 최대 건수 초과로 제거되는 실패 이미지 정리
         */
        if (
            oldArray.length() >
            copyCount
        ) {

            for (
                i in copyCount until
                oldArray.length()
            ) {

                val oldItem =
                    oldArray.optJSONObject(
                        i
                    )

                val oldPath =
                    oldItem
                        ?.optString(
                            "imagePath",
                            ""
                        )
                        .orEmpty()

                deleteRetryImage(
                    context = context,
                    imagePath = oldPath
                )
            }
        }

        saveJsonArray(
            context = context,
            array = newArray
        )
    }

    private fun loadJsonArray(
        context: Context
    ): JSONArray {

        return try {

            val jsonText =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )
                    .getString(
                        KEY_HISTORY,
                        "[]"
                    )
                    ?: "[]"

            JSONArray(
                jsonText
            )

        } catch (
            e: Exception
        ) {

            JSONArray()
        }
    }

    private fun saveJsonArray(
        context: Context,
        array: JSONArray
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_HISTORY,
                array.toString()
            )
            .apply()
    }

    /*
     * =========================================================
     * 재전송용 이미지 저장
     * =========================================================
     */

    private fun saveRetryImage(
        context: Context,
        bitmap: Bitmap,
        recordId: Long
    ): String? {

        return try {

            val directory =
                File(
                    context.filesDir,
                    RETRY_IMAGE_FOLDER
                )

            if (
                !directory.exists()
            ) {

                val created =
                    directory.mkdirs()

                if (
                    !created &&
                    !directory.exists()
                ) {
                    return null
                }
            }

            val file =
                File(
                    directory,
                    "telegram_retry_$recordId.jpg"
                )

            val storageBitmap =
                resizeBitmapForStorage(
                    bitmap
                )

            FileOutputStream(
                file
            ).use { output ->

                val success =
                    storageBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        output
                    )

                if (
                    !success
                ) {
                    return null
                }

                output.flush()
            }

            file.absolutePath

        } catch (
            e: Exception
        ) {

            null
        }
    }

    private fun resizeBitmapForStorage(
        source: Bitmap
    ): Bitmap {

        val width =
            source.width

        val height =
            source.height

        if (
            width <= 0 ||
            height <= 0
        ) {
            return source
        }

        val longestSide =
            maxOf(
                width,
                height
            )

        if (
            longestSide <=
            MAX_IMAGE_SIDE
        ) {
            return source
        }

        val scale =
            MAX_IMAGE_SIDE
                .toFloat() /
                longestSide
                    .toFloat()

        val newWidth =
            maxOf(
                1,
                (
                    width *
                        scale
                    )
                    .toInt()
            )

        val newHeight =
            maxOf(
                1,
                (
                    height *
                        scale
                    )
                    .toInt()
            )

        return Bitmap.createScaledBitmap(
            source,
            newWidth,
            newHeight,
            true
        )
    }

    private fun deleteRetryImage(
        context: Context,
        imagePath: String
    ) {

        if (
            imagePath.isBlank()
        ) {
            return
        }

        try {

            val allowedDirectory =
                File(
                    context.filesDir,
                    RETRY_IMAGE_FOLDER
                )
                    .canonicalFile

            val file =
                File(
                    imagePath
                )
                    .canonicalFile

            val allowedPrefix =
                allowedDirectory.path +
                    File.separator

            if (
                file.path.startsWith(
                    allowedPrefix
                ) &&
                file.exists()
            ) {

                file.delete()
            }

        } catch (
            e: Exception
        ) {
            // 안전 삭제 실패는 무시합니다.
        }
    }
}

/*
 * =============================================================
 * 공용 시간 문자열 Helper
 * =============================================================
 *
 * TelegramDeliveryStore 내부에서만 사용하는 소형 Helper입니다.
 * 별도 파일을 만들지 않기 위해 같은 파일에 두었습니다.
 * =============================================================
 */

private object InspectionTimeHelper {

    fun nowText(): String {

        val format =
            java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.getDefault()
            )

        return format.format(
            java.util.Date()
        )
    }
}
