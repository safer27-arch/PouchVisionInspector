package com.pouchvision.inspector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Model / Line별 검사 기준값 변경 이력 저장소.
 *
 * 이 단계에서는 이력 저장소만 추가합니다.
 * 다음 단계에서 InspectionSpecStore.save/reset과 연결하면
 * 기준값 저장/기본값 복원 시 변경 이력이 자동으로 쌓입니다.
 */
object InspectionSpecChangeHistoryStore {

    private const val PREF_NAME =
        "inspection_spec_change_history"

    private const val KEY_HISTORY =
        "history_json"

    /**
     * 휴대폰 내부 저장 공간이 불필요하게 커지는 것을 막기 위해
     * 최신 500건까지만 유지합니다.
     */
    private const val MAX_ENTRIES = 500

    enum class ChangeAction {
        SAVED,
        RESET_TO_DEFAULT
    }

    data class ChangeRecord(
        val id: String,
        val timestamp: Long,
        val model: String,
        val line: String,
        val inspectionType: InspectionSpecStore.InspectionType,
        val action: ChangeAction,
        val oldNormalBoundary: Double?,
        val oldWarningBoundary: Double?,
        val oldLimitBoundary: Double?,
        val newNormalBoundary: Double,
        val newWarningBoundary: Double,
        val newLimitBoundary: Double
    )

    fun add(
        context: Context,
        model: String,
        line: String,
        inspectionType: InspectionSpecStore.InspectionType,
        action: ChangeAction,
        oldSpec: InspectionSpecStore.InspectionSpec?,
        newSpec: InspectionSpecStore.InspectionSpec
    ) {
        if (
            model.isBlank() ||
            line.isBlank()
        ) {
            return
        }

        val record =
            ChangeRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                model = model,
                line = line,
                inspectionType = inspectionType,
                action = action,
                oldNormalBoundary = oldSpec?.normalBoundary,
                oldWarningBoundary = oldSpec?.warningBoundary,
                oldLimitBoundary = oldSpec?.limitBoundary,
                newNormalBoundary = newSpec.normalBoundary,
                newWarningBoundary = newSpec.warningBoundary,
                newLimitBoundary = newSpec.limitBoundary
            )

        val records =
            getAll(context)
                .toMutableList()

        records.add(
            0,
            record
        )

        if (
            records.size > MAX_ENTRIES
        ) {
            records.subList(
                MAX_ENTRIES,
                records.size
            ).clear()
        }

        saveAll(
            context = context,
            records = records
        )
    }

    fun getAll(
        context: Context
    ): List<ChangeRecord> {
        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val raw =
            prefs.getString(
                KEY_HISTORY,
                null
            )
                ?: return emptyList()

        return try {
            val array =
                JSONArray(raw)

            val records =
                mutableListOf<ChangeRecord>()

            for (
                index in 0 until array.length()
            ) {
                val item =
                    array.optJSONObject(index)
                        ?: continue

                fromJson(item)
                    ?.let {
                        records.add(it)
                    }
            }

            records.sortedByDescending {
                it.timestamp
            }

        } catch (
            _: Exception
        ) {
            emptyList()
        }
    }

    fun getForModelLine(
        context: Context,
        model: String,
        line: String
    ): List<ChangeRecord> {
        return getAll(context)
            .filter {
                it.model == model &&
                    it.line == line
            }
    }

    fun getForModelLineAndType(
        context: Context,
        model: String,
        line: String,
        inspectionType: InspectionSpecStore.InspectionType
    ): List<ChangeRecord> {
        return getAll(context)
            .filter {
                it.model == model &&
                    it.line == line &&
                    it.inspectionType == inspectionType
            }
    }

    fun clearAll(
        context: Context
    ) {
        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    private fun saveAll(
        context: Context,
        records: List<ChangeRecord>
    ) {
        val array =
            JSONArray()

        records.forEach {
            array.put(
                toJson(it)
            )
        }

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

    private fun toJson(
        record: ChangeRecord
    ): JSONObject {
        return JSONObject().apply {
            put(
                "id",
                record.id
            )
            put(
                "timestamp",
                record.timestamp
            )
            put(
                "model",
                record.model
            )
            put(
                "line",
                record.line
            )
            put(
                "inspectionType",
                record.inspectionType.name
            )
            put(
                "action",
                record.action.name
            )

            putNullableDouble(
                key = "oldNormalBoundary",
                value = record.oldNormalBoundary
            )
            putNullableDouble(
                key = "oldWarningBoundary",
                value = record.oldWarningBoundary
            )
            putNullableDouble(
                key = "oldLimitBoundary",
                value = record.oldLimitBoundary
            )

            put(
                "newNormalBoundary",
                record.newNormalBoundary
            )
            put(
                "newWarningBoundary",
                record.newWarningBoundary
            )
            put(
                "newLimitBoundary",
                record.newLimitBoundary
            )
        }
    }

    private fun fromJson(
        item: JSONObject
    ): ChangeRecord? {
        return try {
            val inspectionType =
                InspectionSpecStore.InspectionType.valueOf(
                    item.getString(
                        "inspectionType"
                    )
                )

            val action =
                ChangeAction.valueOf(
                    item.getString(
                        "action"
                    )
                )

            ChangeRecord(
                id = item.optString(
                    "id",
                    UUID.randomUUID().toString()
                ),
                timestamp = item.optLong(
                    "timestamp",
                    0L
                ),
                model = item.optString(
                    "model",
                    ""
                ),
                line = item.optString(
                    "line",
                    ""
                ),
                inspectionType = inspectionType,
                action = action,
                oldNormalBoundary = item.optNullableDouble(
                    "oldNormalBoundary"
                ),
                oldWarningBoundary = item.optNullableDouble(
                    "oldWarningBoundary"
                ),
                oldLimitBoundary = item.optNullableDouble(
                    "oldLimitBoundary"
                ),
                newNormalBoundary = item.getDouble(
                    "newNormalBoundary"
                ),
                newWarningBoundary = item.getDouble(
                    "newWarningBoundary"
                ),
                newLimitBoundary = item.getDouble(
                    "newLimitBoundary"
                )
            )

        } catch (
            _: Exception
        ) {
            null
        }
    }

    private fun JSONObject.putNullableDouble(
        key: String,
        value: Double?
    ) {
        if (
            value == null
        ) {
            put(
                key,
                JSONObject.NULL
            )
        } else {
            put(
                key,
                value
            )
        }
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return try {
            getDouble(key)
        } catch (
            _: Exception
        ) {
            null
        }
    }
}
