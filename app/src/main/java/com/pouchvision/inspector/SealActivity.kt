package com.pouchvision.inspector

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivitySealBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.btnSealGallery.setOnClickListener {

            galleryLauncher.launch("image/*")
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

                analyzeSelectedSealRoi(bitmap)
            }
        }

        binding.btnSealRoiSmaller.setOnClickListener {

            resizeRoi(0.85f)
        }

        binding.btnSealRoiLarger.setOnClickListener {

            resizeRoi(1.15f)
        }

        binding.btnSealRoiReset.setOnClickListener {

            resetRoiPosition()
        }

        binding.btnSealBack.setOnClickListener {

            finish()
        }

        setupRoiDrag()
    }

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvSealStatus.text =
            "사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(uri)

            if (bitmap == null) {

                binding.tvSealStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            lastBitmap =
                bitmap

            binding.sealImagePreview.setImageBitmap(
                bitmap
            )

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
            maxSize / sampleSize >
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

            when (event.action) {

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
                        view.x + dx

                    var newY =
                        view.y + dy

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

                else ->
                    true
            }
        }
    }

    private fun resizeRoi(
        scale: Float
    ) {

        val view =
            binding.sealRoiGuide

        val parent =
            binding.sealImageArea

        if (
            parent.width <= 0 ||
            parent.height <= 0
        ) {

            return
        }

        var newWidth =
            (
                view.width *
                        scale
                ).toInt()

        var newHeight =
            (
                view.height *
                        scale
                ).toInt()

        newWidth =
            newWidth.coerceIn(
                120,
                max(
                    120,
                    (parent.width * 0.95f)
                        .toInt()
                )
            )

        newHeight =
            newHeight.coerceIn(
                50,
                max(
                    50,
                    (parent.height * 0.60f)
                        .toInt()
                )
            )

        val params =
            view.layoutParams

        params.width =
            newWidth

        params.height =
            newHeight

        view.layoutParams =
            params

        resetRoiPosition()
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

        val imageViewWidth =
            binding.sealImagePreview.width
                .toFloat()

        val imageViewHeight =
            binding.sealImagePreview.height
                .toFloat()

        val roiLeft =
            binding.sealRoiGuide.x

        val roiTop =
            binding.sealRoiGuide.y

        val roiRight =
            roiLeft +
                    binding.sealRoiGuide.width

        val roiBottom =
            roiTop +
                    binding.sealRoiGuide.height

        binding.tvSealStatus.text =
            "Seal ROI 분석 중..."

        Thread {

            try {

                val bitmapWidth =
                    source.width.toFloat()

                val bitmapHeight =
                    source.height.toFloat()

                val scale =
                    minOf(
                        imageViewWidth /
                                bitmapWidth,

                        imageViewHeight /
                                bitmapHeight
                    )

                val displayedWidth =
                    bitmapWidth *
                            scale

                val displayedHeight =
                    bitmapHeight *
                            scale

                val offsetX =
                    (
                        imageViewWidth -
                                displayedWidth
                        ) / 2f

                val offsetY =
                    (
                        imageViewHeight -
                                displayedHeight
                        ) / 2f

                var bitmapLeft =
                    (
                        (
                            roiLeft -
                                    offsetX
                            ) /
                                scale
                        ).toInt()

                var bitmapTop =
                    (
                        (
                            roiTop -
                                    offsetY
                            ) /
                                scale
                        ).toInt()

                var bitmapRight =
                    (
                        (
                            roiRight -
                                    offsetX
                            ) /
                                scale
                        ).toInt()

                var bitmapBottom =
                    (
                        (
                            roiBottom -
                                    offsetY
                            ) /
                                scale
                        ).toInt()

                bitmapLeft =
                    bitmapLeft.coerceIn(
                        0,
                        source.width - 1
                    )

                bitmapTop =
                    bitmapTop.coerceIn(
                        0,
                        source.height - 1
                    )

                bitmapRight =
                    bitmapRight.coerceIn(
                        bitmapLeft + 1,
                        source.width
                    )

                bitmapBottom =
                    bitmapBottom.coerceIn(
                        bitmapTop + 1,
                        source.height
                    )

                val roiBitmap =
                    Bitmap.createBitmap(
                        source,
                        bitmapLeft,
                        bitmapTop,
                        bitmapRight - bitmapLeft,
                        bitmapBottom - bitmapTop
                    )

                analyzeSealBitmap(
                    source,
                    roiBitmap,
                    bitmapLeft,
                    bitmapTop
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

        var totalStrength =
            0L

        var irregularCount =
            0L

        var verticalChange =
            0L

        var horizontalChange =
            0L

        var pixelCount =
            0L

        val markedBitmap =
            source.copy(
                Bitmap.Config.ARGB_8888,
                true
            )

        val canvas =
            Canvas(markedBitmap)

        val redPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                color =
                    Color.RED

                style =
                    Paint.Style.FILL

                alpha =
                    210
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

                /*
                 * 강한 국부 변화
                 * → Wrinkle/변형 후보
                 */
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

        /*
         * 직진성 지표
         *
         * 값이 높을수록
         * 불규칙 변화가 많다고 해석
         */
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

        /*
         * Wrinkle 수준
         */
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

        /*
         * 방향성 변화 차이를 이용한
         * 변형 수준
         */
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

        /*
         * Seal 종합 점수
         *
         * 100 = 양호 방향
         */
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

            binding.sealImagePreview.setImageBitmap(
                markedBitmap
            )

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
            Color.red(color)

        val g =
            Color.green(color)

        val b =
            Color.blue(color)

        return (
            0.299 * r +
                    0.587 * g +
                    0.114 * b
            ).toInt()
    }
}
