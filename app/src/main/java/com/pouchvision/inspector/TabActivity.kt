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
import com.pouchvision.inspector.databinding.ActivityTabBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class TabActivity : AppCompatActivity() {

    private lateinit var binding:
            ActivityTabBinding

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private var imageCapture:
            ImageCapture? = null

    /*
     * =========================================================
     * 현재 이미지
     * =========================================================
     */

    private var lastBitmap:
            Bitmap? = null

    /*
     * =========================================================
     * 검사 결과
     * =========================================================
     */

    private var hasInspectionResult =
        false

    private var lastTabScore =
        0.0

    private var lastPositionError =
        0.0

    private var lastTiltError =
        0.0

    private var lastSpacingError =
        0.0

    private var lastLocalDeformation =
        0.0

    private var lastJudgment =
        ""

    private var lastDetails =
        ""

    /*
     * =========================================================
     * 민감도
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

                loadGalleryImage(
                    uri
                )
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

                binding.tvTabStatus.text =
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

        super.onCreate(
            savedInstanceState
        )

        binding =
            ActivityTabBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()

        binding.btnTabCapture
            .setOnClickListener {

                takePhoto()
            }

        binding.btnTabGallery
            .setOnClickListener {

                galleryLauncher.launch(
                    "image/*"
                )
            }

        binding.btnTabInspect
            .setOnClickListener {

                val bitmap =
                    lastBitmap

                if (
                    bitmap == null ||
                    binding.tabImagePreview.visibility !=
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

        binding.btnTabSaveResult
            .setOnClickListener {

                saveCurrentInspectionResult()
            }

        binding.btnTabRoiWidthSmaller
            .setOnClickListener {

                resizeRoiWidth(
                    0.85f
                )
            }

        binding.btnTabRoiWidthLarger
            .setOnClickListener {

                resizeRoiWidth(
                    1.15f
                )
            }

        binding.btnTabRoiHeightSmaller
            .setOnClickListener {

                resizeRoiHeight(
                    0.85f
                )
            }

        binding.btnTabRoiHeightLarger
            .setOnClickListener {

                resizeRoiHeight(
                    1.15f
                )
            }

        binding.btnTabRoiReset
            .setOnClickListener {

                resetRoiPosition()
            }

        binding.btnTabImageReset
            .setOnClickListener {

                resetImageMatrix()
            }

        binding.btnTabCameraMode
            .setOnClickListener {

                showCameraMode()
            }

        binding.btnTabBack
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
     * =========================================================
     * CameraX 시작
     * =========================================================
     */

    private fun startCamera() {

        binding.tvTabStatus.text =
            "카메라 준비 중..."

        binding.tabPreviewView.visibility =
            View.VISIBLE

        binding.tabImagePreview.visibility =
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
                                    .tabPreviewView
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
                    CameraSelector
                        .DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                binding.tvTabStatus.text =
                    "카메라 준비 완료 - Tab 영역을 촬영해주세요."

            } catch (e: Exception) {

                binding.tvTabStatus.text =
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
                "Tab_$name.jpg"
            )

        val outputOptions =
            ImageCapture
                .OutputFileOptions
                .Builder(
                    photoFile
                )
                .build()

        binding.tvTabStatus.text =
            "TAB 사진 촬영 중..."

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

                    try {

                        val bitmap =
                            decodeBitmapFromFile(
                                photoFile
                            )

                        if (bitmap == null) {

                            binding.tvTabStatus.text =
                                "촬영된 사진을 불러올 수 없습니다."

                            return
                        }

                        showSelectedImage(
                            bitmap
                        )

                        binding.tvTabStatus.text =
                            "촬영 완료 - Tab과 주변 Seal이 ROI에 들어오도록 맞춰주세요."

                    } catch (e: Exception) {

                        binding.tvTabStatus.text =
                            "촬영 사진 처리 오류: ${e.message}"
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvTabStatus.text =
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
     * 카메라 화면 복귀
     * =========================================================
     */

    private fun showCameraMode() {

        hasInspectionResult =
            false

        binding.tabImagePreview.visibility =
            View.GONE

        binding.tabPreviewView.visibility =
            View.VISIBLE

        binding.tvTabStatus.text =
            "카메라 화면 - Tab 영역을 맞춘 뒤 사진을 촬영해주세요."

        resetRoiPosition()

        if (
            imageCapture == null
        ) {

            startCamera()
        }
    }

    /*
     * =========================================================
     * 갤러리 사진
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

            showSelectedImage(
                bitmap
            )

            binding.tvTabStatus.text =
                "사진 선택 완료 - Tab과 주변 Seal이 ROI에 들어오도록 맞춰주세요."

        } catch (e: Exception) {

            binding.tvTabStatus.text =
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
     * 이미지 표시
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap =
            bitmap

        hasInspectionResult =
            false

        binding.tabPreviewView.visibility =
            View.GONE

        binding.tabImagePreview.visibility =
            View.VISIBLE

        binding.tabImagePreview
            .setImageBitmap(
                bitmap
            )

        resetImageMatrix()
        resetRoiPosition()
        resetResultDisplay()
    }

    /*
     * =========================================================
     * 결과 초기화
     * =========================================================
     */

    private fun resetResultDisplay() {

        binding.tvTabMetrics.text =
            """
Position Error    : -
Tilt Error        : -
Spacing Error     : -
Local Deformation : -
Tab Score         : -

판정 : -
            """.trimIndent()
    }

    /*
     * =========================================================
     * 결과 저장
     * =========================================================
     */

    private fun saveCurrentInspectionResult() {

        if (
            !hasInspectionResult
        ) {

            Toast.makeText(
                this,
                "먼저 TAB ROI 검사를 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context =
                    this,

                inspectionType =
                    "TAB",

                score =
                    lastTabScore,

                judgment =
                    lastJudgment,

                sensitivity =
                    sensitivity,

                details =
                    lastDetails
            )

        if (success) {

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "TAB 검사 결과 저장 완료\nScore %.1f / 100\n%s",
                    lastTabScore,
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

                hasInspectionResult =
                    false

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

                        binding.tabImagePreview.imageMatrix =
                            imageMatrixValue

                        hasInspectionResult =
                            false

                        return true
                    }
                }
            )

        binding.tabImagePreview
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

                            binding.tabImagePreview.imageMatrix =
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
     * 이미지 원래크기
     * =========================================================
     */

    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.tabImagePreview.visibility !=
            View.VISIBLE
        ) {

            return
        }

        binding.tabImagePreview.post {

            val viewWidth =
                binding.tabImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding.tabImagePreview
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

            binding.tabImagePreview.imageMatrix =
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

        binding.tabRoiGuide
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
                                    .coerceAtLeast(
                                        0
                                    )
                                    .toFloat()
                            )

                        newY =
                            newY.coerceIn(
                                0f,
                                maxY
                                    .coerceAtLeast(
                                        0
                                    )
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
            binding.tabRoiGuide

        val parent =
            binding.tabImageArea

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

        binding.tabImageArea.post {

            val roi =
                binding.tabRoiGuide

            val parent =
                binding.tabImageArea

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
     * ROI 분석 시작
     * =========================================================
     */

    private fun analyzeSelectedRoi(
        source: Bitmap
    ) {

        binding.tvTabStatus.text =
            "TAB ROI 분석 중..."

        hasInspectionResult =
            false

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

                analyzeTabRoi(
                    source,
                    roiBitmap,
                    left,
                    top
                )

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvTabStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    /*
     * =========================================================
     * TAB 분석
     * 기존 공식 유지
     * =========================================================
     */

    private fun analyzeTabRoi(
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

        var strongEdgeCount =
            0L

        var pixelCount =
            0L

        var leftGradient =
            0L

        var rightGradient =
            0L

        var topGradient =
            0L

        var bottomGradient =
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
            roi.width
                .toFloat() /
                analysisWidth
                    .toFloat()

        val yScale =
            roi.height
                .toFloat() /
                analysisHeight
                    .toFloat()

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

                totalGradient +=
                    gradient

                pixelCount++

                if (
                    x <
                    small.width /
                        2
                ) {

                    leftGradient +=
                        gradient

                } else {

                    rightGradient +=
                        gradient
                }

                if (
                    y <
                    small.height /
                        2
                ) {

                    topGradient +=
                        gradient

                } else {

                    bottomGradient +=
                        gradient
                }

                if (
                    gradient >
                    strongThreshold
                ) {

                    strongEdgeCount++

                    if (
                        x % 4 ==
                        0 &&
                        y % 4 ==
                        0
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
                            originalX
                                .toFloat(),
                            originalY
                                .toFloat(),
                            markRadius,
                            redPaint
                        )
                    }
                }
            }
        }

        val averageGradient =
            if (
                pixelCount >
                0
            ) {

                totalGradient
                    .toDouble() /
                    pixelCount
                        .toDouble()

            } else {

                0.0
            }

        val strongEdgeDensity =
            if (
                pixelCount >
                0
            ) {

                strongEdgeCount
                    .toDouble() /
                    pixelCount
                        .toDouble() *
                    100.0

            } else {

                0.0
            }

        val horizontalBalance =
            if (
                totalGradient >
                0
            ) {

                abs(
                    leftGradient -
                        rightGradient
                )
                    .toDouble() /
                    totalGradient
                        .toDouble() *
                    100.0

            } else {

                0.0
            }

        val verticalBalance =
            if (
                totalGradient >
                0
            ) {

                abs(
                    topGradient -
                        bottomGradient
                )
                    .toDouble() /
                    totalGradient
                        .toDouble() *
                    100.0

            } else {

                0.0
            }

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
                    1.2
                ) *
                sensitivityFactor

        val spacingError =
            (
                horizontalBalance *
                    0.85 +
                    strongEdgeDensity *
                    0.5
                ) *
                sensitivityFactor

        val localDeformation =
            (
                averageGradient *
                    0.8 +
                    strongEdgeDensity *
                    2.0
                ) *
                sensitivityFactor

        val defectLevel =
            (
                positionError *
                    0.30 +
                    tiltError *
                    0.25 +
                    spacingError *
                    0.25 +
                    localDeformation *
                    0.20
                )

        val tabScore =
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

                tabScore >=
                    85 ->
                    "정상 후보"

                tabScore >=
                    70 ->
                    "주의 후보"

                tabScore >=
                    50 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }

        lastTabScore =
            tabScore

        lastPositionError =
            positionError

        lastTiltError =
            tiltError

        lastSpacingError =
            spacingError

        lastLocalDeformation =
            localDeformation

        lastJudgment =
            judgment

        lastDetails =
            String.format(
                Locale.getDefault(),

                """
Position Error : %.1f
Tilt Error : %.1f
Spacing Error : %.1f
Local Deformation : %.1f
Tab Score : %.1f / 100
Sensitivity : %d%%
                """.trimIndent(),

                positionError,
                tiltError,
                spacingError,
                localDeformation,
                tabScore,
                sensitivity
            )

        hasInspectionResult =
            true

        runOnUiThread {

            lastBitmap =
                markedBitmap

            binding.tabImagePreview
                .setImageBitmap(
                    markedBitmap
                )

            binding.tabImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvTabStatus.text =
                "TAB ROI 분석 완료 - $judgment"

            binding.tvTabMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
민감도           : %d%%
Position Error   : %.1f
Tilt Error       : %.1f
Spacing Error    : %.1f
Local Deformation: %.1f
Tab Score        : %.1f / 100

판정 : %s

빨간 표시 : 국부 변화가 큰 위치 후보

※ 현재 Position / Tilt / Spacing 값은 Edge 분포 기반 보조 지표입니다.
※ 빨간 표시 자체가 실제 불량 확정 위치를 의미하지 않습니다.
※ 실제 Tab 위치·각도·간격의 치수 판정에는 기준 형상과 Calibration이 필요합니다.
※ 현재 판정 기준은 기준 학습 전 임시값입니다.
                    """.trimIndent(),

                    sensitivity,
                    positionError,
                    tiltError,
                    spacingError,
                    localDeformation,
                    tabScore,
                    judgment
                )
        }
    }

    /*
     * =========================================================
     * Gray
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
