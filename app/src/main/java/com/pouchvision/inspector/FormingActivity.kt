package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityFormingBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class FormingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFormingBinding

    private var lastBitmap: Bitmap? = null

    /*
     * 이미지 확대 / 이동
     */
    private val imageMatrixValue = Matrix()

    private var zoomFactor = 1f

    private var lastImageTouchX = 0f
    private var lastImageTouchY = 0f

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    /*
     * ROI 이동
     */
    private var roiLastTouchX = 0f
    private var roiLastTouchY = 0f


    /*
     * 갤러리
     */
    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                loadGalleryImage(uri)
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityFormingBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        setupImageZoom()

        setupRoiDrag()


        /*
         * 갤러리 선택
         */
        binding.btnFormingGallery.setOnClickListener {

            galleryLauncher.launch(
                "image/*"
            )
        }


        /*
         * Forming 검사
         */
        binding.btnFormingInspect.setOnClickListener {

            val bitmap =
                lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 Forming 사진을 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedFormingRoi(
                    bitmap
                )
            }
        }


        /*
         * ROI 가로 -
         */
        binding.btnFormingRoiWidthSmaller.setOnClickListener {

            resizeRoiWidth(
                0.85f
            )
        }


        /*
         * ROI 가로 +
         */
        binding.btnFormingRoiWidthLarger.setOnClickListener {

            resizeRoiWidth(
                1.15f
            )
        }


        /*
         * ROI 세로 -
         */
        binding.btnFormingRoiHeightSmaller.setOnClickListener {

            resizeRoiHeight(
                0.85f
            )
        }


        /*
         * ROI 세로 +
         */
        binding.btnFormingRoiHeightLarger.setOnClickListener {

            resizeRoiHeight(
                1.15f
            )
        }


        /*
         * ROI 중앙
         */
        binding.btnFormingRoiReset.setOnClickListener {

            resetRoiPosition()
        }


        /*
         * 사진 원래크기
         */
        binding.btnFormingImageReset.setOnClickListener {

            resetImageMatrix()
        }


        /*
         * 메인 메뉴 복귀
         */
        binding.btnFormingBack.setOnClickListener {

            finish()
        }
    }


    /*
     * =========================================================
     * 이미지 확대 / 축소 / 이동
     * =========================================================
     */
    private fun setupImageZoom() {

        scaleGestureDetector =
            ScaleGestureDetector(
                this,
                object :
                    ScaleGestureDetector
                        .SimpleOnScaleGestureListener() {

                    override fun onScale(
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val oldZoom =
                            zoomFactor

                        val newZoom =
                            (
                                zoomFactor *
                                        detector.scaleFactor
                                ).coerceIn(
                                1f,
                                8f
                            )

                        val actualScale =
                            newZoom /
                                    oldZoom

                        zoomFactor =
                            newZoom


                        imageMatrixValue.postScale(
                            actualScale,
                            actualScale,
                            detector.focusX,
                            detector.focusY
                        )


                        binding
                            .formingImagePreview
                            .imageMatrix =
                            imageMatrixValue


                        return true
                    }
                }
            )


        binding.formingImagePreview.setOnTouchListener {
                view,
                event ->


            view.parent
                ?.requestDisallowInterceptTouchEvent(
                    true
                )


            scaleGestureDetector.onTouchEvent(
                event
            )


            when (
                event.actionMasked
            ) {

                MotionEvent.ACTION_DOWN -> {

                    lastImageTouchX =
                        event.x

                    lastImageTouchY =
                        event.y

                    true
                }


                MotionEvent.ACTION_MOVE -> {

                    if (
                        !scaleGestureDetector.isInProgress &&
                        event.pointerCount == 1 &&
                        zoomFactor > 1f
                    ) {

                        val dx =
                            event.x -
                                    lastImageTouchX

                        val dy =
                            event.y -
                                    lastImageTouchY


                        imageMatrixValue.postTranslate(
                            dx,
                            dy
                        )


                        binding
                            .formingImagePreview
                            .imageMatrix =
                            imageMatrixValue
                    }


                    lastImageTouchX =
                        event.x

                    lastImageTouchY =
                        event.y


                    true
                }


                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    view.parent
                        ?.requestDisallowInterceptTouchEvent(
                            false
                        )

                    true
                }


                else ->
                    true
            }
        }
    }


    /*
     * =========================================================
     * 사진 원래크기
     * =========================================================
     */
    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return


        binding.formingImagePreview.post {

            val viewWidth =
                binding
                    .formingImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding
                    .formingImagePreview
                    .height
                    .toFloat()


            if (
                viewWidth <= 0f ||
                viewHeight <= 0f
            ) {

                return@post
            }


            val bitmapWidth =
                bitmap.width.toFloat()

            val bitmapHeight =
                bitmap.height.toFloat()


            val baseScale =
                minOf(
                    viewWidth /
                            bitmapWidth,

                    viewHeight /
                            bitmapHeight
                )


            val displayedWidth =
                bitmapWidth *
                        baseScale

            val displayedHeight =
                bitmapHeight *
                        baseScale


            val dx =
                (
                    viewWidth -
                            displayedWidth
                    ) / 2f

            val dy =
                (
                    viewHeight -
                            displayedHeight
                    ) / 2f


            imageMatrixValue.reset()


            imageMatrixValue.postScale(
                baseScale,
                baseScale
            )


            imageMatrixValue.postTranslate(
                dx,
                dy
            )


            zoomFactor =
                1f


            binding
                .formingImagePreview
                .imageMatrix =
                imageMatrixValue
        }
    }


    /*
     * =========================================================
     * 갤러리 사진 읽기
     * =========================================================
     */
    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvFormingStatus.text =
            "사진 불러오는 중..."


        try {

            val bitmap =
                decodeBitmapFromUri(
                    uri
                )


            if (bitmap == null) {

                binding.tvFormingStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }


            lastBitmap =
                bitmap


            binding
                .formingImagePreview
                .setImageBitmap(
                    bitmap
                )


            resetImageMatrix()

            resetRoiPosition()


            binding.tvFormingStatus.text =
                "사진 선택 완료 - Forming 검사 영역에 ROI를 맞춰주세요."


            binding.tvFormingMetrics.text =
                """
Shape Uniformity : -
Wrinkle Level   : -
Deformation     : -
Symmetry Error  : -
Forming Score   : -

판정 : -
                """.trimIndent()


        } catch (e: Exception) {

            binding.tvFormingStatus.text =
                "사진 불러오기 오류: ${e.message}"
        }
    }


    /*
     * =========================================================
     * 고해상도 사진 메모리 보호
     * =========================================================
     */
    private fun decodeBitmapFromUri(
        uri: Uri
    ): Bitmap? {

        val options =
            BitmapFactory.Options()


        options.inJustDecodeBounds =
            true


        contentResolver
            .openInputStream(uri)
            ?.use {

                BitmapFactory.decodeStream(
                    it,
                    null,
                    options
                )
            }


        val maxSize =
            max(
                options.outWidth,
                options.outHeight
            )


        var sampleSize =
            1


        while (
            maxSize /
                    sampleSize >
            1600
        ) {

            sampleSize *= 2
        }


        val decodeOptions =
            BitmapFactory.Options()


        decodeOptions.inSampleSize =
            sampleSize


        return contentResolver
            .openInputStream(uri)
            ?.use {

                BitmapFactory.decodeStream(
                    it,
                    null,
                    decodeOptions
                )
            }
    }


    /*
     * =========================================================
     * ROI 손가락 이동
     * =========================================================
     */
    private fun setupRoiDrag() {

        binding.formingRoiGuide.setOnTouchListener {
                view,
                event ->


            view.parent
                ?.requestDisallowInterceptTouchEvent(
                    true
                )


            when (
                event.action
            ) {

                MotionEvent.ACTION_DOWN -> {

                    roiLastTouchX =
                        event.rawX

                    roiLastTouchY =
                        event.rawY

                    true
                }


                MotionEvent.ACTION_MOVE -> {

                    val dx =
                        event.rawX -
                                roiLastTouchX

                    val dy =
                        event.rawY -
                                roiLastTouchY


                    var newX =
                        view.x +
                                dx

                    var newY =
                        view.y +
                                dy


                    val parent =
                        binding.formingImageArea


                    val maxX =
                        parent.width -
                                view.width

                    val maxY =
                        parent.height -
                                view.height


                    newX =
                        newX.coerceIn(
                            0f,
                            maxX.toFloat()
                        )

                    newY =
                        newY.coerceIn(
                            0f,
                            maxY.toFloat()
                        )


                    view.x =
                        newX

                    view.y =
                        newY


                    roiLastTouchX =
                        event.rawX

                    roiLastTouchY =
                        event.rawY


                    true
                }


                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    view.parent
                        ?.requestDisallowInterceptTouchEvent(
                            false
                        )

                    true
                }


                else ->
                    true
            }
        }
    }


    /*
     * =========================================================
     * ROI 가로 조절
     * =========================================================
     */
    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.formingRoiGuide

        val parent =
            binding.formingImageArea


        if (
            parent.width <= 0
        ) {

            return
        }


        var newWidth =
            (
                roi.width *
                        scale
                ).toInt()


        newWidth =
            newWidth.coerceIn(
                80,
                max(
                    80,
                    (
                        parent.width *
                                0.95f
                        ).toInt()
                )
            )


        val centerX =
            roi.x +
                    roi.width /
                            2f


        val params =
            roi.layoutParams


        params.width =
            newWidth


        roi.layoutParams =
            params


        roi.post {

            var newX =
                centerX -
                        roi.width /
                                2f


            newX =
                newX.coerceIn(
                    0f,
                    (
                        parent.width -
                                roi.width
                        )
                        .coerceAtLeast(
                            0
                        )
                        .toFloat()
                )


            roi.x =
                newX
        }
    }


    /*
     * =========================================================
     * ROI 세로 조절
     * =========================================================
     */
    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.formingRoiGuide

        val parent =
            binding.formingImageArea


        if (
            parent.height <= 0
        ) {

            return
        }


        var newHeight =
            (
                roi.height *
                        scale
                ).toInt()


        newHeight =
            newHeight.coerceIn(
                60,
                max(
                    60,
                    (
                        parent.height *
                                0.95f
                        ).toInt()
                )
            )


        val centerY =
            roi.y +
                    roi.height /
                            2f


        val params =
            roi.layoutParams


        params.height =
            newHeight


        roi.layoutParams =
            params


        roi.post {

            var newY =
                centerY -
                        roi.height /
                                2f


            newY =
                newY.coerceIn(
                    0f,
                    (
                        parent.height -
                                roi.height
                        )
                        .coerceAtLeast(
                            0
                        )
                        .toFloat()
                )


            roi.y =
                newY
        }
    }


    /*
     * =========================================================
     * ROI 중앙
     * =========================================================
     */
    private fun resetRoiPosition() {

        binding.formingImageArea.post {

            val roi =
                binding.formingRoiGuide

            val parent =
                binding.formingImageArea


            roi.x =
                (
                    parent.width -
                            roi.width
                    ) / 2f


            roi.y =
                (
                    parent.height -
                            roi.height
                    ) / 2f
        }
    }


    /*
     * =========================================================
     * ROI 화면 좌표 → 실제 Bitmap 좌표
     * =========================================================
     */
    private fun analyzeSelectedFormingRoi(
        source: Bitmap
    ) {

        binding.tvFormingStatus.text =
            "Forming ROI 분석 중..."


        val screenRect =
            RectF(
                binding.formingRoiGuide.x,
                binding.formingRoiGuide.y,
                binding.formingRoiGuide.x +
                        binding.formingRoiGuide.width,
                binding.formingRoiGuide.y +
                        binding.formingRoiGuide.height
            )


        val currentMatrix =
            Matrix(
                binding
                    .formingImagePreview
                    .imageMatrix
            )


        Thread {

            try {

                val inverse =
                    Matrix()


                if (
                    !currentMatrix.invert(
                        inverse
                    )
                ) {

                    throw Exception(
                        "이미지 좌표 변환 실패"
                    )
                }


                val bitmapRect =
                    RectF(
                        screenRect
                    )


                inverse.mapRect(
                    bitmapRect
                )


                var left =
                    bitmapRect.left
                        .toInt()

                var top =
                    bitmapRect.top
                        .toInt()

                var right =
                    bitmapRect.right
                        .toInt()

                var bottom =
                    bitmapRect.bottom
                        .toInt()


                left =
                    left.coerceIn(
                        0,
                        source.width - 1
                    )


                top =
                    top.coerceIn(
                        0,
                        source.height - 1
                    )


                right =
                    right.coerceIn(
                        left + 1,
                        source.width
                    )


                bottom =
                    bottom.coerceIn(
                        top + 1,
                        source.height
                    )


                val roiWidth =
                    right -
                            left

                val roiHeight =
                    bottom -
                            top


                if (
                    roiWidth < 10 ||
                    roiHeight < 10
                ) {

                    throw Exception(
                        "ROI를 사진 안쪽에 맞춰주세요."
                    )
                }


                val roiBitmap =
                    Bitmap.createBitmap(
                        source,
                        left,
                        top,
                        roiWidth,
                        roiHeight
                    )


                analyzeFormingBitmap(
                    source,
                    roiBitmap,
                    left,
                    top
                )


            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvFormingStatus.text =
                        "Forming 분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    /*
     * =========================================================
     * Forming 분석
     * =========================================================
     */
    private fun analyzeFormingBitmap(

        source: Bitmap,

        roi: Bitmap,

        roiStartX: Int,

        roiStartY: Int

    ) {

        val analysisWidth =
            320

        val analysisHeight =
            240


        val small =
            Bitmap.createScaledBitmap(
                roi,
                analysisWidth,
                analysisHeight,
                true
            )


        var edgeCount =
            0L

        var strongEdgeCount =
            0L

        var totalStrength =
            0L

        var pixelCount =
            0L


        /*
         * 좌우 대칭 비교
         */
        var symmetryDifference =
            0L

        var symmetryCount =
            0L


        val markedBitmap =
            source.copy(
                Bitmap.Config.ARGB_8888,
                true
            )


        val canvas =
            Canvas(
                markedBitmap
            )


        val redPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.RED

                style =
                    Paint.Style.FILL

                alpha =
                    220
            }


        val xScale =
            roi.width.toFloat() /
                    analysisWidth.toFloat()


        val yScale =
            roi.height.toFloat() /
                    analysisHeight.toFloat()


        val markRadius =
            max(
                2f,
                roi.width.toFloat() /
                        180f
            )


        /*
         * Edge / Wrinkle / Deformation 분석
         */
        for (
            y in 1 until
            small.height - 1
        ) {

            for (
                x in 1 until
                small.width - 1
            ) {

                val center =
                    gray(
                        small.getPixel(
                            x,
                            y
                        )
                    )


                val right =
                    gray(
                        small.getPixel(
                            x + 1,
                            y
                        )
                    )


                val bottom =
                    gray(
                        small.getPixel(
                            x,
                            y + 1
                        )
                    )


                val gradient =
                    abs(
                        center -
                                right
                    ) +
                            abs(
                                center -
                                        bottom
                            )


                totalStrength +=
                    gradient

                pixelCount++


                if (
                    gradient > 40
                ) {

                    edgeCount++
                }


                if (
                    gradient > 75
                ) {

                    strongEdgeCount++


                    /*
                     * 너무 많은 점이 표시되지 않도록
                     * 일부만 표시
                     */
                    if (
                        x % 5 == 0 &&
                        y % 5 == 0
                    ) {

                        val originalX =
                            roiStartX +
                                    (
                                        x *
                                                xScale
                                        ).toInt()


                        val originalY =
                            roiStartY +
                                    (
                                        y *
                                                yScale
                                        ).toInt()


                        canvas.drawCircle(
                            originalX.toFloat(),
                            originalY.toFloat(),
                            markRadius,
                            redPaint
                        )
                    }
                }
            }
        }


        /*
         * =====================================================
         * 좌우 대칭 비교
         * =====================================================
         */
        val halfWidth =
            small.width /
                    2


        for (
            y in 0 until
            small.height
        ) {

            for (
                x in 0 until
                halfWidth
            ) {

                val oppositeX =
                    small.width -
                            1 -
                            x


                val leftGray =
                    gray(
                        small.getPixel(
                            x,
                            y
                        )
                    )


                val rightGray =
                    gray(
                        small.getPixel(
                            oppositeX,
                            y
                        )
                    )


                symmetryDifference +=
                    abs(
                        leftGray -
                                rightGray
                    )


                symmetryCount++
            }
        }


        /*
         * =====================================================
         * 지표 계산
         * =====================================================
         */
        val edgeDensity =
            if (
                pixelCount > 0
            ) {

                edgeCount.toDouble() /
                        pixelCount.toDouble() *
                        100.0

            } else {

                0.0
            }


        val strongEdgeDensity =
            if (
                pixelCount > 0
            ) {

                strongEdgeCount.toDouble() /
                        pixelCount.toDouble() *
                        100.0

            } else {

                0.0
            }


        val averageStrength =
            if (
                pixelCount > 0
            ) {

                totalStrength.toDouble() /
                        pixelCount.toDouble()

            } else {

                0.0
            }


        val symmetryError =
            if (
                symmetryCount > 0
            ) {

                symmetryDifference.toDouble() /
                        symmetryCount.toDouble()

            } else {

                0.0
            }


        /*
         * Shape Error
         *
         * 값이 높을수록
         * 형상 불균일 후보
         */
        val shapeError =
            (
                edgeDensity *
                        1.1 +
                        averageStrength *
                        0.45
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * Wrinkle
         */
        val wrinkleScore =
            (
                strongEdgeDensity *
                        4.0 +
                        edgeDensity *
                        0.7
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * Deformation
         */
        val deformationScore =
            (
                averageStrength *
                        0.9 +
                        strongEdgeDensity *
                        2.5
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * Symmetry Error
         */
        val normalizedSymmetryError =
            (
                symmetryError *
                        1.8
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * 형상 균일도
         * 100 = 양호 방향
         */
        val shapeUniformity =
            (
                100.0 -
                        shapeError
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * Forming 종합 Defect Score
         */
        val defectScore =
            (
                shapeError *
                        0.30 +
                        wrinkleScore *
                        0.25 +
                        deformationScore *
                        0.25 +
                        normalizedSymmetryError *
                        0.20
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * Forming Score
         *
         * 100 = 양호 방향
         */
        val formingScore =
            (
                100.0 -
                        defectScore
                ).coerceIn(
                0.0,
                100.0
            )


        /*
         * 텍스트 판정
         */
        val wrinkleText =
            when {

                wrinkleScore < 20 ->
                    "낮음"

                wrinkleScore < 40 ->
                    "주의"

                wrinkleScore < 60 ->
                    "높음"

                else ->
                    "매우 높음"
            }


        val deformationText =
            when {

                deformationScore < 20 ->
                    "낮음"

                deformationScore < 40 ->
                    "주의"

                deformationScore < 60 ->
                    "높음"

                else ->
                    "매우 높음"
            }


        val symmetryText =
            when {

                normalizedSymmetryError < 20 ->
                    "양호"

                normalizedSymmetryError < 40 ->
                    "주의"

                normalizedSymmetryError < 60 ->
                    "한계"

                else ->
                    "비대칭 후보"
            }


        val judgment =
            when {

                formingScore >= 85 ->
                    "정상 후보"

                formingScore >= 70 ->
                    "주의 후보"

                formingScore >= 50 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }


        runOnUiThread {

            binding
                .formingImagePreview
                .setImageBitmap(
                    markedBitmap
                )


            /*
             * 확대 위치 유지
             */
            binding
                .formingImagePreview
                .imageMatrix =
                imageMatrixValue


            binding.tvFormingStatus.text =
                "Forming ROI 분석 완료"


            binding.tvFormingMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
Shape Uniformity : %.1f / 100
Wrinkle Level   : %s  (%.1f)
Deformation     : %s  (%.1f)
Symmetry Error  : %s  (%.1f)
Forming Score   : %.1f / 100

판정 : %s

빨간 표시 : 국부 형상 이상 후보
※ 현재는 기준 학습 전 임시 판정
                    """.trimIndent(),

                    shapeUniformity,

                    wrinkleText,
                    wrinkleScore,

                    deformationText,
                    deformationScore,

                    symmetryText,
                    normalizedSymmetryError,

                    formingScore,

                    judgment
                )
        }
    }


    /*
     * RGB → Gray
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
            0.299 * r +
                    0.587 * g +
                    0.114 * b
            ).toInt()
    }
}
