package com.pouchvision.inspector

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object InspectionHistoryStore {

    private const val PREF_NAME =
        "pouch_vision_history"

    private const val KEY_HISTORY =
        "inspection_history_json"

    private const val MAX_HISTORY_COUNT =
        300

    /*
     * 검사 결과 사진을 저장할
     * 앱 내부 전용 폴더
     */
    private const val IMAGE_FOLDER =
        "inspection_images"

    /*
     * 저장 사진 최대 크기
     */
    private const val MAX_IMAGE_SIDE =
        1280

    /*
     * JPEG 저장 품질
     */
    private const val JPEG_QUALITY =
        85

    /*
     * =========================================================
     * 검사 이력 1건
     * =========================================================
     */

    data class InspectionRecord(

        val id: Long,

        val dateTime: String,

        val inspectionType: String,

        val score: Double,

        val judgment: String,

        val sensitivity: Int,

        val details: String,

        /*
         * 검사 결과 사진 경로
         *
         * 과거 이력에는 값이 없을 수 있으므로
         * 기본값은 빈 문자열입니다.
         */
        val imagePath: String =
            "",

        /*
         * 종합검사 Session ID
         *
         * 단독 검사:
         *   ""
         *
         * 종합검사:
         *   같은 5개 검사 결과가 동일한 sessionId를 가집니다.
         *
         * 과거 이력과 기존 단독 검사는 빈 문자열로 유지됩니다.
         */
        val sessionId: String =
            "",

        /*
         * 생산 조건
         *
         * Model / Line 선택 기능 추가 이후 저장되는 검사 결과에는
         * 당시 선택되어 있던 생산 조건을 함께 보관합니다.
         *
         * 과거 이력에는 해당 값이 없으므로 빈 문자열을 기본값으로
         * 사용하여 기존 데이터와 호환되도록 합니다.
         */
        val model: String =
            "",

        val line: String =
            ""
    )

    /*
     * =========================================================
     * 검사 결과 저장
     *
     * imageBitmap / sessionId는 선택사항입니다.
     *
     * 따라서 기존 저장 코드도 그대로 동작합니다.
     * =========================================================
     */

    fun save(
        context: Context,
        inspectionType: String,
        score: Double,
        judgment: String,
        sensitivity: Int,
        details: String,
        imageBitmap: Bitmap? = null,
        sessionId: String = ""
    ): Boolean {

        var newlySavedImagePath =
            ""

        return try {

            val prefs =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )

            val oldData =
                prefs.getString(
                    KEY_HISTORY,
                    "[]"
                ) ?: "[]"

            val oldArray =
                JSONArray(
                    oldData
                )

            /*
             * 고유 ID
             */
            val now =
                System.currentTimeMillis()

            /*
             * =================================================
             * 결과 사진 저장
             * =================================================
             */

            if (
                imageBitmap != null
            ) {

                newlySavedImagePath =
                    saveInspectionImage(
                        context = context,
                        bitmap = imageBitmap,
                        recordId = now
                    )
                        ?: return false
            }

            val dateFormat =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                )

            /*
             * =================================================
             * 현재 생산 조건(Model / Line)
             * =================================================
             *
             * 모든 검사 화면이 InspectionHistoryStore.save()를
             * 공통으로 사용하므로 여기에서 한 번만 처리하면
             * Bottom Corner / Seal / Forming / Tab / 분해검사 /
             * 종합검사 결과에 동일하게 적용됩니다.
             */
            val productionContext =
                ProductionContextStore.getCurrent(
                    context
                )

            val record =
                JSONObject()

            record.put(
                "id",
                now
            )

            record.put(
                "dateTime",
                dateFormat.format(
                    Date(now)
                )
            )

            record.put(
                "inspectionType",
                inspectionType
            )

            record.put(
                "score",
                score
            )

            record.put(
                "judgment",
                judgment
            )

            record.put(
                "sensitivity",
                sensitivity
            )

            record.put(
                "details",
                details
            )

            record.put(
                "imagePath",
                newlySavedImagePath
            )

            /*
             * 새로 추가:
             * 종합검사에서만 값이 들어갑니다.
             */
            record.put(
                "sessionId",
                sessionId
            )

            /*
             * 생산 조건 저장
             */
            record.put(
                "model",
                productionContext.model
            )

            record.put(
                "line",
                productionContext.line
            )

            /*
             * =================================================
             * 새 결과를 가장 위에 저장
             * =================================================
             */

            val newArray =
                JSONArray()

            newArray.put(
                record
            )

            /*
             * =================================================
             * 기존 이력 복사
             *
             * 최대 300건
             * =================================================
             */

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
             * =================================================
             * 300건을 초과해 제거되는 오래된 이력의
             * 결과 사진 경로 수집
             * =================================================
             */

            val oldImagePathsToDelete =
                mutableListOf<String>()

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

                    val oldImagePath =
                        oldItem
                            ?.optString(
                                "imagePath",
                                ""
                            )
                            ?: ""

                    if (
                        oldImagePath.isNotBlank()
                    ) {

                        oldImagePathsToDelete.add(
                            oldImagePath
                        )
                    }
                }
            }

            /*
             * =================================================
             * SharedPreferences 저장
             * =================================================
             */

            val saved =
                prefs.edit()
                    .putString(
                        KEY_HISTORY,
                        newArray.toString()
                    )
                    .commit()

            if (
                !saved
            ) {

                if (
                    newlySavedImagePath.isNotBlank()
                ) {

                    deleteImagePath(
                        context,
                        newlySavedImagePath
                    )
                }

                return false
            }

            /*
             * 오래된 이력과 함께 제거된 사진 삭제
             */
            oldImagePathsToDelete
                .forEach {

                    deleteImagePath(
                        context,
                        it
                    )
                }

            true

        } catch (
            e: Exception
        ) {

            /*
             * 문자 이력 저장 실패 시
             * 이번에 새로 생성된 사진만 남지 않도록 삭제
             */
            if (
                newlySavedImagePath.isNotBlank()
            ) {

                deleteImagePath(
                    context,
                    newlySavedImagePath
                )
            }

            false
        }
    }

    /*
     * =========================================================
     * 전체 검사 이력 읽기
     * =========================================================
     */

    fun load(
        context: Context
    ): List<InspectionRecord> {

        val result =
            mutableListOf<InspectionRecord>()

        try {

            val prefs =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )

            val jsonText =
                prefs.getString(
                    KEY_HISTORY,
                    "[]"
                ) ?: "[]"

            val array =
                JSONArray(
                    jsonText
                )

            for (
                i in 0 until
                    array.length()
            ) {

                val item =
                    array.getJSONObject(
                        i
                    )

                result.add(
                    InspectionRecord(

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

                        sensitivity =
                            item.optInt(
                                "sensitivity",
                                60
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

                        /*
                         * 기존 이력에는 sessionId가 없으므로
                         * 자동으로 빈 문자열 처리됩니다.
                         */
                        sessionId =
                            item.optString(
                                "sessionId",
                                ""
                            ),

                        /*
                         * 기존 이력에는 model / line이 없으므로
                         * 빈 문자열로 자동 처리합니다.
                         */
                        model =
                            item.optString(
                                "model",
                                ""
                            ),

                        line =
                            item.optString(
                                "line",
                                ""
                            )
                    )
                )
            }

        } catch (
            e: Exception
        ) {

            return emptyList()
        }

        return result
    }

    /*
     * =========================================================
     * 특정 종합검사 Session의 기록 읽기
     * =========================================================
     */

    fun loadSession(
        context: Context,
        sessionId: String
    ): List<InspectionRecord> {

        if (
            sessionId.isBlank()
        ) {

            return emptyList()
        }

        return load(
            context
        )
            .filter {

                it.sessionId ==
                    sessionId
            }
            .sortedBy {

                it.id
            }
    }

    /*
     * =========================================================
     * 결과 사진 저장
     * =========================================================
     */

    private fun saveInspectionImage(
        context: Context,
        bitmap: Bitmap,
        recordId: Long
    ): String? {

        return try {

            val imageDirectory =
                File(
                    context.filesDir,
                    IMAGE_FOLDER
                )

            if (
                !imageDirectory.exists()
            ) {

                val created =
                    imageDirectory.mkdirs()

                if (
                    !created &&
                    !imageDirectory.exists()
                ) {

                    return null
                }
            }

            val imageFile =
                File(
                    imageDirectory,
                    "inspection_$recordId.jpg"
                )

            val storageBitmap =
                resizeBitmapForStorage(
                    bitmap
                )

            FileOutputStream(
                imageFile
            ).use {

                val compressed =
                    storageBitmap.compress(
                        Bitmap.CompressFormat.JPEG,
                        JPEG_QUALITY,
                        it
                    )

                if (
                    !compressed
                ) {

                    throw Exception(
                        "이미지 압축 실패"
                    )
                }

                it.flush()
            }

            imageFile.absolutePath

        } catch (
            e: Exception
        ) {

            null
        }
    }

    /*
     * =========================================================
     * 결과 사진 크기 조절
     * =========================================================
     */

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

    /*
     * =========================================================
     * 저장된 결과 사진 File 가져오기
     * =========================================================
     */

    fun getImageFile(
        record: InspectionRecord
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
     * 특정 이미지 안전 삭제
     * =========================================================
     */

    private fun deleteImagePath(
        context: Context,
        imagePath: String
    ) {

        if (
            imagePath.isBlank()
        ) {

            return
        }

        try {

            val imageDirectory =
                File(
                    context.filesDir,
                    IMAGE_FOLDER
                )
                    .canonicalFile

            val imageFile =
                File(
                    imagePath
                )
                    .canonicalFile

            val allowedPrefix =
                imageDirectory.path +
                    File.separator

            if (
                imageFile.path.startsWith(
                    allowedPrefix
                )
            ) {

                if (
                    imageFile.exists()
                ) {

                    imageFile.delete()
                }
            }

        } catch (
            e: Exception
        ) {

            /*
             * 사진 삭제 실패가 앱 종료로 이어지지 않도록 합니다.
             */
        }
    }

    /*
     * =========================================================
     * 모든 이력 삭제
     *
     * 문자 이력 + 결과 사진 모두 삭제
     * =========================================================
     */

    fun clearAll(
        context: Context
    ) {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .remove(
                KEY_HISTORY
            )
            .apply()

        try {

            val imageDirectory =
                File(
                    context.filesDir,
                    IMAGE_FOLDER
                )

            if (
                imageDirectory.exists()
            ) {

                imageDirectory
                    .deleteRecursively()
            }

        } catch (
            e: Exception
        ) {

            /*
             * 이미지 삭제 오류가 있어도 앱은 계속 동작합니다.
             */
        }
    }
}
