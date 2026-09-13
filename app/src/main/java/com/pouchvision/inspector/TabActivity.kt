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
import com.pouchvision.inspector.databinding.ActivityTabBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class TabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTabBinding

    private var imageCapture: ImageCapture? = null

    /*
     * lastBitmap:
     *   빨간 표시가 없는 깨끗한 원본 사진
     *
     * lastResultBitmap:
     *   TAB 검사 후 빨간 NG 후보가 표시된 결과 사진
     */
    private var lastBitmap: Bitmap? = null
    private var lastResultBitmap: Bitmap? = null

    private var hasInspectionResult = false

    private var lastTabScore = 0.0
    private var lastPositionError = 0.0
    private var lastTiltError = 0.0
    private var lastSpacingError = 0.0
    private var lastLocalDeformation = 0.0
    private var lastJudgment = ""
    private var lastDetails = ""

    /*
     * 현재 TAB 검사 결과의 Telegram 전송 요청 여부
     *
     * - 같은 결과를 여러 번 저장해도 중복 발송 방지
     * - 새 사진 / ROI / 민감도 변경 / 재검사 시 초기화
     * - 네트워크 실패는 기존 TelegramRetryWorker가 재시도
     */
    private var telegramAlertQueuedForCurrentResult = false

    /*
     * 민감도
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

                binding.tvTabStatus.text =
                    "카메라 권한이 필요합니다."
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

        /*
         * TAB 촬영 표준화 가이드
         *
         * 검사 알고리즘이나 판정 기준은 변경하지 않고,
         * 촬영 거리 / 각도 / 조명 / ROI 위치를
         * 일정하게 맞출 수 있도록 안내합니다.
         */
        binding.btnTabCaptureGuide
            .setOnClickListener {

                CaptureGuideHelper.showGuideDialog(
                    context = this,
                    inspectionType = CaptureGuideHelper.TYPE_TAB
                )
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
     * 사진 / ROI / 민감도 / 확대 위치가 바뀌면
     * 이전 검사 결과를 다시 저장하지 못하게 합니다.
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

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
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

        invalidateInspectionResult()

        binding.tabImagePreview.visibility =
            View.GONE

        binding.tabPreviewView.visibility =
            View.VISIBLE

        binding.tvTabStatus.text =
            "카메라 화면 - Tab 영역을 맞춘 뒤 사진을 촬영해주세요."

        resetRoiPosition()

        if (
            imageCapture ==
            null
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

        invalidateInspectionResult()

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

                invalidateInspectionResult()
                restoreOriginalImage()
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

                        invalidateInspectionResult()
                        restoreOriginalImage()

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

                            binding.tabImagePreview.imageMatrix =
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
            binding.tabImagePreview.visibility !=
            View.VISIBLE
        ) {

            return
        }

        binding.tabImagePreview
            .setImageBitmap(
                bitmap
            )

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

            invalidateInspectionResult()
        }
    }

    private fun restoreOriginalImage() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.tabImagePreview.visibility ==
            View.VISIBLE
        ) {

            binding.tabImagePreview
                .setImageBitmap(
                    bitmap
                )

            binding.tabImagePreview.imageMatrix =
                imageMatrixValue
        }
    }

    /*
     * =========================================================
     * ROI 이동 / 크기
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

            invalidateInspectionResult()
            restoreOriginalImage()
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

        binding.tvTabStatus.text =
            "TAB ROI 분석 중..."

        invalidateInspectionResult()
        restoreOriginalImage()

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

                analyzeTabRoi(
                    source = source,
                    roi = roiBitmap,
                    roiStartX = left,
                    roiStartY = top
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
     * TAB 알고리즘
     *
     * 기존 TAB 점수 계산식과 판정 기준은 유지합니다.
     * =========================================================
     */

    private fun analyzeTabRoi(
        source: Bitmap,
        roi: Bitmap,
        roiStartX: Int,
        roiStartY: Int
    ) {

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
         * TAB V2
         * =====================================================
         *
         * TAB 주변의 노란/주황색 실링부를 중심으로
         * 자동 분석 Window를 잡고,
         * 위치 / 실링 균일성 / 경계 / 국부 변형을 분석합니다.
         */
        val v2 =
            TabInspectionV2.analyze(
                roi = roi,
                sensitivity = sensitivity
            )

        val inspectionSpec =
            InspectionSpecStore.getCurrent(
                context = this,
                inspectionType =
                    InspectionSpecStore.InspectionType.TAB
            )

        lastTabScore =
            v2.qualityScore

        lastPositionError =
            v2.alignmentRisk

        lastTiltError =
            v2.sealUniformityRisk

        lastSpacingError =
            v2.boundaryRisk

        lastLocalDeformation =
            v2.localDeformationRisk

        lastJudgment =
            v2.judgment

        lastDetails =
            String.format(
                Locale.getDefault(),

                """
TAB V2

TAB Presence Confidence : %.1f / 100
Alignment Risk : %.1f / 100
Seal Uniformity Risk : %.1f / 100
Boundary Risk : %.1f / 100
Local Deformation Risk : %.1f / 100
Reflection Risk : %.1f / 100

Baseline Deviation : %.1f / 100
Quality Score : %.1f / 100
Final Judgment : %s

%s

정상 Master 운영 기준
- 현재 제공된 TAB 정상 사진 10장을 기준군으로 사용
- TAB 주변 실링부/경계/국부 변형을 우선 검사
- 파우치 전체의 큰 주름과 반사광은 낮은 가중치
- 실제 NG 샘플이 없으므로 주의/한계정상/불량 Threshold는 임시 기준
                """.trimIndent(),

                v2.tabPresenceConfidence,
                v2.alignmentRisk,
                v2.sealUniformityRisk,
                v2.boundaryRisk,
                v2.localDeformationRisk,
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
                "\n\n※ 정상 판정에서는 빨간 NG 후보를 표시하지 않습니다." +
                "\n※ TAB 주변 실링부를 중심으로 V2 분석합니다."

        if (!photoQuality.isUsable) {

            lastDetails +=
                "\n" +
                    photoQuality.message
        }

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

            binding.tabImagePreview.setImageBitmap(
                displayBitmap
            )

            binding.tabImagePreview.imageMatrix =
                imageMatrixValue

            binding.tvTabStatus.text =
                "TAB V2 분석 완료 - ${v2.judgment}"

            binding.tvTabMetrics.text =
                String.format(
                    Locale.getDefault(),

                    """
TAB V2

TAB 인식 Confidence : %.1f / 100
Alignment Risk : %.1f / 100
Seal Uniformity : %.1f / 100
Boundary Risk : %.1f / 100
Local Deformation : %.1f / 100
Reflection Risk : %.1f / 100

Baseline Deviation : %.1f / 100
Quality Score : %.1f / 100

판정 : %s

%s

※ 정상 판정에서는 빨간 NG 후보를 표시하지 않습니다.
※ TAB 주변 실링부와 경계 변화를 우선 분석합니다.
※ 파우치 전체 반사광/큰 주름은 낮은 가중치입니다.
※ 실제 NG 샘플 확보 후 Threshold를 재보정합니다.
                    """.trimIndent(),

                    v2.tabPresenceConfidence,
                    v2.alignmentRisk,
                    v2.sealUniformityRisk,
                    v2.boundaryRisk,
                    v2.localDeformationRisk,
                    v2.reflectionRisk,
                    v2.baselineDeviation,
                    v2.qualityScore,
                    v2.judgment,
                    v2.reason
                )

            binding.tvTabMetrics.append(
                "\n\n" +
                    photoQualityText +
                    "\n※ 사진 품질은 TAB 판정과 별도의 촬영 상태 보조지표입니다."
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
     * TAB Telegram 자동 알림
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
         * 같은 TAB 검사 결과의 중복 발송을 방지합니다.
         */
        telegramAlertQueuedForCurrentResult =
            true

        TelegramSender.sendInspectionAlert(
            context = this,
            inspectionType = "TAB",
            score = lastTabScore,
            judgment = lastJudgment,
            details = lastDetails,
            resultBitmap = lastResultBitmap
        ) { result ->

            runOnUiThread {

                val message =
                    if (
                        result.success
                    ) {

                        "TAB Telegram 자동전송 완료\n" +
                            "성공 ${result.successCount}개 / " +
                            "실패 ${result.failureCount}개"

                    } else {

                        "TAB Telegram 자동전송 실패\n" +
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
     * TAB 검사 결과 + 결과 사진 저장
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

        val resultBitmap =
            lastResultBitmap

        if (
            resultBitmap ==
            null
        ) {

            Toast.makeText(
                this,
                "TAB 결과 사진이 없습니다. TAB 검사를 다시 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = "TAB",
                score = lastTabScore,
                judgment = lastJudgment,
                sensitivity = sensitivity,
                details = lastDetails,

                /*
                 * 새 기능:
                 * 빨간 NG 후보가 표시된 TAB 결과 사진도 함께 저장
                 */
                imageBitmap = resultBitmap
            )

        if (
            success
        ) {

            /*
             * 검사 결과 + 결과 사진 저장이 성공한 경우에만
             * 설정된 Telegram 정책에 따라 1회 자동전송합니다.
             */
            sendTelegramAlertIfNeeded()

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "TAB 검사 결과 + 사진 저장 완료\nScore %.1f / 100\n%s",
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
