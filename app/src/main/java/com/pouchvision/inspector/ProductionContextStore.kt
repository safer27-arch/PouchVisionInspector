package com.pouchvision.inspector

import android.content.Context

/*
 * =============================================================
 * 생산 조건(Model / Line) 공용 저장소
 * =============================================================
 *
 * 현재 등록 Model : 23종
 *
 * Model별 사용 Line을 현장 조건에 맞게 구분합니다.
 *
 * 예:
 * L10D  -> Line 3
 * H5.5  -> Line 6 / Line 7 / Line 25
 * E78   -> Line 10 / 13 / 14 / 20 / 21 / 22 / 23 / 24
 *
 * 중요
 * - 선택한 Model에 등록된 Line만 사용할 수 있습니다.
 * - 앱을 종료했다 다시 실행해도 마지막 유효 Model / Line을 기억합니다.
 * - 과거 임시 Model A/B/C 또는 잘못된 Line 값은 자동 정리합니다.
 * - 검사 알고리즘과 판정 기준은 변경하지 않습니다.
 * =============================================================
 */

object ProductionContextStore {

    private const val PREF_NAME =
        "production_context_pref"

    private const val KEY_SELECTED_MODEL =
        "selected_model"

    private const val KEY_SELECTED_LINE =
        "selected_line"

    /*
     * =========================================================
     * 현장 Model Master
     * =========================================================
     */

    private val MASTER_MODELS =
        listOf(
            "L10D",
            "H5.5",
            "E85B",
            "E81A",
            "E72B",
            "E71A",
            "E73A",
            "E66A",
            "E61V",
            "H5.0",
            "P37B",
            "P41A",
            "N2.2",
            "P39",
            "LV20",
            "LV9.8",
            "E78",
            "E79",
            "E77A",
            "E90B",
            "E161A",
            "E128A",
            "E129A"
        )

    /*
     * =========================================================
     * Model별 Line Master
     * =========================================================
     */

    private val MODEL_LINE_MAP =
        linkedMapOf(
            "L10D" to listOf("Line 3"),
            "H5.5" to listOf("Line 6", "Line 7", "Line 25"),
            "E85B" to listOf("Line 8"),
            "E81A" to listOf("Line 15", "Line 18"),
            "E72B" to listOf("Line 17"),
            "E71A" to listOf("Line 11", "Line 12"),
            "E73A" to listOf("Line 11", "Line 12"),
            "E66A" to listOf("Line 17"),
            "E61V" to listOf("Line 17"),
            "H5.0" to listOf("Line 16"),
            "P37B" to listOf("Line 9"),
            "P41A" to listOf("Line 9"),
            "N2.2" to listOf("Line 9"),
            "P39" to listOf("Line 9"),
            "LV20" to listOf("Line 9"),
            "LV9.8" to listOf("Line 9"),
            "E78" to listOf("Line 10", "Line 13", "Line 14", "Line 20", "Line 21", "Line 22", "Line 23", "Line 24"),
            "E79" to listOf("Line 10", "Line 13", "Line 14", "Line 20", "Line 21", "Line 22", "Line 23", "Line 24"),
            "E77A" to listOf("Line 10", "Line 13", "Line 14", "Line 20", "Line 21", "Line 22", "Line 23", "Line 24"),
            "E90B" to listOf("Line 23"),
            "E161A" to listOf("Line 27", "Line 28"),
            "E128A" to listOf("Line 26"),
            "E129A" to listOf("Line 26")
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
     * Model 목록
     * =========================================================
     */

    fun getModels(
        context: Context
    ): List<String> {

        return MASTER_MODELS
    }

    /*
     * =========================================================
     * 특정 Model의 Line 목록
     * =========================================================
     */

    fun getLinesForModel(
        model: String
    ): List<String> {

        return MODEL_LINE_MAP[
            model.trim()
        ]
            ?: emptyList()
    }

    /*
     * =========================================================
     * 기존 MenuActivity 호환용
     *
     * 현재 저장된 Model에 해당하는 Line만 반환합니다.
     * 다음 단계에서 MenuActivity가 Model 선택 즉시 이 함수를
     * 갱신하도록 연결합니다.
     * =========================================================
     */

    fun getLines(
        context: Context
    ): List<String> {

        val current =
            getCurrent(
                context
            )

        return getLinesForModel(
            current.model
        )
    }

    /*
     * =========================================================
     * 현재 선택값 읽기
     * =========================================================
     */

    fun getCurrent(
        context: Context
    ): ProductionContext {

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val savedModel =
            prefs.getString(
                KEY_SELECTED_MODEL,
                null
            )
                ?.trim()
                .orEmpty()

        val savedLine =
            prefs.getString(
                KEY_SELECTED_LINE,
                null
            )
                ?.trim()
                .orEmpty()

        val selectedModel =
            if (
                MASTER_MODELS.contains(
                    savedModel
                )
            ) {

                savedModel

            } else {

                MASTER_MODELS.first()
            }

        val validLines =
            getLinesForModel(
                selectedModel
            )

        val selectedLine =
            if (
                validLines.contains(
                    savedLine
                )
            ) {

                savedLine

            } else {

                validLines.firstOrNull()
                    ?: ""
            }

        /*
         * 과거 값이 현재 Master와 맞지 않으면
         * 유효한 Model / Line으로 자동 복구합니다.
         */
        if (
            selectedModel != savedModel ||
            selectedLine != savedLine
        ) {

            prefs.edit()
                .putString(
                    KEY_SELECTED_MODEL,
                    selectedModel
                )
                .putString(
                    KEY_SELECTED_LINE,
                    selectedLine
                )
                .apply()
        }

        return ProductionContext(
            model = selectedModel,
            line = selectedLine
        )
    }

    /*
     * =========================================================
     * 현재 선택값 저장
     * =========================================================
     */

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
            !MASTER_MODELS.contains(
                safeModel
            )
        ) {

            return
        }

        val validLines =
            getLinesForModel(
                safeModel
            )

        if (
            !validLines.contains(
                safeLine
            )
        ) {

            return
        }

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
     * 기존 코드 호환용
     *
     * Master 목록은 이 파일에서 고정 관리하므로
     * 실행 중 임의 추가/삭제는 하지 않습니다.
     * =========================================================
     */

    fun addModel(
        context: Context,
        model: String
    ) {
        // no-op
    }

    fun removeModel(
        context: Context,
        model: String
    ) {
        // no-op
    }

    fun addLine(
        context: Context,
        line: String
    ) {
        // no-op
    }

    fun removeLine(
        context: Context,
        line: String
    ) {
        // no-op
    }

    /*
     * =========================================================
     * 선택값 초기화
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
            .remove(
                KEY_SELECTED_MODEL
            )
            .remove(
                KEY_SELECTED_LINE
            )
            .apply()
    }
}
