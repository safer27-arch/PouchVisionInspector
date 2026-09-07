package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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
import com.pouchvision.inspector.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    /*
     * 항상 깨끗한 원본 사진만 보관
     */
    private var lastBitmap: Bitmap? = null

    /*
     * 검사 결과
     */
    private var hasInspectionResult = false

    private var lastResultScore = 0.0

    private var lastResultJudgment = ""

    private var lastResultDetails = ""

    /*
     * 민감도
     */
    private val preferenceName =
        "pouch_vision_settings"

    private val sensitivityKey =
        "bottom_corner_sensitivity"

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

                loadGalleryImage(
                    uri
                )
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

                binding.tvStatus.text =
                    "카메라 권한이 필요합니다."
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityMainBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupSensitivity()

        setupImageZoom()

        setupRoiDrag()

        /*
         * 사진 촬영
         */
        binding.btnCapture
            .setOnClickListener {

                takePhoto()
            }

        /*
         * 갤러리
         */
        binding.btnGallery
            .setOnClickListener {

                galleryLauncher.launch(
                    "image/*"
                )
            }

        /*
         * Bottom Corner 검사
         */
        binding.btnInspect
            .setOnClickListener {

                val bitmap =
                    lastBitmap

                if (
                    bitmap == null ||
                    binding.imagePreview.visibility !=
                    View.VISIBLE
                ) {

                    Toast.makeText(
                        this,
                        "먼저 사진을 촬영하거나 선택해주세요.",
                        Toast.LENGTH_LONG
                    ).show()

                } else {

                    analyzeSelectedRoi(
                        bitmap
                    )
                }
            }

        /*
         * 결과 저장
         */
        binding.btnSaveResult
            .setOnClickListener {

                saveCurrentInspectionResult()
            }

        /*
         * ROI 가로 -
         */
        binding.btnRoiWidthSmaller
            .setOnClickListener {

                resizeRoiWidth(
                    0.85f
                )
            }

        /*
         * ROI 가로 +
         */
        binding.btnRoiWidthLarger
            .setOnClickListener {

                resizeRoiWidth(
                    1.15f
                )
            }

        /*
         * ROI 세로 -
         */
        binding.btnRoiHeightSmaller
            .setOnClickListener {

                resizeRoiHeight(
                    0.85f
                )
            }

        /*
         * ROI 세로 +
         */
        binding.btnRoiHeightLarger
            .setOnClickListener {

                resizeRoiHeight(
                    1.15f
                )
            }

        /*
         * ROI 중앙
         */
        binding.btnRoiReset
            .setOnClickListener {

                resetRoiPosition()
            }

        /*
         * 사진 원래 크기
         */
        binding.btnImageReset
            .setOnClickListener {

                resetImageMatrix()
            }

        /*
         * 카메라 모드
         */
        binding.btnCameraMode
            .setOnClickListener {

                showCameraMode()
            }

        /*
         * 카메라 권한
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

        binding.seekSensitivity.progress =
            sensitivity

        updateSensitivityText()

        binding.seekSensitivity
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

        binding.btnSensitivityReset
            .setOnClickListener {

                sensitivity =
                    defaultSensitivity

                binding.seekSensitivity.progress =
                    defaultSensitivity

                prefs.edit()
                    .putInt(
                        sensitivityKey,
                        defaultSensitivity
                    )
                    .apply()

                hasInspectionResult =
                    false

                restoreOriginalImage()

                updateSensitivityText()

                Toast.makeText(
                    this,
                    "Bottom Corner 민감도를 60%로 복원했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateSensitivityText() {

        binding.tvSensitivityValue.text =
            "현재 민감도 : ${sensitivity}%"
    }

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private fun startCamera() {

        binding.previewView.visibility =
            View.VISIBLE

        binding.imagePreview.visibility =
            View.GONE

        binding.tvStatus.text =
            "카메라 준비 중..."

        val cameraProviderFuture =
            ProcessCameraProvider
                .getInstance(
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
                                    .previewView
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

                binding.tvStatus.text =
                    "카메라 준비 완료 - Bottom Corner를 촬영해주세요."

            } catch (e: Exception) {

                binding.tvStatus.text =
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

        val contentValues =
            ContentValues().apply {

                put(
                    MediaStore
                        .MediaColumns
                        .DISPLAY_NAME,
                    "PouchVision_$name"
                )

                put(
                    MediaStore
                        .MediaColumns
                        .MIME_TYPE,
                    "image/jpeg"
                )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    put(
                        MediaStore
                            .Images
                            .Media
                            .RELATIVE_PATH,
                        "Pictures/PouchVision"
                    )
                }
            }

        val outputOptions =
            ImageCapture
                .OutputFileOptions
                .Builder(
                    contentResolver,
                    MediaStore
                        .Images
                        .Media
                        .EXTERNAL_CONTENT_URI,
                    contentValues
                )
                .build()

        binding.tvStatus.text =
            "사진 촬영 중..."

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(
                this
            ),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    val uri =
                        outputFileResults.savedUri

                    if (uri != null) {

                        loadGalleryImage(
                            uri
                        )

                    } else {

                        binding.tvStatus.text =
                            "사진은 저장되었지만 이미지를 다시 불러오지 못했습니다."
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvStatus.text =
                        "촬영 오류: ${exception.message}"
                }
            }
        )
    }

    /*
     * =========================================================
     * 갤러리 이미지
     * =========================================================
     */

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvStatus.text =
            "사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(
                    uri
                )

            if (bitmap == null) {

                binding.tvStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            showSelectedImage(
                bitmap
            )

            binding.tvStatus.text =
                "사진 선택 완료 - ROI를 Bottom Corner 주름 부위에 맞춰주세요."

        } catch (e: Exception) {

            binding.tvStatus.text =
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
            .openInputStream(
                uri
            )
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
            .openInputStream(
                uri
            )
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
     * 선택 사진 표시
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap =
            bitmap

        hasInspectionResult =
            false

        binding.previewView.visibility =
            View.GONE

        binding.imagePreview.visibility =
            View.VISIBLE

        binding.imagePreview.setImageBitmap(
            bitmap
        )

        resetImageMatrix()

        resetRoiPosition()

        binding.tvStatus.text =
            "ROI를 Bottom Corner 주름 부위에 맞춰주세요."
    }

    private fun showCameraMode() {

        hasInspectionResult =
            false

        binding.imagePreview.visibility =
            View.GONE

        binding.previewView.visibility =
            View.VISIBLE

        binding.tvStatus.text =
            "카메라 화면 - Bottom Corner를 맞춘 뒤 촬영해주세요."

        resetRoiPosition()

        if (
            imageCapture == null
        ) {

            startCamera()
        }
    }

    private fun restoreOriginalImage() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.imagePreview.visibility ==
            View.VISIBLE
        ) {

            binding.imagePreview.setImageBitmap(
                bitmap
            )

            binding.imagePreview.imageMatrix =
                imageMatrixValue
        }
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

                        binding.imagePreview.imageMatrix =
                            imageMatrixValue

                        hasInspectionResult =
                            false

                        restoreOriginalImage()

                        return true
                    }
                }
            )

        binding.imagePreview
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

                            binding.imagePreview.imageMatrix =
                                imageMatrixValue

                            hasInspectionResult =
                                false

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
            binding.imagePreview.visibility !=
            View.VISIBLE
        ) {

            return
        }

        binding.imagePreview.setImageBitmap(
            bitmap
        )

        binding.imagePreview.post {

            val viewWidth =
                binding.imagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding.imagePreview
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

            binding.imagePreview.imageMatrix =
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

        binding.roiGuide
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

                        val parent =
                            binding.imageArea

                        var newX =
                            view.x +
                                dx

                        var newY =
                            view.y +
                                dy

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

                        hasInspectionResult =
                            false

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

    /*
     * =========================================================
     * ROI 가로 크기
     * =========================================================
     */

    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.roiGuide

        val parent =
            binding.imageArea

        if (
            parent.width <= 0
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

        hasInspectionResult =
            false

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

    /*
     * =========================================================
     * ROI 세로 크기
     * =========================================================
     */

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.roiGuide

        val parent =
            binding.imageArea

        if (
            parent.height <= 0
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

        hasInspectionResult =
            false

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

    /*
     * =========================================================
     * ROI 중앙
     * =========================================================
     */

    private fun resetRoiPosition() {

        binding.imageArea.post {

            val roi =
                binding.roiGuide

            val parent =
                binding.imageArea

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

        binding.tvStatus.text =
            "Bottom Corner 주름 분석 중..."

        hasInspectionResult =
            false

        restoreOriginalImage()

        val screenRect =
            RectF(
                binding.roiGuide.x,
                binding.roiGuide.y,
                binding.roiGuide.x +
                    binding.roiGuide.width,
                binding.roiGuide.y +
                    binding.roiGuide.height
            )

        val currentMatrix =
            Matrix(
                binding.imagePreview.imageMatrix
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
                    roiWidth < 20 ||
                    roiHeight < 20
                ) {

                    throw Exception(
                        "ROI를 사진 안쪽에 맞춰주세요."
                    )
                }

                runBottomCornerAnalysis(
                    source = source,
                    roiLeft = left,
                    roiTop = top,
                    roiWidth = roiWidth,
                    roiHeight = roiHeight
                )

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    /*
     * =========================================================
     * Bottom Corner 전용 판정
     * =========================================================
     */

    private fun runBottomCornerAnalysis(
        source: Bitmap,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int
    ) {

        val result =
            BottomCornerDefectMarker.analyze(
                sourceBitmap = source,
                roiLeft = roiLeft,
                roiTop = roiTop,
                roiWidth = roiWidth,
                roiHeight = roiHeight,
                sensitivity = sensitivity,
                maxRegions = 4
            )

        /*
         * 품질 Score
         *
         * Wrinkle Score가 높을수록 좋지 않으므로
         * Quality Score는 반대로 계산
         */
        val qualityScore =
            (
                100.0 -
                    result.wrinkleScore
                )
                .coerceIn(
                    0.0,
                    100.0
                )

        lastResultScore =
            qualityScore

        lastResultJudgment =
            result.judgment

        lastResultDetails =
            """
Bottom Corner 전용 판정

Wrinkle Score : ${"%.1f".format(result.wrinkleScore)} / 100
Quality Score : ${"%.1f".format(qualityScore)} / 100
판정 : ${result.judgment}

Line Density : ${"%.1f".format(result.lineDensity)}%
Local Contrast : ${"%.1f".format(result.localContrast)}
Concentration : ${"%.1f".format(result.concentration)}
주름 후보 영역 : ${result.regions.size}개

민감도 : ${sensitivity}%

※ 정상 / 주의 / 한계정상은 현재 확보된 실제 샘플을
   기준으로 한 초기 튜닝값입니다.

※ 불량 후보 기준은 실제 불량 샘플이 아직 없어
   현재 한계정상 수준을 초과하는 경우를 임시 기준으로 사용합니다.
            """.trimIndent()

        hasInspectionResult =
            true

        runOnUiThread {

            /*
             * 빨간 표시 이미지만 화면에 표시
             * lastBitmap 원본은 그대로 유지
             */
            binding.imagePreview.setImageBitmap(
                result.bitmap
            )

            binding.imagePreview.imageMatrix =
                imageMatrixValue

            binding.tvStatus.text =
                """
BOTTOM CORNER 검사 완료

Wrinkle Score : ${"%.1f".format(result.wrinkleScore)} / 100
Quality Score : ${"%.1f".format(qualityScore)} / 100

판정 : ${result.judgment}

주름 후보 영역 : ${result.regions.size}개

Line Density : ${"%.1f".format(result.lineDensity)}%
Local Contrast : ${"%.1f".format(result.localContrast)}
Concentration : ${"%.1f".format(result.concentration)}

민감도 : ${sensitivity}%

판정 기준
정상       : Wrinkle Score < 27
주의       : 27 ~ 46.9
한계정상   : 47 ~ 67.9
불량 후보  : 68 이상

※ 넓고 완만한 음영은 가급적 감점하도록 설계했습니다.
※ 빨간 표시 = 주름 의심 후보이며 확정 불량은 아닙니다.
※ 실제 불량 Sample 확보 후 불량 기준은 다시 보정할 수 있습니다.
                """.trimIndent()
        }
    }

    /*
     * =========================================================
     * 검사 결과 저장
     * =========================================================
     */

    private fun saveCurrentInspectionResult() {

        if (
            !hasInspectionResult
        ) {

            Toast.makeText(
                this,
                "먼저 Bottom Corner ROI 검사를 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = "BOTTOM CORNER",
                score = lastResultScore,
                judgment = lastResultJudgment,
                sensitivity = sensitivity,
                details = lastResultDetails
            )

        if (success) {

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "검사 결과 저장 완료\nQuality Score %.1f / 100\n%s",
                    lastResultScore,
                    lastResultJudgment
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
}
