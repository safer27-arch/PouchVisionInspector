package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * TAB V2 - 정상 TAB 사진 10장 기반 현장용 1차 분석기
 *
 * 검사 목적
 * - TAB 중심 위치/정렬
 * - TAB 주변 실링부 균일성
 * - TAB 주변 국부 주름/눌림
 * - TAB-셀 경계의 비정상 변화
 * - 파우치 전체 반사광/큰 주름은 낮은 가중치
 *
 * 실제 NG 샘플이 없으므로 현재 판정 Threshold는 보수적인 임시 기준입니다.
 */
object TabInspectionV2 {

    data class Result(
        val tabPresenceConfidence: Double,
        val alignmentRisk: Double,
        val sealUniformityRisk: Double,
        val boundaryRisk: Double,
        val localDeformationRisk: Double,
        val reflectionRisk: Double,
        val baselineDeviation: Double,
        val qualityScore: Double,
        val judgment: String,
        val reason: String,
        val showDefectMarkers: Boolean
    )

    fun analyze(
        roi: Bitmap,
        sensitivity: Int
    ): Result {

        if (
            roi.width < 20 ||
            roi.height < 20
        ) {
            return emptyResult()
        }

        val targetW =
            min(
                320,
                roi.width
            )
                .coerceAtLeast(
                    40
                )

        val targetH =
            max(
                40,
                (
                    targetW *
                        roi.height.toDouble() /
                        roi.width.toDouble()
                    )
                    .toInt()
            )
                .coerceAtMost(
                    320
                )

        val small =
            if (
                roi.width == targetW &&
                roi.height == targetH
            ) {
                roi
            } else {
                Bitmap.createScaledBitmap(
                    roi,
                    targetW,
                    targetH,
                    true
                )
            }

        val w =
            small.width

        val h =
            small.height

        val pixels =
            IntArray(
                w *
                    h
            )

        small.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        val gray =
            DoubleArray(
                pixels.size
            )

        val saturation =
            DoubleArray(
                pixels.size
            )

        for (
            i in pixels.indices
        ) {

            val c =
                pixels[
                    i
                ]

            val r =
                Color.red(
                    c
                )
                    .toDouble()

            val g =
                Color.green(
                    c
                )
                    .toDouble()

            val b =
                Color.blue(
                    c
                )
                    .toDouble()

            gray[
                i
            ] =
                0.299 *
                    r +
                    0.587 *
                    g +
                    0.114 *
                    b

            val mx =
                max(
                    r,
                    max(
                        g,
                        b
                    )
                )

            val mn =
                min(
                    r,
                    min(
                        g,
                        b
                    )
                )

            saturation[
                i
            ] =
                if (
                    mx <=
                    1.0
                ) {
                    0.0
                } else {
                    (
                        mx -
                            mn
                        ) /
                        mx *
                        100.0
                }
        }

        val blur =
            boxBlur(
                gray,
                w,
                h,
                2
            )

        val localMean =
            boxBlur(
                gray,
                w,
                h,
                9
            )

        val gradient =
            DoubleArray(
                w *
                    h
            )

        var globalGradient =
            0.0

        var globalContrast =
            0.0

        var sampleCount =
            0

        for (
            y in 1 until
                h -
                1
        ) {

            for (
                x in 1 until
                    w -
                    1
            ) {

                val i =
                    y *
                        w +
                        x

                val gx =
                    blur[
                        i +
                            1
                    ] -
                        blur[
                            i -
                                1
                        ]

                val gy =
                    blur[
                        i +
                            w
                    ] -
                        blur[
                            i -
                                w
                        ]

                val g =
                    sqrt(
                        gx *
                            gx +
                            gy *
                            gy
                    )

                gradient[
                    i
                ] =
                    g

                globalGradient +=
                    g

                globalContrast +=
                    abs(
                        gray[
                            i
                        ] -
                            localMean[
                                i
                            ]
                    )

                sampleCount++
            }
        }

        val meanGradient =
            if (
                sampleCount >
                0
            ) {
                globalGradient /
                    sampleCount
            } else {
                0.0
            }

        val meanContrast =
            if (
                sampleCount >
                0
            ) {
                globalContrast /
                    sampleCount
            } else {
                0.0
            }

        /*
         * TAB 사진에서는 노란/주황색 TAB sealing film이
         * 주변 알루미늄/흰색 셀보다 상대적으로 색 Saturation이 높습니다.
         *
         * 이 색 영역의 중심을 찾아 TAB 주변을 자동으로 좁혀 봅니다.
         */
        var satX =
            0.0

        var satY =
            0.0

        var satWeight =
            0.0

        var satCount =
            0

        for (
            y in 0 until
                h
        ) {

            for (
                x in 0 until
                    w
            ) {

                val i =
                    y *
                        w +
                        x

                val s =
                    saturation[
                        i
                    ]

                if (
                    s >=
                    28.0
                ) {

                    val weight =
                        (
                            s -
                                24.0
                            )
                            .coerceAtLeast(
                                1.0
                            )

                    satX +=
                        x *
                            weight

                    satY +=
                        y *
                            weight

                    satWeight +=
                        weight

                    satCount++
                }
            }
        }

        val tabPresenceConfidence =
            (
                satCount
                    .toDouble() /
                    (
                        w *
                            h
                        )
                        .toDouble() *
                    1800.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val centerX =
            if (
                satWeight >
                0.0
            ) {
                satX /
                    satWeight
            } else {
                w /
                    2.0
            }

        val centerY =
            if (
                satWeight >
                0.0
            ) {
                satY /
                    satWeight
            } else {
                h /
                    2.0
            }

        /*
         * 실제 촬영에서는 TAB이 ROI 중앙에 정확히 올 필요는 없지만
         * 지나치게 치우치면 ROI가 TAB 주변을 충분히 포함하지 못한 것으로 본다.
         */
        val normDx =
            abs(
                centerX -
                    w /
                        2.0
            ) /
                max(
                    1.0,
                    w /
                        2.0
                )

        val normDy =
            abs(
                centerY -
                    h /
                        2.0
            ) /
                max(
                    1.0,
                    h /
                        2.0
                )

        val alignmentRisk =
            (
                normDx *
                    58.0 +
                    normDy *
                    42.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * TAB 주변 자동 분석 Window
         */
        val halfW =
            max(
                8,
                (
                    w *
                        0.22
                    )
                    .toInt()
            )

        val halfH =
            max(
                8,
                (
                    h *
                        0.30
                    )
                    .toInt()
            )

        val x0 =
            (
                centerX.toInt() -
                    halfW
                )
                .coerceIn(
                    1,
                    w -
                        2
                )

        val x1 =
            (
                centerX.toInt() +
                    halfW
                )
                .coerceIn(
                    x0 +
                        1,
                    w -
                        1
                )

        val y0 =
            (
                centerY.toInt() -
                    halfH
                )
                .coerceIn(
                    1,
                    h -
                        2
                )

        val y1 =
            (
                centerY.toInt() +
                    halfH
                )
                .coerceIn(
                    y0 +
                        1,
                    h -
                        1
                )

        val edgeThreshold =
            max(
                12.0,
                meanGradient *
                    (
                        1.65 -
                            sensitivity
                                .coerceIn(
                                    0,
                                    100
                                ) *
                            0.0035
                        )
            )

        var strongEdges =
            0

        var windowPixels =
            0

        var windowStrength =
            0.0

        var leftStrength =
            0.0

        var rightStrength =
            0.0

        var leftCount =
            0

        var rightCount =
            0

        var topStrength =
            0.0

        var bottomStrength =
            0.0

        var topCount =
            0

        var bottomCount =
            0

        val localContrastValues =
            mutableListOf<Double>()

        for (
            y in y0 until
                y1
        ) {

            for (
                x in x0 until
                    x1
            ) {

                val i =
                    y *
                        w +
                        x

                val g =
                    gradient[
                        i
                    ]

                windowStrength +=
                    g

                windowPixels++

                if (
                    g >=
                    edgeThreshold
                ) {
                    strongEdges++
                }

                val lc =
                    abs(
                        gray[
                            i
                        ] -
                            localMean[
                                i
                            ]
                    )

                localContrastValues.add(
                    lc
                )

                if (
                    x <
                    centerX
                ) {

                    leftStrength +=
                        g

                    leftCount++

                } else {

                    rightStrength +=
                        g

                    rightCount++
                }

                if (
                    y <
                    centerY
                ) {

                    topStrength +=
                        g

                    topCount++

                } else {

                    bottomStrength +=
                        g

                    bottomCount++
                }
            }
        }

        val strongEdgeDensity =
            if (
                windowPixels >
                0
            ) {
                strongEdges
                    .toDouble() /
                    windowPixels
                        .toDouble() *
                    100.0
            } else {
                0.0
            }

        val avgWindowStrength =
            if (
                windowPixels >
                0
            ) {
                windowStrength /
                    windowPixels
            } else {
                0.0
            }

        val leftAverage =
            if (
                leftCount >
                0
            ) {
                leftStrength /
                    leftCount
            } else {
                0.0
            }

        val rightAverage =
            if (
                rightCount >
                0
            ) {
                rightStrength /
                    rightCount
            } else {
                0.0
            }

        val topAverage =
            if (
                topCount >
                0
            ) {
                topStrength /
                    topCount
            } else {
                0.0
            }

        val bottomAverage =
            if (
                bottomCount >
                0
            ) {
                bottomStrength /
                    bottomCount
            } else {
                0.0
            }

        val sideAsymmetry =
            abs(
                leftAverage -
                    rightAverage
            )

        val verticalAsymmetry =
            abs(
                topAverage -
                    bottomAverage
            )

        /*
         * Seal Uniformity:
         * 좌우/상하의 구조 강도 편차와 국부 Edge 과다를 함께 본다.
         */
        val sealUniformityRisk =
            (
                sideAsymmetry *
                    2.1 +
                    verticalAsymmetry *
                    1.4 +
                    strongEdgeDensity *
                    0.70
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Boundary Risk:
         * TAB 주변 경계가 너무 불연속/복잡해지는 경우.
         */
        val boundaryRisk =
            (
                strongEdgeDensity *
                    1.8 +
                    avgWindowStrength *
                    0.55
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Local Deformation:
         * TAB 주변 국부 Contrast 상위 영역을 사용.
         * 넓은 반사광은 평균화되므로 영향이 줄어든다.
         */
        val sortedContrast =
            localContrastValues
                .sorted()

        val highContrast =
            if (
                sortedContrast.isEmpty()
            ) {
                0.0
            } else {

                val start =
                    (
                        sortedContrast.size *
                            0.90
                        )
                        .toInt()
                        .coerceIn(
                            0,
                            sortedContrast.size -
                                1
                        )

                sortedContrast
                    .subList(
                        start,
                        sortedContrast.size
                    )
                    .average()
            }

        val localDeformationRisk =
            (
                highContrast *
                    2.1 +
                    strongEdgeDensity *
                    0.85
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Reflection Risk는 판정에 매우 낮은 비중만 사용한다.
         */
        val reflectionRisk =
            (
                meanContrast *
                    2.1
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * TAB Presence Confidence가 너무 낮으면
         * 실제 TAB을 ROI 안에 충분히 넣지 못한 가능성이 있으므로
         * 판정 Risk보다는 재촬영/ROI 확인 신호로 사용한다.
         */
        val missingPenalty =
            if (
                tabPresenceConfidence <
                18.0
            ) {
                (
                    18.0 -
                        tabPresenceConfidence
                    ) *
                    1.8
            } else {
                0.0
            }

        val baselineDeviation =
            (
                alignmentRisk *
                    0.16 +
                    sealUniformityRisk *
                    0.28 +
                    boundaryRisk *
                    0.22 +
                    localDeformationRisk *
                    0.27 +
                    reflectionRisk *
                    0.03 +
                    missingPenalty *
                    0.04
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val qualityScore =
            (
                100.0 -
                    baselineDeviation
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 실제 NG 사진이 없기 때문에
         * 불량 판정은 두 개 이상의 위험신호가 동시에 매우 높을 때만 낸다.
         */
        val judgment =
            when {

                (
                    sealUniformityRisk >=
                        88.0 &&
                        localDeformationRisk >=
                        86.0
                    ) ||
                    (
                        boundaryRisk >=
                            92.0 &&
                            localDeformationRisk >=
                            82.0
                        ) ->
                    "불량"

                baselineDeviation >=
                    74.0 ->
                    "한계정상"

                baselineDeviation >=
                    56.0 ->
                    "주의"

                else ->
                    "정상"
            }

        val reason =
            when {

                tabPresenceConfidence <
                    12.0 ->
                    "TAB 색/실링부 인식 Confidence가 낮습니다. ROI가 TAB 주변을 충분히 포함하는지 확인 권고."

                judgment ==
                    "불량" ->
                    "TAB 주변 실링/경계/국부 변형이 정상 기준보다 크게 증가했습니다."

                judgment ==
                    "한계정상" ->
                    "정상 TAB 기준보다 형상 변화가 크게 증가했습니다. 재확인 권고."

                judgment ==
                    "주의" ->
                    "TAB 주변 균일성 또는 국부 변형이 정상 기준보다 증가했습니다."

                else ->
                    "현재 정상 TAB Master 범위로 판단됩니다."
            }

        if (
            small !==
            roi &&
            !small.isRecycled
        ) {
            small.recycle()
        }

        return Result(
            tabPresenceConfidence =
                tabPresenceConfidence,
            alignmentRisk =
                alignmentRisk,
            sealUniformityRisk =
                sealUniformityRisk,
            boundaryRisk =
                boundaryRisk,
            localDeformationRisk =
                localDeformationRisk,
            reflectionRisk =
                reflectionRisk,
            baselineDeviation =
                baselineDeviation,
            qualityScore =
                qualityScore,
            judgment =
                judgment,
            reason =
                reason,
            showDefectMarkers =
                judgment !=
                    "정상"
        )
    }

    private fun boxBlur(
        src: DoubleArray,
        w: Int,
        h: Int,
        radius: Int
    ): DoubleArray {

        if (
            radius <=
            0
        ) {
            return src.copyOf()
        }

        val integral =
            DoubleArray(
                (
                    w +
                        1
                    ) *
                    (
                        h +
                            1
                        )
            )

        for (
            y in 0 until
                h
        ) {

            var row =
                0.0

            for (
                x in 0 until
                    w
            ) {

                row +=
                    src[
                        y *
                            w +
                            x
                    ]

                integral[
                    (
                        y +
                            1
                        ) *
                        (
                            w +
                                1
                            ) +
                        (
                            x +
                                1
                            )
                ] =
                    integral[
                        y *
                            (
                                w +
                                    1
                                ) +
                            (
                                x +
                                    1
                                )
                    ] +
                        row
            }
        }

        val out =
            DoubleArray(
                w *
                    h
            )

        for (
            y in 0 until
                h
        ) {

            val y0 =
                max(
                    0,
                    y -
                        radius
                )

            val y1 =
                min(
                    h -
                        1,
                    y +
                        radius
                )

            for (
                x in 0 until
                    w
            ) {

                val x0 =
                    max(
                        0,
                        x -
                            radius
                    )

                val x1 =
                    min(
                        w -
                            1,
                        x +
                            radius
                    )

                val a =
                    integral[
                        y0 *
                            (
                                w +
                                    1
                                ) +
                            x0
                    ]

                val b =
                    integral[
                        y0 *
                            (
                                w +
                                    1
                                ) +
                            (
                                x1 +
                                    1
                                )
                    ]

                val c =
                    integral[
                        (
                            y1 +
                                1
                            ) *
                            (
                                w +
                                    1
                                ) +
                            x0
                    ]

                val d =
                    integral[
                        (
                            y1 +
                                1
                            ) *
                            (
                                w +
                                    1
                                ) +
                            (
                                x1 +
                                    1
                                )
                    ]

                val count =
                    (
                        x1 -
                            x0 +
                            1
                        ) *
                        (
                            y1 -
                                y0 +
                                1
                            )

                out[
                    y *
                        w +
                        x
                ] =
                    (
                        d -
                            b -
                            c +
                            a
                        ) /
                        count.toDouble()
            }
        }

        return out
    }

    private fun emptyResult(): Result {

        return Result(
            tabPresenceConfidence =
                0.0,
            alignmentRisk =
                0.0,
            sealUniformityRisk =
                0.0,
            boundaryRisk =
                0.0,
            localDeformationRisk =
                0.0,
            reflectionRisk =
                0.0,
            baselineDeviation =
                0.0,
            qualityScore =
                100.0,
            judgment =
                "정상",
            reason =
                "ROI가 너무 작아 TAB V2 분석을 생략했습니다.",
            showDefectMarkers =
                false
        )
    }
}
