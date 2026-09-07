package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
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
        val strength: Double,
        val directionScore: Double
    )

    private data class MutableCluster(
        var centerX: Float,
        var centerY: Float,
        var totalStrength: Double,
        var totalDirectionScore: Double,
        var pointCount: Int
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
            (
                roiLeft +
                    roiWidth
                )
                .coerceIn(
                    safeLeft + 1,
                    sourceBitmap.width
                )

        val safeBottom =
            (
                roiTop +
                    roiHeight
                )
                .coerceIn(
                    safeTop + 1,
                    sourceBitmap.height
                )

        val safeWidth =
            safeRight -
                safeLeft

        val safeHeight =
            safeBottom -
                safeTop

        val roiBitmap =
            Bitmap.createBitmap(
                sourceBitmap,
                safeLeft,
                safeTop,
                safeWidth,
                safeHeight
            )

        val analysisWidth =
            min(
                360,
                max(
                    120,
                    safeWidth
                )
            )

        val scale =
            analysisWidth
                .toFloat() /
                safeWidth.toFloat()

        val analysisHeight =
            max(
                80,
                (
                    safeHeight *
                        scale
                    )
                    .toInt()
            )

        val small =
            Bitmap.createScaledBitmap(
                roiBitmap,
                analysisWidth,
                analysisHeight,
                true
            )

        val gray =
            Array(
                analysisHeight
            ) {
                IntArray(
                    analysisWidth
                )
            }

        for (
            y in 0 until
                analysisHeight
        ) {

            for (
                x in 0 until
                    analysisWidth
            ) {

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
         * -----------------------------------------------------
         * 현재 확보된 실제 샘플 기준
         *
         * 정상:
         * - 넓고 완만한 음영 허용
         * - 긴 직선 외곽선 허용
         *
         * 주의 / 한계정상:
         * - Bottom Corner 근처
         * - 짧고 국부적인 굴곡
         * - 좁은 영역에서 급격한 명암 변화
         * - 여러 변화가 한 곳에 집중
         *
         * 불량:
         * - 현재 한계정상보다 확실히 높은 점수
         * - 향후 실제 불량 Sample 확보 시 재보정
         * -----------------------------------------------------
         */

        val sensitivityFactor =
            0.75 +
                sensitivity.coerceIn(
                    0,
                    100
                ) /
                200.0

        val edgeThreshold =
            (
                72 -
                    sensitivity *
                    0.35
                )
                .toInt()
                .coerceIn(
                    34,
                    72
                )

        val strongThreshold =
            (
                118 -
                    sensitivity *
                    0.45
                )
                .toInt()
                .coerceIn(
                    58,
                    118
                )

        /*
         * 코너 중심 가정:
         * ROI 중앙을 기준으로 사용합니다.
         *
         * 실제 촬영에서는 사용자가 ROI를
         * Bottom Corner에 맞추기 때문에
         * 화면 전체 좌표보다 ROI 내부 상대 위치가
         * 더 안정적입니다.
         */
        val cornerCenterX =
            analysisWidth /
                2f

        val cornerCenterY =
            analysisHeight /
                2f

        val maxDistance =
            sqrt(
                cornerCenterX *
                    cornerCenterX +
                    cornerCenterY *
                        cornerCenterY
            )

        val candidates =
            mutableListOf<CandidatePoint>()

        var totalEdgeCount =
            0

        var totalStrongCount =
            0

        var totalContrast =
            0.0

        var validPixelCount =
            0

        val marginX =
            max(
                4,
                analysisWidth /
                    40
            )

        val marginY =
            max(
                4,
                analysisHeight /
                    40
            )

        for (
            y in marginY until
                analysisHeight -
                    marginY
        ) {

            for (
                x in marginX until
                    analysisWidth -
                    marginX
            ) {

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

                val localHorizontal =
                    abs(
                        gray[y][x + 2] -
                            gray[y][x - 2]
                    )

                val localVertical =
                    abs(
                        gray[y + 2][x] -
                            gray[y - 2][x]
                    )

                /*
                 * 좁은 국부 변화에 더 높은 가중치
                 */
                val gradient =
                    gx +
                        gy

                val localGradient =
                    localHorizontal +
                        localVertical

                /*
                 * 너무 넓고 완만한 음영은
                 * gradient가 작기 때문에 자동 억제됩니다.
                 */
                if (
                    gradient <
                    edgeThreshold
                ) {

                    continue
                }

                totalEdgeCount++

                if (
                    gradient >=
                    strongThreshold
                ) {

                    totalStrongCount++
                }

                validPixelCount++

                /*
                 * 국부 대비
                 */
                val localContrast =
                    (
                        gradient *
                            0.65 +
                            localGradient *
                            0.35
                        )

                totalContrast +=
                    localContrast

                /*
                 * ROI 중심에서 너무 먼 곳은 감점
                 *
                 * 파우치의 긴 외곽선,
                 * 설비 구조물,
                 * 배경 Edge 오검출을 줄이기 위한 목적
                 */
                val dx =
                    x -
                        cornerCenterX

                val dy =
                    y -
                        cornerCenterY

                val distance =
                    sqrt(
                        dx *
                            dx +
                            dy *
                                dy
                    )

                val normalizedDistance =
                    (
                        distance /
                            maxDistance
                        )
                        .coerceIn(
                            0f,
                            1f
                        )

                val proximityScore =
                    (
                        1.0 -
                            normalizedDistance
                        )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                /*
                 * 주름 방향성
                 *
                 * 긴 수직/수평 Edge 하나보다는
                 * 국부적으로 비스듬하거나 방향 변화가 있는
                 * 선에 더 높은 점수를 줍니다.
                 */
                val edgeAngle =
                    atan2(
                        gy.toDouble(),
                        gx.toDouble()
                    )

                val diagonalScore =
                    (
                        abs(
                            kotlin.math.sin(
                                edgeAngle *
                                    2.0
                            )
                        )
                        )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                /*
                 * 너무 강하고 넓은 경계는
                 * 파우치 외곽선일 가능성이 있으므로
                 * 점수를 제한합니다.
                 */
                val boundaryPenalty =
                    if (
                        gradient >
                        220
                    ) {

                        0.60

                    } else {

                        1.0
                    }

                /*
                 * 밝기가 매우 높은 반사광은 일부 감점
                 */
                val brightness =
                    gray[y][x]

                val reflectionPenalty =
                    if (
                        brightness >
                        238
                    ) {

                        0.55

                    } else if (
                        brightness >
                        220
                    ) {

                        0.75

                    } else {

                        1.0
                    }

                val strength =
                    localContrast *
                        (
                            0.45 +
                                proximityScore *
                                0.55
                            ) *
                        (
                            0.55 +
                                diagonalScore *
                                0.45
                            ) *
                        boundaryPenalty *
                        reflectionPenalty *
                        sensitivityFactor

                if (
                    strength <
                    34.0
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
                            strength,
                        directionScore =
                            diagonalScore
                    )
                )
            }
        }

        /*
         * -----------------------------------------------------
         * 후보점 군집화
         * -----------------------------------------------------
         */

        val clusterDistance =
            max(
                14f,
                min(
                    analysisWidth,
                    analysisHeight
                ) *
                    0.075f
            )

        val clusters =
            mutableListOf<MutableCluster>()

        for (
            point in candidates
        ) {

            var bestCluster:
                MutableCluster? =
                null

            var bestDistance =
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
                        dx *
                            dx +
                            dy *
                                dy
                    )

                if (
                    distance <
                    clusterDistance &&
                    distance <
                    bestDistance
                ) {

                    bestDistance =
                        distance

                    bestCluster =
                        cluster
                }
            }

            if (
                bestCluster ==
                null
            ) {

                clusters.add(
                    MutableCluster(
                        centerX =
                            point.x,
                        centerY =
                            point.y,
                        totalStrength =
                            point.strength,
                        totalDirectionScore =
                            point.directionScore,
                        pointCount =
                            1
                    )
                )

            } else {

                val oldCount =
                    bestCluster.pointCount

                val newCount =
                    oldCount +
                        1

                bestCluster.centerX =
                    (
                        bestCluster.centerX *
                            oldCount +
                            point.x
                        ) /
                        newCount

                bestCluster.centerY =
                    (
                        bestCluster.centerY *
                            oldCount +
                            point.y
                        ) /
                        newCount

                bestCluster.totalStrength +=
                    point.strength

                bestCluster.totalDirectionScore +=
                    point.directionScore

                bestCluster.pointCount =
                    newCount
            }
        }

        /*
         * 너무 작은 군집 제거
         */
        val filteredClusters =
            clusters
                .filter {

                    it.pointCount >=
                        4
                }
                .map {

                    val averageStrength =
                        it.totalStrength /
                            it.pointCount

                    val averageDirection =
                        it.totalDirectionScore /
                            it.pointCount

                    val concentrationBonus =
                        min(
                            1.8,
                            1.0 +
                                it.pointCount /
                                22.0
                        )

                    val regionScore =
                        averageStrength *
                            (
                                0.65 +
                                    averageDirection *
                                    0.35
                                ) *
                            concentrationBonus

                    Pair(
                        it,
                        regionScore
                    )
                }
                .sortedByDescending {

                    it.second
                }
                .take(
                    maxRegions
                )

        /*
         * -----------------------------------------------------
         * 전체 Bottom Corner 점수 계산
         * -----------------------------------------------------
         */

        val roiPixelCount =
            max(
                1,
                (
                    analysisWidth -
                        marginX *
                            2
                    ) *
                    (
                        analysisHeight -
                            marginY *
                                2
                        )
            )

        val lineDensity =
            totalEdgeCount
                .toDouble() /
                roiPixelCount
                    .toDouble() *
                100.0

        val strongDensity =
            totalStrongCount
                .toDouble() /
                roiPixelCount
                    .toDouble() *
                100.0

        val averageContrast =
            if (
                validPixelCount >
                0
            ) {

                totalContrast /
                    validPixelCount
                        .toDouble()

            } else {

                0.0
            }

        val topRegionScore =
            filteredClusters
                .firstOrNull()
                ?.second
                ?: 0.0

        val secondRegionScore =
            filteredClusters
                .getOrNull(
                    1
                )
                ?.second
                ?: 0.0

        val regionCount =
            filteredClusters.size

        /*
         * 주름 집중도
         *
         * 한 군집 또는 두 군집에
         * 강한 변화가 집중되는 경우를 강조합니다.
         */
        val concentration =
            (
                topRegionScore *
                    0.70 +
                    secondRegionScore *
                    0.30
                )
                .coerceAtLeast(
                    0.0
                )

        /*
         * -----------------------------------------------------
         * Wrinkle Score
         *
         * 현재 정상 / 주의 / 한계정상 Sample을 기준으로
         * 1차 튜닝용 점수입니다.
         *
         * 향후 실제 불량 Sample 확보 시
         * 아래 threshold만 다시 보정할 수 있습니다.
         * -----------------------------------------------------
         */

        var rawScore =
            (
                lineDensity *
                    0.90 +
                    strongDensity *
                    1.80 +
                    averageContrast *
                    0.18 +
                    concentration *
                    0.22 +
                    regionCount *
                    2.5
                )

        /*
         * 넓은 음영만 있고
         * 실제 국부 군집이 거의 없으면 감점
         */
        if (
            regionCount ==
            0
        ) {

            rawScore *=
                0.45
        }

        /*
         * 후보 영역이 1개 이상이고
         * 국부 집중도가 강하면 가산
         */
        if (
            concentration >
            55.0
        ) {

            rawScore +=
                5.0
        }

        if (
            concentration >
            85.0
        ) {

            rawScore +=
                7.0
        }

        val wrinkleScore =
            rawScore
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * -----------------------------------------------------
         * 4단계 판정
         *
         * 현재 실제 Sample 기준의 초기값
         *
         * 정상        : 0 ~ 27
         * 주의        : 27 ~ 47
         * 한계정상    : 47 ~ 68
         * 불량 후보   : 68 이상
         *
         * 불량은 실제 불량 Sample 미확보 상태이므로
         * 임시 기준입니다.
         * -----------------------------------------------------
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
         * -----------------------------------------------------
         * 원본 복사 후 표시
         * -----------------------------------------------------
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
                            30f
                    )

                style =
                    Paint.Style.FILL
            }

        val scaleBackX =
            safeWidth
                .toFloat() /
                analysisWidth
                    .toFloat()

        val scaleBackY =
            safeHeight
                .toFloat() /
                analysisHeight
                    .toFloat()

        val regions =
            mutableListOf<WrinkleRegion>()

        filteredClusters.forEachIndexed {
                index,
                pair ->

            val cluster =
                pair.first

            val regionScore =
                pair.second

            val centerX =
                safeLeft +
                    cluster.centerX *
                    scaleBackX

            val centerY =
                safeTop +
                    cluster.centerY *
                    scaleBackY

            /*
             * 너무 큰 원이 되지 않도록 제한
             */
            val baseRadius =
                min(
                    safeWidth,
                    safeHeight
                ) *
                    (
                        0.08f +
                            min(
                                0.08f,
                                cluster.pointCount /
                                    180f
                            )
                        )

            val radius =
                baseRadius
                    .coerceIn(
                        min(
                            safeWidth,
                            safeHeight
                        ) *
                            0.055f,

                        min(
                            safeWidth,
                            safeHeight
                        ) *
                            0.16f
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
                        regionScore,
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
                boxLeft
                    .coerceAtLeast(
                        0f
                    )

            val labelTop =
                (
                    boxTop -
                        textPaint.textSize -
                        padding *
                            2
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
                        padding *
                            2,
                    labelTop +
                        textPaint.textSize +
                        padding *
                            2
                ),
                labelBackgroundPaint
            )

            canvas.drawText(
                label,
                labelLeft +
                    padding,
                labelTop +
                    textPaint.textSize +
                    padding *
                        0.5f,
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

                    result.regions.forEachIndexed {
                            index,
                            region ->

                        append(
                            "\n후보 ${index + 1} : " +
                                "강도 %.1f / 포인트 %d"
                                    .format(
                                        region.score,
                                        region.pointCount
                                    )
                        )
                    }
                }
            }

        return """
Wrinkle Score : ${"%.1f".format(result.wrinkleScore)} / 100
판정 : ${result.judgment}

Line Density : ${"%.1f".format(result.lineDensity)}%
Local Contrast : ${"%.1f".format(result.localContrast)}
Concentration : ${"%.1f".format(result.concentration)}

$regionText
        """.trimIndent()
    }

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
            r *
                0.299 +
                g *
                    0.587 +
                b *
                    0.114
            )
            .toInt()
    }
}
