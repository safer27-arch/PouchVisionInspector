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
     * 종합검사 활성 Session
     *
     * TotalInspectionActivity에서 종합검사를 시작하면
     * 이 값을 저장합니다.
     *
     * 이후 Bottom Corner / Seal / Forming / Tab / Disassembly의
     * 기존 저장 코드를 다시 수정하지 않아도,
     * InspectionHistoryStore.save()가 자동으로 현재 Session ID를
     * 검사 결과에 넣어줍니다.
     */
    private const val KEY_ACTIVE_SESSION_ID =
        "active_total_inspection_session_id"

    private const val KEY_ACTIVE_SESSION_START =
        "active_total_inspection_session_start"

    /*
     * 검사 결과 사진 저장 폴더
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
         * 결과 사진 경로
         */
        val imagePath: String =
            "",

        /*
         * 종합검사 Session ID
         *
         * 단독 검사 : ""
         * 종합 검사 : 같은 5개 검사가 동일한 ID
         */
        val sessionId: String =
            ""
    )

    /*
     * =========================================================
     * 종합검사 Session 시작
     * =========================================================
     */

    fun beginSession(
        context: Context,
        sessionId: String
    ): Boolean {

        if (
            sessionId.isBlank()
        ) {

            return false
        }

        return try {

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
                .edit()
                .putString(
                    KEY_ACTIVE_SESSION_ID,
                    sessionId
                )
                .putLong(
                    KEY_ACTIVE_SESSION_START,
                    System.currentTimeMillis()
                )
                .commit()

        } catch (
            e: Exception
        ) {

            false
        }
    }

    /*
     * =========================================================
     * 현재 활성 종합검사 Session ID
     * =========================================================
     */

    fun getActiveSessionId(
        context: Context
    ): String {

        return try {

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
                .getString(
                    KEY_ACTIVE_SESSION_ID,
                    ""
                )
                ?: ""

        } catch (
            e: Exception
        ) {

            ""
        }
    }

    /*
     * =========================================================
     * 현재 활성 종합검사 시작 시각
     * =========================================================
     */

    fun getActiveSessionStart(
        context: Context
    ): Long {

        return try {

            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )
                .getLong(
                    KEY_ACTIVE_SESSION_START,
                    0L
                )

        } catch (
            e: Exception
        ) {

            0L
        }
    }

    /*
     * =========================================================
     * 종합검사 Session 종료
     *
     * expectedSessionId가 들어오면 현재 활성 ID와 같을 때만
     * 종료합니다.
     * =========================================================
     */

    fun endSession(
        context: Context,
        expectedSessionId: String = ""
    ) {

        try {

            val prefs =
                context.getSharedPreferences(
                    PREF_NAME,
                    Context.MODE_PRIVATE
                )

            val current =
                prefs.getString(
                    KEY_ACTIVE_SESSION_ID,
                    ""
                ) ?: ""

            if (
                expectedSessionId.isNotBlank() &&
                current.isNotBlank() &&
                current != expectedSessionId
            ) {

                return
            }

            prefs.edit()
                .remove(
                    KEY_ACTIVE_SESSION_ID
                )
                .remove(
                    KEY_ACTIVE_SESSION_START
                )
                .apply()

        } catch (
            e: Exception
        ) {

            /*
             * Session 종료 실패가 앱 종료로 이어지지 않게 합니다.
             */
        }
    }

    /*
     * =========================================================
     * 검사 결과 저장
     *
     * imageBitmap / sessionId는 선택사항입니다.
     *
     * 중요:
     * sessionId를 직접 전달하지 않아도 현재 종합검사가 활성 상태면
     * 자동으로 활성 Session ID를 사용합니다.
     *
     * 따라서 현재 5개 검사 Activity의 저장 코드를 다시 수정하지
     * 않아도 됩니다.
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
             * 종합검사 Activity가 활성화되어 있으면
             * 기존 개별 검사 저장 코드에도 자동으로 같은 Session ID 부여
             */
            val effectiveSessionId =
                if (
                    sessionId.isNotBlank()
                ) {

                    sessionId

                } else {

                    prefs.getString(
                        KEY_ACTIVE_SESSION_ID,
                        ""
                    ) ?: ""
                }

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

            record.put(
                "sessionId",
                effectiveSessionId
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
             * 최대 건수를 넘어 제거되는 사진 경로 수집
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

                        sessionId =
                            item.optString(
                                "sessionId",
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
     * 특정 종합검사 Session 기록 읽기
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
     * 종합검사 Session 목록
     *
     * History 화면에서 나중에 종합검사 묶음을 표시할 때 사용합니다.
     * =========================================================
     */

    fun loadSessionIds(
        context: Context
    ): List<String> {

        return load(
            context
        )
            .map {

                it.sessionId
            }
            .filter {

                it.isNotBlank()
            }
            .distinct()
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
     * 저장 결과 사진 File
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
             * 사진 삭제 실패가 앱 종료로 이어지지 않게 합니다.
             */
        }
    }

    /*
     * =========================================================
     * 모든 이력 삭제
     *
     * 검사 이력 + 사진 + 활성 Session 상태 모두 삭제
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
            .remove(
                KEY_ACTIVE_SESSION_ID
            )
            .remove(
                KEY_ACTIVE_SESSION_START
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
