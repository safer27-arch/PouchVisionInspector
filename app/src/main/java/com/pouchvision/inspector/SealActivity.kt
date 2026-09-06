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
import com.pouchvision.inspector.databinding.ActivitySealBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SealActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySealBinding

    private var lastBitmap: Bitmap? = null

    private val imageMatrixValue = Matrix()

    private var zoomFactor = 1f

    private var lastImageTouchX = 0f
    private var lastImageTouchY = 0f

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var roiLastTouchX = 0f
    private var roiLastTouchY = 0f


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
            ActivitySealBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        setupImageZoom()

        setupRoiDrag()


        binding.btnSealGallery.setOnClickListener {

            galleryLauncher.launch(
                "image/*"
            )
        }


        binding.btnSealInspect.setOnClickListener {

            val bitmap =
                lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 Seal 사진을 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedSealRoi(
                    bitmap
                )
            }
        }


        binding.btnSealRoiWidthSmaller.setOnClickListener {

            resizeRoiWidth(
                0.85f
            )
        }


        binding.btnSealRoiWidthLarger.setOnClickListener {

            resizeRoiWidth(
                1.15f
            )
        }


        binding.btnSealRoiHeightSmaller.setOnClickListener {

            resizeRoiHeight(
                0.85f
            )
        }


        binding.btnSealRoiHeightLarger.setOnClickListener {

            resizeRoiHeight(
                1.15f
            )
        }


        binding.btnSealRoiReset.setOnClickListener {

            resetRoiPosition()
        }


        binding.btnSealImageReset.setOnClickListener {

            resetImageMatrix()
        }


        binding.btnSealBack.setOnClickListener {

            finish()
        }
    }


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
                            .sealImagePreview
                            .imageMatrix =
                            imageMatrixValue


                        return true
                    }
                }
            )


        binding.sealImagePreview.setOnTouchListener {
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
                        !scaleGestureDetector
                            .isInProgress &&
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
                            .sealImagePreview
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


    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return


        binding.sealImagePreview.post {

            val viewWidth =
                binding
                    .sealImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding
                    .sealImagePreview
                    .height
                    .toFloat()


            if (
                viewWidth <= 0f ||
                viewHeight <= 0f
            ) {

                return@post
            }


            val bitmapWidth =
                bitmap.width
                    .toFloat()

            val bitmapHeight =
                bitmap.height
                    .toFloat()


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
                .sealImagePreview
                .imageMatrix =
                imageMatrixValue
        }
    }


    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvSealStatus.text =
            "사진 불러오는 중..."


        try {

            val bitmap =
                decodeBitmapFromUri(
                    uri
                )


            if (bitmap == null) {

                binding.tvSealStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }


            lastBitmap =
                bitmap


            binding
                .sealImagePreview
                .setImageBitmap(
                    bitmap
                )


            resetImageMatrix()

            resetRoiPosition()


            binding.tvSealStatus.text =
                "사진 선택 완료 - Seal 영역에 ROI를 맞춰주세요."


            binding.tvSealMetrics.text =
                """
Seal Width       : -
Straightness    : -
Wrinkle Level   : -
Deformation     : -
Seal Score      : -

판정 : -
                """.trimIndent()


        } catch (e: Exception) {

            binding.tvSealStatus.text =
                "사진 불러오기 오류: ${e.message}"
        }
    }


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


    private fun setupRoiDrag() {

        binding.sealRoiGuide.setOnTouchListener {
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
                        binding.sealImageArea


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


    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.sealRoiGuide

        val parent =
            binding.sealImageArea


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


    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.sealRoiGuide

        val parent =
            binding.sealImageArea


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
                40,
                max(
                    40,
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


    private fun resetRoiPosition() {

        binding.sealImageArea.post {

            val roi =
                binding.sealRoiGuide

            val parent =
                binding.sealImageArea


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


    private fun analyzeSelectedSealRoi(
        source: Bitmap
    ) {

        binding.tvSealStatus.text =
            "Seal ROI 분석 중..."


        val screenRect =
            RectF(
                binding.sealRoiGuide.x,
                binding.sealRoiGuide.y,
                binding.sealRoiGuide.x +
                        binding.sealRoiGuide.width,
                binding.sealRoiGuide.y +
                        binding.sealRoiGuide.height
            )


        val currentMatrix =
            Matrix(
                binding
                    .sealImagePreview
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


                analyzeSealBitmap(
                    source,
                    roiBitmap,
                    left,
                    top
                )


            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvSealStatus.text =
                        "Seal 분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    private fun analyzeSealBitmap(

        source: Bitmap,

        roi: Bitmap,

        roiStartX: Int,

        roiStartY: Int

    ) {

        val analysisWidth =
            360

        val analysisHeight =
            120


        val small =
            Bitmap.createScaledBitmap(
                roi,
                analysisWidth,
                analysisHeight,
                true
            )


        var edgeCount =
            0L

        var irregularCount =
            0L

        var totalStrength =
            0L

        var horizontalChange =
            0L

        var verticalChange =
            0L

        var pixelCount =
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
                        200f
            )


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


                val horizontalGradient =
                    abs(
                        center -
                                right
                    )


                val verticalGradient =
                    abs(
                        center -
                                bottom
                    )


                val gradient =
                    horizontalGradient +
                            verticalGradient


                totalStrength +=
                    gradient

                horizontalChange +=
                    horizontalGradient

                verticalChange +=
                    verticalGradient

                pixelCount++


                if (
                    gradient > 40
                ) {

                    edgeCount++
                }


                if (
                    gradient > 75
                ) {

                    irregularCount++


                    if (
                        x % 5 == 0 &&
                        y % 4 == 0
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


        val irregularDensity =
            if (
                pixelCount > 0
            ) {

                irregularCount.toDouble() /
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


        val straightnessError =
            (
                irregularDensity *
                        2.5 +
                        averageStrength *
                        0.25
                ).coerceIn(
                0.0,
                100.0
            )


        val wrinkleScore =
            (
                irregularDensity *
                        4.0 +
                        edgeDensity *
                        0.8
                ).coerceIn(
                0.0,
                100.0
            )


        val directionalDifference =
            if (
                pixelCount > 0
            ) {

                abs(
                    horizontalChange -
                            verticalChange
                ).toDouble() /
                        pixelCount.toDouble()

            } else {

                0.0
            }


        val deformationScore =
            (
                directionalDifference *
                        0.7 +
                        irregularDensity *
                        2.0
                ).coerceIn(
                0.0,
                100.0
            )


        val defectScore =
            (
                straightnessError *
                        0.35 +
                        wrinkleScore *
                        0.40 +
                        deformationScore *
                        0.25
                ).coerceIn(
                0.0,
                100.0
            )


        val sealScore =
            (
                100.0 -
                        defectScore
                ).coerceIn(
                0.0,
                100.0
            )


        val straightnessText =
            when {

                straightnessError < 20 ->
                    "양호"

                straightnessError < 40 ->
                    "주의"

                straightnessError < 60 ->
                    "한계"

                else ->
                    "이상 후보"
            }


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


        val judgment =
            when {

                sealScore >= 85 ->
                    "정상 후보"

                sealScore >= 70 ->
                    "주의 후보"

                sealScore >= 50 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }


        runOnUiThread {

            binding
                .sealImagePreview
                .setImageBitmap(
                    markedBitmap
                )


            binding
                .sealImagePreview
                .imageMatrix =
                imageMatrixValue


            binding.tvSealStatus.text =
                "Seal ROI 분석 완료"


            binding.tvSealMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
Seal Width       : 기준 설정 필요
Straightness    : %s  (%.1f)
Wrinkle Level   : %s  (%.1f)
Deformation     : %s  (%.1f)
Seal Score      : %.1f / 100

판정 : %s

빨간 표시 : 국부 이상 후보
※ 현재는 기준 학습 전 임시 판정
                    """.trimIndent(),

                    straightnessText,
                    straightnessError,

                    wrinkleText,
                    wrinkleScore,

                    deformationText,
                    deformationScore,

                    sealScore,

                    judgment
                )
        }
    }


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
