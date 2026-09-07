package com.pouchvision.inspector

import android.Manifest
import android.content.pm.PackageManager
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

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private var imageCapture: ImageCapture? = null

    /*
     * =========================================================
     * 현재 이미지
     * =========================================================
     */

    private var lastBitmap: Bitmap? = null

    /*
     * =========================================================
     * 검사 결과
     * =========================================================
     */

    private var hasInspectionResult = false

    private var lastQualityScore = 0.0
    private var lastSurfaceUniformity = 0.0
    private var lastEdgeDensity = 0.0
    private var lastStrongEdgeDensity = 0.0
    private var lastLocalChange = 0.0
    private var lastJudgment = ""
    private var lastDetails = ""

    /*
     * =========================================================
     * 민감도
     * =========================================================
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

    /*
     * =========================================================
     * 카메라 권한
     * =========================================================
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

    /*
     * =========================================================
     * onCreate
     * =========================================================
     */

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

        /*
         * 사진 촬영
         */

        binding.btnDisassemblyCapture
            .setOnClickListener {

                takePhoto()
            }

        /*
         * 갤러리
         */

        binding.btnDisassemblyGallery
            .setOnClickListener {

                galleryLauncher.launch(
                    "image/*"
                )
            }

        /*
         * 검사
         */

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
         * 결과 저장
         */

        binding.btnDisassemblySaveResult
            .setOnClickListener {

                saveCurrentInspectionResult()
            }

        /*
         * ROI 가로
         */

        binding.btnDisassemblyRoiWidthSmaller
            .setOnClickListener {

                resizeRoiWidth(0.85f)
            }

        binding.btnDisassemblyRoiWidthLarger
            .setOnClickListener {

                resizeRoiWidth(1.15f)
            }

        /*
         * ROI 세로
         */

        binding.btnDisassemblyRoiHeightSmaller
            .setOnClickListener {

                resizeRoiHeight(0.85f)
            }

        binding.btnDisassemblyRoiHeightLarger
            .setOnClickListener {

                resizeRoiHeight(1.15f)
            }

        /*
         * ROI 중앙
         */

        binding.btnDisassemblyRoiReset
            .setOnClickListener {

                resetRoiPosition()
            }

        /*
         * 사진 원래크기
         */

        binding.btnDisassemblyImageReset
            .setOnClickListener {

                resetImageMatrix()
            }

        /*
         * 카메라 복귀
         */

        binding.btnDisassemblyCameraMode
            .setOnClickListener {

                showCameraMode()
            }

        /*
         * 메뉴 복귀
         */

        binding.btnDisassemblyBack
            .setOnClickListener {

                finish()
            }

        /*
         * 카메라 시작
         */

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
     * =========================================================
     * CameraX 시작
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

                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
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
                .Builder(photoFile)
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

    /*
     * =========================================================
     * 촬영 파일 -> Bitmap
     * =========================================================
     */

    private fun decodeBitmapFromFile(
        file: File
    ): Bitmap? {

        val options =
            BitmapFactory.Options()

        options.inJustDecodeBounds =
            true

        BitmapFactory.decodeFile(
            file.absolutePath,
            options
        )

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

        return BitmapFactory.decodeFile(
            file.absolutePath,
            decodeOptions
        )
    }

    /*
     * =========================================================
     * 카메라 화면으로 복귀
     * =========================================================
     */

    private fun showCameraMode() {

        hasInspectionResult =
            false

        binding.disassemblyImagePreview.visibility =
            View.GONE

        binding.disassemblyPreviewView.visibility =
            View.VISIBLE

        binding.tvDisassemblyStatus.text =
            "카메라 화면 - 확인할 분해 영역을 맞춘 뒤 촬영해주세요."

        resetRoiPosition()

        if (imageCapture == null) {

            startCamera()
        }
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

    /*
     * =========================================================
     * URI -> Bitmap
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

    /*
     * =========================================================
     * 선택된 사진 표시
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap =
            bitmap

        hasInspectionResult =
            false

        binding.disassemblyPreviewView.visibility =
            View.GONE

        binding.disassemblyImagePreview.visibility =
            View.VISIBLE

        binding.disassemblyImagePreview
            .setImageBitmap(bitmap)

        resetImageMatrix()
        resetRoiPosition()
        resetResultDisplay()
    }

    /*
     * =========================================================
     * 결과 표시 초기화
     * =========================================================
     */

    private fun resetResultDisplay() {

        binding.tvDisassemblyMetrics.text =
            """
Surface Uniformity : -
Edge Density       : -
Strong Edge        : -
Local Change       : -
Quality Score      : -

판정 : -
            """.trimIndent()
    }

    /*
     * =========================================================
     * 검사 결과 저장
     * =========================================================
     */

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

                        binding
                            .disassemblyImagePreview
                            .imageMatrix =
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
                    .onTouchEvent(event)

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

                            binding
                                .disassemblyImagePreview
                                .imageMatrix =
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

    /*
     * =========================================================
     * 사진 원래크기
     * =========================================================
     */

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

        binding.disassemblyImagePreview.post {

            val viewWidth =
                binding
                    .disassemblyImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding
                    .disassemblyImagePreview
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

            binding
                .disassemblyImagePreview
                .imageMatrix =
                imageMatrixValue

            hasInspectionResult =
                false
        }
    }

    /*
     * =========================================================
     * ROI 이동
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

    /*
     * =========================================================
     * ROI 가로
     * =========================================================
     */

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

    /*
     * =========================================================
     * ROI 세로
     * =========================================================
     */

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

    /*
     * =========================================================
     * ROI 중앙
     * =========================================================
     */

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

            hasInspectionResult =
                false
        }
    }

    /*
     * =========================================================
     * ROI 분석
     * =========================================================
     */

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
                binding
                    .disassemblyImagePreview
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

    /*
     * =========================================================
     * 분해 검사 분석
     * 기존 계산 공식 유지
     * =========================================================
     */

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
                                    )
                                    .toInt()

                        val originalY =
                            roiStartY +
                                (
                                    y *
                                        yScale
                                    )
                                    .toInt()

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
            if (pixelCount > 0) {

                edgeCount
                    .toDouble() /
                    pixelCount.toDouble() *
                    100.0

            } else {

                0.0
            }

        val strongEdgeDensity =
            if (pixelCount > 0) {

                strongEdgeCount
                    .toDouble() /
                    pixelCount.toDouble() *
                    100.0

            } else {

                0.0
            }

        val averageGradient =
            if (pixelCount > 0) {

                totalGradient
                    .toDouble() /
                    pixelCount.toDouble()

            } else {

                0.0
            }

        val grayMean =
            if (pixelCount > 0) {

                graySum
                    .toDouble() /
                    pixelCount.toDouble()

            } else {

                0.0
            }

        val variance =
            if (pixelCount > 0) {

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
            sqrt(
                variance.coerceAtLeast(
                    0.0
                )
            )

        val sensitivityFactor =
            0.55 +
                sensitivity /
                133.3

        /*
         * 국부 변화
         */

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

        /*
         * 표면 균일도
         */

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

        /*
         * 종합 결함량
         */

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

            /*
             * 빨간 후보점이 포함된 분석 이미지 표시
             */

            lastBitmap =
                markedBitmap

            binding.disassemblyImagePreview
                .setImageBitmap(
                    markedBitmap
                )

            binding.disassemblyImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvDisassemblyStatus.text =
                "분해 검사 ROI 분석 완료 - $judgment"

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
※ 전극 패턴, 분리막 무늬, 문자, 조명 반사도 Edge로 검출될 수 있습니다.
※ 현재 수치는 영상 변화 기반 보조 지표입니다.
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

    /*
     * =========================================================
     * Gray 변환
     * =========================================================
     */

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
