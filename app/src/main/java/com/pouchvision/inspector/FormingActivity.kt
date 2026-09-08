package com.pouchvision.inspector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.pouchvision.inspector.databinding.ActivityFormingBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class FormingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFormingBinding

    private var imageCapture: ImageCapture? = null

    /*
     * 원본 사진과 검사 결과 사진
     *
     * lastBitmap       : 빨간 표시가 없는 깨끗한 원본
     * lastResultBitmap : 빨간 NG 후보 표시가 포함된 검사 결과
     */
    private var lastBitmap: Bitmap? = null
    private var lastResultBitmap: Bitmap? = null

    private var hasInspectionResult = false

    private var lastFormingScore = 0.0
    private var lastWrinkle = 0.0
    private var lastDeformation = 0.0
    private var lastSymmetry = 0.0
    private var lastShapeError = 0.0
    private var lastJudgment = ""
    private var lastDetails = ""

    /*
     * 민감도
     */
    private val preferenceName = "pouch_vision_settings"
    private val sensitivityKey = "forming_sensitivity"
    private val defaultSensitivity = 60
    private var sensitivity = defaultSensitivity

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

    /*
     * 카메라 권한
     */
    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                binding.tvFormingStatus.text =
                    "카메라 권한이 필요합니다."
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

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()

        binding.btnFormingCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnFormingGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnFormingInspect.setOnClickListener {

            val bitmap = lastBitmap

            if (
                bitmap == null ||
                binding.formingImagePreview.visibility != View.VISIBLE
            ) {

                Toast.makeText(
                    this,
                    "먼저 사진을 촬영하거나 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedRoi(bitmap)
            }
        }

        /*
         * 이제 점수/판정과 함께
         * 빨간 후보 표시 결과 사진도 저장합니다.
         */
        binding.btnFormingSaveResult.setOnClickListener {
            saveCurrentInspectionResult()
        }

        binding.btnFormingRoiWidthSmaller.setOnClickListener {
            resizeRoiWidth(0.85f)
        }

        binding.btnFormingRoiWidthLarger.setOnClickListener {
            resizeRoiWidth(1.15f)
        }

        binding.btnFormingRoiHeightSmaller.setOnClickListener {
            resizeRoiHeight(0.85f)
        }

        binding.btnFormingRoiHeightLarger.setOnClickListener {
            resizeRoiHeight(1.15f)
        }

        binding.btnFormingRoiReset.setOnClickListener {
            resetRoiPosition()
        }

        binding.btnFormingImageReset.setOnClickListener {
            resetImageMatrix()
        }

        binding.btnFormingCameraMode.setOnClickListener {
            showCameraMode()
        }

        binding.btnFormingBack.setOnClickListener {
            finish()
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA
            )
        }
    }

    /*
     * 사진 / ROI / 민감도 / 확대 상태가 바뀌면
     * 이전 검사 결과를 저장할 수 없도록 무효화
     */
    private fun invalidateInspectionResult() {

        hasInspectionResult = false
        lastResultBitmap = null
    }

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private fun startCamera() {

        binding.tvFormingStatus.text =
            "카메라 준비 중..."

        binding.formingPreviewView.visibility =
            View.VISIBLE

        binding.formingImagePreview.visibility =
            View.GONE

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            try {

                val cameraProvider =
                    cameraProviderFuture.get()

                val preview =
                    Preview.Builder()
                        .build()
                        .also {

                            it.setSurfaceProvider(
                                binding.formingPreviewView.surfaceProvider
                            )
                        }

                imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                binding.tvFormingStatus.text =
                    "카메라 준비 완료 - Forming 영역을 촬영해주세요."

            } catch (e: Exception) {

                binding.tvFormingStatus.text =
                    "카메라 시작 오류: ${e.message}"
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /*
     * =========================================================
     * 사진 촬영
     * =========================================================
     */

    private fun takePhoto() {

        val capture = imageCapture

        if (capture == null) {

            Toast.makeText(
                this,
                "카메라가 아직 준비되지 않았습니다.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        val name =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(
                System.currentTimeMillis()
            )

        val photoFile =
            File(
                cacheDir,
                "Forming_$name.jpg"
            )

        val outputOptions =
            ImageCapture.OutputFileOptions
                .Builder(photoFile)
                .build()

        binding.tvFormingStatus.text =
            "FORMING 사진 촬영 중..."

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    try {

                        val bitmap =
                            decodeBitmapFromFile(
                                photoFile
                            )

                        if (bitmap == null) {

                            binding.tvFormingStatus.text =
                                "촬영된 사진을 불러올 수 없습니다."

                            return
                        }

                        showSelectedImage(bitmap)

                        binding.tvFormingStatus.text =
                            "촬영 완료 - Forming Cup 영역에 ROI를 맞춰주세요."

                    } catch (e: Exception) {

                        binding.tvFormingStatus.text =
                            "촬영 사진 처리 오류: ${e.message}"
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvFormingStatus.text =
                        "촬영 오류: ${exception.message}"
                }
            }
        )
    }

    private fun decodeBitmapFromFile(
        file: File
    ): Bitmap? {

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        BitmapFactory.decodeFile(
            file.absolutePath,
            bounds
        )

        val maxSize =
            max(
                bounds.outWidth,
                bounds.outHeight
            )

        var sampleSize = 1

        while (
            maxSize / sampleSize > 1600
        ) {
            sampleSize *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

        return BitmapFactory.decodeFile(
            file.absolutePath,
            options
        )
    }

    /*
     * =========================================================
     * 갤러리
     * =========================================================
     */

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvFormingStatus.text =
            "FORMING 사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(uri)

            if (bitmap == null) {

                binding.tvFormingStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            showSelectedImage(bitmap)

            binding.tvFormingStatus.text =
                "사진 선택 완료 - Forming Cup 영역에 ROI를 맞춰주세요."

        } catch (e: Exception) {

            binding.tvFormingStatus.text =
                "사진 불러오기 오류: ${e.message}"
        }
    }

    private fun decodeBitmapFromUri(
        uri: Uri
    ): Bitmap? {

        val bounds =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        contentResolver
            .openInputStream(uri)
            ?.use {

                BitmapFactory.decodeStream(
                    it,
                    null,
                    bounds
                )
            }

        val maxSize =
            max(
                bounds.outWidth,
                bounds.outHeight
            )

        var sampleSize = 1

        while (
            maxSize / sampleSize > 1600
        ) {
            sampleSize *= 2
        }

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

        return contentResolver
            .openInputStream(uri)
            ?.use {

                BitmapFactory.decodeStream(
                    it,
                    null,
                    options
                )
            }
    }

    /*
     * =========================================================
     * 사진 표시 / 카메라 복귀
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap = bitmap
        invalidateInspectionResult()

        binding.formingPreviewView.visibility =
            View.GONE

        binding.formingImagePreview.visibility =
            View.VISIBLE

        binding.formingImagePreview.setImageBitmap(
            bitmap
        )

        resetImageMatrix()
        resetRoiPosition()
        resetResultDisplay()
    }

    private fun showCameraMode() {

        invalidateInspectionResult()

        binding.formingImagePreview.visibility =
            View.GONE

        binding.formingPreviewView.visibility =
            View.VISIBLE

        binding.tvFormingStatus.text =
            "카메라 화면 - Forming 영역을 맞춘 뒤 사진을 촬영해주세요."

        resetRoiPosition()

        if (imageCapture == null) {
            startCamera()
        }
    }

    private fun resetResultDisplay() {

        binding.tvFormingMetrics.text =
            """
Wrinkle       : -
Deformation   : -
Symmetry      : -
Shape Error   : -
Forming Score : -

판정 : -
            """.trimIndent()
    }

    private fun restoreOriginalImage() {

        val bitmap =
            lastBitmap ?: return

        if (
            binding.formingImagePreview.visibility ==
            View.VISIBLE
        ) {

            binding.formingImagePreview.setImageBitmap(
                bitmap
            )

            binding.formingImagePreview.imageMatrix =
                imageMatrixValue
        }
    }

    /*
     * =========================================================
     * 민감도
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

        binding.seekFormingSensitivity.progress =
            sensitivity

        updateSensitivityText()

        binding.seekFormingSensitivity
            .setOnSeekBarChangeListener(

                object :
                    SeekBar.OnSeekBarChangeListener {

                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {

                        sensitivity = progress
                        updateSensitivityText()

                        if (fromUser) {

                            invalidateInspectionResult()
                            restoreOriginalImage()

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

        binding.btnFormingSensitivityReset
            .setOnClickListener {

                sensitivity =
                    defaultSensitivity

                binding.seekFormingSensitivity.progress =
                    defaultSensitivity

                prefs.edit()
                    .putInt(
                        sensitivityKey,
                        defaultSensitivity
                    )
                    .apply()

                invalidateInspectionResult()
                restoreOriginalImage()
                updateSensitivityText()

                Toast.makeText(
                    this,
                    "FORMING 민감도를 60%로 복원했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateSensitivityText() {

        binding.tvFormingSensitivityValue.text =
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
                        detector:
                        ScaleGestureDetector
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
                            newZoom / oldZoom

                        zoomFactor =
                            newZoom

                        imageMatrixValue.postScale(
                            actualScale,
                            actualScale,
                            detector.focusX,
                            detector.focusY
                        )

                        binding.formingImagePreview.imageMatrix =
                            imageMatrixValue

                        invalidateInspectionResult()
                        restoreOriginalImage()

                        return true
                    }
                }
            )

        binding.formingImagePreview
            .setOnTouchListener {
                    view,
                    event ->

                view.parent
                    ?.requestDisallowInterceptTouchEvent(
                        true
                    )

                scaleGestureDetector.onTouchEvent(
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

                            binding.formingImagePreview.imageMatrix =
                                imageMatrixValue

                            invalidateInspectionResult()
                            restoreOriginalImage()
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

                    else -> true
                }
            }
    }

    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap ?: return

        if (
            binding.formingImagePreview.visibility !=
            View.VISIBLE
        ) {
            return
        }

        binding.formingImagePreview.setImageBitmap(
            bitmap
        )

        binding.formingImagePreview.post {

            val viewWidth =
                binding.formingImagePreview.width.toFloat()

            val viewHeight =
                binding.formingImagePreview.height.toFloat()

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
                    viewWidth / bitmapWidth,
                    viewHeight / bitmapHeight
                )

            val displayedWidth =
                bitmapWidth * baseScale

            val displayedHeight =
                bitmapHeight * baseScale

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

            zoomFactor = 1f

            binding.formingImagePreview.imageMatrix =
                imageMatrixValue

            invalidateInspectionResult()
        }
    }

    /*
     * =========================================================
     * ROI 이동 / 크기
     * =========================================================
     */

    private fun setupRoiDrag() {

        binding.formingRoiGuide
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

                        val parent =
                            binding.formingImageArea

                        var newX =
                            view.x + dx

                        var newY =
                            view.y + dy

                        val maxX =
                            (
                                parent.width -
                                    view.width
                                )
                                .coerceAtLeast(
                                    0
                                )

                        val maxY =
                            (
                                parent.height -
                                    view.height
                                )
                                .coerceAtLeast(
                                    0
                                )

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

                        view.x = newX
                        view.y = newY

                        roiLastTouchX =
                            event.rawX

                        roiLastTouchY =
                            event.rawY

                        invalidateInspectionResult()
                        restoreOriginalImage()

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

                    else -> true
                }
            }
    }

    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.formingRoiGuide

        val parent =
            binding.formingImageArea

        if (parent.width <= 0) {
            return
        }

        var newWidth =
            (
                roi.width *
                    scale
                )
                .toInt()

        newWidth =
            newWidth.coerceIn(
                80,
                max(
                    80,
                    (
                        parent.width *
                            0.95f
                        )
                        .toInt()
                )
            )

        val centerX =
            roi.x +
                roi.width / 2f

        val params =
            roi.layoutParams

        params.width =
            newWidth

        roi.layoutParams =
            params

        invalidateInspectionResult()
        restoreOriginalImage()

        roi.post {

            var newX =
                centerX -
                    roi.width / 2f

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

            roi.x = newX
        }
    }

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.formingRoiGuide

        val parent =
            binding.formingImageArea

        if (parent.height <= 0) {
            return
        }

        var newHeight =
            (
                roi.height *
                    scale
                )
                .toInt()

        newHeight =
            newHeight.coerceIn(
                60,
                max(
                    60,
                    (
                        parent.height *
                            0.95f
                        )
                        .toInt()
                )
            )

        val centerY =
            roi.y +
                roi.height / 2f

        val params =
            roi.layoutParams

        params.height =
            newHeight

        roi.layoutParams =
            params

        invalidateInspectionResult()
        restoreOriginalImage()

        roi.post {

            var newY =
                centerY -
                    roi.height / 2f

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

            roi.y = newY
        }
    }

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

            invalidateInspectionResult()
            restoreOriginalImage()
        }
    }

    /*
     * =========================================================
     * ROI를 Bitmap 좌표로 변환
     * =========================================================
     */

    private fun analyzeSelectedRoi(
        source: Bitmap
    ) {

        binding.tvFormingStatus.text =
            "FORMING ROI 분석 중..."

        invalidateInspectionResult()
        restoreOriginalImage()

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
                binding.formingImagePreview.imageMatrix
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
                    RectF(screenRect)

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
                    right - left

                val roiHeight =
                    bottom - top

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

                analyzeFormingRoi(
                    source = source,
                    roi = roiBitmap,
                    roiStartX = left,
                    roiStartY = top
                )

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvFormingStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    /*
     * =========================================================
     * FORMING 분석
     *
     * 기존 FORMING 점수 계산식과 판정 기준은 유지합니다.
     * =========================================================
     */

    private fun analyzeFormingRoi(
        source: Bitmap,
        roi: Bitmap,
        roiStartX: Int,
        roiStartY: Int
    ) {

        val analysisWidth = 320
        val analysisHeight = 240


        /*
         * 촬영 이미지 품질 점검
         * 검사 Score / 판정에는 영향을 주지 않습니다.
         */
        val photoQuality =
            ImageQualityChecker.analyzeBitmap(
                roi
            )

        val photoQualityText =
            String.format(
                Locale.getDefault(),
                "사진 품질 : %s (%.1f / 100)\n밝기 %.1f  |  명암 %.1f  |  선명도 %.1f",
                photoQuality.status,
                photoQuality.qualityScore,
                photoQuality.averageBrightness,
                photoQuality.contrast,
                photoQuality.sharpness
            )
        val small =
            Bitmap.createScaledBitmap(
                roi,
                analysisWidth,
                analysisHeight,
                true
            )

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

        var edgeCount = 0L
        var strongEdgeCount = 0L
        var totalStrength = 0L
        var pixelCount = 0L

        var leftGraySum = 0L
        var rightGraySum = 0L
        var leftCount = 0L
        var rightCount = 0L

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

                totalStrength +=
                    gradient

                pixelCount++

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
                }

                if (
                    x <
                    small.width / 2
                ) {

                    leftGraySum +=
                        center

                    leftCount++

                } else {

                    rightGraySum +=
                        center

                    rightCount++
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

        val leftAverage =
            if (
                leftCount > 0
            ) {

                leftGraySum.toDouble() /
                    leftCount.toDouble()

            } else {
                0.0
            }

        val rightAverage =
            if (
                rightCount > 0
            ) {

                rightGraySum.toDouble() /
                    rightCount.toDouble()

            } else {
                0.0
            }

        val symmetryError =
            abs(
                leftAverage -
                    rightAverage
            )

        val sensitivityFactor =
            0.55 +
                sensitivity / 133.3

        val shapeError =
            (
                edgeDensity *
                    1.1 +
                    averageStrength *
                    0.45
                ) *
                sensitivityFactor

        val wrinkle =
            (
                strongEdgeDensity *
                    4.0 +
                    edgeDensity *
                    0.7
                ) *
                sensitivityFactor

        val deformation =
            (
                averageStrength *
                    0.9 +
                    strongEdgeDensity *
                    2.5
                ) *
                sensitivityFactor

        val symmetry =
            symmetryError *
                1.8 *
                sensitivityFactor

        val defectLevel =
            (
                shapeError *
                    0.30 +
                    wrinkle *
                    0.25 +
                    deformation *
                    0.25 +
                    symmetry *
                    0.20
                )

        val formingScore =
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

                formingScore >= 85.0 ->
                    "정상 후보"

                formingScore >= 70.0 ->
                    "주의 후보"

                formingScore >= 50.0 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }

        /*
         * 공용 NG 후보 표시
         * 알고리즘 자체는 이번 단계에서 변경하지 않습니다.
         */
        val markerResult =
            DefectMarker.markDefectRegions(
                sourceBitmap = source,
                roiLeft = roiStartX,
                roiTop = roiStartY,
                roiWidth = roi.width,
                roiHeight = roi.height,
                sensitivity = sensitivity,
                maxRegions = 5
            )

        val regionCount =
            markerResult.regions.size

        val regionSummary =
            DefectMarker.buildRegionSummary(
                markerResult.regions
            )

        lastFormingScore =
            formingScore

        lastWrinkle =
            wrinkle

        lastDeformation =
            deformation

        lastSymmetry =
            symmetry

        lastShapeError =
            shapeError

        lastJudgment =
            judgment

        lastDetails =
            String.format(
                Locale.getDefault(),

                """
Wrinkle : %.1f
Deformation : %.1f
Symmetry : %.1f
Shape Error : %.1f
Forming Score : %.1f / 100
Sensitivity : %d%%
NG 후보 영역 : %d개

%s
                """.trimIndent(),

                wrinkle,
                deformation,
                symmetry,
                shapeError,
                formingScore,
                sensitivity,
                regionCount,
                regionSummary
            )


        lastDetails +=
            "\n\n" +
                photoQualityText

        if (!photoQuality.isUsable) {
            lastDetails +=
                "\n" +
                    photoQuality.message
        }

        /*
         * 핵심 추가:
         * 빨간 후보 표시가 포함된 결과 Bitmap을 보관
         */
        /*
         * 표시 방식만 공통 Renderer로 변경합니다.
         *
         * - FORMING 판정 / 후보 검출 알고리즘은 그대로 유지
         * - "NG 후보 N" 라벨은 ROI 바깥으로 이동
         * - 빨간 원 선 굵기는 기존의 약 50%
         */
        val displayBitmap =
            MarkerDisplayRenderer.renderGeneric(
                sourceBitmap = source,
                roiLeft = roiStartX,
                roiTop = roiStartY,
                roiWidth = roi.width,
                roiHeight = roi.height,
                regions = markerResult.regions
            )

        lastResultBitmap =
            displayBitmap

        hasInspectionResult =
            true

        runOnUiThread {

            /*
             * lastBitmap은 깨끗한 원본 그대로 유지
             */
            binding.formingImagePreview.setImageBitmap(
                displayBitmap
            )

            binding.formingImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvFormingStatus.text =
                "FORMING ROI 분석 완료 - $judgment"

            binding.tvFormingMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
민감도       : %d%%
Wrinkle      : %.1f
Deformation  : %.1f
Symmetry     : %.1f
Shape Error  : %.1f
Forming Score: %.1f / 100

판정 : %s

NG 후보 영역 : %d개
%s

빨간 원/박스 = Forming 영역에서 국부 변화가 큰 검사 후보

※ 빨간 표시는 확정 NG가 아닙니다.
※ 문자·Barcode·반사광도 후보로 검출될 수 있습니다.
※ 현재 수치는 영상 변화 기반 보조 지표입니다.
※ 실제 형상 치수 및 깊이 판정에는 Calibration과 Master Sample이 필요합니다.
                    """.trimIndent(),

                    sensitivity,
                    wrinkle,
                    deformation,
                    symmetry,
                    shapeError,
                    formingScore,
                    judgment,
                    regionCount,
                    regionSummary
                )

            binding.tvFormingMetrics.append(
                "\n\n" +
                    photoQualityText +
                    "\n※ 사진 품질은 검사 판정과 별도의 촬영 상태 보조지표입니다."
            )

            if (!photoQuality.isUsable) {
                Toast.makeText(
                    this,
                    "촬영 상태 재확인 권고\n${photoQuality.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /*
     * =========================================================
     * 검사 결과 + 결과 사진 저장
     * =========================================================
     */

    private fun saveCurrentInspectionResult() {

        if (
            !hasInspectionResult
        ) {

            Toast.makeText(
                this,
                "먼저 FORMING ROI 검사를 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val resultBitmap =
            lastResultBitmap

        if (
            resultBitmap == null
        ) {

            Toast.makeText(
                this,
                "FORMING 결과 사진이 없습니다. FORMING 검사를 다시 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = "FORMING",
                score = lastFormingScore,
                judgment = lastJudgment,
                sensitivity = sensitivity,
                details = lastDetails,

                /*
                 * 새 기능:
                 * 빨간 NG 후보가 표시된 결과 사진 함께 저장
                 */
                imageBitmap = resultBitmap
            )

        if (success) {

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "FORMING 검사 결과 + 사진 저장 완료\nScore %.1f / 100\n%s",
                    lastFormingScore,
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
