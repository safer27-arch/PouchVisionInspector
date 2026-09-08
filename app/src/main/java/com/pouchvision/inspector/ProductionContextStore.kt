package com.pouchvision.inspector

import android.content.Context

/*
 * =============================================================
 * 생산 조건(Model / Line) 공용 저장소
 * =============================================================
 *
 * 목적
 * - 메뉴에서 선택한 Model / Line을 앱 전체에서 공통으로 사용합니다.
 * - Bottom Corner / Seal / Forming / Tab / Disassembly / 종합검사에서
 *   같은 생산 조건을 유지할 수 있도록 합니다.
 * - 앱을 종료했다가 다시 실행해도 마지막 선택값을 기억합니다.
 *
 * 중요
 * - 이 파일은 검사 알고리즘이나 판정 기준을 변경하지 않습니다.
 * - 현재 단계에서는 Model / Line "선택 상태"를 저장하는 기능입니다.
 * =============================================================
 */

object ProductionContextStore {

    private const val PREF_NAME =
        "production_context_pref"

    private const val KEY_SELECTED_MODEL =
        "selected_model"

    private const val KEY_SELECTED_LINE =
        "selected_line"

    private const val KEY_MODEL_LIST =
        "model_list"

    private const val KEY_LINE_LIST =
        "line_list"

    /*
     * 초기 기본값
     *
     * 현장에서 원하는 이름으로 나중에 변경할 수 있습니다.
     */
    private val DEFAULT_MODELS =
        listOf(
            "Model A",
            "Model B",
            "Model C"
        )

    private val DEFAULT_LINES =
        listOf(
            "Line 1",
            "Line 2",
            "Line 3"
        )

    data class ProductionContext(
        val model: String,
        val line: String
    ) {

        fun displayText(): String {

            return "Model : $model  |  Line : $line"
        }
    }

    /*
     * =========================================================
     * 현재 선택값
     * =========================================================
     */

    fun getCurrent(
        context: Context
    ): ProductionContext {

        val models =
            getModels(
                context
            )

        val lines =
            getLines(
                context
            )

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val selectedModel =
            prefs.getString(
                KEY_SELECTED_MODEL,
                null
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: models.firstOrNull()
                ?: "Model A"

        val selectedLine =
            prefs.getString(
                KEY_SELECTED_LINE,
                null
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: lines.firstOrNull()
                ?: "Line 1"

        return ProductionContext(
            model = selectedModel,
            line = selectedLine
        )
    }

    fun setCurrent(
        context: Context,
        model: String,
        line: String
    ) {

        val safeModel =
            model.trim()

        val safeLine =
            line.trim()

        if (
            safeModel.isBlank() ||
            safeLine.isBlank()
        ) {

            return
        }

        /*
         * 선택된 항목이 목록에 없으면 자동으로 목록에도 추가합니다.
         */
        addModel(
            context,
            safeModel
        )

        addLine(
            context,
            safeLine
        )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_SELECTED_MODEL,
                safeModel
            )
            .putString(
                KEY_SELECTED_LINE,
                safeLine
            )
            .apply()
    }

    fun getSelectedModel(
        context: Context
    ): String {

        return getCurrent(
            context
        ).model
    }

    fun getSelectedLine(
        context: Context
    ): String {

        return getCurrent(
            context
        ).line
    }

    /*
     * =========================================================
     * Model 목록
     * =========================================================
     */

    fun getModels(
        context: Context
    ): List<String> {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val saved =
            prefs.getStringSet(
                KEY_MODEL_LIST,
                null
            )

        val result =
            if (
                saved.isNullOrEmpty()
            ) {

                DEFAULT_MODELS

            } else {

                saved.toList()
            }

        return result
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()
            .sorted()
    }

    fun addModel(
        context: Context,
        model: String
    ) {

        val safeModel =
            model.trim()

        if (
            safeModel.isBlank()
        ) {

            return
        }

        val models =
            getModels(
                context
            )
                .toMutableSet()

        models.add(
            safeModel
        )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_MODEL_LIST,
                models
            )
            .apply()
    }

    fun removeModel(
        context: Context,
        model: String
    ) {

        val safeModel =
            model.trim()

        val current =
            getCurrent(
                context
            )

        /*
         * 현재 선택 중인 Model은 실수로 삭제하지 않도록 보호합니다.
         */
        if (
            safeModel.equals(
                current.model,
                ignoreCase = false
            )
        ) {

            return
        }

        val models =
            getModels(
                context
            )
                .toMutableSet()

        models.remove(
            safeModel
        )

        if (
            models.isEmpty()
        ) {

            models.addAll(
                DEFAULT_MODELS
            )
        }

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_MODEL_LIST,
                models
            )
            .apply()
    }

    /*
     * =========================================================
     * Line 목록
     * =========================================================
     */

    fun getLines(
        context: Context
    ): List<String> {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val saved =
            prefs.getStringSet(
                KEY_LINE_LIST,
                null
            )

        val result =
            if (
                saved.isNullOrEmpty()
            ) {

                DEFAULT_LINES

            } else {

                saved.toList()
            }

        return result
            .map {
                it.trim()
            }
            .filter {
                it.isNotBlank()
            }
            .distinct()
            .sorted()
    }

    fun addLine(
        context: Context,
        line: String
    ) {

        val safeLine =
            line.trim()

        if (
            safeLine.isBlank()
        ) {

            return
        }

        val lines =
            getLines(
                context
            )
                .toMutableSet()

        lines.add(
            safeLine
        )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_LINE_LIST,
                lines
            )
            .apply()
    }

    fun removeLine(
        context: Context,
        line: String
    ) {

        val safeLine =
            line.trim()

        val current =
            getCurrent(
                context
            )

        /*
         * 현재 선택 중인 Line은 실수로 삭제하지 않도록 보호합니다.
         */
        if (
            safeLine.equals(
                current.line,
                ignoreCase = false
            )
        ) {

            return
        }

        val lines =
            getLines(
                context
            )
                .toMutableSet()

        lines.remove(
            safeLine
        )

        if (
            lines.isEmpty()
        ) {

            lines.addAll(
                DEFAULT_LINES
            )
        }

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putStringSet(
                KEY_LINE_LIST,
                lines
            )
            .apply()
    }

    /*
     * =========================================================
     * 초기화
     * =========================================================
     */

    fun resetToDefaults(
        context: Context
    ) {

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .clear()
            .apply()
    }
}
