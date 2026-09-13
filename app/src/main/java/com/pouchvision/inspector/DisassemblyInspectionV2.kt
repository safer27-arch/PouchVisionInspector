package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * DISASSEMBLY V2
 *
 * 분해 후 실링부는 작업자가 파우치를 잡아 뜯는 과정에서 위치/각도가 흔들릴 수 있으므로
 * 고정 좌표나 절대 직선 위치를 판정 기준으로 사용하지 않습니다.
 *
 * 핵심:
 * 1) PP/실링 흔적의 연속성
 * 2) 국부 끊김/찢김성 변화
 * 3) 폭/표면 변화의 균일성
 * 4) 강한 국부 Edge 집중
 *
 * 현재는 정상 Master 중심의 1차 현장용 기준입니다.
 * 실제 NG 샘플 확보 후 threshold를 재보정하는 구조입니다.
 */
object DisassemblyInspectionV2 {

    data class Result(
        val qualityScore: Double,
        val surfaceUniformity: Double,
        val continuityRisk: Double,
        val widthVariationRisk: Double,
        val localTearRisk: Double,
        val strongEdgeRisk: Double,
        val positionTolerance: Double,
        val confidence: Double,
        val note: String
    )

    fun analyze(
        roi: Bitmap,
        sensitivity: Int
    ): Result {

        val w = 320
        val h = 240
        val small = Bitmap.createScaledBitmap(roi, w, h, true)

        val gray = Array(h) { DoubleArray(w) }
        var sum = 0.0
        var sum2 = 0.0

        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = small.getPixel(x, y)
                val g = 0.299 * Color.red(c) +
                    0.587 * Color.green(c) +
                    0.114 * Color.blue(c)
                gray[y][x] = g
                sum += g
                sum2 += g * g
            }
        }

        val n = (w * h).toDouble()
        val mean = sum / n
        val std = sqrt(max(0.0, sum2 / n - mean * mean))

        // 위치가 흔들려도 견디도록 ROI를 12개 수평 band로 나누어
        // 각 band의 "변화량"을 비교합니다.
        val bands = 12
        val bandScores = DoubleArray(bands)
        val bandStrong = DoubleArray(bands)

        var totalGrad = 0.0
        var totalStrong = 0.0
        var gradCount = 0

        val sensitivityFactor = 0.82 + sensitivity.coerceIn(0, 100) / 500.0
        val strongThreshold = (58.0 - sensitivity * 0.18).coerceIn(36.0, 58.0)

        for (y in 1 until h - 1) {
            val band = min(bands - 1, y * bands / h)
            for (x in 1 until w - 1) {
                val gx = abs(gray[y][x + 1] - gray[y][x - 1])
                val gy = abs(gray[y + 1][x] - gray[y - 1][x])
                val g = gx + gy
                totalGrad += g
                gradCount++
                bandScores[band] += g
                if (g >= strongThreshold) {
                    bandStrong[band] += 1.0
                    totalStrong += 1.0
                }
            }
        }

        val pixelsPerBand = ((h.toDouble() / bands) * (w - 2)).coerceAtLeast(1.0)
        for (i in 0 until bands) {
            bandScores[i] /= pixelsPerBand
            bandStrong[i] = bandStrong[i] / pixelsPerBand * 100.0
        }

        val avgGrad = if (gradCount > 0) totalGrad / gradCount else 0.0
        val strongDensity = if (gradCount > 0) totalStrong / gradCount * 100.0 else 0.0

        val sortedBand = bandScores.sorted()
        val medianBand = sortedBand[sortedBand.size / 2].coerceAtLeast(1.0)

        // 한두 위치의 강한 변화가 전체를 망치지 않도록 robust deviation 사용
        val bandDeviation = bandScores
            .map { abs(it - medianBand) / medianBand }
            .sorted()
        val robustDeviation = bandDeviation[bandDeviation.size * 3 / 4]

        // 인접 band 사이 급격한 변화 = PP 흔적 끊김/불연속 후보
        var jumpSum = 0.0
        var jumpMax = 0.0
        for (i in 1 until bands) {
            val d = abs(bandScores[i] - bandScores[i - 1]) / medianBand
            jumpSum += d
            jumpMax = max(jumpMax, d)
        }
        val avgJump = jumpSum / (bands - 1)

        // 좌/우 8개 세로 strip의 변화량 차이.
        // 분해 후 위치가 약간 틀어져도 절대 위치 대신 분포 차이만 봅니다.
        val strips = 8
        val stripScores = DoubleArray(strips)
        val stripCount = IntArray(strips)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val s = min(strips - 1, x * strips / w)
                val gx = abs(gray[y][x + 1] - gray[y][x - 1])
                val gy = abs(gray[y + 1][x] - gray[y - 1][x])
                stripScores[s] += gx + gy
                stripCount[s]++
            }
        }
        for (i in 0 until strips) {
            if (stripCount[i] > 0) stripScores[i] /= stripCount[i]
        }
        val stripMean = stripScores.average().coerceAtLeast(1.0)
        val stripCv = sqrt(
            stripScores.map { (it - stripMean) * (it - stripMean) }.average()
        ) / stripMean

        // 정상 Master에서 알루미늄 반사 자체를 불량으로 보지 않도록
        // 전체 밝기/명암은 낮은 가중치로만 사용합니다.
        val continuityRisk = (
            avgJump * 48.0 +
                jumpMax * 18.0
            ).coerceIn(0.0, 100.0) * sensitivityFactor

        val widthVariationRisk = (
            stripCv * 115.0 +
                robustDeviation * 28.0
            ).coerceIn(0.0, 100.0) * sensitivityFactor

        val localTearRisk = (
            max(0.0, strongDensity - 3.0) * 6.0 +
                max(0.0, jumpMax - 0.45) * 42.0
            ).coerceIn(0.0, 100.0) * sensitivityFactor

        val strongEdgeRisk = (
            max(0.0, strongDensity - 2.0) * 5.0 +
                max(0.0, avgGrad - 15.0) * 1.4
            ).coerceIn(0.0, 100.0) * sensitivityFactor

        // 분해 후 위치/각도 불안정을 허용하는 정도.
        // 높은 값일수록 "위치 변화 때문에 판정이 흔들리지 않도록" 설계된 상태.
        val positionTolerance = (
            100.0 -
                min(25.0, robustDeviation * 18.0)
            ).coerceIn(70.0, 100.0)

        // 촬영/ROI 자체의 정보량. 너무 평평하거나 너무 복잡하면 confidence 저하.
        val confidence = (
            100.0 -
                max(0.0, 10.0 - std) * 2.0 -
                max(0.0, strongDensity - 18.0) * 2.2
            ).coerceIn(45.0, 100.0)

        // 정상 Master 기반 보수적 가중치.
        // 반사광보다 "연속성/국부 찢김"을 더 중요하게 봅니다.
        val defect = (
            continuityRisk * 0.34 +
                widthVariationRisk * 0.22 +
                localTearRisk * 0.30 +
                strongEdgeRisk * 0.14
            ).coerceIn(0.0, 100.0)

        val quality = (100.0 - defect).coerceIn(0.0, 100.0)
        val uniformity = (
            100.0 -
                widthVariationRisk * 0.55 -
                strongEdgeRisk * 0.20
            ).coerceIn(0.0, 100.0)

        val note = when {
            confidence < 60.0 ->
                "ROI 정보가 불안정합니다. 실링/PP 흔적이 충분히 포함되도록 ROI를 다시 맞춰주세요."
            localTearRisk >= 65.0 ->
                "국부 찢김/강한 단절 후보가 큽니다. 실제 실링부를 확대 확인하세요."
            continuityRisk >= 65.0 ->
                "PP/실링 흔적의 연속성 변화가 큽니다. 뜯김 방향 영향과 실제 단절을 구분해 확인하세요."
            widthVariationRisk >= 70.0 ->
                "폭/표면 분포 변화가 큽니다. 분해 과정의 위치 흔들림인지 실제 실링 편차인지 확인하세요."
            else ->
                "정상 Master 범위 우선 판정입니다. 분해 후 실링 위치 이동은 허용하고 국부 단절/찢김을 중심으로 봅니다."
        }

        if (small !== roi && !small.isRecycled) small.recycle()

        return Result(
            qualityScore = quality,
            surfaceUniformity = uniformity,
            continuityRisk = continuityRisk.coerceIn(0.0, 100.0),
            widthVariationRisk = widthVariationRisk.coerceIn(0.0, 100.0),
            localTearRisk = localTearRisk.coerceIn(0.0, 100.0),
            strongEdgeRisk = strongEdgeRisk.coerceIn(0.0, 100.0),
            positionTolerance = positionTolerance,
            confidence = confidence,
            note = note
        )
    }
}
