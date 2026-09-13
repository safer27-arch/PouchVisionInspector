package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SEAL V2 field-trial analysis engine.
 *
 * 목적
 * 1) 실링툴 압착 위치의 실제 주름(Seal Wrinkle)을 별도 검출
 * 2) 단순 Edge 개수가 아니라 인접/유사 방향 성분을 하나의 물리 주름으로 그룹화
 * 3) 실링 라인 균일도 / 폭 변화 / 국부 끊김 / PP Flow / 음영 변화 / Cup 침범 위험을
 *    보조 지표로 계산
 *
 * 주의
 * - 현재 길이/폭은 mm가 아니라 ROI 대비 상대값(%)입니다.
 * - 현장 사진을 이용해 보정하기 위한 V2 1차 버전입니다.
 * - 최종 SEAL 불량 기준은 현장 Ground Truth를 쌓은 뒤 보정해야 합니다.
 */
object SealInspectionV2 {

    data class SealWrinkle(
        val index: Int,
        val centerX: Float,
        val centerY: Float,
        val lengthPercent: Double,
        val strength: Double,
        val angleDegree: Double,
        val sealCrossingRisk: Double,
        val shadowRisk: Double
    )

    data class Result(
        val wrinkleCount: Int,
        val wrinkles: List<SealWrinkle>,
        val longestWrinklePercent: Double,
        val averageWrinkleStrength: Double,
        val sealLineUniformity: Double,
        val widthVariationPercent: Double,
        val localDiscontinuityRisk: Double,
        val ppFlowRisk: Double,
        val transparencyShadowRisk: Double,
        val cupIntrusionRisk: Double,
        val overallRisk: Double,
        val qualityScore: Double,
        val suggestedJudgment: String,
        val rawComponentCount: Int
    )

    private data class Component(
        val pixels: Int,
        val cx: Double,
        val cy: Double,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val length: Double,
        val width: Double,
        val angle: Double,
        val strength: Double,
        val shadow: Double
    )

    private data class Cluster(
        val members: MutableList<Component> = mutableListOf()
    )

    fun analyze(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        sensitivity: Int
    ): Result {
        if (sourceBitmap.width <= 2 || sourceBitmap.height <= 2) {
            return emptyResult()
        }

        val safeLeft = roiLeft.coerceIn(0, sourceBitmap.width - 1)
        val safeTop = roiTop.coerceIn(0, sourceBitmap.height - 1)
        val safeWidth = roiWidth
            .coerceAtLeast(2)
            .coerceAtMost(sourceBitmap.width - safeLeft)
        val safeHeight = roiHeight
            .coerceAtLeast(2)
            .coerceAtMost(sourceBitmap.height - safeTop)

        if (safeWidth < 8 || safeHeight < 8) {
            return emptyResult()
        }

        val scale = min(
            1.0,
            280.0 / max(safeWidth, safeHeight).toDouble()
        )

        val workWidth = max(24, (safeWidth * scale).roundToInt())
        val workHeight = max(24, (safeHeight * scale).roundToInt())

        val cropped = Bitmap.createBitmap(
            sourceBitmap,
            safeLeft,
            safeTop,
            safeWidth,
            safeHeight
        )

        val work = if (
            cropped.width == workWidth &&
            cropped.height == workHeight
        ) {
            cropped
        } else {
            Bitmap.createScaledBitmap(
                cropped,
                workWidth,
                workHeight,
                true
            )
        }

        val gray = toGray(work)
        val blurred = boxBlur(gray, workWidth, workHeight, 1)
        val localMean = boxBlur(blurred, workWidth, workHeight, 5)

        val sobel = DoubleArray(workWidth * workHeight)
        var sobelSum = 0.0
        var sobelCount = 0

        for (y in 1 until workHeight - 1) {
            for (x in 1 until workWidth - 1) {
                val i = y * workWidth + x

                val g00 = blurred[(y - 1) * workWidth + (x - 1)]
                val g01 = blurred[(y - 1) * workWidth + x]
                val g02 = blurred[(y - 1) * workWidth + (x + 1)]
                val g10 = blurred[y * workWidth + (x - 1)]
                val g12 = blurred[y * workWidth + (x + 1)]
                val g20 = blurred[(y + 1) * workWidth + (x - 1)]
                val g21 = blurred[(y + 1) * workWidth + x]
                val g22 = blurred[(y + 1) * workWidth + (x + 1)]

                val gx =
                    -g00 - 2.0 * g10 - g20 +
                        g02 + 2.0 * g12 + g22

                val gy =
                    -g00 - 2.0 * g01 - g02 +
                        g20 + 2.0 * g21 + g22

                val mag = sqrt(gx * gx + gy * gy)
                sobel[i] = mag
                sobelSum += mag
                sobelCount++
            }
        }

        val meanSobel =
            if (sobelCount > 0) sobelSum / sobelCount else 0.0

        val sens = sensitivity.coerceIn(0, 100)
        val edgeFactor = 1.95 - sens * 0.009
        val contrastThreshold = 12.0 - sens * 0.055

        val edgeThreshold = max(18.0, meanSobel * edgeFactor)

        val candidate = BooleanArray(workWidth * workHeight)

        val marginX = max(2, (workWidth * 0.035).roundToInt())
        val marginY = max(2, (workHeight * 0.035).roundToInt())

        for (y in marginY until workHeight - marginY) {
            for (x in marginX until workWidth - marginX) {
                val i = y * workWidth + x
                val localContrast = abs(blurred[i] - localMean[i])

                if (
                    sobel[i] >= edgeThreshold &&
                    localContrast >= contrastThreshold
                ) {
                    candidate[i] = true
                }
            }
        }

        val cleaned = removeIsolatedPixels(
            bridgeSmallGaps(
                candidate,
                workWidth,
                workHeight
            ),
            workWidth,
            workHeight
        )

        val components = findComponents(
            cleaned,
            workWidth,
            workHeight,
            sobel,
            blurred,
            localMean
        ).filter { c ->
            val roiDiag = sqrt(
                workWidth.toDouble() * workWidth +
                    workHeight.toDouble() * workHeight
            )

            val minPixels = max(
                5,
                (roiDiag * (0.010 + (100 - sens) * 0.00002)).roundToInt()
            )

            val minLength = roiDiag * 0.032
            val elongation = c.length / max(1.0, c.width)

            c.pixels >= minPixels &&
                c.length >= minLength &&
                elongation >= 1.7
        }

        val dominantSealAngle =
            estimateDominantSealAngle(
                components,
                workWidth,
                workHeight
            )

        val grouped = groupWrinkles(
            components,
            workWidth,
            workHeight
        )

        val diag = sqrt(
            workWidth.toDouble() * workWidth +
                workHeight.toDouble() * workHeight
        )

        val wrinkles =
            grouped.mapIndexedNotNull { index, cluster ->

                val merged = mergeCluster(cluster)
                    ?: return@mapIndexedNotNull null

                val angleDiff =
                    angleDifference(
                        merged.angle,
                        dominantSealAngle
                    )

                // 실링 라인에 수직/사선으로 가로지르는 주름은 Risk 증가.
                val crossingRisk =
                    when {
                        angleDiff >= 65.0 -> 100.0
                        angleDiff >= 45.0 -> 82.0
                        angleDiff >= 25.0 -> 58.0
                        else -> 30.0
                    }

                val lengthPercent =
                    (merged.length / diag * 100.0)
                        .coerceIn(0.0, 100.0)

                SealWrinkle(
                    index = index + 1,
                    centerX = (
                        safeLeft +
                            merged.cx / workWidth * safeWidth
                        ).toFloat(),
                    centerY = (
                        safeTop +
                            merged.cy / workHeight * safeHeight
                        ).toFloat(),
                    lengthPercent = lengthPercent,
                    strength = merged.strength.coerceIn(0.0, 100.0),
                    angleDegree = normalizeAngle(merged.angle),
                    sealCrossingRisk = crossingRisk,
                    shadowRisk = merged.shadow.coerceIn(0.0, 100.0)
                )
            }.sortedByDescending {
                it.lengthPercent *
                    (0.65 + it.sealCrossingRisk / 250.0)
            }

        val sealLineUniformity =
            calculateSealLineUniformity(
                blurred,
                workWidth,
                workHeight,
                dominantSealAngle
            )

        val widthVariation =
            calculateWidthVariation(
                blurred,
                localMean,
                workWidth,
                workHeight,
                dominantSealAngle
            )

        val localDiscontinuityRisk =
            calculateLocalDiscontinuity(
                sobel,
                workWidth,
                workHeight,
                dominantSealAngle
            )

        val ppFlowRisk =
            calculatePpFlowRisk(
                blurred,
                localMean,
                workWidth,
                workHeight,
                dominantSealAngle
            )

        val transparencyShadowRisk =
            calculateShadowVariation(
                blurred,
                localMean
            )

        val cupIntrusionRisk =
            calculateCupIntrusionRisk(
                sobel,
                workWidth,
                workHeight,
                dominantSealAngle
            )

        val longestWrinkle =
            wrinkles.maxOfOrNull { it.lengthPercent } ?: 0.0

        val avgWrinkleStrength =
            if (wrinkles.isNotEmpty()) {
                wrinkles.map { it.strength }.average()
            } else {
                0.0
            }

        val wrinkleRisk =
            calculateWrinkleRisk(wrinkles)

        val overallRisk =
            (
                wrinkleRisk * 0.32 +
                    (100.0 - sealLineUniformity) * 0.18 +
                    widthVariation.coerceAtMost(100.0) * 0.12 +
                    localDiscontinuityRisk * 0.14 +
                    ppFlowRisk * 0.10 +
                    transparencyShadowRisk * 0.07 +
                    cupIntrusionRisk * 0.07
                ).coerceIn(0.0, 100.0)

        val qualityScore =
            (100.0 - overallRisk).coerceIn(0.0, 100.0)

        val suggestedJudgment =
            when {
                // Field-trial provisional rule.
                // 실링툴 위치 주름을 우선 반영하며 현장 Ground Truth로 추후 보정.
                wrinkles.size >= 3 -> "불량"
                wrinkles.size == 2 -> "한계정상"
                wrinkles.size == 1 &&
                    (
                        longestWrinkle >= 22.0 ||
                            wrinkles.first().sealCrossingRisk >= 80.0 ||
                            avgWrinkleStrength >= 78.0
                        ) -> "주의"
                overallRisk >= 72.0 -> "불량"
                overallRisk >= 55.0 -> "한계정상"
                overallRisk >= 38.0 -> "주의"
                else -> "정상"
            }

        if (work !== cropped && !work.isRecycled) {
            work.recycle()
        }
        if (!cropped.isRecycled) {
            cropped.recycle()
        }

        return Result(
            wrinkleCount = wrinkles.size,
            wrinkles = wrinkles,
            longestWrinklePercent = longestWrinkle,
            averageWrinkleStrength = avgWrinkleStrength,
            sealLineUniformity = sealLineUniformity,
            widthVariationPercent = widthVariation,
            localDiscontinuityRisk = localDiscontinuityRisk,
            ppFlowRisk = ppFlowRisk,
            transparencyShadowRisk = transparencyShadowRisk,
            cupIntrusionRisk = cupIntrusionRisk,
            overallRisk = overallRisk,
            qualityScore = qualityScore,
            suggestedJudgment = suggestedJudgment,
            rawComponentCount = components.size
        )
    }

    private fun toGray(bitmap: Bitmap): DoubleArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        return DoubleArray(pixels.size) { i ->
            val c = pixels[i]
            (
                0.299 * Color.red(c) +
                    0.587 * Color.green(c) +
                    0.114 * Color.blue(c)
                )
        }
    }

    private fun boxBlur(
        src: DoubleArray,
        w: Int,
        h: Int,
        radius: Int
    ): DoubleArray {
        if (radius <= 0) return src.copyOf()

        val integral = DoubleArray((w + 1) * (h + 1))

        for (y in 0 until h) {
            var row = 0.0
            for (x in 0 until w) {
                row += src[y * w + x]
                integral[(y + 1) * (w + 1) + (x + 1)] =
                    integral[y * (w + 1) + (x + 1)] + row
            }
        }

        val out = DoubleArray(w * h)

        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h - 1, y + radius)

            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)

                val a = integral[y0 * (w + 1) + x0]
                val b = integral[y0 * (w + 1) + (x1 + 1)]
                val c = integral[(y1 + 1) * (w + 1) + x0]
                val d = integral[(y1 + 1) * (w + 1) + (x1 + 1)]

                val count =
                    (x1 - x0 + 1) *
                        (y1 - y0 + 1)

                out[y * w + x] =
                    (d - b - c + a) /
                        count.toDouble()
            }
        }

        return out
    }

    private fun bridgeSmallGaps(
        src: BooleanArray,
        w: Int,
        h: Int
    ): BooleanArray {
        val out = src.copyOf()

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (src[i]) continue

                val left = src[i - 1]
                val right = src[i + 1]
                val up = src[i - w]
                val down = src[i + w]

                val d1 = src[i - w - 1] && src[i + w + 1]
                val d2 = src[i - w + 1] && src[i + w - 1]

                if (
                    (left && right) ||
                    (up && down) ||
                    d1 ||
                    d2
                ) {
                    out[i] = true
                }
            }
        }

        return out
    }

    private fun removeIsolatedPixels(
        src: BooleanArray,
        w: Int,
        h: Int
    ): BooleanArray {
        val out = src.copyOf()

        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (!src[i]) continue

                var neighbors = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        if (src[(y + dy) * w + (x + dx)]) {
                            neighbors++
                        }
                    }
                }

                if (neighbors <= 1) {
                    out[i] = false
                }
            }
        }

        return out
    }

    private fun findComponents(
        mask: BooleanArray,
        w: Int,
        h: Int,
        sobel: DoubleArray,
        gray: DoubleArray,
        localMean: DoubleArray
    ): List<Component> {
        val visited = BooleanArray(mask.size)
        val qx = IntArray(mask.size)
        val qy = IntArray(mask.size)
        val components = mutableListOf<Component>()

        for (sy in 0 until h) {
            for (sx in 0 until w) {
                val start = sy * w + sx

                if (!mask[start] || visited[start]) {
                    continue
                }

                var head = 0
                var tail = 0

                qx[tail] = sx
                qy[tail] = sy
                tail++
                visited[start] = true

                val xs = mutableListOf<Double>()
                val ys = mutableListOf<Double>()

                var minX = sx
                var maxX = sx
                var minY = sy
                var maxY = sy
                var strengthSum = 0.0
                var shadowSum = 0.0

                while (head < tail) {
                    val x = qx[head]
                    val y = qy[head]
                    head++

                    xs.add(x.toDouble())
                    ys.add(y.toDouble())

                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)

                    val i = y * w + x
                    strengthSum += sobel[i]
                    shadowSum += abs(gray[i] - localMean[i])

                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue

                            val nx = x + dx
                            val ny = y + dy

                            if (
                                nx !in 0 until w ||
                                ny !in 0 until h
                            ) {
                                continue
                            }

                            val ni = ny * w + nx
                            if (mask[ni] && !visited[ni]) {
                                visited[ni] = true
                                qx[tail] = nx
                                qy[tail] = ny
                                tail++
                            }
                        }
                    }
                }

                val n = xs.size
                if (n < 2) continue

                val cx = xs.average()
                val cy = ys.average()

                var sxx = 0.0
                var syy = 0.0
                var sxy = 0.0

                for (i in xs.indices) {
                    val dx = xs[i] - cx
                    val dy = ys[i] - cy

                    sxx += dx * dx
                    syy += dy * dy
                    sxy += dx * dy
                }

                sxx /= n
                syy /= n
                sxy /= n

                val theta =
                    0.5 * atan2(
                        2.0 * sxy,
                        sxx - syy
                    )

                val ct = cos(theta)
                val st = sin(theta)

                var minMajor = Double.POSITIVE_INFINITY
                var maxMajor = Double.NEGATIVE_INFINITY
                var minMinor = Double.POSITIVE_INFINITY
                var maxMinor = Double.NEGATIVE_INFINITY

                for (i in xs.indices) {
                    val dx = xs[i] - cx
                    val dy = ys[i] - cy

                    val major = dx * ct + dy * st
                    val minor = -dx * st + dy * ct

                    minMajor = min(minMajor, major)
                    maxMajor = max(maxMajor, major)
                    minMinor = min(minMinor, minor)
                    maxMinor = max(maxMinor, minor)
                }

                val majorLength =
                    max(1.0, maxMajor - minMajor)

                val minorWidth =
                    max(1.0, maxMinor - minMinor)

                val angleDeg =
                    normalizeAngle(
                        Math.toDegrees(theta)
                    )

                val strength =
                    (strengthSum / n / 4.5)
                        .coerceIn(0.0, 100.0)

                val shadow =
                    (shadowSum / n * 4.0)
                        .coerceIn(0.0, 100.0)

                components.add(
                    Component(
                        pixels = n,
                        cx = cx,
                        cy = cy,
                        minX = minX,
                        minY = minY,
                        maxX = maxX,
                        maxY = maxY,
                        length = majorLength,
                        width = minorWidth,
                        angle = angleDeg,
                        strength = strength,
                        shadow = shadow
                    )
                )
            }
        }

        return components
    }

    private fun estimateDominantSealAngle(
        components: List<Component>,
        w: Int,
        h: Int
    ): Double {
        if (components.isEmpty()) {
            return if (w >= h) 0.0 else 90.0
        }

        // 실링툴 압착 라인은 일반적으로 ROI의 긴 방향과 가깝다고 보고
        // 긴 성분에 가중치를 주어 대표 방향을 구합니다.
        var sx = 0.0
        var sy = 0.0
        var weightSum = 0.0

        for (c in components.sortedByDescending { it.length }.take(12)) {
            val rad = Math.toRadians(c.angle * 2.0)
            val weight = max(1.0, c.length)
            sx += cos(rad) * weight
            sy += sin(rad) * weight
            weightSum += weight
        }

        if (weightSum <= 0.0) {
            return if (w >= h) 0.0 else 90.0
        }

        val angle =
            Math.toDegrees(
                0.5 * atan2(sy, sx)
            )

        return normalizeAngle(angle)
    }

    private fun groupWrinkles(
        components: List<Component>,
        w: Int,
        h: Int
    ): List<Cluster> {
        val diag = sqrt(
            w.toDouble() * w +
                h.toDouble() * h
        )

        val sorted =
            components.sortedByDescending { it.length }

        val clusters = mutableListOf<Cluster>()

        for (component in sorted) {
            var bestCluster: Cluster? = null
            var bestScore = Double.POSITIVE_INFINITY

            for (cluster in clusters) {
                val merged = mergeCluster(cluster)
                    ?: continue

                val angleDiff =
                    angleDifference(
                        component.angle,
                        merged.angle
                    )

                if (angleDiff > 24.0) continue

                val dx = component.cx - merged.cx
                val dy = component.cy - merged.cy
                val distance = sqrt(dx * dx + dy * dy)

                val distanceLimit =
                    max(
                        diag * 0.075,
                        (component.length + merged.length) * 0.55
                    )

                if (distance > distanceLimit) continue

                val theta =
                    Math.toRadians(merged.angle)

                val perp =
                    abs(
                        -sin(theta) * dx +
                            cos(theta) * dy
                    )

                if (perp > diag * 0.055) continue

                val score =
                    distance +
                        angleDiff * 1.5 +
                        perp * 2.0

                if (score < bestScore) {
                    bestScore = score
                    bestCluster = cluster
                }
            }

            if (bestCluster != null) {
                bestCluster.members.add(component)
            } else {
                clusters.add(
                    Cluster(
                        mutableListOf(component)
                    )
                )
            }
        }

        return clusters
    }

    private fun mergeCluster(
        cluster: Cluster
    ): Component? {
        val m = cluster.members
        if (m.isEmpty()) return null
        if (m.size == 1) return m.first()

        val totalWeight =
            m.sumOf {
                max(1.0, it.length)
            }

        val cx =
            m.sumOf {
                it.cx * max(1.0, it.length)
            } / totalWeight

        val cy =
            m.sumOf {
                it.cy * max(1.0, it.length)
            } / totalWeight

        var vx = 0.0
        var vy = 0.0

        for (c in m) {
            val rad =
                Math.toRadians(c.angle * 2.0)
            val weight = max(1.0, c.length)

            vx += cos(rad) * weight
            vy += sin(rad) * weight
        }

        val angle =
            normalizeAngle(
                Math.toDegrees(
                    0.5 * atan2(vy, vx)
                )
            )

        val theta =
            Math.toRadians(angle)

        var minMajor = Double.POSITIVE_INFINITY
        var maxMajor = Double.NEGATIVE_INFINITY
        var minMinor = Double.POSITIVE_INFINITY
        var maxMinor = Double.NEGATIVE_INFINITY

        for (c in m) {
            val dx = c.cx - cx
            val dy = c.cy - cy

            val majorCenter =
                dx * cos(theta) +
                    dy * sin(theta)

            val minorCenter =
                -dx * sin(theta) +
                    dy * cos(theta)

            minMajor = min(
                minMajor,
                majorCenter - c.length / 2.0
            )
            maxMajor = max(
                maxMajor,
                majorCenter + c.length / 2.0
            )

            minMinor = min(
                minMinor,
                minorCenter - c.width / 2.0
            )
            maxMinor = max(
                maxMinor,
                minorCenter + c.width / 2.0
            )
        }

        return Component(
            pixels = m.sumOf { it.pixels },
            cx = cx,
            cy = cy,
            minX = m.minOf { it.minX },
            minY = m.minOf { it.minY },
            maxX = m.maxOf { it.maxX },
            maxY = m.maxOf { it.maxY },
            length = max(1.0, maxMajor - minMajor),
            width = max(1.0, maxMinor - minMinor),
            angle = angle,
            strength = m.map { it.strength }.average(),
            shadow = m.map { it.shadow }.average()
        )
    }

    private fun calculateWrinkleRisk(
        wrinkles: List<SealWrinkle>
    ): Double {
        if (wrinkles.isEmpty()) {
            return 0.0
        }

        val countRisk =
            when {
                wrinkles.size >= 3 -> 100.0
                wrinkles.size == 2 -> 72.0
                else -> 35.0
            }

        val longest =
            wrinkles.maxOf {
                it.lengthPercent
            }

        val avgStrength =
            wrinkles.map {
                it.strength
            }.average()

        val avgCrossing =
            wrinkles.map {
                it.sealCrossingRisk
            }.average()

        val avgShadow =
            wrinkles.map {
                it.shadowRisk
            }.average()

        return (
            countRisk * 0.40 +
                longest.coerceAtMost(60.0) / 60.0 * 100.0 * 0.20 +
                avgStrength * 0.16 +
                avgCrossing * 0.16 +
                avgShadow * 0.08
            ).coerceIn(0.0, 100.0)
    }

    private fun calculateSealLineUniformity(
        gray: DoubleArray,
        w: Int,
        h: Int,
        sealAngle: Double
    ): Double {
        val horizontal =
            angleDifference(sealAngle, 0.0) <=
                angleDifference(sealAngle, 90.0)

        val profile =
            if (horizontal) {
                DoubleArray(w) { x ->
                    var sum = 0.0
                    var count = 0

                    val y0 = (h * 0.28).roundToInt()
                    val y1 = (h * 0.72).roundToInt()

                    for (y in y0 until y1) {
                        sum += gray[y * w + x]
                        count++
                    }

                    if (count > 0) sum / count else 0.0
                }
            } else {
                DoubleArray(h) { y ->
                    var sum = 0.0
                    var count = 0

                    val x0 = (w * 0.28).roundToInt()
                    val x1 = (w * 0.72).roundToInt()

                    for (x in x0 until x1) {
                        sum += gray[y * w + x]
                        count++
                    }

                    if (count > 0) sum / count else 0.0
                }
            }

        if (profile.isEmpty()) {
            return 100.0
        }

        val mean = profile.average()

        val std =
            sqrt(
                profile.map {
                    val d = it - mean
                    d * d
                }.average()
            )

        return (
            100.0 -
                std * 3.0
            ).coerceIn(0.0, 100.0)
    }

    private fun calculateWidthVariation(
        gray: DoubleArray,
        localMean: DoubleArray,
        w: Int,
        h: Int,
        sealAngle: Double
    ): Double {
        val horizontal =
            angleDifference(sealAngle, 0.0) <=
                angleDifference(sealAngle, 90.0)

        val widths = mutableListOf<Double>()

        if (horizontal) {
            val step = max(1, w / 24)

            for (x in 0 until w step step) {
                var count = 0

                for (y in 0 until h) {
                    val i = y * w + x
                    if (abs(gray[i] - localMean[i]) >= 10.0) {
                        count++
                    }
                }

                widths.add(count.toDouble())
            }
        } else {
            val step = max(1, h / 24)

            for (y in 0 until h step step) {
                var count = 0

                for (x in 0 until w) {
                    val i = y * w + x
                    if (abs(gray[i] - localMean[i]) >= 10.0) {
                        count++
                    }
                }

                widths.add(count.toDouble())
            }
        }

        if (widths.size < 2) {
            return 0.0
        }

        val mean = widths.average()
        if (mean <= 0.5) {
            return 0.0
        }

        val std =
            sqrt(
                widths.map {
                    val d = it - mean
                    d * d
                }.average()
            )

        return (
            std / mean * 100.0
            ).coerceIn(0.0, 100.0)
    }

    private fun calculateLocalDiscontinuity(
        sobel: DoubleArray,
        w: Int,
        h: Int,
        sealAngle: Double
    ): Double {
        val horizontal =
            angleDifference(sealAngle, 0.0) <=
                angleDifference(sealAngle, 90.0)

        val profile =
            if (horizontal) {
                DoubleArray(w) { x ->
                    var sum = 0.0
                    for (y in 0 until h) {
                        sum += sobel[y * w + x]
                    }
                    sum / h
                }
            } else {
                DoubleArray(h) { y ->
                    var sum = 0.0
                    for (x in 0 until w) {
                        sum += sobel[y * w + x]
                    }
                    sum / w
                }
            }

        if (profile.size < 4) {
            return 0.0
        }

        val mean = profile.average()
        if (mean <= 1.0) {
            return 0.0
        }

        var weakRuns = 0
        var currentRun = 0
        val weakThreshold = mean * 0.45

        for (v in profile) {
            if (v < weakThreshold) {
                currentRun++
            } else {
                if (currentRun >= 2) {
                    weakRuns += currentRun
                }
                currentRun = 0
            }
        }

        if (currentRun >= 2) {
            weakRuns += currentRun
        }

        return (
            weakRuns.toDouble() /
                profile.size *
                180.0
            ).coerceIn(0.0, 100.0)
    }

    private fun calculatePpFlowRisk(
        gray: DoubleArray,
        localMean: DoubleArray,
        w: Int,
        h: Int,
        sealAngle: Double
    ): Double {
        val horizontal =
            angleDifference(sealAngle, 0.0) <=
                angleDifference(sealAngle, 90.0)

        fun meanContrast(
            start: Int,
            end: Int,
            alongHeight: Boolean
        ): Double {
            var sum = 0.0
            var count = 0

            if (alongHeight) {
                for (y in start until end) {
                    for (x in 0 until w) {
                        val i = y * w + x
                        sum += abs(gray[i] - localMean[i])
                        count++
                    }
                }
            } else {
                for (x in start until end) {
                    for (y in 0 until h) {
                        val i = y * w + x
                        sum += abs(gray[i] - localMean[i])
                        count++
                    }
                }
            }

            return if (count > 0) sum / count else 0.0
        }

        val a: Double
        val b: Double

        if (horizontal) {
            a = meanContrast(0, max(1, h / 2), true)
            b = meanContrast(h / 2, h, true)
        } else {
            a = meanContrast(0, max(1, w / 2), false)
            b = meanContrast(w / 2, w, false)
        }

        val asymmetry =
            abs(a - b) /
                max(1.0, (a + b) / 2.0)

        return (
            asymmetry * 120.0
            ).coerceIn(0.0, 100.0)
    }

    private fun calculateShadowVariation(
        gray: DoubleArray,
        localMean: DoubleArray
    ): Double {
        if (gray.isEmpty()) return 0.0

        val values =
            DoubleArray(gray.size) { i ->
                abs(gray[i] - localMean[i])
            }

        val mean = values.average()

        val std =
            sqrt(
                values.map {
                    val d = it - mean
                    d * d
                }.average()
            )

        return (
            mean * 2.4 +
                std * 1.8
            ).coerceIn(0.0, 100.0)
    }

    private fun calculateCupIntrusionRisk(
        sobel: DoubleArray,
        w: Int,
        h: Int,
        sealAngle: Double
    ): Double {
        val horizontal =
            angleDifference(sealAngle, 0.0) <=
                angleDifference(sealAngle, 90.0)

        var inner = 0.0
        var outer = 0.0
        var innerCount = 0
        var outerCount = 0

        if (horizontal) {
            val split = (h * 0.72).roundToInt()

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val v = sobel[y * w + x]

                    if (y >= split) {
                        outer += v
                        outerCount++
                    } else if (y >= h / 2) {
                        inner += v
                        innerCount++
                    }
                }
            }
        } else {
            val split = (w * 0.72).roundToInt()

            for (x in 0 until w) {
                for (y in 0 until h) {
                    val v = sobel[y * w + x]

                    if (x >= split) {
                        outer += v
                        outerCount++
                    } else if (x >= w / 2) {
                        inner += v
                        innerCount++
                    }
                }
            }
        }

        val innerMean =
            if (innerCount > 0) inner / innerCount else 0.0

        val outerMean =
            if (outerCount > 0) outer / outerCount else 0.0

        if (innerMean <= 1.0) {
            return 0.0
        }

        return (
            outerMean /
                innerMean *
                55.0
            ).coerceIn(0.0, 100.0)
    }

    private fun normalizeAngle(
        angle: Double
    ): Double {
        var a = angle

        while (a < 0.0) a += 180.0
        while (a >= 180.0) a -= 180.0

        return a
    }

    private fun angleDifference(
        a: Double,
        b: Double
    ): Double {
        val d =
            abs(
                normalizeAngle(a) -
                    normalizeAngle(b)
            )

        return min(
            d,
            180.0 - d
        )
    }

    private fun emptyResult(): Result {
        return Result(
            wrinkleCount = 0,
            wrinkles = emptyList(),
            longestWrinklePercent = 0.0,
            averageWrinkleStrength = 0.0,
            sealLineUniformity = 100.0,
            widthVariationPercent = 0.0,
            localDiscontinuityRisk = 0.0,
            ppFlowRisk = 0.0,
            transparencyShadowRisk = 0.0,
            cupIntrusionRisk = 0.0,
            overallRisk = 0.0,
            qualityScore = 100.0,
            suggestedJudgment = "정상",
            rawComponentCount = 0
        )
    }
}
