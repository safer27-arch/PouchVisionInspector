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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityTabBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class TabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTabBinding

    private var lastBitmap: Bitmap? = null

    /*
     * =========================================================
     * TAB 민감도
     * =========================================================
     */

    private val preferenceName =
        "pouch_vision_settings"

    private val sensitivityKey =
        "tab_sensitivity"

    private val defaultSensitivity =
        60

    private var sensitivity =
        defaultSensitivity


    /*
     * =========================================================
     * 이미지 확대 / 이동
     * =========================================================
     */

    private val imageMatrixValue =
        Matrix()

    private var zoomFactor =
        1f

    private var lastImageTouchX =
        0f

    private var lastImageTouchY =
        0f

    private lateinit var scaleGestureDetector:
            ScaleGestureDetector


    /*
     * =========================================================
     * ROI 이동
     * =========================================================
     */

    private var roiLastTouchX =
        0f

    private var roiLastTouchY =
        0f


    /*
     * =========================================================
     * 갤러리
     * =========================================================
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
            ActivityTabBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()


        binding.btnTabGallery.setOnClickListener {

            galleryLauncher.launch(
                "image/*"
            )
        }


        binding.btnTabInspect.setOnClickListener {

            val bitmap =
                lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 TAB 사진을 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedTabRoi(
                    bitmap
                )
            }
        }


        binding.btnTabRoiWidthSmaller.setOnClickListener {

            resizeRoiWidth(
                0.85f
            )
        }


        binding.btnTabRoiWidthLarger.setOnClickListener {

            resizeRoiWidth(
                1.15f
            )
        }


        binding.btnTabRoiHeightSmaller.setOnClickListener {

            resizeRoiHeight(
                0.85f
            )
        }


        binding.btnTabRoiHeightLarger.setOnClickListener {

            resizeRoiHeight(
                1.15f
            )
        }


        binding.btnTabRoiReset.setOnClickListener {

            resetRoiPosition()
        }


        binding.btnTabImageReset.setOnClickListener {

            resetImageMatrix()
        }


        binding.btnTabBack.setOnClickListener {

            finish()
        }
    }


    /*
     * =========================================================
     * 민감도 설정
     * =========================================================
     */

    private fun setupSensitivity() {

        val prefs =
            getSharedPreferences(
                preferenceName,
                MODE_PRIVATE
            )


        sensitivity =
            prefs.getInt(
                sensitivityKey,
                defaultSensitivity
            ).coerceIn(
                0,
                100
            )


        binding.seekTabSensitivity.progress =
            sensitivity


        updateSensitivityText()


        binding.seekTabSensitivity
            .setOnSeekBarChangeListener(

                object :
                    SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        sensitivity =
                            progress


                        updateSensitivityText()


                        if (fromUser) {

                            prefs.edit()
                                .putInt(
                                    sensitivityKey,
                                    sensitivity
                                )
                                .apply()
                        }
                    }


                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {
                    }


                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {

                        prefs.edit()
                            .putInt(
                                sensitivityKey,
                                sensitivity
                            )
                            .apply()
                    }
                }
            )


        binding.btnTabSensitivityReset
            .setOnClickListener {

                sensitivity =
                    defaultSensitivity


                binding.seekTabSensitivity.progress =
                    defaultSensitivity


                prefs.edit()
                    .putInt(
                        sensitivityKey,
                        defaultSensitivity
                    )
                    .apply()


                updateSensitivityText()


                Toast.makeText(
                    this,
                    "TAB 민감도를 60%로 복원했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    private fun updateSensitivityText() {

        binding.tvTabSensitivityValue.text =
            "현재 민감도 : ${sensitivity}%"
    }


    /*
     * =========================================================
     * 이미지 확대 / 이동
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


                        binding.tabImagePreview.imageMatrix =
                            imageMatrixValue


                        return true
                    }
                }
            )


        binding.tabImagePreview
            .setOnTouchListener { view, event ->

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


                            binding.tabImagePreview.imageMatrix =
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
     * 이미지 원래크기
     * =========================================================
     */

    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return


        binding.tabImagePreview.post {

            val viewWidth =
                binding
                    .tabImagePreview
                    .width
                    .toFloat()


            val viewHeight =
                binding
                    .tabImagePreview
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


            binding.tabImagePreview.imageMatrix =
                imageMatrixValue
        }
    }


    /*
     * =========================================================
     * 갤러리 이미지
     * =========================================================
     */

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvTabStatus.text =
            "TAB 사진 불러오는 중..."


        try {

            val bitmap =
                decodeBitmapFromUri(
                    uri
                )


            if (bitmap == null) {

                binding.tvTabStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }


            lastBitmap =
                bitmap


            binding.tabImagePreview.setImageBitmap(
                bitmap
            )


            resetImageMatrix()

            resetRoiPosition()


            binding.tvTabStatus.text =
                "사진 선택 완료 - TAB 검사 영역에 ROI를 맞춰주세요."


            binding.tvTabMetrics.text =
                """
Tab Position      : -
Tab Tilt          : -
Spacing Balance   : -
Local Deformation : -
Tab Score         : -

판정 : -
                """.trimIndent()


        } catch (e: Exception) {

            binding.tvTabStatus.text =
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


    /*
     * =========================================================
     * ROI 이동
     * =========================================================
     */

    private fun setupRoiDrag() {

        binding.tabRoiGuide
            .setOnTouchListener { view, event ->

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
                            binding.tabImageArea


                        val maxX =
                            parent.width -
                                    view.width


                        val maxY =
                            parent.height -
                                    view.height


                        newX =
                            newX.coerceIn(
                                0f,
                                maxX
                                    .coerceAtLeast(0)
                                    .toFloat()
                            )


                        newY =
                            newY.coerceIn(
                                0f,
                                maxY
                                    .coerceAtLeast(0)
                                    .toFloat()
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
     * ROI 가로
     * =========================================================
     */

    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.tabRoiGuide


        val parent =
            binding.tabImageArea


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
                        .coerceAtLeast(0)
                        .toFloat()
                )


            roi.x =
                newX
        }
    }


    /*
     * =========================================================
     * ROI 세로
     * =========================================================
     */

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.tabRoiGuide


        val parent =
            binding.tabImageArea


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
                        .coerceAtLeast(0)
                        .toFloat()
                )


            roi.y =
                newY
        }
    }


    private fun resetRoiPosition() {

        binding.tabImageArea.post {

            val roi =
                binding.tabRoiGuide


            val parent =
                binding.tabImageArea


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
     * ROI 좌표 변환
     * =========================================================
     */

    private fun analyzeSelectedTabRoi(
        source: Bitmap
    ) {

        binding.tvTabStatus.text =
            "TAB ROI 분석 중..."


        val screenRect =
            RectF(
                binding.tabRoiGuide.x,
                binding.tabRoiGuide.y,
                binding.tabRoiGuide.x +
                        binding.tabRoiGuide.width,
                binding.tabRoiGuide.y +
                        binding.tabRoiGuide.height
            )


        val currentMatrix =
            Matrix(
                binding.tabImagePreview.imageMatrix
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


                analyzeTabBitmap(
                    source,
                    roiBitmap,
                    left,
                    top
                )


            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvTabStatus.text =
                        "TAB 분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    /*
     * =========================================================
     * TAB 분석
     * =========================================================
     */

    private fun analyzeTabBitmap(
        source: Bitmap,
        roi: Bitmap,
        roiStartX: Int,
        roiStartY: Int
    ) {

        val analysisWidth =
            320


        val analysisHeight =
            220


        val small =
            Bitmap.createScaledBitmap(
                roi,
                analysisWidth,
                analysisHeight,
                true
            )


        /*
         * 민감도가 올라가면
         * 작은 Edge도 더 잘 검출
         */

        val edgeThreshold =
            (
                70 -
                        sensitivity *
                        0.50
                )
                .toInt()
                .coerceIn(
                    18,
                    65
                )


        val strongThreshold =
            (
                115 -
                        sensitivity *
                        0.67
                )
                .toInt()
                .coerceIn(
                    35,
                    105
                )


        var totalGradient =
            0L


        var pixelCount =
            0L


        var strongEdgeCount =
            0L


        var leftStrength =
            0L


        var rightStrength =
            0L


        var topStrength =
            0L


        var bottomStrength =
            0L


        var leftCount =
            0L


        var rightCount =
            0L


        var topCount =
            0L


        var bottomCount =
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
                        190f
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


                val rightPixel =
                    gray(
                        small.getPixel(
                            x + 1,
                            y
                        )
                    )


                val bottomPixel =
                    gray(
                        small.getPixel(
                            x,
                            y + 1
                        )
                    )


                val gradient =
                    abs(
                        center -
                                rightPixel
                    ) +
                            abs(
                                center -
                                        bottomPixel
                            )


                totalGradient +=
                    gradient


                pixelCount++


                if (
                    x <
                    small.width /
                            2
                ) {

                    leftStrength +=
                        gradient

                    leftCount++

                } else {

                    rightStrength +=
                        gradient

                    rightCount++
                }


                if (
                    y <
                    small.height /
                            2
                ) {

                    topStrength +=
                        gradient

                    topCount++

                } else {

                    bottomStrength +=
                        gradient

                    bottomCount++
                }


                if (
                    gradient >
                    strongThreshold
                ) {

                    strongEdgeCount++


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


        val averageGradient =
            if (
                pixelCount > 0
            ) {

                totalGradient.toDouble() /
                        pixelCount.toDouble()

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


        val leftAverage =
            if (
                leftCount > 0
            ) {

                leftStrength.toDouble() /
                        leftCount

            } else {

                0.0
            }


        val rightAverage =
            if (
                rightCount > 0
            ) {

                rightStrength.toDouble() /
                        rightCount

            } else {

                0.0
            }


        val topAverage =
            if (
                topCount > 0
            ) {

                topStrength.toDouble() /
                        topCount

            } else {

                0.0
            }


        val bottomAverage =
            if (
                bottomCount > 0
            ) {

                bottomStrength.toDouble() /
                        bottomCount

            } else {

                0.0
            }


        val horizontalBalance =
            abs(
                leftAverage -
                        rightAverage
            )


        val verticalBalance =
            abs(
                topAverage -
                        bottomAverage
            )


        /*
         * 민감도 영향
         * 약 60%에서 1.0 수준
         */

        val sensitivityFactor =
            0.55 +
                    sensitivity /
                    133.3


        val positionError =
            (
                horizontalBalance *
                        0.75 +
                        verticalBalance *
                        0.25
                ) *
                    sensitivityFactor


        val tiltError =
            (
                verticalBalance *
                        0.70 +
                        strongEdgeDensity *
                        1.20
                ) *
                    sensitivityFactor


        val spacingError =
            (
                horizontalBalance *
                        0.85 +
                        strongEdgeDensity *
                        0.50
                ) *
                    sensitivityFactor


        val localDeformation =
            (
                averageGradient *
                        0.80 +
                        strongEdgeDensity *
                        2.00
                ) *
                    sensitivityFactor


        val limitedPosition =
            positionError.coerceIn(
                0.0,
                100.0
            )


        val limitedTilt =
            tiltError.coerceIn(
                0.0,
                100.0
            )


        val limitedSpacing =
            spacingError.coerceIn(
                0.0,
                100.0
            )


        val limitedDeformation =
            localDeformation.coerceIn(
                0.0,
                100.0
            )


        val defectScore =
            (
                limitedPosition *
                        0.30 +
                        limitedTilt *
                        0.25 +
                        limitedSpacing *
                        0.25 +
                        limitedDeformation *
                        0.20
                )
                .coerceIn(
                    0.0,
                    100.0
                )


        val tabScore =
            (
                100.0 -
                        defectScore
                )
                .coerceIn(
                    0.0,
                    100.0
                )


        val positionText =
            levelText(
                limitedPosition
            )


        val tiltText =
            levelText(
                limitedTilt
            )


        val spacingText =
            levelText(
                limitedSpacing
            )


        val deformationText =
            levelText(
                limitedDeformation
            )


        val judgment =
            when {

                tabScore >= 85 ->
                    "정상 후보"

                tabScore >= 70 ->
                    "주의 후보"

                tabScore >= 50 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }


        runOnUiThread {

            binding.tabImagePreview.setImageBitmap(
                markedBitmap
            )


            binding.tabImagePreview.imageMatrix =
                imageMatrixValue


            binding.tvTabStatus.text =
                "TAB ROI 분석 완료"


            binding.tvTabMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
민감도            : %d%%
Tab Position      : %s  (%.1f)
Tab Tilt          : %s  (%.1f)
Spacing Balance   : %s  (%.1f)
Local Deformation : %s  (%.1f)
Tab Score         : %.1f / 100

판정 : %s

빨간 표시 : 국부 변화가 큰 위치 후보

※ 민감도는 이미지 검출 수준입니다.
※ 현재 TAB 위치/기울기/간격 값은 영상 Edge 분포를 이용한 임시 분석값입니다.
※ 실제 TAB 치수 판정은 기준 위치와 길이 Calibration을 추가해야 합니다.
                    """.trimIndent(),

                    sensitivity,
                    positionText,
                    limitedPosition,
                    tiltText,
                    limitedTilt,
                    spacingText,
                    limitedSpacing,
                    deformationText,
                    limitedDeformation,
                    tabScore,
                    judgment
                )
        }
    }


    private fun levelText(
        value: Double
    ): String {

        return when {

            value < 15 ->
                "양호"

            value < 30 ->
                "주의"

            value < 50 ->
                "한계"

            else ->
                "이상 후보"
        }
    }


    /*
     * =========================================================
     * RGB → Gray
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
            0.299 * r +
                    0.587 * g +
                    0.114 * b
            ).toInt()
    }
}
