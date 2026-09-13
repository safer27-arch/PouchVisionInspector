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
import com.pouchvision.inspector.databinding.ActivityDisassemblyBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class DisassemblyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisassemblyBinding

    private var imageCapture: ImageCapture? = null

    /*
     * lastBitmap:
     * 빨간 표시가 없는 깨끗한 원본 사진
     *
     * lastResultBitmap:
     * 분해검사 후 빨간 NG 후보가 표시된 결과 사진
     */
    private var lastBitmap: Bitmap? = null
    private var lastResultBitmap: Bitmap? = null

    private var hasInspectionResult = false

    private var lastQualityScore = 0.0
    private var lastSurfaceUniformity = 0.0
    private var lastEdgeDensity = 0.0
    private var lastStrongEdgeDensity = 0.0
    private var lastLocalChange = 0.0
    private var lastJudgment = ""
    private var lastDetails = ""

    /*
     * 현재 분해검사 결과의 Telegram 전송 요청 여부
     *
     * - 같은 결과를 여러 번 저장해도 중복 발송 방지
     * - 새 사진 / ROI / 민감도 변경 / 재검사 시 초기화
     * - 실제 Telegram 전송은 로컬 이력 저장 성공 후에만 실행
     */
    private var telegramAlertQueuedForCurrentResult = false

    /*
     * 민감도
     */
    private val preferenceName =
        "pouch_vision_settings"

    private val sensitivityKey =
        "disassembly_sensitivity"

    private val defaultSensitivity =
        60

    private var sensitivity =
        defaultSensitivity

    /*
     * 이미지 확대 / 이동
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
     * ROI 이동
     */
    private var roiLastTouchX =
        0f

    private var roiLastTouchY =
        0f

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

                binding.tvDisassemblyStatus.text =
                    "카메라 권한이 필요합니다."
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

        setContentView(
            binding.root
        )

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()

        binding.btnDisassemblyCapture
            .setOnClickListener {
                takePhoto()
            }

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

                if (
                    bitmap == null ||
                    binding.disassemblyImagePreview.visibility !=
                    View.VISIBLE
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
         * 검사 결과 + 결과 사진 저장
         */
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

        binding.btnDisassemblyCameraMode
            .setOnClickListener {
                showCameraMode()
            }

        /*
         * 분해검사 촬영 표준화 가이드
         *
         * 검사 알고리즘이나 판정 기준은 변경하지 않고,
         * 촬영 거리 / 각도 / 조명 / ROI 위치를
         * 일정하게 맞출 수 있도록 안내합니다.
         */
        binding.btnDisassemblyCaptureGuide
            .setOnClickListener {

                CaptureGuideHelper.showGuideDialog(
                    context = this,
                    inspectionType = CaptureGuideHelper.TYPE_DISASSEMBLY
                )
            }

        binding.btnDisassemblyBack
            .setOnClickListener {
                finish()
            }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
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
     * 이전 검사 결과를 저장하지 못하도록 무효화
     */
    private fun invalidateInspectionResult() {

        hasInspectionResult =
            false

        lastResultBitmap =
            null

        telegramAlertQueuedForCurrentResult =
            false
    }

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private fun startCamera() {

        binding.tvDisassemblyStatus.text =
            "카메라 준비 중..."

        binding.disassemblyPreviewView.visibility =
            View.VISIBLE

        binding.disassemblyImagePreview.visibility =
            View.GONE

        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(
                this
            )

        cameraProviderFuture.addListener({

            try {

                val cameraProvider =
                    cameraProviderFuture.get()

                val preview =
                    Preview.Builder()
                        .build()
                        .also {

                            it.setSurfaceProvider(
                                binding
                                    .disassemblyPreviewView
                                    .surfaceProvider
                            )
                        }

                imageCapture =
                    ImageCapture.Builder()
                        .setCaptureMode(
                            ImageCapture
                                .CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .build()

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                binding.tvDisassemblyStatus.text =
                    "카메라 준비 완료 - 분해 검사 영역을 촬영해주세요."

            } catch (e: Exception) {

                binding.tvDisassemblyStatus.text =
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

        val capture =
            imageCapture

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
                "Disassembly_$name.jpg"
            )

        val outputOptions =
            ImageCapture
                .OutputFileOptions
                .Builder(
                    photoFile
                )
                .build()

        binding.tvDisassemblyStatus.text =
            "분해 검사 사진 촬영 중..."

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

                            binding.tvDisassemblyStatus.text =
                                "촬영된 사진을 불러올 수 없습니다."

                            return
                        }

                        showSelectedImage(bitmap)

                        binding.tvDisassemblyStatus.text =
                            "촬영 완료 - 확인할 분해 영역에 ROI를 맞춰주세요."

                    } catch (e: Exception) {

                        binding.tvDisassemblyStatus.text =
                            "촬영 사진 처리 오류: ${e.message}"
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvDisassemblyStatus.text =
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

        var sampleSize =
            1

        while (
            maxSize / sampleSize >
            1600
        ) {

            sampleSize *=
                2
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

            showSelectedImage(bitmap)

            binding.tvDisassemblyStatus.text =
                "사진 선택 완료 - 확인할 분해 영역에 ROI를 맞춰주세요."

        } catch (e: Exception) {

            binding.tvDisassemblyStatus.text =
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

        var sampleSize =
            1

        while (
            maxSize / sampleSize >
            1600
        ) {

            sampleSize *=
                2
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
     * 선택 이미지 표시 / 카메라 복귀
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap =
            bitmap

        invalidateInspectionResult()

        binding.disassemblyPreviewView.visibility =
            View.GONE

        binding.disassemblyImagePreview.visibility =
            View.VISIBLE

        binding.disassemblyImagePreview.setImageBitmap(
            bitmap
        )

        resetImageMatrix()
        resetRoiPosition()
        resetResultDisplay()
    }

    private fun showCameraMode() {

        invalidateInspectionResult()

        binding.disassemblyImagePreview.visibility =
            View.GONE

        binding.disassemblyPreviewView.visibility =
            View.VISIBLE

        binding.tvDisassemblyStatus.text =
            "카메라 화면 - 분해 검사 영역을 맞춘 뒤 촬영해주세요."

        resetRoiPosition()

        if (
            imageCapture ==
            null
        ) {

            startCamera()
        }
    }

    private fun restoreOriginalImage() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.disassemblyImagePreview.visibility ==
            View.VISIBLE
        ) {

            binding.disassemblyImagePreview.setImageBitmap(
                bitmap
            )

            binding.disassemblyImagePreview.imageMatrix =
                imageMatrixValue
        }
    }

    private fun resetResultDisplay() {

        binding.tvDisassemblyMetrics.text =
            """
DISASSEMBLY V2.2
PP/Seal 연속성 Risk : -
폭/표면 Variation : -
국부 찢김 Risk : -
Strong Edge Risk : -
Quality Score : -

판정 : -

NG 후보 영역 : -
            """.trimIndent()
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

                invalidateInspectionResult()
                restoreOriginalImage()
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

                        invalidateInspectionResult()
                        restoreOriginalImage()

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
                            event.pointerCount ==
                            1 &&
                            zoomFactor >
                            1f
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

                    else ->
                        true
                }
            }
    }

    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.disassemblyImagePreview.visibility !=
            View.VISIBLE
        ) {

            return
        }

        binding.disassemblyImagePreview.setImageBitmap(
            bitmap
        )

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
                viewWidth <=
                0f ||
                viewHeight <=
                0f
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
                    ) /
                    2f

            val dy =
                (
                    viewHeight -
                        displayedHeight
                    ) /
                    2f

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

            invalidateInspectionResult()
        }
    }

    /*
     * =========================================================
     * ROI 이동 / 크기
     * =========================================================
     */

    private fun setupRoiDrag() {

        binding.disassemblyRoiGuide
            .setOnTouchListener {
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
                            binding.disassemblyImageArea

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

                        view.x =
                            newX

                        view.y =
                            newY

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

        if (
            parent.width <=
            0
        ) {

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
                roi.width /
                    2f

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
            binding.disassemblyRoiGuide

        val parent =
            binding.disassemblyImageArea

        if (
            parent.height <=
            0
        ) {

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
                roi.height /
                    2f

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

        binding.disassemblyImageArea.post {

            val roi =
                binding.disassemblyRoiGuide

            val parent =
                binding.disassemblyImageArea

            roi.x =
                (
                    parent.width -
                        roi.width
                    ) /
                    2f

            roi.y =
                (
                    parent.height -
                        roi.height
                    ) /
                    2f

            invalidateInspectionResult()
            restoreOriginalImage()
        }
    }

    /*
     * =========================================================
     * 화면 ROI → 실제 Bitmap 좌표
     * =========================================================
     */

    private fun analyzeSelectedRoi(
        source: Bitmap
    ) {

        binding.tvDisassemblyStatus.text =
            "분해 검사 ROI 분석 중..."

        invalidateInspectionResult()
        restoreOriginalImage()

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
                        source.width -
                            1
                    )

                top =
                    top.coerceIn(
                        0,
                        source.height -
                            1
                    )

                right =
                    right.coerceIn(
                        left +
                            1,
                        source.width
                    )

                bottom =
                    bottom.coerceIn(
                        top +
                            1,
                        source.height
                    )

                val roiWidth =
                    right -
                        left

                val roiHeight =
                    bottom -
                        top

                if (
                    roiWidth <
                    10 ||
                    roiHeight <
                    10
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
                    source = source,
                    roi = roiBitmap,
                    roiStartX = left,
                    roiStartY = top
                )

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvDisassemblyStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    /*
     * =========================================================
     * 분해 검사 알고리즘
     *
     * 기존 점수 계산식과 판정 기준은 유지합니다.
     * 이번 수정 목적은 결과 사진 저장 기능 추가입니다.
     * =========================================================
     */

    private fun analyzeDisassemblyRoi(
        source: Bitmap,
        roi: Bitmap,
        roiStartX: Int,
        roiStartY: Int
    ) {
        val photoQuality = ImageQualityChecker.analyzeBitmap(roi)
        val photoQualityText = String.format(
            Locale.getDefault(),
            "사진 품질 : %s (%.1f / 100)\n밝기 %.1f  |  명암 %.1f  |  선명도 %.1f",
            photoQuality.status,
            photoQuality.qualityScore,
            photoQuality.averageBrightness,
            photoQuality.contrast,
            photoQuality.sharpness
        )

        val result = DisassemblyInspectionV2.analyze(
            roi = roi,
            sensitivity = sensitivity
        )

        val inspectionSpec = InspectionSpecStore.getCurrent(
            context = this,
            inspectionType = InspectionSpecStore.InspectionType.DISASSEMBLY
        )
        val judgment = inspectionSpec.judge(result.qualityScore)

        // V2.2: 단일 Risk가 아니라 복수 신호가 함께 높을 때만 빨간 후보를 표시합니다.
        // 정상 사진의 Cell 경계/반사/접힘 때문에 큰 빨간 원이 생기는 것을 억제합니다.
        val corroboratedDefect =
            listOf(
                result.continuityRisk >= 62.0,
                result.widthVariationRisk >= 52.0,
                result.localTearRisk >= 62.0,
                result.strongEdgeRisk >= 28.0
            ).count { it } >= 2

        val showCandidates =
            corroboratedDefect &&
                result.qualityScore < 70.0

        val markerResult = if (showCandidates) {
            DefectMarker.markDefectRegions(
                sourceBitmap = source,
                roiLeft = roiStartX,
                roiTop = roiStartY,
                roiWidth = roi.width,
                roiHeight = roi.height,
                sensitivity = sensitivity,
                maxRegions = 3
            )
        } else {
            null
        }

        val regionCount = markerResult?.regions?.size ?: 0
        val regionSummary = if (markerResult != null) {
            DefectMarker.buildRegionSummary(markerResult.regions)
        } else {
            "정상 Master 교차검증: 복수 이상 신호가 없어 빨간 후보 표시 없음"
        }

        lastQualityScore = result.qualityScore
        lastSurfaceUniformity = result.surfaceUniformity
        lastEdgeDensity = result.continuityRisk
        lastStrongEdgeDensity = result.strongEdgeRisk
        lastLocalChange = result.localTearRisk
        lastJudgment = judgment

        lastDetails = String.format(
            Locale.getDefault(),
            """
DISASSEMBLY V2.2 - 정밀판정
인식 Confidence : %.1f / 100
PP/Seal 연속성 Risk : %.1f / 100
폭/표면 Variation : %.1f / 100
국부 찢김 Risk : %.1f / 100
Strong Edge Risk : %.1f / 100
위치 흔들림 허용도 : %.1f / 100
Surface Uniformity : %.1f / 100
Quality Score : %.1f / 100
Sensitivity : %d%%

판정 : %s

%s
NG 후보 영역 : %d개
%s
            """.trimIndent(),
            result.confidence,
            result.continuityRisk,
            result.widthVariationRisk,
            result.localTearRisk,
            result.strongEdgeRisk,
            result.positionTolerance,
            result.surfaceUniformity,
            result.qualityScore,
            sensitivity,
            judgment,
            result.note,
            regionCount,
            regionSummary
        )

        lastDetails +=
            "\n\n현재 Model / Line 판정 기준\n" +
                inspectionSpec.criteriaText() +
                "\n\n" + photoQualityText +
                "\n※ 사진 품질은 검사 판정과 별도의 촬영 상태 보조지표입니다." +
                "\n※ V2.2는 정상 Master 교차검증 버전이며 단일 Risk만으로 NG 후보를 확정하지 않습니다.
※ 실제 NG 확보 후 Threshold를 최종 보정합니다."

        if (!photoQuality.isUsable) {
            lastDetails += "\n" + photoQuality.message
        }

        val displayBitmap = if (markerResult != null && markerResult.regions.isNotEmpty()) {
            MarkerDisplayRenderer.renderGeneric(
                sourceBitmap = source,
                roiLeft = roiStartX,
                roiTop = roiStartY,
                roiWidth = roi.width,
                roiHeight = roi.height,
                regions = markerResult.regions
            )
        } else {
            source.copy(Bitmap.Config.ARGB_8888, true)
        }

        lastResultBitmap = displayBitmap
        hasInspectionResult = true

        runOnUiThread {
            binding.disassemblyImagePreview.setImageBitmap(displayBitmap)
            binding.disassemblyImagePreview.imageMatrix = imageMatrixValue
            binding.tvDisassemblyStatus.text =
                "DISASSEMBLY V2.2 분석 완료 - $judgment"
            binding.tvDisassemblyMetrics.text = lastDetails

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
     * 분해검사 Telegram 자동 알림
     * =========================================================
     */

    private fun sendTelegramAlertIfNeeded() {

        if (
            telegramAlertQueuedForCurrentResult
        ) {
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
         * 같은 분해검사 결과의 중복 발송을 방지합니다.
         */
        telegramAlertQueuedForCurrentResult =
            true

        TelegramSender.sendInspectionAlert(
            context = this,
            inspectionType = "DISASSEMBLY",
            score = lastQualityScore,
            judgment = lastJudgment,
            details = lastDetails,
            resultBitmap = lastResultBitmap
        ) { result ->

            runOnUiThread {

                val message =
                    if (
                        result.success
                    ) {

                        "분해검사 Telegram 자동전송 완료\n" +
                            "성공 ${result.successCount}개 / " +
                            "실패 ${result.failureCount}개"

                    } else {

                        "분해검사 Telegram 자동전송 실패\n" +
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
                "먼저 분해 검사를 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val resultBitmap =
            lastResultBitmap

        if (
            resultBitmap ==
            null
        ) {

            Toast.makeText(
                this,
                "분해 검사 결과 사진이 없습니다. 검사를 다시 실행해주세요.",
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
                details = lastDetails,

                /*
                 * 새 기능:
                 * 빨간 NG 후보가 표시된 결과 사진도 함께 저장
                 */
                imageBitmap = resultBitmap
            )

        if (
            success
        ) {

            /*
             * 검사 결과 + 사진 저장 성공 후에만
             * Telegram 정책에 따라 현재 결과를 1회 전송합니다.
             */
            sendTelegramAlertIfNeeded()

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "분해 검사 결과 + 사진 저장 완료\nQuality Score %.1f / 100\n%s",
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
