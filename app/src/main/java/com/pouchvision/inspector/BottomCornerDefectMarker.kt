package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object BottomCornerDefectMarker {

    data class WrinkleRegion(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val score: Double,
        val pointCount: Int
    )

    data class Result(
        val bitmap: Bitmap,
        val regions: List<WrinkleRegion>,
        val wrinkleScore: Double,
        val judgment: String,
        val localContrast: Double,
        val lineDensity: Double,
        val concentration: Double
    )

    private data class CandidatePoint(
        val x: Float,
        val y: Float,
        val strength: Double
    )

    private data class Cluster(
        var centerX: Float,
        var centerY: Float,
        var pointCount: Int,
        var totalStrength: Double,

        var minX: Float,
        var maxX: Float,
        var minY: Float,
        var maxY: Float,

        var sumX: Double,
        var sumY: Double,
        var sumXX: Double,
        var sumYY: Double
    )

    private data class ScoredCluster(
        val cluster: Cluster,
        val score: Double,
        val straightPenalty: Double
    )

    fun analyze(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        sensitivity: Int,
        maxRegions: Int = 4
    ): Result {

        /*
         * =====================================================
         * 1. 안전한 ROI
         * =====================================================
         */

        val safeLeft =
            roiLeft.coerceIn(
                0,
                sourceBitmap.width - 1
            )

        val safeTop =
            roiTop.coerceIn(
                0,
                sourceBitmap.height - 1
            )

        val safeRight =
            (roiLeft + roiWidth)
                .coerceIn(
                    safeLeft + 1,
                    sourceBitmap.width
                )

        val safeBottom =
            (roiTop + roiHeight)
                .coerceIn(
                    safeTop + 1,
                    sourceBitmap.height
                )

        val safeWidth =
            safeRight - safeLeft

        val safeHeight =
            safeBottom - safeTop

        val roiBitmap =
            Bitmap.createBitmap(
                sourceBitmap,
                safeLeft,
                safeTop,
                safeWidth,
                safeHeight
            )

        /*
         * =====================================================
         * 2. 분석용 크기로 축소
         * =====================================================
         */

        val analysisWidth =
            min(
                340,
                max(
                    120,
                    safeWidth
                )
            )

        val scale =
            analysisWidth.toFloat() /
                safeWidth.toFloat()

        val analysisHeight =
            max(
                80,
                (safeHeight * scale)
                    .toInt()
            )

        val small =
            Bitmap.createScaledBitmap(
                roiBitmap,
                analysisWidth,
                analysisHeight,
                true
            )

        /*
         * =====================================================
         * 3. Gray 변환
         * =====================================================
         */

        val gray =
            Array(
                analysisHeight
            ) {
                IntArray(
                    analysisWidth
                )
            }

        for (y in 0 until analysisHeight) {

            for (x in 0 until analysisWidth) {

                gray[y][x] =
                    grayValue(
                        small.getPixel(
                            x,
                            y
                        )
                    )
            }
        }

        /*
         * =====================================================
         * 4. Local Mean
         *
         * 넓고 완만한 음영을 제거하기 위한 핵심입니다.
         *
         * 정상 Sample에서 보였던
         * 넓은 밝기 변화는 억제하고,
         * 짧고 급격한 주름만 남기는 목적입니다.
         * =====================================================
         */

        val integral =
            Array(
                analysisHeight + 1
            ) {
                LongArray(
                    analysisWidth + 1
                )
            }

        for (y in 0 until analysisHeight) {

            var rowSum =
                0L

            for (x in 0 until analysisWidth) {

                rowSum +=
                    gray[y][x]

                integral[y + 1][x + 1] =
                    integral[y][x + 1] +
                        rowSum
            }
        }

        fun localMean(
            x: Int,
            y: Int,
            radius: Int
        ): Double {

            val left =
                max(
                    0,
                    x - radius
                )

            val right =
                min(
                    analysisWidth - 1,
                    x + radius
                )

            val top =
                max(
                    0,
                    y - radius
                )

            val bottom =
                min(
                    analysisHeight - 1,
                    y + radius
                )

            val sum =
                integral[bottom + 1][right + 1] -
                    integral[top][right + 1] -
                    integral[bottom + 1][left] +
                    integral[top][left]

            val count =
                (right - left + 1) *
                    (bottom - top + 1)

            return sum.toDouble() /
                max(
                    1,
                    count
                )
        }

        /*
         * =====================================================
         * 5. 민감도
         *
         * 기존보다 영향력을 완화했습니다.
         *
         * 60% = 기준
         * 80%에서도 과도한 점수 상승을 막습니다.
         * =====================================================
         */

        val safeSensitivity =
            sensitivity.coerceIn(
                0,
                100
            )

        val sensitivityOffset =
            safeSensitivity -
                60

        val gradientThreshold =
            (
                54 -
                    sensitivityOffset *
                    0.12
                )
                .toInt()
                .coerceIn(
                    42,
                    64
                )

        val residualThreshold =
            (
                14 -
                    sensitivityOffset *
                    0.035
                )
                .toInt()
                .coerceIn(
                    10,
                    17
                )

        val sensitivityFactor =
            (
                1.0 +
                    sensitivityOffset *
                    0.003
                )
                .coerceIn(
                    0.88,
                    1.12
                )

        /*
         * =====================================================
         * 6. ROI 외곽 제외
         *
         * 긴 파우치 외곽선 / 설비 Edge 영향 감소
         * =====================================================
         */

        val marginX =
            max(
                6,
                (analysisWidth * 0.07)
                    .toInt()
            )

        val marginY =
            max(
                6,
                (analysisHeight * 0.07)
                    .toInt()
            )

        val candidates =
            mutableListOf<CandidatePoint>()

        var totalCandidateStrength =
            0.0

        var totalResidual =
            0.0

        var acceptedPixelCount =
            0

        /*
         * =====================================================
         * 7. 후보점 검출
         * =====================================================
         */

        for (
            y in marginY until
                analysisHeight - marginY
        ) {

            for (
                x in marginX until
                    analysisWidth - marginX
        ) {

                /*
                 * Sobel과 유사한 간단한 Gradient
                 */
                val gx =
                    abs(
                        gray[y][x + 1] -
                            gray[y][x - 1]
                    )

                val gy =
                    abs(
                        gray[y + 1][x] -
                            gray[y - 1][x]
                    )

                val gradient =
                    gx + gy

                if (
                    gradient <
                    gradientThreshold
                ) {

                    continue
                }

                /*
                 * 주변 평균과 현재 Pixel 차이
                 *
                 * 넓은 음영이면 차이가 작고,
                 * 좁은 주름이면 차이가 커집니다.
                 */
                val meanSmall =
                    localMean(
                        x,
                        y,
                        4
                    )

                val meanLarge =
                    localMean(
                        x,
                        y,
                        9
                    )

                val residualSmall =
                    abs(
                        gray[y][x] -
                            meanSmall
                    )

                val residualLarge =
                    abs(
                        gray[y][x] -
                            meanLarge
                    )

                val residual =
                    residualSmall *
                        0.65 +
                        residualLarge *
                        0.35

                if (
                    residual <
                    residualThreshold
                ) {

                    continue
                }

                /*
                 * 매우 밝은 반사는 감점
                 */
                val brightness =
                    gray[y][x]

                val reflectionPenalty =
                    when {

                        brightness >= 245 ->
                            0.35

                        brightness >= 232 ->
                            0.55

                        brightness >= 218 ->
                            0.78

                        else ->
                            1.0
                    }

                /*
                 * 매우 강한 단일 Edge도
                 * 파우치 경계일 가능성이 있으므로 감점
                 */
                val extremeEdgePenalty =
                    when {

                        gradient >= 230 ->
                            0.50

                        gradient >= 190 ->
                            0.72

                        else ->
                            1.0
                    }

                /*
                 * ROI 가장자리 쪽으로 갈수록
                 * 조금씩 감점
                 */
                val nx =
                    abs(
                        x -
                            analysisWidth / 2f
                    ) /
                        (
                            analysisWidth /
                                2f
                            )

                val ny =
                    abs(
                        y -
                            analysisHeight / 2f
                    ) /
                        (
                            analysisHeight /
                                2f
                            )

                val centerPenalty =
                    (
                        1.0 -
                            max(
                                nx,
                                ny
                            ) *
                            0.35
                        )
                        .coerceIn(
                            0.55,
                            1.0
                        )

                val strength =
                    (
                        residual *
                            1.45 +
                            gradient *
                            0.18
                        ) *
                        reflectionPenalty *
                        extremeEdgePenalty *
                        centerPenalty *
                        sensitivityFactor

                /*
                 * 약한 후보 제거
                 */
                if (
                    strength <
                    24.0
                ) {

                    continue
                }

                candidates.add(
                    CandidatePoint(
                        x =
                            x.toFloat(),

                        y =
                            y.toFloat(),

                        strength =
                            strength
                    )
                )

                totalCandidateStrength +=
                    strength

                totalResidual +=
                    residual

                acceptedPixelCount++
            }
        }

        /*
         * =====================================================
         * 8. 1차 군집화
         * =====================================================
         */

        val clusterDistance =
            max(
                12f,
                min(
                    analysisWidth,
                    analysisHeight
                ) *
                    0.065f
            )

        val clusters =
            mutableListOf<Cluster>()

        for (
            point in candidates
        ) {

            var nearest:
                Cluster? =
                null

            var nearestDistance =
                Float.MAX_VALUE

            for (
                cluster in clusters
            ) {

                val dx =
                    point.x -
                        cluster.centerX

                val dy =
                    point.y -
                        cluster.centerY

                val distance =
                    sqrt(
                        dx * dx +
                            dy * dy
                    )

                if (
                    distance <
                    clusterDistance &&
                    distance <
                    nearestDistance
                ) {

                    nearest =
                        cluster

                    nearestDistance =
                        distance
                }
            }

            if (
                nearest ==
                null
            ) {

                clusters.add(
                    Cluster(
                        centerX =
                            point.x,

                        centerY =
                            point.y,

                        pointCount =
                            1,

                        totalStrength =
                            point.strength,

                        minX =
                            point.x,

                        maxX =
                            point.x,

                        minY =
                            point.y,

                        maxY =
                            point.y,

                        sumX =
                            point.x.toDouble(),

                        sumY =
                            point.y.toDouble(),

                        sumXX =
                            point.x *
                                point.x.toDouble(),

                        sumYY =
                            point.y *
                                point.y.toDouble()
                    )
                )

            } else {

                addPointToCluster(
                    nearest,
                    point
                )
            }
        }

        /*
         * =====================================================
         * 9. 가까운 군집 추가 병합
         *
         * 기존 화면에서 후보 1,2,3,4가
         * 같은 Corner 주변에 겹쳐 나왔던 문제를
         * 줄이기 위한 부분입니다.
         * =====================================================
         */

        val mergeDistance =
            max(
                20f,
                min(
                    analysisWidth,
                    analysisHeight
                ) *
                    0.14f
            )

        var merged =
            true

        while (merged) {

            merged =
                false

            outer@
            for (
                i in 0 until
                    clusters.size
            ) {

                for (
                    j in i + 1 until
                        clusters.size
                ) {

                    val a =
                        clusters[i]

                    val b =
                        clusters[j]

                    val dx =
                        a.centerX -
                            b.centerX

                    val dy =
                        a.centerY -
                            b.centerY

                    val distance =
                        sqrt(
                            dx * dx +
                                dy * dy
                        )

                    if (
                        distance <=
                        mergeDistance
                    ) {

                        mergeClusters(
                            a,
                            b
                        )

                        clusters.removeAt(
                            j
                        )

                        merged =
                            true

                        break@outer
                    }
                }
            }
        }

        /*
         * =====================================================
         * 10. 군집 평가
         * =====================================================
         */

        val scoredClusters =
            clusters
                .filter {

                    it.pointCount >=
                        5
                }
                .map { cluster ->

                    val width =
                        max(
                            1f,
                            cluster.maxX -
                                cluster.minX
                        )

                    val height =
                        max(
                            1f,
                            cluster.maxY -
                                cluster.minY
                        )

                    val longSide =
                        max(
                            width,
                            height
                        )

                    val shortSide =
                        min(
                            width,
                            height
                        )

                    val aspectRatio =
                        longSide /
                            max(
                                1f,
                                shortSide
                            )

                    val longSideRatio =
                        longSide /
                            min(
                                analysisWidth,
                                analysisHeight
                            )
                                .toFloat()

                    /*
                     * 길고 매우 얇은 선은
                     * 정상 Edge일 가능성이 높음
                     */
                    val straightPenalty =
                        when {

                            aspectRatio >= 7.0 &&
                                longSideRatio >= 0.30 ->
                                0.25

                            aspectRatio >= 5.0 &&
                                longSideRatio >= 0.25 ->
                                0.45

                            aspectRatio >= 3.5 &&
                                longSideRatio >= 0.22 ->
                                0.70

                            else ->
                                1.0
                        }

                    val averageStrength =
                        cluster.totalStrength /
                            cluster.pointCount

                    /*
                     * 점 개수가 무조건 많다고
                     * 높은 점수를 주지 않도록
                     * Bonus를 제한
                     */
                    val pointBonus =
                        (
                            1.0 +
                                min(
                                    0.30,
                                    cluster.pointCount /
                                        100.0
                                )
                            )

                    val regionScore =
                        averageStrength *
                            pointBonus *
                            straightPenalty

                    ScoredCluster(
                        cluster =
                            cluster,

                        score =
                            regionScore,

                        straightPenalty =
                            straightPenalty
                    )
                }
                .filter {

                    it.score >=
                        22.0
                }
                .sortedByDescending {

                    it.score
                }
                .take(
                    maxRegions
                )

        /*
         * =====================================================
         * 11. 전체 특성값
         * =====================================================
         */

        val usableWidth =
            max(
                1,
                analysisWidth -
                    marginX * 2
            )

        val usableHeight =
            max(
                1,
                analysisHeight -
                    marginY * 2
            )

        val usablePixelCount =
            max(
                1,
                usableWidth *
                    usableHeight
            )

        val lineDensity =
            candidates.size
                .toDouble() /
                usablePixelCount
                    .toDouble() *
                100.0

        val averageContrast =
            if (
                acceptedPixelCount >
                0
            ) {

                totalResidual /
                    acceptedPixelCount

            } else {

                0.0
            }

        /*
         * =====================================================
         * 12. Concentration 재계산
         *
         * 기존에는 Region 절대 강도가 그대로 들어가
         * 정상 사진도 90 이상으로 올라갈 수 있었습니다.
         *
         * 이번에는 후보점이 어느 정도 한 곳에
         * 집중되는지만 0~100으로 계산합니다.
         * =====================================================
         */

        val totalFilteredPoints =
            scoredClusters.sumOf {

                it.cluster.pointCount
            }

        val topPoints =
            scoredClusters
                .firstOrNull()
                ?.cluster
                ?.pointCount
                ?: 0

        val concentration =
            if (
                totalFilteredPoints >
                0
            ) {

                (
                    topPoints.toDouble() /
                        totalFilteredPoints
                            .toDouble() *
                        100.0
                    )
                    .coerceIn(
                        0.0,
                        100.0
                    )

            } else {

                0.0
            }

        /*
         * =====================================================
         * 13. Wrinkle Score 2차 보정
         *
         * 중요:
         *
         * Concentration 단독으로 점수가
         * 폭등하지 않도록 변경했습니다.
         *
         * 실제 국부 대비 + 후보 강도 +
         * 후보 영역 존재 여부 중심입니다.
         * =====================================================
         */

        val topScore =
            scoredClusters
                .getOrNull(
                    0
                )
                ?.score
                ?: 0.0

        val secondScore =
            scoredClusters
                .getOrNull(
                    1
                )
                ?.score
                ?: 0.0

        val thirdScore =
            scoredClusters
                .getOrNull(
                    2
                )
                ?.score
                ?: 0.0

        val regionStrength =
            topScore *
                0.65 +
                secondScore *
                0.25 +
                thirdScore *
                0.10

        var rawScore =
            (
                averageContrast *
                    0.45 +
                    regionStrength *
                    0.38 +
                    lineDensity *
                    1.50 +
                    scoredClusters.size *
                    1.50 +
                    concentration *
                    0.035
                )

        /*
         * 후보가 없거나 매우 약하면
         * 정상 쪽으로 크게 감점
         */
        if (
            scoredClusters.isEmpty()
        ) {

            rawScore *=
                0.28

        } else if (
            scoredClusters.size ==
            1
        ) {

            rawScore *=
                0.82
        }

        /*
         * 가장 강한 후보가 약하면
         * 정상적인 반사/Edge일 가능성이 높음
         */
        if (
            topScore <
            32.0
        ) {

            rawScore *=
                0.72
        }

        /*
         * 후보가 한 영역에 매우 집중돼도
         * 국부 대비가 충분하지 않으면
         * 단순 Corner 경계로 판단하여 감점
         */
        if (
            concentration >
            80.0 &&
            averageContrast <
            19.0
        ) {

            rawScore *=
                0.68
        }

        /*
         * 강한 실제 국부 변화가 있을 때만 가산
         */
        if (
            topScore >
            52.0 &&
            averageContrast >
            22.0
        ) {

            rawScore +=
                4.0
        }

        if (
            topScore >
            68.0 &&
            averageContrast >
            28.0
        ) {

            rawScore +=
                6.0
        }

        val wrinkleScore =
            (
                rawScore *
                    sensitivityFactor
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * =====================================================
         * 14. 4단계 판정
         *
         * 경계값은 아직 유지합니다.
         *
         * 정상       < 27
         * 주의       < 47
         * 한계정상   < 68
         * 불량 후보  >= 68
         *
         * 먼저 알고리즘 자체 변화만 평가합니다.
         * =====================================================
         */

        val judgment =
            when {

                wrinkleScore <
                    27.0 ->

                    "정상"

                wrinkleScore <
                    47.0 ->

                    "주의"

                wrinkleScore <
                    68.0 ->

                    "한계정상"

                else ->

                    "불량 후보"
            }

        /*
         * =====================================================
         * 15. 화면 표시
         * =====================================================
         */

        val markedBitmap =
            sourceBitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        val canvas =
            Canvas(
                markedBitmap
            )

        val circlePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.RED

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    max(
                        4f,
                        sourceBitmap.width /
                            220f
                    )
            }

        val boxPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.RED

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    max(
                        3f,
                        sourceBitmap.width /
                            260f
                    )
            }

        val labelBackgroundPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.argb(
                        210,
                        190,
                        0,
                        0
                    )

                style =
                    Paint.Style.FILL
            }

        val textPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.WHITE

                textSize =
                    max(
                        24f,
                        sourceBitmap.width /
                            32f
                    )

                style =
                    Paint.Style.FILL
            }

        val scaleBackX =
            safeWidth.toFloat() /
                analysisWidth.toFloat()

        val scaleBackY =
            safeHeight.toFloat() /
                analysisHeight.toFloat()

        val regions =
            mutableListOf<WrinkleRegion>()

        scoredClusters.forEachIndexed {
                index,
                scored ->

            val cluster =
                scored.cluster

            val centerX =
                safeLeft +
                    cluster.centerX *
                    scaleBackX

            val centerY =
                safeTop +
                    cluster.centerY *
                    scaleBackY

            /*
             * 후보 실제 크기 참고
             */
            val clusterWidth =
                max(
                    1f,
                    cluster.maxX -
                        cluster.minX
                ) *
                    scaleBackX

            val clusterHeight =
                max(
                    1f,
                    cluster.maxY -
                        cluster.minY
                ) *
                    scaleBackY

            /*
             * 기존보다 지나치게 큰 원 방지
             */
            val calculatedRadius =
                max(
                    clusterWidth,
                    clusterHeight
                ) *
                    0.55f

            val minRadius =
                min(
                    safeWidth,
                    safeHeight
                ) *
                    0.055f

            val maxRadius =
                min(
                    safeWidth,
                    safeHeight
                ) *
                    0.14f

            val radius =
                calculatedRadius
                    .coerceIn(
                        minRadius,
                        maxRadius
                    )

            regions.add(
                WrinkleRegion(
                    centerX =
                        centerX,

                    centerY =
                        centerY,

                    radius =
                        radius,

                    score =
                        scored.score,

                    pointCount =
                        cluster.pointCount
                )
            )

            canvas.drawCircle(
                centerX,
                centerY,
                radius,
                circlePaint
            )

            val boxLeft =
                centerX -
                    radius

            val boxTop =
                centerY -
                    radius

            val boxRight =
                centerX +
                    radius

            val boxBottom =
                centerY +
                    radius

            canvas.drawRect(
                RectF(
                    boxLeft,
                    boxTop,
                    boxRight,
                    boxBottom
                ),
                boxPaint
            )

            val label =
                "주름 후보 ${index + 1}"

            val textWidth =
                textPaint.measureText(
                    label
                )

            val padding =
                10f

            val labelLeft =
                boxLeft.coerceAtLeast(
                    0f
                )

            val labelTop =
                (
                    boxTop -
                        textPaint.textSize -
                        padding * 2
                    )
                    .coerceAtLeast(
                        0f
                    )

            canvas.drawRect(
                RectF(
                    labelLeft,
                    labelTop,
                    labelLeft +
                        textWidth +
                        padding * 2,
                    labelTop +
                        textPaint.textSize +
                        padding * 2
                ),
                labelBackgroundPaint
            )

            canvas.drawText(
                label,
                labelLeft +
                    padding,
                labelTop +
                    textPaint.textSize +
                    padding * 0.5f,
                textPaint
            )
        }

        return Result(
            bitmap =
                markedBitmap,

            regions =
                regions,

            wrinkleScore =
                wrinkleScore,

            judgment =
                judgment,

            localContrast =
                averageContrast,

            lineDensity =
                lineDensity,

            concentration =
                concentration
        )
    }

    /*
     * =========================================================
     * 군집에 Point 추가
     * =========================================================
     */

    private fun addPointToCluster(
        cluster: Cluster,
        point: CandidatePoint
    ) {

        val oldCount =
            cluster.pointCount

        val newCount =
            oldCount + 1

        cluster.centerX =
            (
                cluster.centerX *
                    oldCount +
                    point.x
                ) /
                newCount

        cluster.centerY =
            (
                cluster.centerY *
                    oldCount +
                    point.y
                ) /
                newCount

        cluster.pointCount =
            newCount

        cluster.totalStrength +=
            point.strength

        cluster.minX =
            min(
                cluster.minX,
                point.x
            )

        cluster.maxX =
            max(
                cluster.maxX,
                point.x
            )

        cluster.minY =
            min(
                cluster.minY,
                point.y
            )

        cluster.maxY =
            max(
                cluster.maxY,
                point.y
            )

        cluster.sumX +=
            point.x

        cluster.sumY +=
            point.y

        cluster.sumXX +=
            point.x *
                point.x.toDouble()

        cluster.sumYY +=
            point.y *
                point.y.toDouble()
    }

    /*
     * =========================================================
     * 군집 병합
     * =========================================================
     */

    private fun mergeClusters(
        a: Cluster,
        b: Cluster
    ) {

        val totalCount =
            a.pointCount +
                b.pointCount

        if (
            totalCount <=
            0
        ) {

            return
        }

        a.centerX =
            (
                a.centerX *
                    a.pointCount +
                    b.centerX *
                    b.pointCount
                ) /
                totalCount

        a.centerY =
            (
                a.centerY *
                    a.pointCount +
                    b.centerY *
                    b.pointCount
                ) /
                totalCount

        a.pointCount =
            totalCount

        a.totalStrength +=
            b.totalStrength

        a.minX =
            min(
                a.minX,
                b.minX
            )

        a.maxX =
            max(
                a.maxX,
                b.maxX
            )

        a.minY =
            min(
                a.minY,
                b.minY
            )

        a.maxY =
            max(
                a.maxY,
                b.maxY
            )

        a.sumX +=
            b.sumX

        a.sumY +=
            b.sumY

        a.sumXX +=
            b.sumXX

        a.sumYY +=
            b.sumYY
    }

    /*
     * =========================================================
     * 결과 상세 Summary
     * =========================================================
     */

    fun buildSummary(
        result: Result
    ): String {

        val regionText =
            if (
                result.regions.isEmpty()
            ) {

                "주름 후보 영역 : 없음"

            } else {

                buildString {

                    append(
                        "주름 후보 영역 : ${result.regions.size}개"
                    )

                    result.regions
                        .forEachIndexed {
                                index,
                                region ->

                            append(
                                "\n후보 ${index + 1}" +
                                    " : 강도 " +
                                    "%.1f".format(
                                        region.score
                                    ) +
                                    " / 포인트 " +
                                    region.pointCount
                            )
                        }
                }
            }

        return """
Wrinkle Score : ${"%.1f".format(result.wrinkleScore)} / 100
판정 : ${result.judgment}

Line Density : ${"%.2f".format(result.lineDensity)}%
Local Contrast : ${"%.1f".format(result.localContrast)}
Concentration : ${"%.1f".format(result.concentration)}

$regionText
        """.trimIndent()
    }

    /*
     * =========================================================
     * RGB → Gray
     * =========================================================
     */

    private fun grayValue(
        color: Int
    ): Int {

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
            r * 0.299 +
                g * 0.587 +
                b * 0.114
            )
            .toInt()
    }
}
