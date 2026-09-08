package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/*
 * =============================================================
 * 촬영 이미지 품질 자동 점검
 * =============================================================
 *
 * 목적
 * - 검사 알고리즘을 바꾸지 않고,
 *   검사 전에 사진 자체의 품질을 점검합니다.
 *
 * 확인 항목
 * 1) 너무 어두운 사진
 * 2) 과도하게 밝아 날아간 사진
 * 3) 명암이 너무 낮은 사진
 * 4) 흔들림 / 초점 불량 가능성이 높은 사진
 *
 * 중요
 * - 이 기능은 OK/NG 판정 기능이 아닙니다.
 * - 검사 결과를 막지 않고 "재촬영 권고"만 표시하도록 사용합니다.
 * - 실제 적용 시 현장 조명과 카메라 조건에 맞게 기준값을
 *   추가 보정할 수 있습니다.
 * =============================================================
 */

object ImageQualityChecker {

    data class QualityResult(

        /*
         * 검사에 사용하기에 충분한 사진인지
         *
         * false여도 검사를 강제로 막지는 않고
         * 재촬영 권고용으로 사용합니다.
         */
        val isUsable: Boolean,

        /*
         * 0 ~ 100
         * 높을수록 사진 품질이 안정적이라는 의미의 보조 점수
         */
        val qualityScore: Double,

        /*
         * 평균 밝기 0 ~ 255
         */
        val averageBrightness: Double,

        /*
         * 명암 표준편차
         */
        val contrast: Double,

        /*
         * 선명도 보조값
         *
         * 인접 픽셀 변화량 기반이며
         * 절대적인 광학 해상도 측정값은 아닙니다.
         */
        val sharpness: Double,

        /*
         * 너무 어두운 픽셀 비율
         */
        val darkPixelRate: Double,

        /*
         * 과노출에 가까운 픽셀 비율
         */
        val brightPixelRate: Double,

        /*
         * 화면 표시용 상태
         */
        val status: String,

        /*
         * 화면 표시용 상세 안내
         */
        val message: String
    )

    /*
     * =========================================================
     * ROI 품질 분석
     * =========================================================
     */

    fun analyzeRoi(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int
    ): QualityResult {

        if (
            sourceBitmap.width <= 1 ||
            sourceBitmap.height <= 1
        ) {

            return invalidResult(
                "사진 크기가 너무 작습니다."
            )
        }

        val left =
            roiLeft.coerceIn(
                0,
                sourceBitmap.width - 1
            )

        val top =
            roiTop.coerceIn(
                0,
                sourceBitmap.height - 1
            )

        val right =
            (
                roiLeft +
                    roiWidth
                )
                .coerceIn(
                    left + 1,
                    sourceBitmap.width
                )

        val bottom =
            (
                roiTop +
                    roiHeight
                )
                .coerceIn(
                    top + 1,
                    sourceBitmap.height
                )

        val width =
            right -
                left

        val height =
            bottom -
                top

        if (
            width < 12 ||
            height < 12
        ) {

            return invalidResult(
                "ROI가 너무 작습니다."
            )
        }

        val roiBitmap =
            Bitmap.createBitmap(
                sourceBitmap,
                left,
                top,
                width,
                height
            )

        return analyzeBitmap(
            roiBitmap
        )
    }

    /*
     * =========================================================
     * 전체 Bitmap 품질 분석
     * =========================================================
     */

    fun analyzeBitmap(
        sourceBitmap: Bitmap
    ): QualityResult {

        if (
            sourceBitmap.width <= 1 ||
            sourceBitmap.height <= 1
        ) {

            return invalidResult(
                "사진 크기가 너무 작습니다."
            )
        }

        /*
         * 저사양 Android에서도 빠르게 처리하기 위해
         * 분석 이미지를 최대 약 320px 수준으로 축소합니다.
         */
        val maxSide =
            max(
                sourceBitmap.width,
                sourceBitmap.height
            )

        val scale =
            if (
                maxSide >
                320
            ) {

                320.0 /
                    maxSide.toDouble()

            } else {

                1.0
            }

        val analysisWidth =
            max(
                16,
                (
                    sourceBitmap.width *
                        scale
                    )
                    .toInt()
            )

        val analysisHeight =
            max(
                16,
                (
                    sourceBitmap.height *
                        scale
                    )
                    .toInt()
            )

        val bitmap =
            if (
                analysisWidth !=
                    sourceBitmap.width ||
                analysisHeight !=
                    sourceBitmap.height
            ) {

                Bitmap.createScaledBitmap(
                    sourceBitmap,
                    analysisWidth,
                    analysisHeight,
                    true
                )

            } else {

                sourceBitmap
            }

        var count =
            0L

        var graySum =
            0.0

        var graySquareSum =
            0.0

        var darkCount =
            0L

        var brightCount =
            0L

        var gradientSum =
            0.0

        var gradientCount =
            0L

        /*
         * 가장자리 2px는 제외합니다.
         */
        for (
            y in 1 until
                bitmap.height - 1
        ) {

            for (
                x in 1 until
                    bitmap.width - 1
            ) {

                val center =
                    gray(
                        bitmap.getPixel(
                            x,
                            y
                        )
                    )

                val right =
                    gray(
                        bitmap.getPixel(
                            x + 1,
                            y
                        )
                    )

                val left =
                    gray(
                        bitmap.getPixel(
                            x - 1,
                            y
                        )
                    )

                val bottom =
                    gray(
                        bitmap.getPixel(
                            x,
                            y + 1
                        )
                    )

                val top =
                    gray(
                        bitmap.getPixel(
                            x,
                            y - 1
                        )
                    )

                count++

                graySum +=
                    center

                graySquareSum +=
                    center *
                        center

                if (
                    center <
                    35.0
                ) {

                    darkCount++
                }

                if (
                    center >
                    245.0
                ) {

                    brightCount++
                }

                /*
                 * 단순 선명도 보조지표
                 *
                 * 좌우 + 상하 변화량을 사용합니다.
                 * 흔들리거나 초점이 맞지 않으면 일반적으로
                 * 이 값이 낮아집니다.
                 */
                val horizontal =
                    abs(
                        right -
                            left
                    )

                val vertical =
                    abs(
                        bottom -
                            top
                    )

                gradientSum +=
                    horizontal +
                        vertical

                gradientCount++
            }
        }

        if (
            count <= 0
        ) {

            return invalidResult(
                "사진을 분석할 수 없습니다."
            )
        }

        val averageBrightness =
            graySum /
                count.toDouble()

        val variance =
            (
                graySquareSum /
                    count.toDouble()
                ) -
                averageBrightness *
                    averageBrightness

        val contrast =
            sqrt(
                variance.coerceAtLeast(
                    0.0
                )
            )

        val sharpness =
            if (
                gradientCount >
                0
            ) {

                gradientSum /
                    gradientCount.toDouble()

            } else {

                0.0
            }

        val darkPixelRate =
            darkCount
                .toDouble() /
                count.toDouble() *
                100.0

        val brightPixelRate =
            brightCount
                .toDouble() /
                count.toDouble() *
                100.0

        /*
         * =====================================================
         * 현재 임시 권고 기준
         *
         * 판정 Spec가 아니라 "촬영상태 권고 기준"입니다.
         * =====================================================
         */

        val tooDark =
            averageBrightness <
                55.0 ||
                darkPixelRate >
                40.0

        val tooBright =
            averageBrightness >
                225.0 ||
                brightPixelRate >
                35.0

        val lowContrast =
            contrast <
                18.0

        val lowSharpness =
            sharpness <
                12.0

        /*
         * 품질 보조 Score
         */
        var penalty =
            0.0

        if (
            tooDark
        ) {

            penalty +=
                28.0
        }

        if (
            tooBright
        ) {

            penalty +=
                28.0
        }

        if (
            lowContrast
        ) {

            penalty +=
                22.0
        }

        if (
            lowSharpness
        ) {

            penalty +=
                30.0
        }

        /*
         * 경계 부근은 완만하게 추가 감점
         */
        if (
            averageBrightness in
            55.0..75.0
        ) {

            penalty +=
                8.0
        }

        if (
            averageBrightness in
            205.0..225.0
        ) {

            penalty +=
                8.0
        }

        if (
            contrast in
            18.0..25.0
        ) {

            penalty +=
                6.0
        }

        if (
            sharpness in
            12.0..18.0
        ) {

            penalty +=
                8.0
        }

        val qualityScore =
            (
                100.0 -
                    penalty
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val issues =
            mutableListOf<String>()

        if (
            tooDark
        ) {

            issues.add(
                "사진이 너무 어둡습니다."
            )
        }

        if (
            tooBright
        ) {

            issues.add(
                "반사 또는 과노출이 많습니다."
            )
        }

        if (
            lowContrast
        ) {

            issues.add(
                "검사 부위의 명암 차이가 부족합니다."
            )
        }

        if (
            lowSharpness
        ) {

            issues.add(
                "초점 또는 흔들림 상태를 확인해주세요."
            )
        }

        val isUsable =
            issues.isEmpty()

        val status =
            if (
                isUsable
            ) {

                "촬영 상태 양호"

            } else {

                "재촬영 권고"
            }

        val message =
            if (
                isUsable
            ) {

                """
촬영 상태가 검사에 사용하기에 양호합니다.

밝기, 명암, 선명도에 큰 이상이 없습니다.
                """.trimIndent()

            } else {

                buildString {

                    append(
                        "촬영 상태 확인이 필요합니다.\n\n"
                    )

                    issues.forEach {

                        append(
                            "• "
                        )

                        append(
                            it
                        )

                        append(
                            "\n"
                        )
                    }

                    append(
                        "\n검사는 계속할 수 있지만 재촬영을 권장합니다."
                    )
                }
            }

        return QualityResult(
            isUsable =
                isUsable,

            qualityScore =
                qualityScore,

            averageBrightness =
                averageBrightness,

            contrast =
                contrast,

            sharpness =
                sharpness,

            darkPixelRate =
                darkPixelRate,

            brightPixelRate =
                brightPixelRate,

            status =
                status,

            message =
                message
        )
    }

    /*
     * =========================================================
     * 분석 불가
     * =========================================================
     */

    private fun invalidResult(
        message: String
    ): QualityResult {

        return QualityResult(
            isUsable =
                false,

            qualityScore =
                0.0,

            averageBrightness =
                0.0,

            contrast =
                0.0,

            sharpness =
                0.0,

            darkPixelRate =
                0.0,

            brightPixelRate =
                0.0,

            status =
                "촬영 상태 확인 필요",

            message =
                message
        )
    }

    /*
     * =========================================================
     * RGB → Gray
     * =========================================================
     */

    private fun gray(
        color: Int
    ): Double {

        val r =
            Color.red(
                color
            )

        val g =
            Color.green(
                color
            )

        val b =
            Color.blue(
                color
            )

        return (
            0.299 *
                r +
                0.587 *
                g +
                0.114 *
                b
            )
    }
}
