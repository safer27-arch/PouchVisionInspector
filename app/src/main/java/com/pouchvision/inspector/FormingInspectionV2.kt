package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * FORMING V2 - 정상 Master 기반 현장용 1차 분석기
 *
 * 현재 기준:
 * - 사용자가 제공한 정상 FORMING 사진 10장
 * - FRONT: 금형이 실제 누르는 면 / Stack Cell이 들어가는 Cup 측
 * - BACK : 셀 외곽부 측
 *
 * 설계 원칙:
 * 1) 얇은 파우치의 큰 반사광/완만한 울렁임은 결함으로 과대평가하지 않는다.
 * 2) 두 Cup의 외곽 Wall / Corner / 중앙 경계 / 국부 급격한 Fold를 우선 본다.
 * 3) 실제 NG 샘플이 없으므로 현재 판정 기준은 보수적인 임시 기준이다.
 * 4) 실제 불량 샘플 확보 후 이 파일의 threshold만 재보정하는 구조다.
 */
object FormingInspectionV2 {

    data class Result(
        val faceHint: String,
        val faceConfidence: Double,
        val shapeStability: Double,
        val cornerRisk: Double,
        val wallRisk: Double,
        val centerBoundaryRisk: Double,
        val localFoldRisk: Double,
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

        val gray =
            DoubleArray(
                w *
                    h
            )

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

        for (
            i in pixels.indices
        ) {

            val c =
                pixels[
                    i
                ]

            gray[
                i
            ] =
                0.299 *
                    Color.red(
                        c
                    ) +
                    0.587 *
                    Color.green(
                        c
                    ) +
                    0.114 *
                    Color.blue(
                        c
                    )
        }

        val blur =
            boxBlur(
                gray,
                w,
                h,
                3
            )

        val local =
            boxBlur(
                gray,
                w,
                h,
                11
            )

        val gradient =
            DoubleArray(
                w *
                    h
            )

        var globalGradientSum =
            0.0

        var globalContrastSum =
            0.0

        var samples =
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

                globalGradientSum +=
                    g

                globalContrastSum +=
                    abs(
                        gray[
                            i
                        ] -
                            local[
                                i
                            ]
                    )

                samples++
            }
        }

        val meanGradient =
            if (
                samples >
                0
            ) {
                globalGradientSum /
                    samples
            } else {
                0.0
            }

        val meanLocalContrast =
            if (
                samples >
                0
            ) {
                globalContrastSum /
                    samples
            } else {
                0.0
            }

        /*
         * ROI 내부를 다음과 같이 나눠 본다.
         *
         * - 바깥 14%: Cup Wall / Edge에 가까운 영역
         * - 네 Corner 20% x 20%
         * - 중앙 수평 Boundary: 두 Cup 사이의 경계
         * - 중앙 넓은 면: 반사광 영향이 커서 낮은 가중치
         */
        val bandX =
            max(
                3,
                (
                    w *
                        0.14
                    )
                    .toInt()
            )

        val bandY =
            max(
                3,
                (
                    h *
                        0.14
                    )
                    .toInt()
            )

        val cornerW =
            max(
                4,
                (
                    w *
                        0.20
                    )
                    .toInt()
            )

        val cornerH =
            max(
                4,
                (
                    h *
                        0.20
                    )
                    .toInt()
            )

        val edgeThreshold =
            max(
                12.0,
                meanGradient *
                    (
                        1.55 -
                            sensitivity
                                .coerceIn(
                                    0,
                                    100
                                ) *
                            0.0035
                        )
            )

        fun regionStats(
            x0: Int,
            y0: Int,
            x1: Int,
            y1: Int
        ): Pair<Double, Double> {

            var strong =
                0

            var total =
                0

            var strength =
                0.0

            val sx =
                x0.coerceIn(
                    1,
                    w -
                        2
                )

            val sy =
                y0.coerceIn(
                    1,
                    h -
                        2
                )

            val ex =
                x1.coerceIn(
                    sx +
                        1,
                    w -
                        1
                )

            val ey =
                y1.coerceIn(
                    sy +
                        1,
                    h -
                        1
                )

            for (
                y in sy until
                    ey
            ) {

                for (
                    x in sx until
                        ex
                ) {

                    val g =
                        gradient[
                            y *
                                w +
                                x
                        ]

                    strength +=
                        g

                    total++

                    if (
                        g >=
                        edgeThreshold
                    ) {
                        strong++
                    }
                }
            }

            if (
                total <=
                0
            ) {
                return Pair(
                    0.0,
                    0.0
                )
            }

            return Pair(
                strong
                    .toDouble() /
                    total
                        .toDouble() *
                    100.0,
                strength /
                    total
            )
        }

        val leftBand =
            regionStats(
                1,
                bandY,
                bandX,
                h -
                    bandY
            )

        val rightBand =
            regionStats(
                w -
                    bandX,
                bandY,
                w -
                    1,
                h -
                    bandY
            )

        val topBand =
            regionStats(
                bandX,
                1,
                w -
                    bandX,
                bandY
            )

        val bottomBand =
            regionStats(
                bandX,
                h -
                    bandY,
                w -
                    bandX,
                h -
                    1
            )

        val tl =
            regionStats(
                1,
                1,
                cornerW,
                cornerH
            )

        val tr =
            regionStats(
                w -
                    cornerW,
                1,
                w -
                    1,
                cornerH
            )

        val bl =
            regionStats(
                1,
                h -
                    cornerH,
                cornerW,
                h -
                    1
            )

        val br =
            regionStats(
                w -
                    cornerW,
                h -
                    cornerH,
                w -
                    1,
                h -
                    1
            )

        val centerY =
            h /
                2

        val centerBandHalf =
            max(
                2,
                (
                    h *
                        0.035
                    )
                    .toInt()
            )

        val centerBoundary =
            regionStats(
                bandX,
                centerY -
                    centerBandHalf,
                w -
                    bandX,
                centerY +
                    centerBandHalf
            )

        val centerSurface =
            regionStats(
                bandX +
                    2,
                bandY +
                    2,
                w -
                    bandX -
                    2,
                h -
                    bandY -
                    2
            )

        /*
         * Wall risk:
         * 한쪽 Wall만 유난히 강해지는 비대칭과 평균 강도를 함께 본다.
         */
        val wallDensityAverage =
            listOf(
                leftBand.first,
                rightBand.first,
                topBand.first,
                bottomBand.first
            )
                .average()

        val wallDensitySpread =
            listOf(
                leftBand.first,
                rightBand.first,
                topBand.first,
                bottomBand.first
            )
                .let {
                    it.maxOrNull()!! -
                        it.minOrNull()!!
                }

        val wallRisk =
            (
                wallDensityAverage *
                    1.15 +
                    wallDensitySpread *
                    1.35
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Corner risk:
         * 네 코너 중 하나만 급격히 튀는 경우에 민감하도록
         * 평균보다 최대값 비중을 높인다.
         */
        val cornerDensities =
            listOf(
                tl.first,
                tr.first,
                bl.first,
                br.first
            )

        val cornerAverage =
            cornerDensities
                .average()

        val cornerMax =
            cornerDensities
                .maxOrNull()
                ?: 0.0

        val cornerSpread =
            cornerMax -
                (
                    cornerDensities
                        .minOrNull()
                        ?: 0.0
                    )

        val cornerRisk =
            (
                cornerAverage *
                    0.70 +
                    cornerMax *
                    0.65 +
                    cornerSpread *
                    0.60
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 중앙 경계 Risk:
         * 중앙 경계 자체는 정상적으로 선이 존재하므로
         * "선이 있는 것"보다 주변 대비 과도하게 강해지는 정도를 본다.
         */
        val centerBoundaryRisk =
            (
                max(
                    0.0,
                    centerBoundary.first -
                        wallDensityAverage *
                            0.85
                ) *
                    2.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Local Fold:
         * 중앙 넓은 면에서 강한 Edge가 많이 나오는 경우.
         * 다만 얇은 파우치 반사 때문에 낮은 가중치로만 사용한다.
         */
        val localFoldRisk =
            (
                max(
                    0.0,
                    centerSurface.first -
                        wallDensityAverage *
                            0.45
                ) *
                    1.45
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Reflection Risk:
         * 판정 자체보다 "사진이 반사광 영향을 많이 받는지" 표시용.
         */
        val reflectionRisk =
            (
                meanLocalContrast *
                    2.3
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Shape Stability:
         * 높을수록 정상 Master와 비슷한 상태.
         */
        val baselineDeviation =
            (
                cornerRisk *
                    0.30 +
                    wallRisk *
                    0.30 +
                    centerBoundaryRisk *
                    0.18 +
                    localFoldRisk *
                    0.17 +
                    reflectionRisk *
                    0.05
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val shapeStability =
            (
                100.0 -
                    baselineDeviation
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * FRONT / BACK 자동 힌트
         *
         * 현재는 FRONT 기준 1장, BACK 기준 1장만 명확히 라벨되어 있어
         * 확정 자동분류를 하면 과신 위험이 있다.
         *
         * 따라서 현 단계는 "힌트"만 제공하고,
         * confidence가 낮으면 반드시 확인 필요로 표시한다.
         */
        val verticalBalance =
            abs(
                topBand.second -
                    bottomBand.second
            )

        val sideBalance =
            abs(
                leftBand.second -
                    rightBand.second
            )

        val faceSignal =
            verticalBalance -
                sideBalance *
                    0.65

        val faceConfidence =
            (
                abs(
                    faceSignal
                ) *
                    3.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val faceHint =
            when {

                faceConfidence <
                    58.0 ->
                    "확인 필요"

                faceSignal >=
                    0.0 ->
                    "FRONT 추정"

                else ->
                    "BACK 추정"
            }

        /*
         * 실제 NG 사진이 없는 상태이므로 불량 조건을 보수적으로 설정.
         * 반사광 하나만으로 불량이 되지 않는다.
         */
        val judgment =
            when {

                (
                    cornerRisk >=
                        88.0 &&
                        wallRisk >=
                        82.0
                    ) ||
                    (
                        centerBoundaryRisk >=
                            90.0 &&
                            localFoldRisk >=
                            78.0
                        ) ->
                    "불량"

                baselineDeviation >=
                    76.0 ->
                    "한계정상"

                baselineDeviation >=
                    58.0 ->
                    "주의"

                else ->
                    "정상"
            }

        val reason =
            when (
                judgment
            ) {

                "불량" ->
                    "Cup Corner/Wall 또는 중앙 경계에서 정상 Master 대비 큰 형상 변화가 확인되었습니다."

                "한계정상" ->
                    "정상 10장보다 형상 변화가 크게 증가했습니다. 재확인 권고."

                "주의" ->
                    "정상 Master 대비 Corner/Wall 변화가 증가했습니다. 추세 확인 권고."

                else ->
                    "현재 정상 FORMING Master 범위로 판단됩니다."
            }

        if (
            small !==
            roi &&
            !small.isRecycled
        ) {
            small.recycle()
        }

        return Result(
            faceHint =
                faceHint,
            faceConfidence =
                faceConfidence,
            shapeStability =
                shapeStability,
            cornerRisk =
                cornerRisk,
            wallRisk =
                wallRisk,
            centerBoundaryRisk =
                centerBoundaryRisk,
            localFoldRisk =
                localFoldRisk,
            reflectionRisk =
                reflectionRisk,
            baselineDeviation =
                baselineDeviation,
            qualityScore =
                shapeStability,
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
            faceHint =
                "확인 필요",
            faceConfidence =
                0.0,
            shapeStability =
                100.0,
            cornerRisk =
                0.0,
            wallRisk =
                0.0,
            centerBoundaryRisk =
                0.0,
            localFoldRisk =
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
                "ROI가 너무 작아 FORMING V2 분석을 생략했습니다.",
            showDefectMarkers =
                false
        )
    }
}
