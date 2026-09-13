package com.pouchvision.inspector

import kotlin.math.max
import kotlin.math.min

/**
 * SEAL 정상 Baseline V2
 *
 * 현재 확보된 정상 기준:
 * - 정상 10셀
 * - 정면/사선 2개 촬영 조건 = 총 20장
 *
 * 중요한 운영 원칙
 * 1) 정상 실링 압흔, 알루미늄 반사, 반복적인 가장자리 패턴은 NG로 보지 않는다.
 * 2) DefectMarker의 raw 후보 개수는 최종 판정에 사용하지 않는다.
 * 3) 주름/크랙은 "개수"보다 길이 + 강도 + 실링선 Crossing + 국부 단절을 우선 본다.
 * 4) 실제 NG 샘플이 없으므로 현재 임계값은 현장 적용용 임시 기준이다.
 * 5) 실제 NG가 확보되면 이 파일만 교체하여 기준을 보정하는 구조로 사용한다.
 */
object SealBaselineV2 {

    data class Evaluation(
        val judgment: String,
        val baselineDeviation: Double,
        val qualifiedWrinkleCount: Int,
        val severeWrinkleCount: Int,
        val crackRisk: Double,
        val reason: String,
        val showDefectMarkers: Boolean
    )

    fun evaluate(
        result: SealInspectionV2.Result
    ): Evaluation {

        /*
         * 공용 Edge detector가 정상 실링 압흔을 여러 개로 쪼개 잡는 문제가 있으므로,
         * 모든 wrinkle 후보를 그대로 세지 않습니다.
         *
         * 아래 조건을 만족하는 구조만 "의미 있는 Seal Wrinkle"로 인정합니다.
         */
        val qualified =
            result.wrinkles.filter { w ->
                w.lengthPercent >= 10.0 &&
                    w.strength >= 58.0 &&
                    w.sealCrossingRisk >= 55.0
            }

        /*
         * 실제 크랙/대형 주름 후보:
         * 길고, 강하고, 실링선을 확실히 가로지르며,
         * 음영 변화까지 동반한 경우만 엄격하게 인정합니다.
         */
        val severe =
            result.wrinkles.filter { w ->
                w.lengthPercent >= 18.0 &&
                    w.strength >= 72.0 &&
                    w.sealCrossingRisk >= 80.0 &&
                    w.shadowRisk >= 28.0
            }

        val longestQualified =
            qualified.maxOfOrNull {
                it.lengthPercent
            } ?: 0.0

        val strongestQualified =
            qualified.maxOfOrNull {
                it.strength
            } ?: 0.0

        val maxCrossing =
            qualified.maxOfOrNull {
                it.sealCrossingRisk
            } ?: 0.0

        /*
         * 정상 20장처럼 촬영각도/반사광 차이가 큰 조건을 고려하여
         * Shadow / PP Flow는 가중치를 낮게 둡니다.
         *
         * 반대로 실링 연속성, 폭 변화, 실제 강한 선형 구조는 더 중요하게 봅니다.
         */
        val uniformityPenalty =
            (100.0 - result.sealLineUniformity)
                .coerceIn(
                    0.0,
                    100.0
                )

        val widthPenalty =
            result.widthVariationPercent
                .coerceIn(
                    0.0,
                    100.0
                )

        val discontinuityPenalty =
            result.localDiscontinuityRisk
                .coerceIn(
                    0.0,
                    100.0
                )

        val ppFlowPenalty =
            result.ppFlowRisk
                .coerceIn(
                    0.0,
                    100.0
                )

        val shadowPenalty =
            result.transparencyShadowRisk
                .coerceIn(
                    0.0,
                    100.0
                )

        val cupPenalty =
            result.cupIntrusionRisk
                .coerceIn(
                    0.0,
                    100.0
                )

        val wrinklePenalty =
            (
                qualified.size * 10.0 +
                    min(
                        30.0,
                        longestQualified * 0.8
                    ) +
                    strongestQualified * 0.12 +
                    maxCrossing * 0.08
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val baselineDeviation =
            (
                uniformityPenalty * 0.17 +
                    widthPenalty * 0.16 +
                    discontinuityPenalty * 0.24 +
                    ppFlowPenalty * 0.07 +
                    shadowPenalty * 0.05 +
                    cupPenalty * 0.06 +
                    wrinklePenalty * 0.25
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 크랙 Risk는 일반 Baseline Deviation과 별도 계산합니다.
         * 실제 NG가 없는 현재 단계에서는 매우 보수적으로 NG를 냅니다.
         */
        val severeMax =
            severe.maxByOrNull {
                it.lengthPercent *
                    (
                        0.5 +
                            it.strength /
                            200.0
                        )
            }

        val crackRisk =
            if (
                severeMax ==
                null
            ) {
                0.0
            } else {
                (
                    severeMax.lengthPercent * 1.4 +
                        severeMax.strength * 0.35 +
                        severeMax.sealCrossingRisk * 0.20 +
                        severeMax.shadowRisk * 0.10
                    )
                    .coerceIn(
                        0.0,
                        100.0
                    )
            }

        /*
         * 임시 현장 판정
         *
         * 정상:
         * - 정상 20장 범위에 포함될 가능성이 높은 변화
         *
         * 주의:
         * - 정상 범위를 조금 벗어난 변화
         *
         * 한계정상:
         * - 정상군과 분명히 다른 변화이나 실제 NG 검증은 아직 안 됨
         *
         * 불량:
         * - 강한 크랙/주름 조건이 동시에 충족되는 경우만
         */
        val judgment =
            when {

                severe.size >=
                    2 -> {
                    "불량"
                }

                severe.size ==
                    1 &&
                    crackRisk >=
                    82.0 &&
                    result.localDiscontinuityRisk >=
                    60.0 -> {
                    "불량"
                }

                severe.isNotEmpty() ||
                    baselineDeviation >=
                    74.0 -> {
                    "한계정상"
                }

                qualified.size >=
                    2 ||
                    baselineDeviation >=
                    54.0 -> {
                    "주의"
                }

                else -> {
                    "정상"
                }
            }

        val reason =
            when (
                judgment
            ) {

                "불량" ->
                    "정상 Baseline 대비 강한 크랙/주름성 선형 구조가 확인되었습니다."

                "한계정상" ->
                    "정상 Baseline보다 큰 형상 변화가 확인되었습니다. 재확인 권고."

                "주의" ->
                    "정상 10셀/20장 기준보다 변화가 증가했습니다. 추세 확인 권고."

                else ->
                    "현재 정상 Master Baseline 범위로 판단됩니다."
            }

        return Evaluation(
            judgment = judgment,
            baselineDeviation = baselineDeviation,
            qualifiedWrinkleCount =
                qualified.size,
            severeWrinkleCount =
                severe.size,
            crackRisk = crackRisk,
            reason = reason,
            showDefectMarkers =
                judgment !=
                    "정상"
        )
    }

    fun worseJudgment(
        left: String,
        right: String
    ): String {

        fun rank(
            value: String
        ): Int {
            return when {
                value.contains(
                    "불량"
                ) -> 4

                value.contains(
                    "한계"
                ) -> 3

                value.contains(
                    "주의"
                ) -> 2

                else -> 1
            }
        }

        return if (
            rank(
                left
            ) >=
            rank(
                right
            )
        ) {
            left
        } else {
            right
        }
    }
}
