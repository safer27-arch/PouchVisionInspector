package com.pouchvision.inspector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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


    data class InspectionRecord(
        val id: Long,
        val dateTime: String,
        val inspectionType: String,
        val score: Double,
        val judgment: String,
        val sensitivity: Int,
        val details: String
    )


    fun save(
        context: Context,
        inspectionType: String,
        score: Double,
        judgment: String,
        sensitivity: Int,
        details: String
    ): Boolean {

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
                JSONArray(oldData)

            val newArray =
                JSONArray()


            /*
             * 새 검사 결과
             */
            val now =
                System.currentTimeMillis()

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
             * 가장 최근 결과를 위쪽에 저장
             */
            newArray.put(
                record
            )


            /*
             * 기존 이력 추가
             *
             * 휴대폰 저장 공간과 화면 속도를 고려하여
             * 최대 300건까지만 유지
             */
            val copyCount =
                minOf(
                    oldArray.length(),
                    MAX_HISTORY_COUNT - 1
                )

            for (i in 0 until copyCount) {

                newArray.put(
                    oldArray.getJSONObject(i)
                )
            }


            prefs.edit()
                .putString(
                    KEY_HISTORY,
                    newArray.toString()
                )
                .apply()

            true

        } catch (e: Exception) {

            false
        }
    }


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
                JSONArray(jsonText)


            for (i in 0 until array.length()) {

                val item =
                    array.getJSONObject(i)

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
                            )
                    )
                )
            }

        } catch (e: Exception) {

            return emptyList()
        }

        return result
    }


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
    }
}
