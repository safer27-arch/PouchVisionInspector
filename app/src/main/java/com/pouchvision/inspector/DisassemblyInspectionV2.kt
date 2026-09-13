package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * DISASSEMBLY V2.2
 *
 * 정상 분해사진에서 Cell 외곽선, TAB, 알루미늄 반사광을 찢김으로 오인하던
 * V2.1에서 남아 있던 단독 국부 찢김 과검출을 줄이기 위한 정상 Master 교차검증 버전입니다.
 *
 * 원칙
 * 1) 분해 과정의 위치/각도 이동은 허용
 * 2) ROI 외곽의 강한 직선 Edge 영향 억제
 * 3) 전체 밝기보다 PP/Seal 흔적의 국부적인 불연속과 분포 변화에 가중
 * 4) 실제 NG 샘플 확보 전에는 보수적으로 정상 Master 범위를 우선
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

    fun analyze(roi: Bitmap, sensitivity: Int): Result {
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

        // ROI 외곽은 파우치 절단면/Cell 경계가 들어오기 쉬우므로 분석에서 제외합니다.
        val x0 = (w * 0.08).toInt()
        val x1 = (w * 0.92).toInt()
        val y0 = (h * 0.10).toInt()
        val y1 = (h * 0.90).toInt()

        val rowCount = 12
        val colCount = 10
        val rowScore = DoubleArray(rowCount)
        val rowN = IntArray(rowCount)
        val colScore = DoubleArray(colCount)
        val colN = IntArray(colCount)

        var gradSum = 0.0
        var gradN = 0
        var strongN = 0
        val strongThreshold = (72.0 - sensitivity.coerceIn(0, 100) * 0.16)
            .coerceIn(52.0, 72.0)

        for (y in max(1, y0) until min(h - 1, y1)) {
            for (x in max(1, x0) until min(w - 1, x1)) {
                val gx = abs(gray[y][x + 1] - gray[y][x - 1])
                val gy = abs(gray[y + 1][x] - gray[y - 1][x])
                // 한 방향의 긴 직선보다 국부적인 2D 변화에 더 반응하도록 제한합니다.
                val g = min(90.0, gx + gy)
                gradSum += g
                gradN++
                if (g >= strongThreshold) strongN++

                val r = min(rowCount - 1, (y - y0) * rowCount / max(1, y1 - y0))
                val c = min(colCount - 1, (x - x0) * colCount / max(1, x1 - x0))
                rowScore[r] += g
                rowN[r]++
                colScore[c] += g
                colN[c]++
            }
        }

        for (i in rowScore.indices) if (rowN[i] > 0) rowScore[i] /= rowN[i]
        for (i in colScore.indices) if (colN[i] > 0) colScore[i] /= colN[i]

        val avgGrad = if (gradN > 0) gradSum / gradN else 0.0
        val strongDensity = if (gradN > 0) strongN.toDouble() / gradN * 100.0 else 0.0

        val rowMedian = median(rowScore).coerceAtLeast(1.0)
        val colMedian = median(colScore).coerceAtLeast(1.0)
        val rowMad = median(rowScore.map { abs(it - rowMedian) }.toDoubleArray()).coerceAtLeast(0.5)
        val colMad = median(colScore.map { abs(it - colMedian) }.toDoubleArray()).coerceAtLeast(0.5)

        // 인접 band 변화. 최대 한 지점보다 전체적인 안정성을 우선합니다.
        val rowJumps = mutableListOf<Double>()
        for (i in 1 until rowScore.size) {
            rowJumps += abs(rowScore[i] - rowScore[i - 1]) / rowMedian
        }
        val typicalJump = percentile(rowJumps, 0.70)
        val highJump = percentile(rowJumps, 0.90)

        val colCv = sqrt(
            colScore.map { (it - colMedian) * (it - colMedian) }.average()
        ) / colMedian

        // 매우 튀는 band 비율만 국부 단절 후보로 사용합니다.
        val rowOutlierRatio = rowScore.count {
            abs(it - rowMedian) > rowMad * 3.2 + 2.0
        }.toDouble() / rowScore.size
        val colOutlierRatio = colScore.count {
            abs(it - colMedian) > colMad * 3.2 + 2.0
        }.toDouble() / colScore.size

        // 민감도는 V2보다 영향 범위를 작게 하여 정상사진이 급격히 흔들리지 않게 합니다.
        val sf = (0.90 + (sensitivity.coerceIn(0, 100) - 60) * 0.0025)
            .coerceIn(0.80, 1.05)

        val continuityRisk = (
            typicalJump * 38.0 +
                max(0.0, highJump - 0.35) * 22.0
            ).coerceIn(0.0, 62.0) * sf

        val widthVariationRisk = (
            colCv * 70.0 +
                colOutlierRatio * 22.0
            ).coerceIn(0.0, 62.0) * sf

        val localTearRisk = (
            rowOutlierRatio * 48.0 +
                colOutlierRatio * 30.0 +
                max(0.0, highJump - 0.55) * 24.0
            ).coerceIn(0.0, 68.0) * sf

        // 강한 Edge가 조금 존재하는 것은 정상 구조/반사로 간주합니다.
        val strongEdgeRisk = (
            max(0.0, strongDensity - 9.0) * 2.2 +
                max(0.0, avgGrad - 24.0) * 0.8
            ).coerceIn(0.0, 55.0) * sf

        val positionTolerance = (
            100.0 - min(18.0, (rowMad / rowMedian + colMad / colMedian) * 18.0)
            ).coerceIn(80.0, 100.0)

        val confidence = (
            100.0 -
                max(0.0, 9.0 - std) * 2.0 -
                max(0.0, strongDensity - 28.0) * 1.5
            ).coerceIn(55.0, 100.0)

        /*
         * V2.2 정상 Master 교차검증
         *
         * 정상 분해사진에서는 "국부 찢김 Risk" 하나만 높아지는 경우가 있었습니다.
         * 이는 실제 찢김보다 파우치 접힘/반사/Cell 경계 영향일 가능성이 높습니다.
         *
         * 따라서 아래 4개 신호 중 2개 이상이 동시에 높을 때만
         * 실제 결함 가능성을 강하게 점수에 반영합니다.
         */
        val continuityHigh = continuityRisk >= 62.0
        val widthHigh = widthVariationRisk >= 52.0
        val tearHigh = localTearRisk >= 62.0
        val strongHigh = strongEdgeRisk >= 28.0

        val corroborationCount =
            listOf(
                continuityHigh,
                widthHigh,
                tearHigh,
                strongHigh
            ).count { it }

        /*
         * 정상 Master에서 확인된 변동 범위는 "무결함 기준선"으로 흡수합니다.
         * - 연속성 Risk 약 50대: 분해 위치/주름 영향으로 허용
         * - 폭/표면 Variation 약 10~20대: 정상
         * - 국부 찢김 단독 상승: 강한 감점 금지
         */
        val continuityPenalty =
            max(0.0, continuityRisk - 43.0) * 0.78

        val widthPenalty =
            max(0.0, widthVariationRisk - 28.0) * 0.72

        val strongPenalty =
            max(0.0, strongEdgeRisk - 18.0) * 0.70

        val tearPenalty =
            if (corroborationCount >= 2) {
                max(0.0, localTearRisk - 28.0) * 0.95
            } else {
                // 단독 찢김 신호는 정상 반사/접힘 가능성이 높아 보조 가중치만 적용
                max(0.0, localTearRisk - 45.0) * 0.16
            }

        val synergyPenalty =
            when {
                corroborationCount >= 4 -> 18.0
                corroborationCount == 3 -> 11.0
                corroborationCount == 2 -> 5.0
                else -> 0.0
            }

        val defect = (
            continuityPenalty * 0.48 +
                widthPenalty * 0.18 +
                tearPenalty * 0.24 +
                strongPenalty * 0.10 +
                synergyPenalty
            ).coerceIn(0.0, 100.0)

        val quality =
            (100.0 - defect)
                .coerceIn(0.0, 100.0)

        val uniformity = (
            100.0 -
                max(0.0, widthVariationRisk - 15.0) * 0.40 -
                max(0.0, continuityRisk - 40.0) * 0.20
            ).coerceIn(0.0, 100.0)

        val note = when {
            confidence < 65.0 ->
                "ROI 정보가 불안정합니다. PP/실링 흔적이 충분히 포함되도록 ROI를 다시 맞춰주세요."

            corroborationCount >= 2 && tearHigh ->
                "국부 단절/찢김 신호가 다른 이상 신호와 함께 확인됩니다. 실제 실링/PP 흔적을 확대 확인하세요."

            corroborationCount >= 2 ->
                "복수 이상 신호가 동시에 확인됩니다. PP/실링 흔적을 확대 확인하세요."

            tearHigh ->
                "국부 찢김 신호가 단독으로 높지만 정상 Master의 접힘/반사 가능성을 고려해 보조 신호로만 반영했습니다."

            else ->
                "정상 Master 우선 판정입니다. Cell 외곽선/TAB/알루미늄 반사와 분해 후 위치 이동은 결함 판단에서 억제합니다."
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

    private fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }

    private fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val idx = ((s.size - 1) * p.coerceIn(0.0, 1.0)).toInt()
        return s[idx]
    }
}
