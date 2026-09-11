package com.pouchvision.inspector

import android.content.Context

/*
 * =============================================================
 * Model / Line별 검사 기준값 저장소
 * =============================================================
 *
 * 목적
 * - 같은 검사 항목이라도 Model / Line별로 서로 다른 판정 기준을
 *   사용할 수 있도록 기준값을 저장합니다.
 *
 * 현재 기본값
 *
 * 1) BOTTOM CORNER
 *    - Wrinkle Score가 낮을수록 양호
 *    - 정상       : < 27
 *    - 주의       : 27 ~ < 47
 *    - 한계정상   : 47 ~ < 68
 *    - 불량 후보  : >= 68
 *
 * 2) SEAL / FORMING / TAB / DISASSEMBLY
 *    - Quality Score가 높을수록 양호
 *    - 정상 후보       : >= 85
 *    - 주의 후보       : >= 70
 *    - 한계정상 후보   : >= 50
 *    - 불량 후보       : < 50
 *
 * 중요
 * - 이 파일을 추가하는 단계에서는 기존 검사 판정 로직을
 *   변경하지 않습니다.
 * - 다음 단계에서 기준값 설정 화면을 만든 뒤,
 *   각 검사 Activity와 하나씩 연결합니다.
 * =============================================================
 */

object InspectionSpecStore {

    private const val PREF_NAME =
        "inspection_spec_by_model_line"

    enum class InspectionType(
        val displayName: String
    ) {

        BOTTOM_CORNER(
            "BOTTOM CORNER"
        ),

        SEAL(
            "SEAL"
        ),

        FORMING(
            "FORMING"
        ),

        TAB(
            "TAB"
        ),

        DISASSEMBLY(
            "DISASSEMBLY"
        )
    }

    enum class ScoreDirection {

        /*
         * 점수가 낮을수록 양호
         * 예: Bottom Corner Wrinkle Score
         */
        LOWER_IS_BETTER,

        /*
         * 점수가 높을수록 양호
         * 예: Seal / Forming / Tab / Disassembly Quality Score
         */
        HIGHER_IS_BETTER
    }

    data class InspectionSpec(
        val inspectionType: InspectionType,
        val scoreDirection: ScoreDirection,

        /*
         * LOWER_IS_BETTER
         * normalBoundary  = 27
         * warningBoundary = 47
         * limitBoundary   = 68
         *
         * HIGHER_IS_BETTER
         * normalBoundary  = 85
         * warningBoundary = 70
         * limitBoundary   = 50
         */
        val normalBoundary: Double,
        val warningBoundary: Double,
        val limitBoundary: Double
    ) {

        /*
         * =====================================================
         * 기준값으로 판정
         * =====================================================
         */

        fun judge(
            score: Double
        ): String {

            return when (
                scoreDirection
            ) {

                ScoreDirection.LOWER_IS_BETTER -> {

                    when {

                        score <
                            normalBoundary ->
                            "정상"

                        score <
                            warningBoundary ->
                            "주의"

                        score <
                            limitBoundary ->
                            "한계정상"

                        else ->
                            "불량 후보"
                    }
                }

                ScoreDirection.HIGHER_IS_BETTER -> {

                    when {

                        score >=
                            normalBoundary ->
                            "정상 후보"

                        score >=
                            warningBoundary ->
                            "주의 후보"

                        score >=
                            limitBoundary ->
                            "한계정상 후보"

                        else ->
                            "불량 후보"
                    }
                }
            }
        }

        /*
         * =====================================================
         * 화면에 표시할 판정 기준 설명
         * =====================================================
         */

        fun criteriaText(): String {

            return when (
                scoreDirection
            ) {

                ScoreDirection.LOWER_IS_BETTER -> {

                    """
정상       : Score < ${format(normalBoundary)}
주의       : ${format(normalBoundary)} ~ < ${format(warningBoundary)}
한계정상   : ${format(warningBoundary)} ~ < ${format(limitBoundary)}
불량 후보  : ${format(limitBoundary)} 이상
                    """.trimIndent()
                }

                ScoreDirection.HIGHER_IS_BETTER -> {

                    """
정상 후보       : Score >= ${format(normalBoundary)}
주의 후보       : Score >= ${format(warningBoundary)}
한계정상 후보   : Score >= ${format(limitBoundary)}
불량 후보       : Score < ${format(limitBoundary)}
                    """.trimIndent()
                }
            }
        }

        private fun format(
            value: Double
        ): String {

            return if (
                value %
                1.0 ==
                0.0
            ) {

                value
                    .toInt()
                    .toString()

            } else {

                String.format(
                    java.util.Locale.getDefault(),
                    "%.1f",
                    value
                )
            }
        }
    }

    /*
     * =========================================================
     * 현재 선택된 Model / Line 기준 읽기
     * =========================================================
     */

    fun getCurrent(
        context: Context,
        inspectionType: InspectionType
    ): InspectionSpec {

        val production =
            ProductionContextStore.getCurrent(
                context
            )

        return get(
            context = context,
            model = production.model,
            line = production.line,
            inspectionType = inspectionType
        )
    }

    /*
     * =========================================================
     * 특정 Model / Line 기준 읽기
     * =========================================================
     */

    fun get(
        context: Context,
        model: String,
        line: String,
        inspectionType: InspectionType
    ): InspectionSpec {

        val default =
            defaultSpec(
                inspectionType
            )

        val prefs =
            context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
            )

        val prefix =
            keyPrefix(
                model = model,
                line = line,
                inspectionType = inspectionType
            )

        val normal =
            readDouble(
                prefs = prefs,
                key = "${prefix}_normal",
                defaultValue = default.normalBoundary
            )

        val warning =
            readDouble(
                prefs = prefs,
                key = "${prefix}_warning",
                defaultValue = default.warningBoundary
            )

        val limit =
            readDouble(
                prefs = prefs,
                key = "${prefix}_limit",
                defaultValue = default.limitBoundary
            )

        val loaded =
            InspectionSpec(
                inspectionType = inspectionType,
                scoreDirection = default.scoreDirection,
                normalBoundary = normal,
                warningBoundary = warning,
                limitBoundary = limit
            )

        /*
         * 저장값이 깨졌거나 순서가 잘못되어 있으면
         * 안전하게 기본값을 사용합니다.
         */
        return if (
            isValid(
                loaded
            )
        ) {
            loaded
        } else {
            default
        }
    }

    /*
     * =========================================================
     * 기준 저장
     * =========================================================
     */

    fun save(
        context: Context,
        model: String,
        line: String,
        spec: InspectionSpec
    ): Boolean {

        if (
            model.isBlank() ||
            line.isBlank()
        ) {
            return false
        }

        if (
            !isValid(
                spec
            )
        ) {
            return false
        }

        val prefix =
            keyPrefix(
                model = model,
                line = line,
                inspectionType = spec.inspectionType
            )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putLong(
                "${prefix}_normal",
                spec.normalBoundary.toRawBits()
            )
            .putLong(
                "${prefix}_warning",
                spec.warningBoundary.toRawBits()
            )
            .putLong(
                "${prefix}_limit",
                spec.limitBoundary.toRawBits()
            )
            .apply()

        return true
    }

    /*
     * =========================================================
     * 특정 검사 항목을 기본값으로 복원
     * =========================================================
     */

    fun reset(
        context: Context,
        model: String,
        line: String,
        inspectionType: InspectionType
    ) {

        val prefix =
            keyPrefix(
                model = model,
                line = line,
                inspectionType = inspectionType
            )

        context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .remove(
                "${prefix}_normal"
            )
            .remove(
                "${prefix}_warning"
            )
            .remove(
                "${prefix}_limit"
            )
            .apply()
    }

    /*
     * =========================================================
     * 현재 Model / Line의 전체 검사 기준 초기화
     * =========================================================
     */

    fun resetAllForModelLine(
        context: Context,
        model: String,
        line: String
    ) {

        InspectionType.values()
            .forEach { type ->

                reset(
                    context = context,
                    model = model,
                    line = line,
                    inspectionType = type
                )
            }
    }

    /*
     * =========================================================
     * 기본 기준
     * =========================================================
     */

    fun defaultSpec(
        inspectionType: InspectionType
    ): InspectionSpec {

        return when (
            inspectionType
        ) {

            InspectionType.BOTTOM_CORNER -> {

                InspectionSpec(
                    inspectionType =
                        InspectionType.BOTTOM_CORNER,

                    scoreDirection =
                        ScoreDirection.LOWER_IS_BETTER,

                    normalBoundary =
                        27.0,

                    warningBoundary =
                        47.0,

                    limitBoundary =
                        68.0
                )
            }

            InspectionType.SEAL,
            InspectionType.FORMING,
            InspectionType.TAB,
            InspectionType.DISASSEMBLY -> {

                InspectionSpec(
                    inspectionType =
                        inspectionType,

                    scoreDirection =
                        ScoreDirection.HIGHER_IS_BETTER,

                    normalBoundary =
                        85.0,

                    warningBoundary =
                        70.0,

                    limitBoundary =
                        50.0
                )
            }
        }
    }

    /*
     * =========================================================
     * 유효성 검사
     * =========================================================
     */

    private fun isValid(
        spec: InspectionSpec
    ): Boolean {

        val values =
            listOf(
                spec.normalBoundary,
                spec.warningBoundary,
                spec.limitBoundary
            )

        if (
            values.any {
                !it.isFinite() ||
                it <
                    0.0 ||
                it >
                    100.0
            }
        ) {
            return false
        }

        return when (
            spec.scoreDirection
        ) {

            ScoreDirection.LOWER_IS_BETTER -> {

                spec.normalBoundary <
                    spec.warningBoundary &&
                    spec.warningBoundary <
                    spec.limitBoundary
            }

            ScoreDirection.HIGHER_IS_BETTER -> {

                spec.normalBoundary >
                    spec.warningBoundary &&
                    spec.warningBoundary >
                    spec.limitBoundary
            }
        }
    }

    /*
     * =========================================================
     * SharedPreferences Helper
     * =========================================================
     */

    private fun keyPrefix(
        model: String,
        line: String,
        inspectionType: InspectionType
    ): String {

        return sanitize(
            model
        ) +
            "__" +
            sanitize(
                line
            ) +
            "__" +
            inspectionType.name
    }

    private fun sanitize(
        value: String
    ): String {

        return value
            .trim()
            .replace(
                Regex(
                    "[^A-Za-z0-9가-힣._-]"
                ),
                "_"
            )
    }

    private fun readDouble(
        prefs: android.content.SharedPreferences,
        key: String,
        defaultValue: Double
    ): Double {

        return if (
            prefs.contains(
                key
            )
        ) {

            Double.fromBits(
                prefs.getLong(
                    key,
                    defaultValue.toRawBits()
                )
            )

        } else {

            defaultValue
        }
    }
}
