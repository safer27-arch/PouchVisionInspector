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
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pouchvision.inspector.databinding.ActivityDisassemblyBinding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class DisassemblyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisassemblyBinding

    private var lastBitmap: Bitmap? = null

    private var hasInspectionResult = false

    private var lastQualityScore = 0.0
    private var lastSurfaceUniformity = 0.0
    private var lastEdgeDensity = 0.0
    private var lastStrongEdgeDensity = 0.0
    private var lastLocalChange = 0.0

    private var lastJudgment = ""
    private var lastDetails = ""

    private val preferenceName =
        "pouch_vision_settings"

    private val sensitivityKey =
        "disassembly_sensitivity"

    private val defaultSensitivity =
        60

    private var sensitivity =
        defaultSensitivity

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

    private var roiLastTouchX =
        0f

    private var roiLastTouchY =
        0f

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
            ActivityDisassemblyBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()

        binding.btnDisassemblyGallery
            .setOnClickListener {

                galleryLauncher.launch(
                    "image/*"
                )
            }

        binding.btnDisassemblyInspect
            .setOnClickListener {

                val bitmap =
                    lastBitmap

                if (bitmap == null) {

                    Toast.makeText(
                        this,
                        "먼저 분해 검사 사진을 선택해주세요.",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    analyzeSelectedRoi(bitmap)
                }
            }

        binding.btnDisassemblySaveResult
            .setOnClickListener {

                saveCurrentInspectionResult()
            }

        binding.btnDisassemblyRoiWidthSmaller
            .setOnClickListener {

                resizeRoiWidth(0.85f)
            }

        binding.btnDisassemblyRoiWidthLarger
            .setOnClickListener {

                resizeRoiWidth(1.15f)
            }

        binding.btnDisassemblyRoiHeightSmaller
            .setOnClickListener {

                resizeRoiHeight(0.85f)
            }

        binding.btnDisassemblyRoiHeightLarger
            .setOnClickListener {

                resizeRoiHeight(1.15f)
            }

        binding.btnDisassemblyRoiReset
            .setOnClickListener {

                resetRoiPosition()
            }

        binding.btnDisassemblyImageReset
            .setOnClickListener {

                resetImageMatrix()
            }

        binding.btnDisassemblyBack
            .setOnClickListener {

                finish()
            }
    }

    private fun saveCurrentInspectionResult() {

        if (!hasInspectionResult) {

            Toast.makeText(
                this,
                "먼저 분해 검사를 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = "DISASSEMBLY",
                score = lastQualityScore,
                judgment = lastJudgment,
                sensitivity = sensitivity,
                details = lastDetails
            )

        if (success) {

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "분해 검사 결과 저장 완료\nQuality Score %.1f / 100\n%s",
                    lastQualityScore,
                    lastJudgment
                ),
                Toast.LENGTH_LONG
            ).show()

        } else {

            Toast.makeText(
                this,
                "검사 결과 저장에 실패했습니다.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

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
            )
                .coerceIn(
                    0,
                    100
                )

        binding.seekDisassemblySensitivity.progress =
            sensitivity

        updateSensitivityText()

        binding.seekDisassemblySensitivity
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

                            hasInspectionResult =
                                false

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

        binding.btnDisassemblySensitivityReset
            .setOnClickListener {

                sensitivity =
                    defaultSensitivity

                binding.seekDisassemblySensitivity.progress =
                    defaultSensitivity

                prefs.edit()
                    .putInt(
                        sensitivityKey,
                        defaultSensitivity
                    )
                    .apply()

                hasInspectionResult =
                    false

                updateSensitivityText()

                Toast.makeText(
                    this,
                    "분해 검사 민감도를 60%로 복원했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateSensitivityText() {

        binding.tvDisassemblySensitivityValue.text =
            "현재 민감도 : ${sensitivity}%"
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
                                )
                                .coerceIn(
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

                        binding.disassemblyImagePreview.imageMatrix =
                            imageMatrixValue

                        hasInspectionResult =
                            false

                        return true
                    }
                }
            )

        binding.disassemblyImagePreview
            .setOnTouchListener {
                    view,
                    event ->

                view.parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

                scaleGestureDetector
                    .onTouchEvent(
                        event
                    )

                when (event.actionMasked) {

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

                            binding.disassemblyImagePreview.imageMatrix =
                                imageMatrixValue

                            hasInspectionResult =
                                false
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

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvDisassemblyStatus.text =
            "분해 검사 사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(uri)

            if (bitmap == null) {

                binding.tvDisassemblyStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            lastBitmap =
                bitmap

            hasInspectionResult =
                false

            binding.disassemblyImagePreview.visibility =
                View.VISIBLE

            binding.disassemblyImagePreview.setImageBitmap(
                bitmap
            )

            resetImageMatrix()
            resetRoiPosition()

            binding.tvDisassemblyStatus.text =
                "사진 선택 완료 - 확인할 분해 영역에 ROI를 맞춰주세요."

            binding.tvDisassemblyMetrics.text =
                """
Surface Uniformity : -
Edge Density       : -
Strong Edge        : -
Local Change       : -
Quality Score      : -

판정 : -
                """.trimIndent()

        } catch (e: Exception) {

            binding.tvDisassemblyStatus.text =
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

            sampleSize *=
                2
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

    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return

        binding.disassemblyImagePreview.post {

            val viewWidth =
                binding.disassemblyImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding.disassemblyImagePreview
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

            binding.disassemblyImagePreview.imageMatrix =
                imageMatrixValue

            hasInspectionResult =
                false
        }
    }

    private fun setupRoiDrag() {

        binding.disassemblyRoiGuide
            .setOnTouchListener {
                    view,
                    event ->

                view.parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

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
                            view.x +
                                dx

                        var newY =
                            view.y +
                                dy

                        val parent =
                            binding.disassemblyImageArea

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

                        hasInspectionResult =
                            false

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
            binding.disassemblyRoiGuide

        val parent =
            binding.disassemblyImageArea

        if (parent.width <= 0) {
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

        hasInspectionResult =
            false

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

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.disassemblyRoiGuide

        val parent =
            binding.disassemblyImageArea

        if (parent.height <= 0) {
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

        hasInspectionResult =
            false

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

        binding.disassemblyImageArea.post {

            val roi =
                binding.disassemblyRoiGuide

            val parent =
                binding.disassemblyImageArea

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

            hasInspectionResult =
                false
        }
    }

    private fun analyzeSelectedRoi(
        source: Bitmap
    ) {

        binding.tvDisassemblyStatus.text =
            "분해 검사 ROI 분석 중..."

        hasInspectionResult =
            false

        val screenRect =
            RectF(
                binding.disassemblyRoiGuide.x,
                binding.disassemblyRoiGuide.y,
                binding.disassemblyRoiGuide.x +
                    binding.disassemblyRoiGuide.width,
                binding.disassemblyRoiGuide.y +
                    binding.disassemblyRoiGuide.height
            )

        val currentMatrix =
            Matrix(
                binding.disassemblyImagePreview.imageMatrix
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
                    bitmapRect.left.toInt()

                var top =
                    bitmapRect.top.toInt()

                var right =
                    bitmapRect.right.toInt()

                var bottom =
                    bitmapRect.bottom.toInt()

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

                analyzeDisassemblyRoi(
                    source,
                    roiBitmap,
                    left,
                    top
                )

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvDisassemblyStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    private fun analyzeDisassemblyRoi(
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

        val edgeThreshold =
            (
                72 -
                    sensitivity *
                    0.50
                )
                .toInt()
                .coerceIn(
                    18,
                    68
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

        var edgeCount =
            0L

        var strongEdgeCount =
            0L

        var totalGradient =
            0L

        var pixelCount =
            0L

        var graySum =
            0L

        var graySquareSum =
            0.0

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
            )
                .apply {

                    color =
                        Color.RED

                    style =
                        Paint.Style.FILL

                    alpha =
                        220
                }

        val xScale =
            roi.width
                .toFloat() /
                analysisWidth.toFloat()

        val yScale =
            roi.height
                .toFloat() /
                analysisHeight.toFloat()

        val markRadius =
            max(
                2f,
                roi.width
                    .toFloat() /
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

                pixelCount++

                totalGradient +=
                    gradient

                graySum +=
                    center

                graySquareSum +=
                    center.toDouble() *
                        center.toDouble()

                if (
                    gradient >
                    edgeThreshold
                ) {

                    edgeCount++
                }

                if (
                    gradient >
                    strongThreshold
                ) {

                    strongEdgeCount++

                    if (
                        x % 4 == 0 &&
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

                edgeCount
                    .toDouble() /
                    pixelCount.toDouble() *
                    100.0

            } else {

                0.0
            }

        val strongEdgeDensity =
            if (
                pixelCount > 0
            ) {

                strongEdgeCount
                    .toDouble() /
                    pixelCount.toDouble() *
                    100.0

            } else {

                0.0
            }

        val averageGradient =
            if (
                pixelCount > 0
            ) {

                totalGradient
                    .toDouble() /
                    pixelCount.toDouble()

            } else {

                0.0
            }

        val grayMean =
            if (
                pixelCount > 0
            ) {

                graySum
                    .toDouble() /
                    pixelCount.toDouble()

            } else {

                0.0
            }

        val variance =
            if (
                pixelCount > 0
            ) {

                (
                    graySquareSum /
                        pixelCount.toDouble()
                    ) -
                    (
                        grayMean *
                            grayMean
                    )

            } else {

                0.0
            }

        val textureVariation =
            kotlin.math.sqrt(
                variance
                    .coerceAtLeast(
                        0.0
                    )
            )

        val sensitivityFactor =
            0.55 +
                sensitivity /
                133.3

        val localChange =
            (
                edgeDensity *
                    1.4 +
                    strongEdgeDensity *
                    3.0 +
                    averageGradient *
                    0.50
                ) *
                sensitivityFactor

        val surfacePenalty =
            (
                textureVariation *
                    0.70 +
                    strongEdgeDensity *
                    1.8
                ) *
                sensitivityFactor

        val surfaceUniformity =
            (
                100.0 -
                    surfacePenalty
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val defectLevel =
            (
                localChange *
                    0.60 +
                    (
                        100.0 -
                            surfaceUniformity
                        ) *
                        0.40
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val qualityScore =
            (
                100.0 -
                    defectLevel
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        val judgment =
            when {

                qualityScore >= 85 ->
                    "정상 후보"

                qualityScore >= 70 ->
                    "주의 후보"

                qualityScore >= 50 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }

        lastQualityScore =
            qualityScore

        lastSurfaceUniformity =
            surfaceUniformity

        lastEdgeDensity =
            edgeDensity

        lastStrongEdgeDensity =
            strongEdgeDensity

        lastLocalChange =
            localChange

        lastJudgment =
            judgment

        lastDetails =
            String.format(
                Locale.getDefault(),

                """
Surface Uniformity : %.1f / 100
Edge Density : %.1f%%
Strong Edge : %.1f%%
Local Change : %.1f
Quality Score : %.1f / 100
Sensitivity : %d%%
                """.trimIndent(),

                surfaceUniformity,
                edgeDensity,
                strongEdgeDensity,
                localChange,
                qualityScore,
                sensitivity
            )

        hasInspectionResult =
            true

        runOnUiThread {

            binding.disassemblyImagePreview.setImageBitmap(
                markedBitmap
            )

            binding.disassemblyImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvDisassemblyStatus.text =
                "분해 검사 ROI 분석 완료"

            binding.tvDisassemblyMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
민감도            : %d%%
Surface Uniformity: %.1f / 100
Edge Density      : %.1f%%
Strong Edge       : %.1f%%
Local Change      : %.1f
Quality Score     : %.1f / 100

판정 : %s

빨간 표시 : 표면 변화가 큰 위치 후보

※ 빨간 표시는 실제 불량 확정이 아니라 확인 필요 위치입니다.
※ 전극 패턴, 조명 반사, 분리막 무늬도 Edge로 검출될 수 있습니다.
※ 실제 양산 판정에는 정상 Master Sample과 실제 불량품 검증이 필요합니다.
                    """.trimIndent(),

                    sensitivity,
                    surfaceUniformity,
                    edgeDensity,
                    strongEdgeDensity,
                    localChange,
                    qualityScore,
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
            )
            .toInt()
    }
}
