package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/*
 * =============================================================
 * 검사 후보 표시 전용 Renderer
 * =============================================================
 *
 * 목적
 * 1) 판정 / 검출 알고리즘은 전혀 변경하지 않습니다.
 * 2) 후보 위치를 표시하는 빨간 원의 굵기만 기존의 약 50%로 줄입니다.
 * 3) "주름 후보 1", "NG 후보 1" 같은 빨간 라벨은
 *    녹색 ROI 내부를 가리지 않도록 ROI 바깥쪽에 배치합니다.
 *
 * 적용 대상
 * - Bottom Corner
 * - Seal
 * - Forming
 * - Tab
 * - Disassembly
 *
 * 후보 영역 계산은 기존
 * BottomCornerDefectMarker / DefectMarker 결과를 그대로 사용합니다.
 * =============================================================
 */

object MarkerDisplayRenderer {

    private data class SimpleRegion(
        val centerX: Float,
        val centerY: Float,
        val radius: Float
    )

    private enum class LabelSide {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    /*
     * =========================================================
     * Bottom Corner 표시
     * =========================================================
     */

    fun renderBottomCorner(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        regions: List<BottomCornerDefectMarker.WrinkleRegion>
    ): Bitmap {

        val simpleRegions =
            regions.map {

                SimpleRegion(
                    centerX = it.centerX,
                    centerY = it.centerY,
                    radius = it.radius
                )
            }

        return render(
            sourceBitmap = sourceBitmap,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiWidth = roiWidth,
            roiHeight = roiHeight,
            regions = simpleRegions,
            labelPrefix = "주름 후보",

            /*
             * 기존 Bottom Corner 원 굵기:
             * max(4f, width / 220f)
             *
             * 변경:
             * 약 50%
             */
            circleStrokeWidth =
                max(
                    2f,
                    sourceBitmap.width /
                        440f
                ),

            /*
             * 후보 사각형 굵기는 기존 수준 유지
             */
            boxStrokeWidth =
                max(
                    3f,
                    sourceBitmap.width /
                        260f
                ),

            textSize =
                max(
                    24f,
                    sourceBitmap.width /
                        32f
                ),

            labelRed =
                190
        )
    }

    /*
     * =========================================================
     * Seal / Forming / Tab / Disassembly 표시
     * =========================================================
     */

    fun renderGeneric(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        regions: List<DefectMarker.DefectRegion>
    ): Bitmap {

        val simpleRegions =
            regions.map {

                SimpleRegion(
                    centerX = it.centerX,
                    centerY = it.centerY,
                    radius = it.radius
                )
            }

        return render(
            sourceBitmap = sourceBitmap,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiWidth = roiWidth,
            roiHeight = roiHeight,
            regions = simpleRegions,
            labelPrefix = "NG 후보",

            /*
             * 기존 공용 원 굵기:
             * max(4f, width / 350f)
             *
             * 변경:
             * 약 50%
             */
            circleStrokeWidth =
                max(
                    2f,
                    sourceBitmap.width /
                        700f
                ),

            /*
             * 후보 사각형 굵기는 기존 수준 유지
             */
            boxStrokeWidth =
                max(
                    3f,
                    sourceBitmap.width /
                        450f
                ),

            textSize =
                max(
                    26f,
                    sourceBitmap.width /
                        35f
                ),

            labelRed =
                220
        )
    }

    /*
     * =========================================================
     * 공용 그리기
     * =========================================================
     */

    private fun render(
        sourceBitmap: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int,
        regions: List<SimpleRegion>,
        labelPrefix: String,
        circleStrokeWidth: Float,
        boxStrokeWidth: Float,
        textSize: Float,
        labelRed: Int
    ): Bitmap {

        val resultBitmap =
            sourceBitmap.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        if (
            regions.isEmpty()
        ) {

            return resultBitmap
        }

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
                    circleStrokeWidth
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
                    boxStrokeWidth
            }

        val labelBackgroundPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.argb(
                        210,
                        labelRed,
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

                this.textSize =
                    textSize

                isFakeBoldText =
                    true
            }

        /*
         * 라벨과 후보 영역을 연결하는 가는 선
         *
         * 라벨을 ROI 밖으로 옮겨도 어느 후보의 라벨인지
         * 쉽게 확인할 수 있게 합니다.
         */
        val leaderPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.RED

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    max(
                        1.5f,
                        circleStrokeWidth *
                            0.75f
                    )
            }

        /*
         * 후보 원 / 사각형
         */
        regions.forEach { region ->

            canvas.drawCircle(
                region.centerX,
                region.centerY,
                region.radius,
                circlePaint
            )

            canvas.drawRect(
                RectF(
                    region.centerX -
                        region.radius,

                    region.centerY -
                        region.radius,

                    region.centerX +
                        region.radius,

                    region.centerY +
                        region.radius
                ),
                boxPaint
            )
        }

        /*
         * ROI 안전 좌표
         */
        val imageWidth =
            resultBitmap.width
                .toFloat()

        val imageHeight =
            resultBitmap.height
                .toFloat()

        val safeRoiLeft =
            roiLeft
                .toFloat()
                .coerceIn(
                    0f,
                    imageWidth
                )

        val safeRoiTop =
            roiTop
                .toFloat()
                .coerceIn(
                    0f,
                    imageHeight
                )

        val safeRoiRight =
            (
                roiLeft +
                    roiWidth
                )
                .toFloat()
                .coerceIn(
                    safeRoiLeft,
                    imageWidth
                )

        val safeRoiBottom =
            (
                roiTop +
                    roiHeight
                )
                .toFloat()
                .coerceIn(
                    safeRoiTop,
                    imageHeight
                )

        val outerGap =
            max(
                8f,
                sourceBitmap.width /
                    180f
            )

        val paddingX =
            max(
                8f,
                sourceBitmap.width /
                    180f
            )

        val paddingY =
            max(
                6f,
                sourceBitmap.width /
                    260f
            )

        val rowGap =
            max(
                6f,
                sourceBitmap.width /
                    260f
            )

        val labelHeight =
            textPaint.textSize +
                paddingY *
                2f

        val labels =
            regions.indices.map { index ->

                "$labelPrefix ${index + 1}"
            }

        val labelWidths =
            labels.map {

                textPaint.measureText(
                    it
                ) +
                    paddingX *
                    2f
            }

        val maxLabelWidth =
            labelWidths.maxOrNull()
                ?: 0f

        val totalLabelHeight =
            labels.size *
                labelHeight +
                max(
                    0,
                    labels.size -
                        1
                ) *
                rowGap

        val spaceTop =
            safeRoiTop

        val spaceBottom =
            imageHeight -
                safeRoiBottom

        val spaceLeft =
            safeRoiLeft

        val spaceRight =
            imageWidth -
                safeRoiRight

        /*
         * 가능한 경우 우선순위:
         * 위 → 아래 → 왼쪽 → 오른쪽
         *
         * 공간이 부족하면 가장 넓은 바깥 공간을 선택합니다.
         */
        val side =
            when {

                spaceTop >=
                    totalLabelHeight +
                    outerGap ->

                    LabelSide.TOP

                spaceBottom >=
                    totalLabelHeight +
                    outerGap ->

                    LabelSide.BOTTOM

                spaceLeft >=
                    maxLabelWidth +
                    outerGap ->

                    LabelSide.LEFT

                spaceRight >=
                    maxLabelWidth +
                    outerGap ->

                    LabelSide.RIGHT

                else -> {

                    listOf(
                        LabelSide.TOP to
                            spaceTop,

                        LabelSide.BOTTOM to
                            spaceBottom,

                        LabelSide.LEFT to
                            spaceLeft,

                        LabelSide.RIGHT to
                            spaceRight
                    )
                        .maxByOrNull {

                            it.second
                        }
                        ?.first
                        ?: LabelSide.TOP
                }
            }

        /*
         * 각 라벨을 ROI 바깥쪽에 배치
         */
        labels.forEachIndexed {
                index,
                label ->

            val labelWidth =
                labelWidths[index]

            val rect =
                calculateLabelRect(
                    side = side,
                    index = index,
                    labelWidth = labelWidth,
                    labelHeight = labelHeight,
                    rowGap = rowGap,
                    outerGap = outerGap,
                    roiLeft = safeRoiLeft,
                    roiTop = safeRoiTop,
                    roiRight = safeRoiRight,
                    roiBottom = safeRoiBottom,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    totalLabelHeight = totalLabelHeight
                )

            canvas.drawRect(
                rect,
                labelBackgroundPaint
            )

            canvas.drawText(
                label,
                rect.left +
                    paddingX,

                rect.top +
                    paddingY +
                    textPaint.textSize,

                textPaint
            )

            /*
             * 라벨 → 해당 후보로 연결하는 가는 선
             */
            val region =
                regions[index]

            val startX: Float
            val startY: Float

            when (
                side
            ) {

                LabelSide.TOP -> {

                    startX =
                        rect.centerX()

                    startY =
                        rect.bottom
                }

                LabelSide.BOTTOM -> {

                    startX =
                        rect.centerX()

                    startY =
                        rect.top
                }

                LabelSide.LEFT -> {

                    startX =
                        rect.right

                    startY =
                        rect.centerY()
                }

                LabelSide.RIGHT -> {

                    startX =
                        rect.left

                    startY =
                        rect.centerY()
                }
            }

            canvas.drawLine(
                startX,
                startY,
                region.centerX,
                region.centerY,
                leaderPaint
            )
        }

        return resultBitmap
    }

    /*
     * =========================================================
     * 라벨 위치 계산
     * =========================================================
     */

    private fun calculateLabelRect(
        side: LabelSide,
        index: Int,
        labelWidth: Float,
        labelHeight: Float,
        rowGap: Float,
        outerGap: Float,
        roiLeft: Float,
        roiTop: Float,
        roiRight: Float,
        roiBottom: Float,
        imageWidth: Float,
        imageHeight: Float,
        totalLabelHeight: Float
    ): RectF {

        var left: Float
        var top: Float

        when (
            side
        ) {

            LabelSide.TOP -> {

                left =
                    roiLeft

                top =
                    roiTop -
                        outerGap -
                        totalLabelHeight +
                        index *
                        (
                            labelHeight +
                                rowGap
                            )
            }

            LabelSide.BOTTOM -> {

                left =
                    roiLeft

                top =
                    roiBottom +
                        outerGap +
                        index *
                        (
                            labelHeight +
                                rowGap
                            )
            }

            LabelSide.LEFT -> {

                left =
                    roiLeft -
                        outerGap -
                        labelWidth

                top =
                    roiTop +
                        index *
                        (
                            labelHeight +
                                rowGap
                            )
            }

            LabelSide.RIGHT -> {

                left =
                    roiRight +
                        outerGap

                top =
                    roiTop +
                        index *
                        (
                            labelHeight +
                                rowGap
                            )
            }
        }

        /*
         * 이미지 바깥으로 나가지 않게 제한
         *
         * ROI가 화면 대부분을 차지해 물리적으로 바깥 공간이
         * 매우 부족한 경우에는 이미지 가장자리 쪽에 최대한 붙입니다.
         */
        left =
            left.coerceIn(
                0f,
                max(
                    0f,
                    imageWidth -
                        labelWidth
                )
            )

        top =
            top.coerceIn(
                0f,
                max(
                    0f,
                    imageHeight -
                        labelHeight
                )
            )

        return RectF(
            left,
            top,
            left +
                labelWidth,
            top +
                labelHeight
        )
    }
}
