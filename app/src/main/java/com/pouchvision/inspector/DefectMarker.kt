package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object DefectMarker {

    /*
     * =========================================================
     * 하나의 NG 후보 영역
     * =========================================================
     */

    data class DefectRegion(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val pointCount: Int,
        val strength: Double
    )

    /*
     * =========================================================
     * 분석 결과
     * =========================================================
     */

    data class MarkerResult(
        val bitmap: Bitmap,
        val regions: List<DefectRegion>
    )

    /*
     * =========================================================
     * 후보 포인트
     * =========================================================
     */

    private data class CandidatePoint(
        val x: Float,
        val y: Float,
        val strength: Int
    )

    /*
     * =========================================================
     * 메인 함수
     *
     * sourceBitmap : 원본사진
     * roiLeft      : ROI 시작 X
     * roiTop       : ROI 시작 Y
     * roiWidth     : ROI 폭
     * roiHeight    : ROI 높이
     * sensitivity  : 0 ~ 100
     *
     * =========================================================
     */

    fun markDefectRegions(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        sensitivity: Int,
        maxRegions: Int = 5
    ): MarkerResult {

        /*
         * 잘못된 ROI 방지
         */

        if (
            roiWidth <= 5 ||
            roiHeight <= 5 ||
            roiLeft < 0 ||
            roiTop < 0 ||
            roiLeft + roiWidth > sourceBitmap.width ||
            roiTop + roiHeight > sourceBitmap.height
        ) {

            return MarkerResult(
                bitmap = sourceBitmap.copy(
                    Bitmap.Config.ARGB_8888,
                    true
                ),
                regions = emptyList()
            )
        }

        /*
         * ROI 추출
         */

        val roiBitmap =
            Bitmap.createBitmap(
                sourceBitmap,
                roiLeft,
                roiTop,
                roiWidth,
                roiHeight
            )

        /*
         * 분석용 작은 이미지
         *
         * 휴대폰 성능 부담을 줄이기 위해
         * 320 x 240 이하로 분석
         */

        val analysisWidth =
            min(
                320,
                roiWidth
            )

        val analysisHeight =
            min(
                240,
                roiHeight
            )

        val smallBitmap =
            Bitmap.createScaledBitmap(
                roiBitmap,
                analysisWidth,
                analysisHeight,
                true
            )

        /*
         * 민감도
         *
         * 민감도가 높을수록
         * 작은 변화도 후보로 검출
         */

        val threshold =
            (
                125 -
                    sensitivity.coerceIn(
                        0,
                        100
                    ) *
                    0.75
                )
                .toInt()
                .coerceIn(
                    35,
                    115
                )

        val candidates =
            mutableListOf<CandidatePoint>()

        val scaleX =
            roiWidth.toFloat() /
                analysisWidth.toFloat()

        val scaleY =
            roiHeight.toFloat() /
                analysisHeight.toFloat()

        /*
         * =========================================================
         * Edge 후보 검출
         * =========================================================
         */

        for (
            y in 2 until
                analysisHeight - 2 step 3
        ) {

            for (
                x in 2 until
                    analysisWidth - 2 step 3
            ) {

                val center =
                    gray(
                        smallBitmap.getPixel(
                            x,
                            y
                        )
                    )

                val left =
                    gray(
                        smallBitmap.getPixel(
                            x - 1,
                            y
                        )
                    )

                val right =
                    gray(
                        smallBitmap.getPixel(
                            x + 1,
                            y
                        )
                    )

                val top =
                    gray(
                        smallBitmap.getPixel(
                            x,
                            y - 1
                        )
                    )

                val bottom =
                    gray(
                        smallBitmap.getPixel(
                            x,
                            y + 1
                        )
                    )

                val horizontal =
                    kotlin.math.abs(
                        right -
                            left
                    )

                val vertical =
                    kotlin.math.abs(
                        bottom -
                            top
                    )

                val local =
                    (
                        kotlin.math.abs(
                            center -
                                right
                        ) +
                            kotlin.math.abs(
                                center -
                                    bottom
                            )
                        )

                val gradient =
                    horizontal +
                        vertical +
                        local

                if (
                    gradient >=
                    threshold
                ) {

                    val originalX =
                        roiLeft +
                            x *
                            scaleX

                    val originalY =
                        roiTop +
                            y *
                            scaleY

                    candidates.add(
                        CandidatePoint(
                            x =
                                originalX,

                            y =
                                originalY,

                            strength =
                                gradient
                        )
                    )
                }
            }
        }

        /*
         * 후보가 없을 경우
         */

        if (
            candidates.isEmpty()
        ) {

            return MarkerResult(
                bitmap =
                    sourceBitmap.copy(
                        Bitmap.Config.ARGB_8888,
                        true
                    ),

                regions =
                    emptyList()
            )
        }

        /*
         * =========================================================
         * 가까운 후보 포인트를 하나의 영역으로 묶기
         * =========================================================
         */

        val clusterDistance =
            max(
                24f,
                min(
                    roiWidth,
                    roiHeight
                ) *
                    0.12f
            )

        val used =
            BooleanArray(
                candidates.size
            )

        val regions =
            mutableListOf<DefectRegion>()

        for (
            i in
            candidates.indices
        ) {

            if (
                used[i]
            ) {

                continue
            }

            val base =
                candidates[i]

            val cluster =
                mutableListOf<CandidatePoint>()

            cluster.add(
                base
            )

            used[i] =
                true

            var changed =
                true

            while (
                changed
            ) {

                changed =
                    false

                for (
                    j in
                    candidates.indices
                ) {

                    if (
                        used[j]
                    ) {

                        continue
                    }

                    val candidate =
                        candidates[j]

                    var nearCluster =
                        false

                    for (
                        existing in
                        cluster
                    ) {

                        val dx =
                            candidate.x -
                                existing.x

                        val dy =
                            candidate.y -
                                existing.y

                        val distanceSquared =
                            dx *
                                dx +
                                dy *
                                dy

                        if (
                            distanceSquared <=
                            clusterDistance *
                                clusterDistance
                        ) {

                            nearCluster =
                                true

                            break
                        }
                    }

                    if (
                        nearCluster
                    ) {

                        used[j] =
                            true

                        cluster.add(
                            candidate
                        )

                        changed =
                            true
                    }
                }
            }

            /*
             * 너무 작은 후보 묶음은 제거
             */

            if (
                cluster.size <
                3
            ) {

                continue
            }

            var minX =
                Float.MAX_VALUE

            var minY =
                Float.MAX_VALUE

            var maxX =
                -Float.MAX_VALUE

            var maxY =
                -Float.MAX_VALUE

            var totalStrength =
                0.0

            for (
                point in
                cluster
            ) {

                minX =
                    min(
                        minX,
                        point.x
                    )

                minY =
                    min(
                        minY,
                        point.y
                    )

                maxX =
                    max(
                        maxX,
                        point.x
                    )

                maxY =
                    max(
                        maxY,
                        point.y
                    )

                totalStrength +=
                    point.strength
            }

            val centerX =
                (
                    minX +
                        maxX
                    ) /
                    2f

            val centerY =
                (
                    minY +
                        maxY
                    ) /
                    2f

            val width =
                maxX -
                    minX

            val height =
                maxY -
                    minY

            val radius =
                (
                    max(
                        width,
                        height
                    ) /
                        2f +
                        18f
                    )
                    .coerceAtLeast(
                        24f
                    )

            val averageStrength =
                totalStrength /
                    cluster.size

            regions.add(
                DefectRegion(
                    centerX =
                        centerX,

                    centerY =
                        centerY,

                    radius =
                        radius,

                    pointCount =
                        cluster.size,

                    strength =
                        averageStrength
                )
            )
        }

        /*
         * =========================================================
         * 중요도가 높은 영역 우선
         * =========================================================
         */

        val sortedRegions =
            regions
                .sortedWith(
                    compareByDescending<DefectRegion> {
                        it.pointCount
                    }
                        .thenByDescending {
                            it.strength
                        }
                )
                .take(
                    maxRegions
                )

        /*
         * =========================================================
         * 빨간 원 / 박스 / 번호 표시
         * =========================================================
         */

        val resultBitmap =
            sourceBitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        val canvas =
            Canvas(
                resultBitmap
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
                            350f
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
                            450f
                    )
            }

        val labelBackgroundPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.argb(
                        210,
                        220,
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

                style =
                    Paint.Style.FILL

                textSize =
                    max(
                        26f,
                        sourceBitmap.width /
                            35f
                    )

                isFakeBoldText =
                    true
            }

        sortedRegions
            .forEachIndexed {
                    index,
                    region ->

                val radius =
                    region.radius

                /*
                 * 큰 빨간 원
                 */

                canvas.drawCircle(
                    region.centerX,
                    region.centerY,
                    radius,
                    circlePaint
                )

                /*
                 * 사각 박스
                 */

                val rect =
                    RectF(
                        region.centerX -
                            radius,

                        region.centerY -
                            radius,

                        region.centerX +
                            radius,

                        region.centerY +
                            radius
                    )

                canvas.drawRect(
                    rect,
                    boxPaint
                )

                /*
                 * 번호 표시
                 */

                val label =
                    "NG 후보 ${index + 1}"

                val textWidth =
                    textPaint.measureText(
                        label
                    )

                val labelLeft =
                    (
                        region.centerX -
                            radius
                        )
                        .coerceAtLeast(
                            0f
                        )

                val labelTop =
                    (
                        region.centerY -
                            radius -
                            textPaint.textSize -
                            10f
                        )
                        .coerceAtLeast(
                            0f
                        )

                val labelRect =
                    RectF(
                        labelLeft,
                        labelTop,
                        labelLeft +
                            textWidth +
                            16f,
                        labelTop +
                            textPaint.textSize +
                            12f
                    )

                canvas.drawRect(
                    labelRect,
                    labelBackgroundPaint
                )

                canvas.drawText(
                    label,
                    labelLeft +
                        8f,
                    labelTop +
                        textPaint.textSize,
                    textPaint
                )
            }

        return MarkerResult(
            bitmap =
                resultBitmap,

            regions =
                sortedRegions
        )
    }

    /*
     * =========================================================
     * 결과 설명 문자열
     * =========================================================
     */

    fun buildRegionSummary(
        regions: List<DefectRegion>
    ): String {

        if (
            regions.isEmpty()
        ) {

            return "강한 국부 변화 후보 영역 없음"
        }

        val builder =
            StringBuilder()

        builder.append(
            "NG 후보 영역 : ${regions.size}개"
        )

        regions
            .forEachIndexed {
                    index,
                    region ->

                builder.append(
                    "\n"
                )

                builder.append(
                    "${index + 1}) 후보점 ${region.pointCount}개"
                )
            }

        return builder.toString()
    }

    /*
     * =========================================================
     * Gray
     * =========================================================
     */

    private fun gray(
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
            0.299 *
                r +
                0.587 *
                g +
                0.114 *
                b
            )
            .toInt()
    }
}
