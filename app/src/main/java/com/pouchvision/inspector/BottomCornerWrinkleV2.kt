package com.pouchvision.inspector

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * =============================================================
 * Bottom Corner Physical Wrinkle Detector V2
 * =============================================================
 *
 * 현장 Trial 버전
 *
 * 목적
 * 1. 기존 빨간 후보 영역 개수를 그대로 주름 개수로 사용하지 않음
 * 2. 서로 가까우며 같은 방향으로 이어지는 후보를 하나의
 *    Physical Wrinkle로 그룹화
 * 3. 실제 주름 개수 / 길이 / 강도 / 음영 Risk를 계산
 *
 * Master 판정
 *
 * 0개 -> 정상
 * 1개 -> 정상
 * 2개 -> 한계정상
 * 3개 이상 -> 불량
 *
 * 중요
 * 현재 3개 이상 실제 NG Master Sample이 없으므로
 * 3+ 규칙은 구현하지만 검출 정확도 검증은 추후 진행합니다.
 * =============================================================
 */

object BottomCornerWrinkleV2 {

    data class PhysicalWrinkle(
        val index: Int,

        /*
         * 원본 Bitmap 좌표
         */
        val centerX: Float,
        val centerY: Float,

        /*
         * ROI 기준 상대 길이
         *
         * 0~100 %
         *
         * 촬영거리 차이가 있으므로 현재는 mm가 아닌
         * ROI 대각선 대비 상대길이를 사용합니다.
         */
        val lengthPercent: Double,

        /*
         * 0~100
         */
        val strength: Double,

        /*
         * -90 ~ +90 degree
         */
        val angleDegree: Double,

        /*
         * 0~100
         */
        val shadowRisk: Double
    )

    data class Result(
        val wrinkleCount: Int,

        val wrinkles:
            List<PhysicalWrinkle>,

        val longestLengthPercent:
            Double,

        val totalLengthPercent:
            Double,

        val averageStrength:
            Double,

        val shadowRisk:
            Double,

        val riskScore:
            Double,

        val judgment:
            String,

        /*
         * Debug / 현장 보정용
         */
        val rawComponentCount:
            Int,

        val candidatePixelRatio:
            Double
    )

    private data class RawComponent(
        val pixels: Int,

        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,

        val centerX: Double,
        val centerY: Double,

        val length: Double,
        val angleRad: Double,

        val strength: Double,
        val shadowRisk: Double
    )

    private data class Cluster(
        val components:
            MutableList<RawComponent>
    )

    /*
     * =========================================================
     * Public Analyze
     * =========================================================
     */

    fun analyze(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        sensitivity: Int
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

        val safeWidth =
            roiWidth.coerceIn(
                1,
                sourceBitmap.width - safeLeft
            )

        val safeHeight =
            roiHeight.coerceIn(
                1,
                sourceBitmap.height - safeTop
            )

        /*
         * 계산량 제한.
         *
         * 휴대폰에서 실시간으로 사용하기 위해
         * ROI를 최대 약 240px 규모로 축소하여 분석합니다.
         */
        val maxSide =
            max(
                safeWidth,
                safeHeight
            )

        val scale =
            if (
                maxSide > 240
            ) {

                240.0 /
                    maxSide.toDouble()

            } else {

                1.0
            }

        val workWidth =
            max(
                32,
                (
                    safeWidth *
                        scale
                    ).toInt()
            )

        val workHeight =
            max(
                32,
                (
                    safeHeight *
                        scale
                    ).toInt()
            )

        val roiBitmap =
            Bitmap.createBitmap(
                sourceBitmap,
                safeLeft,
                safeTop,
                safeWidth,
                safeHeight
            )

        val workBitmap =
            if (
                roiBitmap.width ==
                    workWidth &&
                roiBitmap.height ==
                    workHeight
            ) {

                roiBitmap

            } else {

                Bitmap.createScaledBitmap(
                    roiBitmap,
                    workWidth,
                    workHeight,
                    true
                )
            }

        val gray =
            toGray(
                workBitmap
            )

        val smooth =
            blur3x3(
                gray,
                workWidth,
                workHeight
            )

        /*
         * 넓은 조명 음영과
         * 가는 실제 crease를 구분하기 위한
         * local background.
         */
        val background =
            boxBlur(
                smooth,
                workWidth,
                workHeight,
                radius = 5
            )

        val localContrast =
            DoubleArray(
                workWidth *
                    workHeight
            )

        for (
            i in localContrast.indices
        ) {

            localContrast[i] =
                abs(
                    smooth[i] -
                        background[i]
                )
        }

        val gradient =
            sobelMagnitude(
                smooth,
                workWidth,
                workHeight
            )

        /*
         * 민감도 60을 기준으로 설계.
         *
         * 민감도가 올라가면 threshold가 내려가
         * 더 약한 주름도 후보로 들어옵니다.
         */
        val sensitivitySafe =
            sensitivity
                .coerceIn(
                    0,
                    100
                )

        val gradientThreshold =
            (
                34.0 -
                    sensitivitySafe *
                    0.16
                )
                .coerceIn(
                    14.0,
                    34.0
                )

        val contrastThreshold =
            (
                15.0 -
                    sensitivitySafe *
                    0.07
                )
                .coerceIn(
                    7.0,
                    15.0
                )

        val binary =
            BooleanArray(
                workWidth *
                    workHeight
            )

        /*
         * ROI 경계 자체를 주름으로 오인하지 않도록
         * 가장자리 일부 제외.
         */
        val marginX =
            max(
                2,
                (
                    workWidth *
                        0.035
                    ).toInt()
            )

        val marginY =
            max(
                2,
                (
                    workHeight *
                        0.035
                    ).toInt()
            )

        var candidatePixels =
            0

        for (
            y in marginY until
                workHeight - marginY
        ) {

            for (
                x in marginX until
                    workWidth - marginX
            ) {

                val index =
                    y *
                        workWidth +
                        x

                val isCandidate =
                    gradient[index] >=
                        gradientThreshold &&
                        localContrast[index] >=
                        contrastThreshold

                if (
                    isCandidate
                ) {

                    binary[index] =
                        true

                    candidatePixels++
                }
            }
        }

        /*
         * 작은 점 노이즈 제거 + 끊어진 선 연결
         */
        val cleaned =
            morphology(
                binary,
                workWidth,
                workHeight
            )

        val components =
            findComponents(
                binary =
                    cleaned,

                gray =
                    smooth,

                localContrast =
                    localContrast,

                width =
                    workWidth,

                height =
                    workHeight
            )

        /*
         * 물리적으로 의미 있는 선형 component만 남김
         */
        val roiDiagonal =
            hypot(
                workWidth.toDouble(),
                workHeight.toDouble()
            )

        val minimumLength =
            roiDiagonal *
                0.035

        val minimumPixels =
            max(
                5,
                (
                    roiDiagonal *
                        0.025
                    ).toInt()
            )

        val lineComponents =
            components
                .filter {

                    val boxWidth =
                        (
                            it.maxX -
                                it.minX +
                                1
                            )
                            .toDouble()

                    val boxHeight =
                        (
                            it.maxY -
                                it.minY +
                                1
                            )
                            .toDouble()

                    val longSide =
                        max(
                            boxWidth,
                            boxHeight
                        )

                    val shortSide =
                        max(
                            1.0,
                            min(
                                boxWidth,
                                boxHeight
                            )
                        )

                    val elongation =
                        longSide /
                            shortSide

                    it.pixels >=
                        minimumPixels &&

                        it.length >=
                        minimumLength &&

                        /*
                         * 너무 둥근 반사점은 제외
                         */
                        elongation >=
                        1.35
                }

        /*
         * 같은 실제 주름에서 나온
         * 양쪽 edge 또는 끊어진 segment를 병합합니다.
         */
        val clusters =
            mergePhysicalWrinkles(
                lineComponents,
                roiDiagonal
            )

        val physicalWrinkles =
            clusters
                .mapNotNull { cluster ->

                    buildPhysicalWrinkle(
                        cluster =
                            cluster,

                        roiLeft =
                            safeLeft,

                        roiTop =
                            safeTop,

                        sourceRoiWidth =
                            safeWidth,

                        sourceRoiHeight =
                            safeHeight,

                        workWidth =
                            workWidth,

                        workHeight =
                            workHeight,

                        roiDiagonal =
                            roiDiagonal
                    )
                }
                /*
                 * 위험도가 높은 주름을 먼저
                 */
                .sortedByDescending {

                    it.lengthPercent *
                        0.55 +
                        it.strength *
                        0.30 +
                        it.shadowRisk *
                        0.15
                }
                /*
                 * 현재 현장 판정상 6개까지만 관리.
                 */
                .take(
                    6
                )
                .mapIndexed {
                        index,
                        wrinkle ->

                    wrinkle.copy(
                        index =
                            index + 1
                    )
                }

        val wrinkleCount =
            physicalWrinkles.size

        val longestLength =
            physicalWrinkles
                .maxOfOrNull {

                    it.lengthPercent
                }
                ?: 0.0

        val totalLength =
            physicalWrinkles
                .sumOf {

                    it.lengthPercent
                }
                .coerceAtMost(
                    100.0
                )

        val averageStrength =
            if (
                physicalWrinkles.isEmpty()
            ) {

                0.0

            } else {

                physicalWrinkles
                    .map {

                        it.strength
                    }
                    .average()
            }

        val averageShadow =
            if (
                physicalWrinkles.isEmpty()
            ) {

                /*
                 * 주름으로 확정되지 않아도
                 * 사람이 놓치는 넓은 음영 정보를
                 * 보조 Risk로 남기기 위해
                 * ROI local contrast를 사용합니다.
                 */
                calculateGlobalShadowRisk(
                    localContrast
                )

            } else {

                physicalWrinkles
                    .map {

                        it.shadowRisk
                    }
                    .average()
            }

        /*
         * Risk Score는 판정 자체를 바꾸지 않는
         * 보조지표입니다.
         */
        val countRisk =
            when {

                wrinkleCount >= 3 ->
                    100.0

                wrinkleCount == 2 ->
                    70.0

                wrinkleCount == 1 ->
                    35.0

                else ->
                    0.0
            }

        val lengthRisk =
            (
                longestLength *
                    2.2
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val riskScore =
            (
                countRisk *
                    0.55 +

                    lengthRisk *
                    0.20 +

                    averageStrength *
                    0.15 +

                    averageShadow *
                    0.10
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val judgment =
            judgmentFromCount(
                wrinkleCount
            )

        val candidateRatio =
            if (
                workWidth *
                    workHeight >
                0
            ) {

                candidatePixels
                    .toDouble() /
                    (
                        workWidth *
                            workHeight
                        )
                    .toDouble() *
                    100.0

            } else {

                0.0
            }

        if (
            workBitmap !==
            roiBitmap
        ) {

            workBitmap.recycle()
        }

        roiBitmap.recycle()

        return Result(

            wrinkleCount =
                wrinkleCount,

            wrinkles =
                physicalWrinkles,

            longestLengthPercent =
                longestLength,

            totalLengthPercent =
                totalLength,

            averageStrength =
                averageStrength,

            shadowRisk =
                averageShadow,

            riskScore =
                riskScore,

            judgment =
                judgment,

            rawComponentCount =
                lineComponents.size,

            candidatePixelRatio =
                candidateRatio
        )
    }

    /*
     * =========================================================
     * Master Judgment
     * =========================================================
     */

    fun judgmentFromCount(
        count: Int
    ): String {

        return when {

            count >= 3 ->
                "불량"

            count == 2 ->
                "한계정상"

            else ->
                "정상"
        }
    }

    /*
     * =========================================================
     * Gray
     * =========================================================
     */

    private fun toGray(
        bitmap: Bitmap
    ): DoubleArray {

        val width =
            bitmap.width

        val height =
            bitmap.height

        val pixels =
            IntArray(
                width *
                    height
            )

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val gray =
            DoubleArray(
                pixels.size
            )

        for (
            i in pixels.indices
        ) {

            val color =
                pixels[i]

            val r =
                (
                    color shr 16
                    ) and 0xFF

            val g =
                (
                    color shr 8
                    ) and 0xFF

            val b =
                color and 0xFF

            gray[i] =
                r *
                    0.299 +
                    g *
                    0.587 +
                    b *
                    0.114
        }

        return gray
    }

    /*
     * =========================================================
     * Small Blur
     * =========================================================
     */

    private fun blur3x3(
        source: DoubleArray,
        width: Int,
        height: Int
    ): DoubleArray {

        return boxBlur(
            source,
            width,
            height,
            radius = 1
        )
    }

    /*
     * =========================================================
     * Integral Box Blur
     * =========================================================
     */

    private fun boxBlur(
        source: DoubleArray,
        width: Int,
        height: Int,
        radius: Int
    ): DoubleArray {

        val result =
            DoubleArray(
                width *
                    height
            )

        val integralWidth =
            width + 1

        val integral =
            DoubleArray(
                (
                    width + 1
                    ) *
                    (
                        height + 1
                        )
            )

        for (
            y in 1..height
        ) {

            var rowSum =
                0.0

            for (
                x in 1..width
            ) {

                rowSum +=
                    source[
                        (
                            y - 1
                            ) *
                            width +
                            (
                                x - 1
                                )
                    ]

                integral[
                    y *
                        integralWidth +
                        x
                ] =
                    integral[
                        (
                            y - 1
                            ) *
                            integralWidth +
                            x
                    ] +
                        rowSum
            }
        }

        for (
            y in 0 until height
        ) {

            val y1 =
                max(
                    0,
                    y - radius
                )

            val y2 =
                min(
                    height - 1,
                    y + radius
                )

            for (
                x in 0 until width
            ) {

                val x1 =
                    max(
                        0,
                        x - radius
                    )

                val x2 =
                    min(
                        width - 1,
                        x + radius
                    )

                val ax =
                    x1

                val ay =
                    y1

                val bx =
                    x2 + 1

                val by =
                    y2 + 1

                val sum =
                    integral[
                        by *
                            integralWidth +
                            bx
                    ] -
                        integral[
                            ay *
                                integralWidth +
                                bx
                        ] -
                        integral[
                            by *
                                integralWidth +
                                ax
                        ] +
                        integral[
                            ay *
                                integralWidth +
                                ax
                        ]

                val count =
                    (
                        x2 -
                            x1 +
                            1
                        ) *
                        (
                            y2 -
                                y1 +
                                1
                            )

                result[
                    y *
                        width +
                        x
                ] =
                    sum /
                        count.toDouble()
            }
        }

        return result
    }

    /*
     * =========================================================
     * Sobel
     * =========================================================
     */

    private fun sobelMagnitude(
        gray: DoubleArray,
        width: Int,
        height: Int
    ): DoubleArray {

        val result =
            DoubleArray(
                width *
                    height
            )

        for (
            y in 1 until
                height - 1
        ) {

            for (
                x in 1 until
                    width - 1
            ) {

                val p00 =
                    gray[
                        (
                            y - 1
                            ) *
                            width +
                            (
                                x - 1
                                )
                    ]

                val p01 =
                    gray[
                        (
                            y - 1
                            ) *
                            width +
                            x
                    ]

                val p02 =
                    gray[
                        (
                            y - 1
                            ) *
                            width +
                            (
                                x + 1
                                )
                    ]

                val p10 =
                    gray[
                        y *
                            width +
                            (
                                x - 1
                                )
                    ]

                val p12 =
                    gray[
                        y *
                            width +
                            (
                                x + 1
                                )
                    ]

                val p20 =
                    gray[
                        (
                            y + 1
                            ) *
                            width +
                            (
                                x - 1
                                )
                    ]

                val p21 =
                    gray[
                        (
                            y + 1
                            ) *
                            width +
                            x
                    ]

                val p22 =
                    gray[
                        (
                            y + 1
                            ) *
                            width +
                            (
                                x + 1
                                )
                    ]

                val gx =
                    -p00 +
                        p02 -
                        2.0 *
                        p10 +
                        2.0 *
                        p12 -
                        p20 +
                        p22

                val gy =
                    -p00 -
                        2.0 *
                        p01 -
                        p02 +
                        p20 +
                        2.0 *
                        p21 +
                        p22

                result[
                    y *
                        width +
                        x
                ] =
                    sqrt(
                        gx *
                            gx +
                            gy *
                            gy
                    )
            }
        }

        return result
    }

    /*
     * =========================================================
     * Morphology
     * =========================================================
     */

    private fun morphology(
        input: BooleanArray,
        width: Int,
        height: Int
    ): BooleanArray {

        /*
         * 1차:
         * 주변 후보가 전혀 없는 isolated pixel 제거
         */
        val cleaned =
            BooleanArray(
                input.size
            )

        for (
            y in 1 until
                height - 1
        ) {

            for (
                x in 1 until
                    width - 1
            ) {

                val index =
                    y *
                        width +
                        x

                if (
                    !input[index]
                ) {
                    continue
                }

                var neighbors =
                    0

                for (
                    dy in -1..1
                ) {

                    for (
                        dx in -1..1
                    ) {

                        if (
                            dx == 0 &&
                            dy == 0
                        ) {
                            continue
                        }

                        if (
                            input[
                                (
                                    y + dy
                                    ) *
                                    width +
                                    (
                                        x + dx
                                        )
                            ]
                        ) {

                            neighbors++
                        }
                    }
                }

                if (
                    neighbors >= 1
                ) {

                    cleaned[index] =
                        true
                }
            }
        }

        /*
         * 2차:
         * 1 pixel 정도 끊어진 선 연결
         */
        val connected =
            cleaned.copyOf()

        for (
            y in 1 until
                height - 1
        ) {

            for (
                x in 1 until
                    width - 1
            ) {

                val index =
                    y *
                        width +
                        x

                if (
                    cleaned[index]
                ) {
                    continue
                }

                val horizontal =
                    cleaned[
                        y *
                            width +
                            (
                                x - 1
                                )
                    ] &&
                        cleaned[
                            y *
                                width +
                                (
                                    x + 1
                                    )
                        ]

                val vertical =
                    cleaned[
                        (
                            y - 1
                            ) *
                            width +
                            x
                    ] &&
                        cleaned[
                            (
                                y + 1
                                ) *
                                width +
                                x
                        ]

                val diagonal1 =
                    cleaned[
                        (
                            y - 1
                            ) *
                            width +
                            (
                                x - 1
                                )
                    ] &&
                        cleaned[
                            (
                                y + 1
                                ) *
                                width +
                                (
                                    x + 1
                                    )
                        ]

                val diagonal2 =
                    cleaned[
                        (
                            y - 1
                            ) *
                            width +
                            (
                                x + 1
                                )
                    ] &&
                        cleaned[
                            (
                                y + 1
                                ) *
                                width +
                                (
                                    x - 1
                                    )
                        ]

                if (
                    horizontal ||
                    vertical ||
                    diagonal1 ||
                    diagonal2
                ) {

                    connected[index] =
                        true
                }
            }
        }

        return connected
    }

    /*
     * =========================================================
     * Connected Components
     * =========================================================
     */

    private fun findComponents(
        binary: BooleanArray,
        gray: DoubleArray,
        localContrast: DoubleArray,
        width: Int,
        height: Int
    ): List<RawComponent> {

        val visited =
            BooleanArray(
                binary.size
            )

        val result =
            mutableListOf<
                RawComponent
            >()

        val queueX =
            IntArray(
                binary.size
            )

        val queueY =
            IntArray(
                binary.size
            )

        for (
            startY in 1 until
                height - 1
        ) {

            for (
                startX in 1 until
                    width - 1
            ) {

                val startIndex =
                    startY *
                        width +
                        startX

                if (
                    !binary[startIndex] ||
                    visited[startIndex]
                ) {
                    continue
                }

                var head =
                    0

                var tail =
                    0

                queueX[tail] =
                    startX

                queueY[tail] =
                    startY

                tail++

                visited[startIndex] =
                    true

                var pixelCount =
                    0

                var minX =
                    startX

                var maxX =
                    startX

                var minY =
                    startY

                var maxY =
                    startY

                var sumX =
                    0.0

                var sumY =
                    0.0

                var sumXX =
                    0.0

                var sumYY =
                    0.0

                var sumXY =
                    0.0

                var strengthSum =
                    0.0

                var contrastSum =
                    0.0

                while (
                    head <
                    tail
                ) {

                    val x =
                        queueX[
                            head
                        ]

                    val y =
                        queueY[
                            head
                        ]

                    head++

                    val index =
                        y *
                            width +
                            x

                    pixelCount++

                    minX =
                        min(
                            minX,
                            x
                        )

                    maxX =
                        max(
                            maxX,
                            x
                        )

                    minY =
                        min(
                            minY,
                            y
                        )

                    maxY =
                        max(
                            maxY,
                            y
                        )

                    sumX +=
                        x.toDouble()

                    sumY +=
                        y.toDouble()

                    sumXX +=
                        x.toDouble() *
                            x.toDouble()

                    sumYY +=
                        y.toDouble() *
                            y.toDouble()

                    sumXY +=
                        x.toDouble() *
                            y.toDouble()

                    contrastSum +=
                        localContrast[
                            index
                        ]

                    /*
                     * 주위와 명암 차이
                     */
                    strengthSum +=
                        abs(
                            gray[index] -
                                neighborhoodMean(
                                    gray =
                                        gray,

                                    width =
                                        width,

                                    height =
                                        height,

                                    x =
                                        x,

                                    y =
                                        y,

                                    radius =
                                        2
                                )
                        )

                    for (
                        dy in -1..1
                    ) {

                        for (
                            dx in -1..1
                        ) {

                            if (
                                dx == 0 &&
                                dy == 0
                            ) {
                                continue
                            }

                            val nx =
                                x +
                                    dx

                            val ny =
                                y +
                                    dy

                            if (
                                nx <= 0 ||
                                ny <= 0 ||
                                nx >=
                                width - 1 ||
                                ny >=
                                height - 1
                            ) {
                                continue
                            }

                            val ni =
                                ny *
                                    width +
                                    nx

                            if (
                                binary[ni] &&
                                !visited[ni]
                            ) {

                                visited[ni] =
                                    true

                                queueX[tail] =
                                    nx

                                queueY[tail] =
                                    ny

                                tail++
                            }
                        }
                    }
                }

                if (
                    pixelCount <= 0
                ) {
                    continue
                }

                val cx =
                    sumX /
                        pixelCount

                val cy =
                    sumY /
                        pixelCount

                val covarianceXX =
                    sumXX /
                        pixelCount -
                        cx *
                        cx

                val covarianceYY =
                    sumYY /
                        pixelCount -
                        cy *
                        cy

                val covarianceXY =
                    sumXY /
                        pixelCount -
                        cx *
                        cy

                /*
                 * PCA 주요 방향
                 */
                val angle =
                    0.5 *
                        atan2(
                            2.0 *
                                covarianceXY,

                            covarianceXX -
                                covarianceYY
                        )

                val boxLength =
                    hypot(
                        (
                            maxX -
                                minX
                            ).toDouble(),

                        (
                            maxY -
                                minY
                            ).toDouble()
                    )

                val strength =
                    (
                        strengthSum /
                            pixelCount *
                            5.0
                        )
                        .coerceIn(
                            0.0,
                            100.0
                        )

                val shadow =
                    (
                        contrastSum /
                            pixelCount *
                            4.0
                        )
                        .coerceIn(
                            0.0,
                            100.0
                        )

                result.add(
                    RawComponent(
                        pixels =
                            pixelCount,

                        minX =
                            minX,

                        minY =
                            minY,

                        maxX =
                            maxX,

                        maxY =
                            maxY,

                        centerX =
                            cx,

                        centerY =
                            cy,

                        length =
                            boxLength,

                        angleRad =
                            angle,

                        strength =
                            strength,

                        shadowRisk =
                            shadow
                    )
                )
            }
        }

        return result
    }

    /*
     * =========================================================
     * Physical Wrinkle Grouping
     * =========================================================
     */

    private fun mergePhysicalWrinkles(
        components: List<RawComponent>,
        roiDiagonal: Double
    ): List<Cluster> {

        if (
            components.isEmpty()
        ) {

            return emptyList()
        }

        val sorted =
            components
                .sortedByDescending {

                    it.length
                }

        val clusters =
            mutableListOf<
                Cluster
            >()

        for (
            component in sorted
        ) {

            var bestCluster:
                Cluster? =
                null

            var bestScore =
                Double.MAX_VALUE

            for (
                cluster in clusters
            ) {

                val representative =
                    cluster
                        .components
                        .maxByOrNull {

                            it.length
                        }
                        ?: continue

                val angleDifference =
                    angleDifferenceDegrees(
                        representative.angleRad,
                        component.angleRad
                    )

                if (
                    angleDifference >
                    24.0
                ) {
                    continue
                }

                val centerDistance =
                    hypot(
                        representative.centerX -
                            component.centerX,

                        representative.centerY -
                            component.centerY
                    )

                /*
                 * 같은 wrinkle의 양쪽 edge 또는
                 * 끊어진 segment가 있을 수 있으므로
                 * ROI 대각선 약 12% 이내까지 병합 허용.
                 */
                val maxDistance =
                    roiDiagonal *
                        0.12 +
                        max(
                            representative.length,
                            component.length
                        ) *
                        0.55

                if (
                    centerDistance >
                    maxDistance
                ) {
                    continue
                }

                /*
                 * 대표 주름 축에서 너무 멀리 떨어진
                 * 평행선은 다른 주름일 가능성이 큼.
                 */
                val perpendicularDistance =
                    perpendicularDistance(
                        x =
                            component.centerX,

                        y =
                            component.centerY,

                        lineX =
                            representative.centerX,

                        lineY =
                            representative.centerY,

                        angle =
                            representative.angleRad
                    )

                val maxPerpendicular =
                    roiDiagonal *
                        0.055

                if (
                    perpendicularDistance >
                    maxPerpendicular
                ) {
                    continue
                }

                val score =
                    centerDistance +
                        perpendicularDistance *
                        2.0 +
                        angleDifference

                if (
                    score <
                    bestScore
                ) {

                    bestScore =
                        score

                    bestCluster =
                        cluster
                }
            }

            if (
                bestCluster != null
            ) {

                bestCluster
                    .components
                    .add(
                        component
                    )

            } else {

                clusters.add(
                    Cluster(
                        mutableListOf(
                            component
                        )
                    )
                )
            }
        }

        return clusters
    }

    /*
     * =========================================================
     * Cluster -> Physical Wrinkle
     * =========================================================
     */

    private fun buildPhysicalWrinkle(
        cluster: Cluster,
        roiLeft: Int,
        roiTop: Int,
        sourceRoiWidth: Int,
        sourceRoiHeight: Int,
        workWidth: Int,
        workHeight: Int,
        roiDiagonal: Double
    ): PhysicalWrinkle? {

        if (
            cluster.components.isEmpty()
        ) {
            return null
        }

        val totalWeight =
            cluster
                .components
                .sumOf {

                    max(
                        1,
                        it.pixels
                    )
                }
                .toDouble()

        val centerX =
            cluster
                .components
                .sumOf {

                    it.centerX *
                        max(
                            1,
                            it.pixels
                        )
                } /
                totalWeight

        val centerY =
            cluster
                .components
                .sumOf {

                    it.centerY *
                        max(
                            1,
                            it.pixels
                        )
                } /
                totalWeight

        /*
         * 가장 긴 component의 방향 사용
         */
        val main =
            cluster
                .components
                .maxByOrNull {

                    it.length
                }
                ?: return null

        val angle =
            main.angleRad

        val ux =
            cos(
                angle
            )

        val uy =
            sin(
                angle
            )

        var minProjection =
            Double.MAX_VALUE

        var maxProjection =
            -Double.MAX_VALUE

        for (
            component in
            cluster.components
        ) {

            val points =
                listOf(
                    component.minX.toDouble() to
                        component.minY.toDouble(),

                    component.minX.toDouble() to
                        component.maxY.toDouble(),

                    component.maxX.toDouble() to
                        component.minY.toDouble(),

                    component.maxX.toDouble() to
                        component.maxY.toDouble()
                )

            for (
                point in points
            ) {

                val projection =
                    point.first *
                        ux +
                        point.second *
                        uy

                minProjection =
                    min(
                        minProjection,
                        projection
                    )

                maxProjection =
                    max(
                        maxProjection,
                        projection
                    )
            }
        }

        val physicalLength =
            (
                maxProjection -
                    minProjection
                )
                .coerceAtLeast(
                    0.0
                )

        val lengthPercent =
            (
                physicalLength /
                    roiDiagonal *
                    100.0
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        /*
         * 너무 짧은 cluster는 최종 Physical Wrinkle에서 제외
         */
        if (
            lengthPercent <
            3.5
        ) {

            return null
        }

        val strength =
            cluster
                .components
                .map {

                    it.strength
                }
                .average()
                .coerceIn(
                    0.0,
                    100.0
                )

        val shadowRisk =
            cluster
                .components
                .map {

                    it.shadowRisk
                }
                .average()
                .coerceIn(
                    0.0,
                    100.0
                )

        val sourceX =
            roiLeft +
                (
                    centerX /
                        workWidth.toDouble() *
                        sourceRoiWidth
                    )
                    .toFloat()

        val sourceY =
            roiTop +
                (
                    centerY /
                        workHeight.toDouble() *
                        sourceRoiHeight
                    )
                    .toFloat()

        var angleDegree =
            Math.toDegrees(
                angle
            )

        /*
         * 보기 편하게 -90 ~ +90
         */
        while (
            angleDegree >
            90.0
        ) {

            angleDegree -=
                180.0
        }

        while (
            angleDegree <
            -90.0
        ) {

            angleDegree +=
                180.0
        }

        return PhysicalWrinkle(
            index =
                0,

            centerX =
                sourceX,

            centerY =
                sourceY,

            lengthPercent =
                lengthPercent,

            strength =
                strength,

            angleDegree =
                angleDegree,

            shadowRisk =
                shadowRisk
        )
    }

    /*
     * =========================================================
     * Helpers
     * =========================================================
     */

    private fun angleDifferenceDegrees(
        angle1: Double,
        angle2: Double
    ): Double {

        var diff =
            abs(
                Math.toDegrees(
                    angle1 -
                        angle2
                )
            )

        while (
            diff >
            180.0
        ) {

            diff -=
                180.0
        }

        if (
            diff >
            90.0
        ) {

            diff =
                180.0 -
                    diff
        }

        return abs(
            diff
        )
    }

    private fun perpendicularDistance(
        x: Double,
        y: Double,
        lineX: Double,
        lineY: Double,
        angle: Double
    ): Double {

        val dx =
            x -
                lineX

        val dy =
            y -
                lineY

        return abs(
            -sin(
                angle
            ) *
                dx +
                cos(
                    angle
                ) *
                dy
        )
    }

    private fun neighborhoodMean(
        gray: DoubleArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        radius: Int
    ): Double {

        var sum =
            0.0

        var count =
            0

        val startY =
            max(
                0,
                y - radius
            )

        val endY =
            min(
                height - 1,
                y + radius
            )

        val startX =
            max(
                0,
                x - radius
            )

        val endX =
            min(
                width - 1,
                x + radius
            )

        for (
            yy in startY..endY
        ) {

            for (
                xx in startX..endX
            ) {

                sum +=
                    gray[
                        yy *
                            width +
                            xx
                    ]

                count++
            }
        }

        return if (
            count >
            0
        ) {

            sum /
                count.toDouble()

        } else {

            0.0
        }
    }

    private fun calculateGlobalShadowRisk(
        localContrast: DoubleArray
    ): Double {

        if (
            localContrast.isEmpty()
        ) {

            return 0.0
        }

        val sorted =
            localContrast
                .sorted()

        /*
         * 상위 10% 음영 변화 수준
         */
        val startIndex =
            (
                sorted.size *
                    0.90
                )
                .toInt()
                .coerceIn(
                    0,
                    sorted.lastIndex
                )

        val highValues =
            sorted.subList(
                startIndex,
                sorted.size
            )

        val average =
            if (
                highValues.isEmpty()
            ) {

                0.0

            } else {

                highValues.average()
            }

        return (
            average *
                2.8
            )
            .coerceIn(
                0.0,
                100.0
            )
    }
}
