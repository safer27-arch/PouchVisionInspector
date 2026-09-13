package com.pouchvision.inspector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MoldMaintenanceStore {

    private const val PREF_NAME = "mold_maintenance_pref"
    private const val KEY_STATES = "states_json"
    private const val KEY_HISTORY = "history_json"

    data class MoldState(
        val model: String,
        val line: String,
        val moldId: String = "M-01",
        val punchId: String = "P-01",
        val totalShot: Int = 0,
        val shotSinceClean: Int = 0,
        val cleaningLimit: Int = 1000,
        val replacementLimit: Int = 50000,
        val lastCleanTime: Long = 0L,
        val lastReplaceTime: Long = 0L,
        val cleanAlertLevel: Int = 0,
        val replaceAlertLevel: Int = 0
    )

    data class HistoryRecord(
        val id: Long,
        val dateTime: String,
        val model: String,
        val line: String,
        val moldId: String,
        val punchId: String,
        val action: String,
        val shotValue: Int,
        val note: String
    )

    fun loadState(
        context: Context,
        model: String,
        line: String
    ): MoldState {
        val states = loadStates(context)
        val key = stateKey(model, line)
        val obj = states.optJSONObject(key)

        return if (obj == null) {
            MoldState(model = model, line = line)
        } else {
            MoldState(
                model = model,
                line = line,
                moldId = obj.optString("moldId", "M-01"),
                punchId = obj.optString("punchId", "P-01"),
                totalShot = obj.optInt("totalShot", 0),
                shotSinceClean = obj.optInt("shotSinceClean", 0),
                cleaningLimit = obj.optInt("cleaningLimit", 1000).coerceAtLeast(1),
                replacementLimit = obj.optInt("replacementLimit", 50000).coerceAtLeast(1),
                lastCleanTime = obj.optLong("lastCleanTime", 0L),
                lastReplaceTime = obj.optLong("lastReplaceTime", 0L),
                cleanAlertLevel = obj.optInt("cleanAlertLevel", 0),
                replaceAlertLevel = obj.optInt("replaceAlertLevel", 0)
            )
        }
    }

    fun saveSettings(
        context: Context,
        state: MoldState
    ) {
        saveState(context, state)
    }

    fun addShot(
        context: Context,
        model: String,
        line: String,
        amount: Int,
        note: String = ""
    ): MoldState {
        val current = loadState(context, model, line)
        val safe = amount.coerceAtLeast(0)

        val updated = current.copy(
            totalShot = current.totalShot + safe,
            shotSinceClean = current.shotSinceClean + safe
        )

        saveState(context, updated)
        addHistory(
            context = context,
            state = updated,
            action = "SHOT +$safe",
            shotValue = updated.totalShot,
            note = note
        )
        return updated
    }

    fun setShot(
        context: Context,
        model: String,
        line: String,
        totalShot: Int,
        shotSinceClean: Int? = null,
        note: String = ""
    ): MoldState {
        val current = loadState(context, model, line)
        val safeTotal = totalShot.coerceAtLeast(0)
        val safeClean = (shotSinceClean ?: safeTotal).coerceAtLeast(0)

        val updated = current.copy(
            totalShot = safeTotal,
            shotSinceClean = safeClean
        )

        saveState(context, updated)
        addHistory(
            context = context,
            state = updated,
            action = "SHOT 직접입력",
            shotValue = updated.totalShot,
            note = note
        )
        return updated
    }

    fun markCleaned(
        context: Context,
        model: String,
        line: String,
        note: String = ""
    ): MoldState {
        val current = loadState(context, model, line)
        val now = System.currentTimeMillis()

        val updated = current.copy(
            shotSinceClean = 0,
            lastCleanTime = now,
            cleanAlertLevel = 0
        )

        saveState(context, updated)
        addHistory(
            context = context,
            state = updated,
            action = "청소 완료",
            shotValue = updated.totalShot,
            note = note
        )
        return updated
    }

    fun markReplaced(
        context: Context,
        model: String,
        line: String,
        note: String = ""
    ): MoldState {
        val current = loadState(context, model, line)
        val now = System.currentTimeMillis()

        val updated = current.copy(
            totalShot = 0,
            shotSinceClean = 0,
            lastReplaceTime = now,
            lastCleanTime = now,
            cleanAlertLevel = 0,
            replaceAlertLevel = 0
        )

        saveState(context, updated)
        addHistory(
            context = context,
            state = current,
            action = "금형/Punch 교체 완료",
            shotValue = current.totalShot,
            note = note
        )
        return updated
    }

    fun updateAlertLevels(
        context: Context,
        state: MoldState,
        cleanLevel: Int,
        replaceLevel: Int
    ) {
        saveState(
            context,
            state.copy(
                cleanAlertLevel = cleanLevel,
                replaceAlertLevel = replaceLevel
            )
        )
    }

    fun loadHistory(
        context: Context,
        model: String? = null,
        line: String? = null
    ): List<HistoryRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        val result = mutableListOf<HistoryRecord>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val record = HistoryRecord(
                id = item.optLong("id", 0L),
                dateTime = item.optString("dateTime", ""),
                model = item.optString("model", ""),
                line = item.optString("line", ""),
                moldId = item.optString("moldId", ""),
                punchId = item.optString("punchId", ""),
                action = item.optString("action", ""),
                shotValue = item.optInt("shotValue", 0),
                note = item.optString("note", "")
            )

            if (
                (model == null || record.model == model) &&
                (line == null || record.line == line)
            ) {
                result.add(record)
            }
        }

        return result.sortedByDescending { it.id }
    }

    fun csvText(
        context: Context,
        model: String? = null,
        line: String? = null
    ): String {
        val rows = loadHistory(context, model, line)
        return buildString {
            append("DateTime,Model,Line,Mold ID,Punch ID,Action,Shot,Note\n")
            rows.forEach {
                append(csv(it.dateTime)).append(",")
                append(csv(it.model)).append(",")
                append(csv(it.line)).append(",")
                append(csv(it.moldId)).append(",")
                append(csv(it.punchId)).append(",")
                append(csv(it.action)).append(",")
                append(it.shotValue).append(",")
                append(csv(it.note)).append("\n")
            }
        }
    }

    fun alertLevel(
        value: Int,
        limit: Int
    ): Int {
        if (limit <= 0) return 0
        val ratio = value.toDouble() / limit.toDouble()

        return when {
            ratio >= 1.0 -> 3
            ratio >= 0.95 -> 2
            ratio >= 0.80 -> 1
            else -> 0
        }
    }

    private fun saveState(
        context: Context,
        state: MoldState
    ) {
        val states = loadStates(context)
        val obj = JSONObject().apply {
            put("moldId", state.moldId)
            put("punchId", state.punchId)
            put("totalShot", state.totalShot)
            put("shotSinceClean", state.shotSinceClean)
            put("cleaningLimit", state.cleaningLimit)
            put("replacementLimit", state.replacementLimit)
            put("lastCleanTime", state.lastCleanTime)
            put("lastReplaceTime", state.lastReplaceTime)
            put("cleanAlertLevel", state.cleanAlertLevel)
            put("replaceAlertLevel", state.replaceAlertLevel)
        }
        states.put(stateKey(state.model, state.line), obj)

        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATES, states.toString())
            .apply()
    }

    private fun loadStates(context: Context): JSONObject {
        val text = context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STATES, "{}")
            ?: "{}"

        return try {
            JSONObject(text)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun addHistory(
        context: Context,
        state: MoldState,
        action: String,
        shotValue: Int,
        note: String
    ) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldArray = try {
            JSONArray(prefs.getString(KEY_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val item = JSONObject().apply {
            put("id", now)
            put("dateTime", formatter.format(Date(now)))
            put("model", state.model)
            put("line", state.line)
            put("moldId", state.moldId)
            put("punchId", state.punchId)
            put("action", action)
            put("shotValue", shotValue)
            put("note", note.trim())
        }

        val newArray = JSONArray()
        newArray.put(item)

        for (i in 0 until minOf(oldArray.length(), 499)) {
            newArray.put(oldArray.opt(i))
        }

        prefs.edit()
            .putString(KEY_HISTORY, newArray.toString())
            .apply()
    }

    private fun stateKey(
        model: String,
        line: String
    ): String {
        return model.trim() + "||" + line.trim()
    }

    private fun csv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
