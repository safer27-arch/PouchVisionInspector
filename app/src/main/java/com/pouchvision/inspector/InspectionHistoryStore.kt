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
     *
     * 너무 큰 원본 사진을 그대로 300장 저장하면
     * 휴대폰 저장공간을 많이 사용할 수 있으므로
     * 긴 변 기준 최대 1280 pixel로 저장합니다.
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
         * 새로 추가
         *
         * 과거 이력에는 값이 없을 수 있으므로
         * 기본값은 빈 문자열
         */
        val imagePath: String =
            ""
    )

    /*
     * =========================================================
     * 검사 결과 저장
     *
     * imageBitmap은 선택 사항입니다.
     *
     * 따라서 기존 코드:
     *
     * InspectionHistoryStore.save(...)
     *
     * 는 그대로 사용할 수 있습니다.
     *
     * 결과 사진을 저장하고 싶은 검사만:
     *
     * imageBitmap = 결과Bitmap
     *
     * 을 추가하면 됩니다.
     * =========================================================
     */

    fun save(
        context: Context,
        inspectionType: String,
        score: Double,
        judgment: String,
        sensitivity: Int,
        details: String,
        imageBitmap: Bitmap? = null
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
             *
             * imageBitmap이 없으면
             * 기존 방식처럼 문자 이력만 저장됩니다.
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

            /*
             * 새로 추가
             */
            record.put(
                "imagePath",
                newlySavedImagePath
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
             * 300건을 초과해 삭제되는 오래된 이력의
             * 사진 경로를 미리 수집
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
             *
             * commit()을 사용해서 실제 저장 성공 여부를
             * 확인합니다.
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

                /*
                 * 문자 이력이 저장되지 않았다면
                 * 이번에 만든 사진도 삭제
                 */
                if (
                    newlySavedImagePath
                        .isNotBlank()
                ) {

                    deleteImagePath(
                        context,
                        newlySavedImagePath
                    )
                }

                return false
            }

            /*
             * =================================================
             * 이력이 300건을 넘어가면서 제거된
             * 오래된 사진 삭제
             * =================================================
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
             * 저장 중 오류가 발생했는데
             * 사진 파일만 만들어진 경우
             * 남지 않도록 삭제
             */
            if (
                newlySavedImagePath
                    .isNotBlank()
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

                        /*
                         * 기존에 저장된 과거 이력에는
                         * imagePath가 없으므로 빈 문자열 처리
                         */
                        imagePath =
                            item.optString(
                                "imagePath",
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

            /*
             * 검사 종류와 관계없이
             * recordId가 고유 파일명이 됩니다.
             */
            val imageFile =
                File(
                    imageDirectory,
                    "inspection_$recordId.jpg"
                )

            /*
             * 저장공간 절약을 위해
             * 필요하면 크기를 줄입니다.
             */
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

        /*
         * 이미 1280 이하라면
         * 원본 Bitmap 그대로 사용
         */
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
     * 저장된 사진 파일 가져오기
     *
     * 나중에 HistoryActivity에서 사용합니다.
     * =========================================================
     */

    fun getImageFile(
        record: InspectionRecord
    ): File? {

        if (
            record.imagePath
                .isBlank()
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

            /*
             * 앱의 inspection_images 폴더 안에 있는
             * 파일만 삭제합니다.
             */
            val allowedPrefix =
                imageDirectory.path +
                    File.separator

            if (
                imageFile.path
                    .startsWith(
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
             * 사진 삭제 실패가
             * 앱 종료로 이어지지 않도록 합니다.
             */
        }
    }

    /*
     * =========================================================
     * 모든 이력 삭제
     *
     * 문자 이력 + 검사 사진 모두 삭제
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

        /*
         * 앱 내부 검사 사진 폴더 삭제
         */
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
             * 이미지 삭제 오류가 있어도
             * 앱은 계속 동작
             */
        }
    }
}
