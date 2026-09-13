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
     * 현재 FORMING 검사 결과의 Telegram 전송 요청 여부
     * 같은 결과 중복 발송 방지용입니다.
     */
    private var telegramAlertQueuedForCurrentResult = false

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

        /*
         * FORMING 촬영 표준화 가이드
         *
         * 검사 알고리즘이나 판정 기준은 변경하지 않고,
         * 촬영 거리 / 각도 / 조명 / ROI 위치를
         * 일정하게 맞출 수 있도록 안내합니다.
         */
        binding.btnFormingCaptureGuide.setOnClickListener {

            CaptureGuideHelper.showGuideDialog(
                context = this,
                inspectionType = CaptureGuideHelper.TYPE_FORMING
            )
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
        telegramAlertQueuedForCurrentResult = false
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
FORMING V2
면 자동판별 : -
Shape Stability : -
Corner Risk : -
Wall Risk : -
Center Boundary : -
Local Fold : -
Baseline Deviation : -
Quality Score : -

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

        /*
         * 촬영 이미지 품질 점검.
         * 사진 품질은 판정과 별도의 보조지표로만 사용합니다.
         */
        val photoQuality =
            ImageQualityChecker.analyzeBitmap(
                roi
            )

        val photoQualityText =
            String.format(
                Locale.getDefault(),
                "사진 품질 : %s (%.1f / 100)\n밝기 %.1f | 명암 %.1f | 선명도 %.1f",
                photoQuality.status,
                photoQuality.qualityScore,
                photoQuality.averageBrightness,
                photoQuality.contrast,
                photoQuality.sharpness
            )

        /*
         * =====================================================
         * FORMING V2
         * =====================================================
         *
         * 파우치 전체 반사광보다
         * Cup Corner / Wall / 중앙 경계 / 국부 Fold를 우선 분석합니다.
         */
        val v2 =
            FormingInspectionV2.analyze(
                roi = roi,
                sensitivity = sensitivity
            )

        /*
         * 기존 Model / Line 기준은 참고용으로 남깁니다.
         * V2 현장 판정에는 직접 사용하지 않습니다.
         */
        val inspectionSpec =
            InspectionSpecStore.getCurrent(
                context = this,
                inspectionType =
                    InspectionSpecStore.InspectionType.FORMING
            )

        lastFormingScore =
            v2.qualityScore

        lastWrinkle =
            v2.localFoldRisk

        lastDeformation =
            v2.wallRisk

        lastSymmetry =
            v2.cornerRisk

        lastShapeError =
            v2.baselineDeviation

        lastJudgment =
            v2.judgment

        lastDetails =
            String.format(
                Locale.getDefault(),

                """
FORMING V2

면 자동판별 : %s
면 판별 Confidence : %.1f / 100

Shape Stability : %.1f / 100
Corner Risk : %.1f / 100
Wall Risk : %.1f / 100
Center Boundary Risk : %.1f / 100
Local Fold Risk : %.1f / 100
Reflection Risk : %.1f / 100

Baseline Deviation : %.1f / 100
Quality Score : %.1f / 100
Final Judgment : %s

%s

정상 Master 운영 기준
- 정상 FORMING 사진 10장을 기준군으로 사용
- FRONT = 금형이 실제 누르는 면 / Stack Cell이 들어가는 Cup 측
- BACK = 셀 외곽부 측
- 넓은 파우치의 완만한 울렁임과 반사광은 낮은 가중치
- Cup Corner / Wall / 중앙 경계의 급격한 형상 변화를 우선 감지
- 실제 NG 샘플이 없으므로 현재 주의/한계정상/불량 기준은 임시 기준
                """.trimIndent(),

                v2.faceHint,
                v2.faceConfidence,
                v2.shapeStability,
                v2.cornerRisk,
                v2.wallRisk,
                v2.centerBoundaryRisk,
                v2.localFoldRisk,
                v2.reflectionRisk,
                v2.baselineDeviation,
                v2.qualityScore,
                v2.judgment,
                v2.reason
            )

        lastDetails +=
            "\n\n현재 Model / Line 기존 기준 (참고용)\n" +
                inspectionSpec.criteriaText() +
                "\n\n" +
                photoQualityText +
                "\n\n※ FRONT/BACK 자동판별은 현재 힌트 단계입니다." +
                "\n※ Confidence가 낮으면 면을 확정하지 않고 '확인 필요'로 표시합니다."

        if (!photoQuality.isUsable) {

            lastDetails +=
                "\n" +
                    photoQuality.message
        }

        /*
         * 정상 판정이면 공용 빨간 NG 후보를 표시하지 않습니다.
         * 정상 파우치 반사/울렁임이 NG 후보로 보이는 오검출을 줄이기 위함입니다.
         */
        val displayBitmap =
            if (
                v2.showDefectMarkers
            ) {

                val markerResult =
                    DefectMarker.markDefectRegions(
                        sourceBitmap = source,
                        roiLeft = roiStartX,
                        roiTop = roiStartY,
                        roiWidth = roi.width,
                        roiHeight = roi.height,
                        sensitivity = sensitivity,
                        maxRegions = 4
                    )

                MarkerDisplayRenderer.renderGeneric(
                    sourceBitmap = source,
                    roiLeft = roiStartX,
                    roiTop = roiStartY,
                    roiWidth = roi.width,
                    roiHeight = roi.height,
                    regions = markerResult.regions
                )

            } else {

                source.copy(
                    Bitmap.Config.ARGB_8888,
                    true
                )
            }

        lastResultBitmap =
            displayBitmap

        hasInspectionResult =
            true

        runOnUiThread {

            binding.formingImagePreview.setImageBitmap(
                displayBitmap
            )

            binding.formingImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvFormingStatus.text =
                "FORMING V2 분석 완료 - ${v2.judgment}"

            binding.tvFormingMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
FORMING V2

면 자동판별 : %s
Confidence : %.1f / 100

Shape Stability : %.1f / 100
Corner Risk : %.1f / 100
Wall Risk : %.1f / 100
Center Boundary : %.1f / 100
Local Fold : %.1f / 100
Reflection Risk : %.1f / 100

Baseline Deviation : %.1f / 100
Quality Score : %.1f / 100

판정 : %s

%s

※ 정상 판정에서는 빨간 NG 후보를 표시하지 않습니다.
※ 큰 반사광/완만한 파우치 울렁임은 낮은 가중치로 처리합니다.
※ 핵심은 Cup Corner / Wall / 중앙 경계 형상 변화입니다.
※ FRONT/BACK 자동판별은 현재 보조 힌트이며 낮은 Confidence에서는 확인이 필요합니다.
                    """.trimIndent(),

                    v2.faceHint,
                    v2.faceConfidence,
                    v2.shapeStability,
                    v2.cornerRisk,
                    v2.wallRisk,
                    v2.centerBoundaryRisk,
                    v2.localFoldRisk,
                    v2.reflectionRisk,
                    v2.baselineDeviation,
                    v2.qualityScore,
                    v2.judgment,
                    v2.reason
                )

            binding.tvFormingMetrics.append(
                "\n\n" +
                    photoQualityText +
                    "\n※ 사진 품질은 FORMING 판정과 별도의 촬영 상태 보조지표입니다."
            )

            if (!photoQuality.isUsable) {

                Toast.makeText(
                    this,
                    "촬영 상태 재확인 권고\n${photoQuality.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        if (
            !roi.isRecycled
        ) {
            roi.recycle()
        }
    }

    /*
     * =========================================================
     * FORMING Telegram 자동 알림
     * =========================================================
     */

    private fun sendTelegramAlertIfNeeded() {

        if (telegramAlertQueuedForCurrentResult) {
            return
        }

        if (
            !TelegramSettingsStore.isReady(
                this
            )
        ) {
            return
        }

        if (
            !TelegramSettingsStore.shouldSendForJudgment(
                context = this,
                judgment = lastJudgment
            )
        ) {
            return
        }

        /*
         * 실제 전송 대상임이 확인된 뒤 먼저 잠가
         * 같은 결과의 중복 발송을 방지합니다.
         */
        telegramAlertQueuedForCurrentResult = true

        TelegramSender.sendInspectionAlert(
            context = this,
            inspectionType = "FORMING",
            score = lastFormingScore,
            judgment = lastJudgment,
            details = lastDetails,
            resultBitmap = lastResultBitmap
        ) { result ->

            runOnUiThread {

                val message =
                    if (
                        result.success
                    ) {

                        "FORMING Telegram 자동전송 완료\n" +
                            "성공 ${result.successCount}개 / " +
                            "실패 ${result.failureCount}개"

                    } else {

                        "FORMING Telegram 자동전송 실패\n" +
                            result.message
                    }

                Toast.makeText(
                    this,
                    message,
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

            /*
             * 검사 결과 + 사진 저장 성공 후에만
             * Telegram 정책에 따라 1회 자동전송합니다.
             */
            sendTelegramAlertIfNeeded()

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
