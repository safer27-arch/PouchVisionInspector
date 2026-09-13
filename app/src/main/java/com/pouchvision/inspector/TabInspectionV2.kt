package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * TAB V2.1 - TAB Damage + PP FLOW 정밀판정
 *
 * 사용자 기준:
 * 1) TAB 자체 데미지는 민감하게 판정
 * 2) 파우치 실링툴에 의한 PP FLOW의
 *    - 직진성
 *    - 흘러내림 / Sag
 *    - 폭
 *    - 두께(색/명암 기반 상대 추정)
 *    - 파우치 Cup과의 거리
 *    를 치명인자로 관리
 *
 * 현재는 정상 사진 10장을 기준으로 한 현장용 1차 규칙입니다.
 * 실제 NG 샘플이 확보되면 Threshold를 재보정해야 합니다.
 */
object TabInspectionV2 {

    data class Result(
        val tabPresenceConfidence: Double,

        val tabDamageRisk: Double,

        val ppFlowStraightnessRisk: Double,
        val ppFlowWidthVariation: Double,
        val ppFlowSagRisk: Double,
        val ppFlowThicknessRisk: Double,
        val cupDistanceRisk: Double,

        val sealUniformityRisk: Double,
        val boundaryRisk: Double,
        val reflectionRisk: Double,

        val ppFlowMeanWidthPercent: Double,
        val ppFlowThicknessIndex: Double,
        val cupDistancePercent: Double,

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

        if (roi.width < 30 || roi.height < 30) {
            return emptyResult()
        }

        val targetW = min(360, roi.width).coerceAtLeast(60)
        val targetH =
            max(
                60,
                (
                    targetW *
                        roi.height.toDouble() /
                        roi.width.toDouble()
                    ).toInt()
            ).coerceAtMost(420)

        val small =
            if (roi.width == targetW && roi.height == targetH) {
                roi
            } else {
                Bitmap.createScaledBitmap(
                    roi,
                    targetW,
                    targetH,
                    true
                )
            }

        val w = small.width
        val h = small.height

        val pixels = IntArray(w * h)

        small.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        val gray = DoubleArray(w * h)
        val sat = DoubleArray(w * h)
        val hue = DoubleArray(w * h)

        for (i in pixels.indices) {

            val c = pixels[i]

            val r = Color.red(c).toDouble()
            val g = Color.green(c).toDouble()
            val b = Color.blue(c).toDouble()

            gray[i] =
                0.299 * r +
                    0.587 * g +
                    0.114 * b

            val hsv = FloatArray(3)
            Color.RGBToHSV(
                r.toInt(),
                g.toInt(),
                b.toInt(),
                hsv
            )

            hue[i] = hsv[0].toDouble()
            sat[i] = hsv[1].toDouble() * 100.0
        }

        val blur = boxBlur(gray, w, h, 2)
        val localMean = boxBlur(gray, w, h, 9)

        val gradient = DoubleArray(w * h)

        var globalGradientSum = 0.0
        var globalContrastSum = 0.0
        var globalSamples = 0

        for (y in 1 until h - 1) {

            for (x in 1 until w - 1) {

                val i = y * w + x

                val gx =
                    blur[i + 1] -
                        blur[i - 1]

                val gy =
                    blur[i + w] -
                        blur[i - w]

                val g =
                    sqrt(
                        gx * gx +
                            gy * gy
                    )

                gradient[i] = g

                globalGradientSum += g

                globalContrastSum +=
                    abs(
                        gray[i] -
                            localMean[i]
                    )

                globalSamples++
            }
        }

        val meanGradient =
            if (globalSamples > 0) {
                globalGradientSum /
                    globalSamples
            } else {
                0.0
            }

        val reflectionRisk =
            if (globalSamples > 0) {
                (
                    globalContrastSum /
                        globalSamples *
                        2.0
                    ).coerceIn(
                    0.0,
                    100.0
                )
            } else {
                0.0
            }

        /*
         * =====================================================
         * 1. PP FLOW 색 영역 탐색
         * =====================================================
         *
         * 현재 사진에서 PP FLOW는 노랑/주황 계열로 보이므로
         * Warm color + saturation을 이용합니다.
         * 너무 어두운/밝은 반사광은 제외합니다.
         */
        val warmMask =
            BooleanArray(
                w *
                    h
            )

        var warmCount = 0

        for (y in 1 until h - 1) {

            for (x in 1 until w - 1) {

                val i = y * w + x

                val warmHue =
                    hue[i] >= 18.0 &&
                        hue[i] <= 65.0

                val enoughSat =
                    sat[i] >=
                        25.0

                val enoughBrightness =
                    gray[i] >=
                        45.0 &&
                        gray[i] <=
                        245.0

                val candidate =
                    warmHue &&
                        enoughSat &&
                        enoughBrightness

                warmMask[i] =
                    candidate

                if (candidate) {
                    warmCount++
                }
            }
        }

        val tabPresenceConfidence =
            (
                warmCount
                    .toDouble() /
                    (w * h)
                        .toDouble() *
                    2200.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * warm PP FLOW component의 bbox와 row profile을 구함.
         */
        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1

        for (y in 0 until h) {

            for (x in 0 until w) {

                if (
                    warmMask[
                        y *
                            w +
                            x
                    ]
                ) {

                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
        }

        if (
            maxX < minX ||
            maxY < minY
        ) {

            if (
                small !== roi &&
                !small.isRecycled
            ) {
                small.recycle()
            }

            return Result(
                tabPresenceConfidence =
                    tabPresenceConfidence,
                tabDamageRisk =
                    0.0,
                ppFlowStraightnessRisk =
                    0.0,
                ppFlowWidthVariation =
                    0.0,
                ppFlowSagRisk =
                    0.0,
                ppFlowThicknessRisk =
                    0.0,
                cupDistanceRisk =
                    0.0,
                sealUniformityRisk =
                    0.0,
                boundaryRisk =
                    0.0,
                reflectionRisk =
                    reflectionRisk,
                ppFlowMeanWidthPercent =
                    0.0,
                ppFlowThicknessIndex =
                    0.0,
                cupDistancePercent =
                    0.0,
                baselineDeviation =
                    22.0,
                qualityScore =
                    78.0,
                judgment =
                    "정상",
                reason =
                    "TAB/PP FLOW 색 영역 인식 Confidence가 낮습니다. ROI 위치를 확인해주세요.",
                showDefectMarkers =
                    false
            )
        }

        val flowHeight =
            max(
                1,
                maxY -
                    minY +
                    1
            )

        val rowCenters =
            mutableListOf<Double>()

        val rowWidths =
            mutableListOf<Double>()

        val rowBrightness =
            mutableListOf<Double>()

        for (y in minY..maxY) {

            var rowMinX =
                w

            var rowMaxX =
                -1

            var brightnessSum =
                0.0

            var rowCount =
                0

            for (x in minX..maxX) {

                val i =
                    y *
                        w +
                        x

                if (
                    warmMask[i]
                ) {

                    rowMinX =
                        min(
                            rowMinX,
                            x
                        )

                    rowMaxX =
                        max(
                            rowMaxX,
                            x
                        )

                    brightnessSum +=
                        gray[i]

                    rowCount++
                }
            }

            if (
                rowCount >
                0 &&
                rowMaxX >=
                rowMinX
            ) {

                rowCenters.add(
                    (
                        rowMinX +
                            rowMaxX
                        ) /
                        2.0
                )

                rowWidths.add(
                    (
                        rowMaxX -
                            rowMinX +
                            1
                        )
                        .toDouble()
                )

                rowBrightness.add(
                    brightnessSum /
                        rowCount
                )
            }
        }

        val meanWidth =
            if (
                rowWidths.isNotEmpty()
            ) {
                rowWidths.average()
            } else {
                0.0
            }

        val ppFlowMeanWidthPercent =
            (
                meanWidth /
                    w.toDouble() *
                    100.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 2. PP FLOW 직진성
         * =====================================================
         *
         * 각 row의 중심선이 좌우로 얼마나 흔들리는지 계산.
         */
        val meanCenter =
            if (
                rowCenters.isNotEmpty()
            ) {
                rowCenters.average()
            } else {
                w /
                    2.0
            }

        val centerStd =
            if (
                rowCenters.isNotEmpty()
            ) {

                sqrt(
                    rowCenters
                        .map {
                            val d =
                                it -
                                    meanCenter

                            d *
                                d
                        }
                        .average()
                )

            } else {
                0.0
            }

        val ppFlowStraightnessRisk =
            (
                centerStd /
                    max(
                        1.0,
                        meanWidth
                    ) *
                    55.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 3. PP FLOW 폭 변화
         * =====================================================
         */
        val widthStd =
            if (
                rowWidths.isNotEmpty()
            ) {

                sqrt(
                    rowWidths
                        .map {
                            val d =
                                it -
                                    meanWidth

                            d *
                                d
                        }
                        .average()
                )

            } else {
                0.0
            }

        val ppFlowWidthVariation =
            (
                widthStd /
                    max(
                        1.0,
                        meanWidth
                    ) *
                    100.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 4. PP FLOW 흘러내림 / Sag
         * =====================================================
         *
         * flow 상/중/하 구간 중심선의 이동량 + 폭 증가를 같이 봄.
         */
        fun segmentMean(
            values: List<Double>,
            startRatio: Double,
            endRatio: Double
        ): Double {

            if (
                values.isEmpty()
            ) {
                return 0.0
            }

            val start =
                (
                    values.size *
                        startRatio
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        values.size -
                            1
                    )

            val end =
                (
                    values.size *
                        endRatio
                    )
                    .toInt()
                    .coerceIn(
                        start +
                            1,
                        values.size
                    )

            return values
                .subList(
                    start,
                    end
                )
                .average()
        }

        val upperCenter =
            segmentMean(
                rowCenters,
                0.05,
                0.33
            )

        val middleCenter =
            segmentMean(
                rowCenters,
                0.34,
                0.66
            )

        val lowerCenter =
            segmentMean(
                rowCenters,
                0.67,
                0.95
            )

        val upperWidth =
            segmentMean(
                rowWidths,
                0.05,
                0.33
            )

        val lowerWidth =
            segmentMean(
                rowWidths,
                0.67,
                0.95
            )

        val sagShift =
            max(
                abs(
                    lowerCenter -
                        middleCenter
                ),
                abs(
                    upperCenter -
                        middleCenter
                )
            )

        val sagWidthExpansion =
            max(
                0.0,
                lowerWidth -
                    upperWidth
            )

        val ppFlowSagRisk =
            (
                sagShift /
                    max(
                        1.0,
                        meanWidth
                    ) *
                    55.0 +
                    sagWidthExpansion /
                    max(
                        1.0,
                        meanWidth
                    ) *
                    45.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 5. PP FLOW 두께 상대지수
         * =====================================================
         *
         * 실제 두께(mm)가 아니라 색/명암 기반 상대 Index.
         * 동일 조명/카메라 조건에서 추세용으로만 사용.
         */
        val meanFlowBrightness =
            if (
                rowBrightness.isNotEmpty()
            ) {
                rowBrightness.average()
            } else {
                0.0
            }

        val ppFlowThicknessIndex =
            (
                (
                    255.0 -
                        meanFlowBrightness
                    ) /
                    255.0 *
                    100.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val brightnessStd =
            if (
                rowBrightness.isNotEmpty()
            ) {

                sqrt(
                    rowBrightness
                        .map {
                            val d =
                                it -
                                    meanFlowBrightness

                            d *
                                d
                        }
                        .average()
                )

            } else {
                0.0
            }

        val ppFlowThicknessRisk =
            (
                brightnessStd *
                    2.0 +
                    abs(
                        ppFlowThicknessIndex -
                            42.0
                    ) *
                    0.45
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 6. Cup과 PP FLOW 거리
         * =====================================================
         *
         * PP FLOW bbox에서 ROI 안쪽 방향으로 가장 가까운 강한 수직 경계를 찾는다.
         * 실제 mm가 아닌 ROI width 대비 상대 거리(%).
         */
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
                            0.003
                        )
            )

        val searchY0 =
            minY.coerceIn(
                1,
                h -
                    2
            )

        val searchY1 =
            maxY.coerceIn(
                searchY0 +
                    1,
                h -
                    1
            )

        var bestBoundaryX =
            -1

        var bestBoundaryScore =
            0.0

        /*
         * PP FLOW가 좌측/우측 어느 쪽에 있든,
         * ROI 중심 방향을 "Cup 안쪽"으로 가정.
         */
        val flowOnLeft =
            meanCenter <
                w /
                    2.0

        val searchStart =
            if (
                flowOnLeft
            ) {
                maxX +
                    1
            } else {
                minX -
                    1
            }

        val searchEnd =
            if (
                flowOnLeft
            ) {
                min(
                    w -
                        2,
                    (
                        w *
                            0.80
                        )
                        .toInt()
                )
            } else {
                max(
                    1,
                    (
                        w *
                            0.20
                        )
                        .toInt()
                )
            }

        if (
            flowOnLeft
        ) {

            for (
                x in searchStart..
                    searchEnd
            ) {

                var score =
                    0.0

                var count =
                    0

                for (
                    y in searchY0 until
                        searchY1
                ) {

                    val i =
                        y *
                            w +
                            x

                    score +=
                        gradient[i]

                    count++
                }

                val avg =
                    if (
                        count >
                        0
                    ) {
                        score /
                            count
                    } else {
                        0.0
                    }

                if (
                    avg >
                    bestBoundaryScore
                ) {

                    bestBoundaryScore =
                        avg

                    bestBoundaryX =
                        x
                }
            }

        } else {

            for (
                x in searchStart downTo
                    searchEnd
            ) {

                var score =
                    0.0

                var count =
                    0

                for (
                    y in searchY0 until
                        searchY1
                ) {

                    val i =
                        y *
                            w +
                            x

                    score +=
                        gradient[i]

                    count++
                }

                val avg =
                    if (
                        count >
                        0
                    ) {
                        score /
                            count
                    } else {
                        0.0
                    }

                if (
                    avg >
                    bestBoundaryScore
                ) {

                    bestBoundaryScore =
                        avg

                    bestBoundaryX =
                        x
                }
            }
        }

        val rawCupDistancePx =
            if (
                bestBoundaryX >=
                0
            ) {
                if (
                    flowOnLeft
                ) {
                    bestBoundaryX -
                        maxX
                } else {
                    minX -
                        bestBoundaryX
                }
            } else {
                0
            }

        val cupDistancePercent =
            (
                rawCupDistancePx
                    .toDouble() /
                    w.toDouble() *
                    100.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 정상 사진 기준에서 너무 가깝거나 너무 멀면 Risk 상승.
         * 실제 mm 기준은 추후 scale calibration 필요.
         */
        val cupDistanceRisk =
            when {

                cupDistancePercent <
                    3.0 ->
                    (
                        3.0 -
                            cupDistancePercent
                        ) *
                        22.0

                cupDistancePercent >
                    18.0 ->
                    (
                        cupDistancePercent -
                            18.0
                        ) *
                        5.0

                else ->
                    0.0
            }
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 7. TAB Damage Risk
         * =====================================================
         *
         * PP FLOW보다 금속 TAB 쪽의 국부 강한 Edge/불연속을 더 민감하게 봄.
         * Warm bbox 바깥쪽의 금속 TAB 영역을 추정해서 검사.
         */
        val tabRegionX0 =
            if (
                flowOnLeft
            ) {
                max(
                    1,
                    minX -
                        (
                            meanWidth *
                                2.2
                            )
                            .toInt()
                )
            } else {
                min(
                    w -
                        2,
                    maxX +
                        1
                )
            }

        val tabRegionX1 =
            if (
                flowOnLeft
            ) {
                max(
                    tabRegionX0 +
                        1,
                    minX -
                        1
                )
            } else {
                min(
                    w -
                        1,
                    maxX +
                        (
                            meanWidth *
                                2.2
                            )
                            .toInt()
                )
            }

        var tabStrong =
            0

        var tabPixels =
            0

        var tabPeak =
            0.0

        val tx0 =
            min(
                tabRegionX0,
                tabRegionX1
            )
                .coerceIn(
                    1,
                    w -
                        2
                )

        val tx1 =
            max(
                tabRegionX0,
                tabRegionX1
            )
                .coerceIn(
                    tx0 +
                        1,
                    w -
                        1
                )

        for (
            y in searchY0 until
                searchY1
        ) {

            for (
                x in tx0 until
                    tx1
            ) {

                val g =
                    gradient[
                        y *
                            w +
                            x
                    ]

                tabPeak =
                    max(
                        tabPeak,
                        g
                    )

                tabPixels++

                if (
                    g >=
                    edgeThreshold *
                        1.25
                ) {
                    tabStrong++
                }
            }
        }

        val tabStrongDensity =
            if (
                tabPixels >
                0
            ) {
                tabStrong
                    .toDouble() /
                    tabPixels
                        .toDouble() *
                    100.0
            } else {
                0.0
            }

        /*
         * TAB Damage는 민감하게:
         * 상대적으로 작은 강한 Edge 증가도 Risk에 크게 반영.
         */
        val tabDamageRisk =
            (
                tabStrongDensity *
                    2.3 +
                    max(
                        0.0,
                        tabPeak -
                            edgeThreshold *
                                1.4
                    ) *
                    0.65
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * Seal Uniformity / Boundary 보조지표
         */
        val sealUniformityRisk =
            (
                ppFlowStraightnessRisk *
                    0.35 +
                    ppFlowWidthVariation *
                    0.30 +
                    ppFlowThicknessRisk *
                    0.20 +
                    ppFlowSagRisk *
                    0.15
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val boundaryRisk =
            (
                cupDistanceRisk *
                    0.50 +
                    ppFlowSagRisk *
                    0.25 +
                    ppFlowStraightnessRisk *
                    0.25
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 치명인자 우선 가중치
         */
        val baselineDeviation =
            (
                tabDamageRisk *
                    0.30 +
                    ppFlowStraightnessRisk *
                    0.13 +
                    ppFlowWidthVariation *
                    0.13 +
                    ppFlowSagRisk *
                    0.12 +
                    ppFlowThicknessRisk *
                    0.10 +
                    cupDistanceRisk *
                    0.14 +
                    reflectionRisk *
                    0.03 +
                    (
                        100.0 -
                            tabPresenceConfidence
                        ) *
                    0.05
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
         * 치명인자 override
         * - TAB Damage는 단독으로도 더 민감하게 판정
         * - PP FLOW는 여러 항목이 동시에 높을 때 상향 판정
         */
        val criticalPpFlowCount =
            listOf(
                ppFlowStraightnessRisk,
                ppFlowWidthVariation,
                ppFlowSagRisk,
                ppFlowThicknessRisk,
                cupDistanceRisk
            )
                .count {
                    it >=
                        72.0
                }

        val judgment =
            when {

                tabDamageRisk >=
                    88.0 ->
                    "불량"

                tabDamageRisk >=
                    72.0 ->
                    "한계정상"

                criticalPpFlowCount >=
                    3 ->
                    "불량"

                criticalPpFlowCount ==
                    2 ->
                    "한계정상"

                baselineDeviation >=
                    70.0 ->
                    "한계정상"

                baselineDeviation >=
                    52.0 ||
                    tabDamageRisk >=
                    55.0 ->
                    "주의"

                else ->
                    "정상"
            }

        val reason =
            when {

                tabPresenceConfidence <
                    12.0 ->
                    "TAB/PP FLOW 인식 Confidence가 낮습니다. ROI에 TAB과 주변 Seal을 포함해주세요."

                tabDamageRisk >=
                    72.0 ->
                    "TAB 자체 데미지 Risk가 높습니다. TAB 변형/찍힘/Edge 손상 재확인 권고."

                criticalPpFlowCount >=
                    2 ->
                    "PP FLOW 직진성/폭/흘러내림/두께/컵 거리 중 복수 치명인자가 정상 기준에서 이탈했습니다."

                judgment ==
                    "주의" ->
                    "TAB 또는 PP FLOW 지표가 정상 Master 범위보다 증가했습니다."

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

            tabDamageRisk =
                tabDamageRisk,

            ppFlowStraightnessRisk =
                ppFlowStraightnessRisk,
            ppFlowWidthVariation =
                ppFlowWidthVariation,
            ppFlowSagRisk =
                ppFlowSagRisk,
            ppFlowThicknessRisk =
                ppFlowThicknessRisk,
            cupDistanceRisk =
                cupDistanceRisk,

            sealUniformityRisk =
                sealUniformityRisk,
            boundaryRisk =
                boundaryRisk,
            reflectionRisk =
                reflectionRisk,

            ppFlowMeanWidthPercent =
                ppFlowMeanWidthPercent,
            ppFlowThicknessIndex =
                ppFlowThicknessIndex,
            cupDistancePercent =
                cupDistancePercent,

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

            tabDamageRisk =
                0.0,

            ppFlowStraightnessRisk =
                0.0,
            ppFlowWidthVariation =
                0.0,
            ppFlowSagRisk =
                0.0,
            ppFlowThicknessRisk =
                0.0,
            cupDistanceRisk =
                0.0,

            sealUniformityRisk =
                0.0,
            boundaryRisk =
                0.0,
            reflectionRisk =
                0.0,

            ppFlowMeanWidthPercent =
                0.0,
            ppFlowThicknessIndex =
                0.0,
            cupDistancePercent =
                0.0,

            baselineDeviation =
                0.0,
            qualityScore =
                100.0,
            judgment =
                "정상",
            reason =
                "ROI가 너무 작아 TAB V2.1 분석을 생략했습니다.",
            showDefectMarkers =
                false
        )
    }
}
