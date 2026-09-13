package com.pouchvision.inspector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.pouchvision.inspector.databinding.ActivitySealBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class SealActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySealBinding

    /*
     * =========================================================
     * CameraX
     * =========================================================
     */

    private var imageCapture: ImageCapture? = null

    /*
     * =========================================================
     * 사진 / 검사 결과
     * =========================================================
     *
     * lastBitmap:
     *   항상 빨간 표시가 없는 깨끗한 원본 사진
     *
     * lastResultBitmap:
     *   SEAL 검사 후 빨간 NG 후보 표시가 포함된 결과 사진
     *
     * 검사 결과 저장 시 lastResultBitmap을
     * InspectionHistoryStore에 함께 저장합니다.
     * =========================================================
     */

    private var lastBitmap: Bitmap? = null
    private var lastResultBitmap: Bitmap? = null

    private var hasInspectionResult = false

    private var lastSealScore = 0.0
    private var lastSealUniformity = 0.0
    private var lastEdgeDensity = 0.0
    private var lastStrongEdgeDensity = 0.0
    private var lastEdgeStrength = 0.0

    private var lastJudgment = ""
    private var lastDetails = ""

    /*
     * 현재 SEAL 검사 결과의 Telegram 전송 요청 여부입니다.
     * 같은 결과의 중복 저장/발송을 막고,
     * 새 사진·ROI·민감도 변경·재검사 시 초기화합니다.
     */
    private var telegramAlertQueuedForCurrentResult = false

    /*
     * =========================================================
     * 민감도
     * =========================================================
     */

    private val preferenceName =
        "pouch_vision_settings"

    private val sensitivityKey =
        "seal_sensitivity"

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


    private enum class SealRoiSide {
        LEFT,
        RIGHT
    }

    private var selectedSealRoiSide =
        SealRoiSide.LEFT

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

                binding.tvSealStatus.text =
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
            ActivitySealBinding.inflate(
                layoutInflater
            )

        setContentView(
            binding.root
        )

        setupSensitivity()
        setupImageZoom()
        setupRoiDrag()
        setupDualRoiControls()
        resetDualRoiPositions()

        /*
         * 사진 촬영
         */
        binding.btnSealCapture
            .setOnClickListener {

                takePhoto()
            }

        /*
         * 갤러리 선택
         */
        binding.btnSealGallery
            .setOnClickListener {

                galleryLauncher.launch(
                    "image/*"
                )
            }

        /*
         * SEAL 검사
         */
        binding.btnSealInspect
            .setOnClickListener {

                val bitmap =
                    lastBitmap

                if (
                    bitmap == null ||
                    binding.sealImagePreview.visibility !=
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
         * 검사 결과 + 결과 사진 저장
         */
        binding.btnSealSaveResult
            .setOnClickListener {

                saveCurrentInspectionResult()
            }

        /*
         * ROI 가로
         */
        binding.btnSealRoiWidthSmaller
            .setOnClickListener {

                resizeRoiWidth(
                    0.85f
                )
            }

        binding.btnSealRoiWidthLarger
            .setOnClickListener {

                resizeRoiWidth(
                    1.15f
                )
            }

        /*
         * ROI 세로
         */
        binding.btnSealRoiHeightSmaller
            .setOnClickListener {

                resizeRoiHeight(
                    0.85f
                )
            }

        binding.btnSealRoiHeightLarger
            .setOnClickListener {

                resizeRoiHeight(
                    1.15f
                )
            }

        /*
         * ROI 중앙
         */
        binding.btnSealRoiReset
            .setOnClickListener {

                resetDualRoiPositions()
            }

        /*
         * 사진 원래크기
         */
        binding.btnSealImageReset
            .setOnClickListener {

                resetImageMatrix()
            }

        /*
         * 카메라 화면으로 돌아가기
         */
        binding.btnSealCameraMode
            .setOnClickListener {

                showCameraMode()
            }

        /*
         * SEAL 촬영 표준화 가이드
         *
         * 검사 알고리즘이나 판정 기준은 변경하지 않고,
         * 촬영 거리 / 각도 / 조명 / ROI 위치를
         * 일정하게 맞출 수 있도록 안내합니다.
         */
        binding.btnSealCaptureGuide
            .setOnClickListener {

                CaptureGuideHelper.showGuideDialog(
                    context = this,
                    inspectionType = CaptureGuideHelper.TYPE_SEAL
                )
            }

        /*
         * 뒤로가기
         */
        binding.btnSealBack
            .setOnClickListener {

                finish()
            }

        /*
         * 카메라 권한 확인
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
     * 이전 검사 결과 무효화
     * =========================================================
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
     * CameraX 시작
     * =========================================================
     */

    private fun startCamera() {

        binding.tvSealStatus.text =
            "카메라 준비 중..."

        binding.sealPreviewView.visibility =
            View.VISIBLE

        binding.sealImagePreview.visibility =
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
                                    .sealPreviewView
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

                binding.tvSealStatus.text =
                    "카메라 준비 완료 - Seal 영역을 촬영해주세요."

            } catch (e: Exception) {

                binding.tvSealStatus.text =
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
                "Seal_$name.jpg"
            )

        val outputOptions =
            ImageCapture
                .OutputFileOptions
                .Builder(
                    photoFile
                )
                .build()

        binding.tvSealStatus.text =
            "SEAL 사진 촬영 중..."

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(
                this
            ),

            object :
                ImageCapture
                    .OnImageSavedCallback {

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

                            binding.tvSealStatus.text =
                                "촬영된 사진을 불러올 수 없습니다."

                            return
                        }

                        showSelectedImage(
                            bitmap
                        )

                        binding.tvSealStatus.text =
                            "촬영 완료 - 검사할 Seal Line에 ROI를 맞춰주세요."

                    } catch (e: Exception) {

                        binding.tvSealStatus.text =
                            "촬영 사진 처리 오류: ${e.message}"
                    }
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvSealStatus.text =
                        "촬영 오류: ${exception.message}"
                }
            }
        )
    }

    /*
     * =========================================================
     * 파일 Bitmap
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
     * 카메라 화면
     * =========================================================
     */

    private fun showCameraMode() {

        invalidateInspectionResult()

        binding.sealImagePreview.visibility =
            View.GONE

        binding.sealPreviewView.visibility =
            View.VISIBLE

        binding.tvSealStatus.text =
            "카메라 화면 - Seal 영역을 맞춘 뒤 사진을 촬영해주세요."

        resetDualRoiPositions()

        if (
            imageCapture ==
            null
        ) {

            startCamera()
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

        binding.tvSealStatus.text =
            "SEAL 사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(
                    uri
                )

            if (bitmap == null) {

                binding.tvSealStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            showSelectedImage(
                bitmap
            )

            binding.tvSealStatus.text =
                "사진 선택 완료 - 검사할 Seal Line에 ROI를 맞춰주세요."

        } catch (e: Exception) {

            binding.tvSealStatus.text =
                "사진 불러오기 오류: ${e.message}"
        }
    }

    /*
     * =========================================================
     * URI Bitmap
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
     * 사진 표시
     * =========================================================
     */

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        lastBitmap =
            bitmap

        invalidateInspectionResult()

        binding.sealPreviewView.visibility =
            View.GONE

        binding.sealImagePreview.visibility =
            View.VISIBLE

        binding.sealImagePreview
            .setImageBitmap(
                bitmap
            )

        resetImageMatrix()
        resetDualRoiPositions()
        resetResultDisplay()
    }

    /*
     * =========================================================
     * 결과 표시 초기화
     * =========================================================
     */

    private fun resetResultDisplay() {

        binding.tvSealMetrics.text =
            """
Seal Uniformity : -
Edge Density    : -
Strong Edge     : -
Edge Strength   : -
Seal Score      : -

판정 : -

※ 실제 Seal Width(mm)는 Calibration이 필요합니다.
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
            ).coerceIn(
                0,
                100
            )

        binding.seekSealSensitivity.progress =
            sensitivity

        updateSensitivityText()

        binding.seekSealSensitivity
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

        binding.btnSealSensitivityReset
            .setOnClickListener {

                sensitivity =
                    defaultSensitivity

                binding.seekSealSensitivity.progress =
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
                    "SEAL 민감도를 60%로 복원했습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun updateSensitivityText() {

        binding.tvSealSensitivityValue.text =
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

                        binding.sealImagePreview.imageMatrix =
                            imageMatrixValue

                        invalidateInspectionResult()
                        restoreOriginalImage()

                        return true
                    }
                }
            )

        binding.sealImagePreview
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

                            binding.sealImagePreview.imageMatrix =
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
            binding.sealImagePreview.visibility !=
            View.VISIBLE
        ) {

            return
        }

        binding.sealImagePreview
            .setImageBitmap(
                bitmap
            )

        binding.sealImagePreview.post {

            val viewWidth =
                binding.sealImagePreview
                    .width
                    .toFloat()

            val viewHeight =
                binding.sealImagePreview
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

            binding.sealImagePreview.imageMatrix =
                imageMatrixValue

            invalidateInspectionResult()
        }
    }

    /*
     * =========================================================
     * 원본 사진으로 복원
     * =========================================================
     */

    private fun restoreOriginalImage() {

        val bitmap =
            lastBitmap
                ?: return

        if (
            binding.sealImagePreview.visibility ==
            View.VISIBLE
        ) {

            binding.sealImagePreview
                .setImageBitmap(
                    bitmap
                )

            binding.sealImagePreview.imageMatrix =
                imageMatrixValue
        }
    }

    /*
     * =========================================================
     * ROI 손가락 이동
     * =========================================================
     */

    private fun setupDualRoiControls() {

        binding.btnSealSelectLeft
            .setOnClickListener {
                selectedSealRoiSide =
                    SealRoiSide.LEFT

                updateSelectedRoiUi()
            }

        binding.btnSealSelectRight
            .setOnClickListener {
                selectedSealRoiSide =
                    SealRoiSide.RIGHT

                updateSelectedRoiUi()
            }

        updateSelectedRoiUi()
    }

    private fun updateSelectedRoiUi() {

        val leftSelected =
            selectedSealRoiSide ==
                SealRoiSide.LEFT

        binding.btnSealSelectLeft.alpha =
            if (leftSelected) 1.0f else 0.55f

        binding.btnSealSelectRight.alpha =
            if (leftSelected) 0.55f else 1.0f

        binding.tvSealGuide.text =
            if (leftSelected) {
                "LEFT ROI 선택됨 - 좌측 실링부에 맞춰주세요"
            } else {
                "RIGHT ROI 선택됨 - 우측 실링부에 맞춰주세요"
            }
    }

    private fun selectedRoiView(): View {

        return if (
            selectedSealRoiSide ==
            SealRoiSide.LEFT
        ) {
            binding.sealRoiGuide
        } else {
            binding.sealRoiGuideRight
        }
    }

    private fun setupRoiDrag() {

        setupSingleRoiDrag(
            binding.sealRoiGuide,
            SealRoiSide.LEFT
        )

        setupSingleRoiDrag(
            binding.sealRoiGuideRight,
            SealRoiSide.RIGHT
        )
    }

    private fun setupSingleRoiDrag(
        roiView: View,
        side: SealRoiSide
    ) {

        roiView.setOnTouchListener {
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

                    selectedSealRoiSide =
                        side

                    updateSelectedRoiUi()

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
                        binding.sealImageArea

                    var newX =
                        view.x +
                            dx

                    var newY =
                        view.y +
                            dy

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

                    updateRoiLabels()

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
            selectedRoiView()

        val parent =
            binding.sealImageArea

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
                55,
                max(
                    55,
                    (
                        parent.width *
                            0.48f
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

            updateRoiLabels()
        }
    }

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            selectedRoiView()

        val parent =
            binding.sealImageArea

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

            updateRoiLabels()
        }
    }

    private fun resetDualRoiPositions() {

        binding.sealImageArea.post {

            val parent =
                binding.sealImageArea

            val left =
                binding.sealRoiGuide

            val right =
                binding.sealRoiGuideRight

            val centerYLeft =
                (
                    parent.height -
                        left.height
                    ) /
                    2f

            val centerYRight =
                (
                    parent.height -
                        right.height
                    ) /
                    2f

            left.x =
                (
                    parent.width *
                        0.20f -
                        left.width /
                            2f
                    )
                    .coerceIn(
                        0f,
                        (
                            parent.width -
                                left.width
                            )
                            .coerceAtLeast(
                                0
                            )
                            .toFloat()
                    )

            right.x =
                (
                    parent.width *
                        0.80f -
                        right.width /
                            2f
                    )
                    .coerceIn(
                        0f,
                        (
                            parent.width -
                                right.width
                            )
                            .coerceAtLeast(
                                0
                            )
                            .toFloat()
                    )

            left.y =
                centerYLeft

            right.y =
                centerYRight

            updateRoiLabels()

            invalidateInspectionResult()
            restoreOriginalImage()
        }
    }

    private fun updateRoiLabels() {

        val left =
            binding.sealRoiGuide

        val right =
            binding.sealRoiGuideRight

        binding.tvSealRoiLeftLabel.x =
            left.x +
                4f

        binding.tvSealRoiLeftLabel.y =
            left.y +
                4f

        binding.tvSealRoiRightLabel.x =
            right.x +
                4f

        binding.tvSealRoiRightLabel.y =
            right.y +
                4f
    }

    private fun getBitmapRoi(
        bitmap: Bitmap,
        roi: View
    ): RectF? {

        if (
            binding.sealImagePreview.visibility !=
            View.VISIBLE
        ) {

            return null
        }

        val imageView =
            binding.sealImagePreview

        val roiRectOnView =
            RectF(
                roi.x,
                roi.y,
                roi.x +
                    roi.width,
                roi.y +
                    roi.height
            )

        val inverse =
            Matrix()

        if (
            !imageView.imageMatrix.invert(
                inverse
            )
        ) {

            return null
        }

        val bitmapRect =
            RectF(
                roiRectOnView
            )

        inverse.mapRect(
            bitmapRect
        )

        bitmapRect.left =
            bitmapRect.left
                .coerceIn(
                    0f,
                    bitmap.width.toFloat()
                )

        bitmapRect.top =
            bitmapRect.top
                .coerceIn(
                    0f,
                    bitmap.height.toFloat()
                )

        bitmapRect.right =
            bitmapRect.right
                .coerceIn(
                    0f,
                    bitmap.width.toFloat()
                )

        bitmapRect.bottom =
            bitmapRect.bottom
                .coerceIn(
                    0f,
                    bitmap.height.toFloat()
                )

        if (
            bitmapRect.width() <
            10f ||
            bitmapRect.height() <
            10f
        ) {

            return null
        }

        return bitmapRect
    }

    private data class SealSideResult(
        val side: String,
        val left: Int,
        val top: Int,
        val width: Int,
        val height: Int,
        val result: SealInspectionV2.Result,
        val baseline: SealBaselineV2.Evaluation,
        val photoQualityText: String
    )

    private fun analyzeSealSide(
        source: Bitmap,
        roiRect: RectF,
        side: String
    ): SealSideResult {

        val left =
            roiRect.left
                .toInt()
                .coerceIn(
                    0,
                    source.width -
                        1
                )

        val top =
            roiRect.top
                .toInt()
                .coerceIn(
                    0,
                    source.height -
                        1
                )

        val right =
            roiRect.right
                .toInt()
                .coerceIn(
                    left +
                        1,
                    source.width
                )

        val bottom =
            roiRect.bottom
                .toInt()
                .coerceIn(
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

        val roiBitmap =
            Bitmap.createBitmap(
                source,
                left,
                top,
                roiWidth,
                roiHeight
            )

        val photoQuality =
            ImageQualityChecker.analyzeBitmap(
                roiBitmap
            )

        val photoQualityText =
            String.format(
                Locale.getDefault(),
                "%s 사진 품질 : %s (%.1f / 100)\\n밝기 %.1f | 명암 %.1f | 선명도 %.1f",
                side,
                photoQuality.status,
                photoQuality.qualityScore,
                photoQuality.averageBrightness,
                photoQuality.contrast,
                photoQuality.sharpness
            )

        val v2 =
            SealInspectionV2.analyze(
                sourceBitmap = source,
                roiLeft = left,
                roiTop = top,
                roiWidth = roiWidth,
                roiHeight = roiHeight,
                sensitivity = sensitivity
            )

        val baseline =
            SealBaselineV2.evaluate(
                v2
            )

        if (
            !roiBitmap.isRecycled
        ) {
            roiBitmap.recycle()
        }

        return SealSideResult(
            side = side,
            left = left,
            top = top,
            width = roiWidth,
            height = roiHeight,
            result = v2,
            baseline = baseline,
            photoQualityText = photoQualityText
        )
    }

    private fun worseJudgment(
        left: String,
        right: String
    ): String {

        fun rank(
            value: String
        ): Int {

            return when {
                value.contains("불량") -> 4
                value.contains("한계") -> 3
                value.contains("주의") -> 2
                else -> 1
            }
        }

        return if (
            rank(left) >=
            rank(right)
        ) {
            left
        } else {
            right
        }
    }

    private fun buildSideText(
        sideResult: SealSideResult
    ): String {

        val v =
            sideResult.result

        val wrinkleDetail =
            if (
                v.wrinkles.isEmpty()
            ) {
                "검출된 Seal Wrinkle 없음"
            } else {
                v.wrinkles.joinToString(
                    separator = "\\n"
                ) { wrinkle ->
                    String.format(
                        Locale.getDefault(),
                        "#%d 길이 %.1f%% | 강도 %.0f | 음영 %.0f | Crossing %.0f",
                        wrinkle.index,
                        wrinkle.lengthPercent,
                        wrinkle.strength,
                        wrinkle.shadowRisk,
                        wrinkle.sealCrossingRisk
                    )
                }
            }

        return String.format(
            Locale.getDefault(),
            """
%s SEAL

판정 : %s
Baseline Deviation : %.1f / 100
유효 주름/크랙 후보 : %d개
고위험 크랙 후보 : %d개
Crack Risk : %.1f / 100
Seal Wrinkle(raw) : %d개
최장 주름 : %.1f%%
평균 주름 강도 : %.1f / 100
Seal Line Uniformity : %.1f / 100
Width Variation : %.1f%%
Local Discontinuity : %.1f / 100
PP Flow Risk : %.1f / 100
Shadow Risk : %.1f / 100
Cup Intrusion Risk : %.1f / 100
Overall Risk : %.1f / 100
Quality Score : %.1f / 100

%s
%s
            """.trimIndent(),
            sideResult.side,
            sideResult.baseline.judgment,
            sideResult.baseline.baselineDeviation,
            sideResult.baseline.qualifiedWrinkleCount,
            sideResult.baseline.severeWrinkleCount,
            sideResult.baseline.crackRisk,
            v.wrinkleCount,
            v.longestWrinklePercent,
            v.averageWrinkleStrength,
            v.sealLineUniformity,
            v.widthVariationPercent,
            v.localDiscontinuityRisk,
            v.ppFlowRisk,
            v.transparencyShadowRisk,
            v.cupIntrusionRisk,
            v.overallRisk,
            v.qualityScore,
            wrinkleDetail,
            sideResult.photoQualityText
        )
    }

    /*
     * =========================================================
     * SEAL 검사
     *
     * 중요:
     * 기존 SEAL 판정 수식과 기준은 변경하지 않았습니다.
     * 이번 수정의 목적은 결과 사진 저장 기능 추가입니다.
     * =========================================================
     */


    private fun analyzeSelectedRoi(
        bitmap: Bitmap
    ) {

        binding.tvSealStatus.text =
            "SEAL 좌/우 동시 분석 중..."

        invalidateInspectionResult()
        restoreOriginalImage()

        val leftRoi =
            getBitmapRoi(
                bitmap,
                binding.sealRoiGuide
            )

        val rightRoi =
            getBitmapRoi(
                bitmap,
                binding.sealRoiGuideRight
            )

        if (
            leftRoi ==
            null ||
            rightRoi ==
            null
        ) {

            binding.tvSealStatus.text =
                "LEFT / RIGHT ROI 영역을 확인해주세요."

            return
        }

        Thread {

            try {

                val leftResult =
                    analyzeSealSide(
                        source = bitmap,
                        roiRect = leftRoi,
                        side = "LEFT"
                    )

                val rightResult =
                    analyzeSealSide(
                        source = bitmap,
                        roiRect = rightRoi,
                        side = "RIGHT"
                    )

                val finalJudgment =
                    SealBaselineV2.worseJudgment(
                        leftResult.baseline.judgment,
                        rightResult.baseline.judgment
                    )

                val worstQuality =
                    minOf(
                        leftResult.result.qualityScore,
                        rightResult.result.qualityScore
                    )

                val avgUniformity =
                    (
                        leftResult.result.sealLineUniformity +
                            rightResult.result.sealLineUniformity
                        ) /
                        2.0

                lastSealScore =
                    worstQuality

                lastSealUniformity =
                    avgUniformity

                lastEdgeDensity =
                    0.0

                lastStrongEdgeDensity =
                    0.0

                lastEdgeStrength =
                    0.0

                lastJudgment =
                    finalJudgment

                val inspectionSpec =
                    InspectionSpecStore.getCurrent(
                        context = this,
                        inspectionType =
                            InspectionSpecStore.InspectionType.SEAL
                    )

                val leftText =
                    buildSideText(
                        leftResult
                    )

                val rightText =
                    buildSideText(
                        rightResult
                    )

                lastDetails =
                    """
SEAL V2 DUAL ROI

$leftText

------------------------------

$rightText

==============================
종합 판정 : $finalJudgment
종합 Quality Score : ${String.format(Locale.getDefault(), "%.1f", worstQuality)} / 100

정상 Master Baseline 운영 원칙
- 현재 확보된 정상 10셀 / 20장(정면+사선)은 정상 변동 참고군으로 사용
- 실제 NG 이미지가 없으므로 주의/한계정상/불량 경계는 임시 기준
- raw NG 후보 개수는 최종 판정에서 제외
- 촬영각도에 따라 이동하는 큰 반사광은 보조지표로만 사용
- 새로운 주름/크랙성 선, 길이, 강도, 실링선 Crossing을 우선 위험신호로 사용
- 현장 정상 데이터가 누적되면 기준을 재보정

현재 Model / Line 기존 기준 (참고용)
${inspectionSpec.criteriaText()}
                    """.trimIndent()

                /*
                 * 정상 Baseline으로 판정된 쪽은 빨간 NG 후보를 표시하지 않습니다.
                 * 정상 실링 압흔/반사광이 NG 후보로 보이는 오검출을 줄이기 위함입니다.
                 */
                val leftMarked =
                    if (
                        leftResult.baseline.showDefectMarkers
                    ) {

                        val leftMarkerResult =
                            DefectMarker.markDefectRegions(
                                sourceBitmap = bitmap,
                                roiLeft = leftResult.left,
                                roiTop = leftResult.top,
                                roiWidth = leftResult.width,
                                roiHeight = leftResult.height,
                                sensitivity = sensitivity,
                                maxRegions = 4
                            )

                        MarkerDisplayRenderer.renderGeneric(
                            sourceBitmap = bitmap,
                            roiLeft = leftResult.left,
                            roiTop = leftResult.top,
                            roiWidth = leftResult.width,
                            roiHeight = leftResult.height,
                            regions = leftMarkerResult.regions
                        )

                    } else {

                        bitmap.copy(
                            Bitmap.Config.ARGB_8888,
                            true
                        )
                    }

                val finalMarked =
                    if (
                        rightResult.baseline.showDefectMarkers
                    ) {

                        val rightMarkerResult =
                            DefectMarker.markDefectRegions(
                                sourceBitmap = bitmap,
                                roiLeft = rightResult.left,
                                roiTop = rightResult.top,
                                roiWidth = rightResult.width,
                                roiHeight = rightResult.height,
                                sensitivity = sensitivity,
                                maxRegions = 4
                            )

                        MarkerDisplayRenderer.renderGeneric(
                            sourceBitmap = leftMarked,
                            roiLeft = rightResult.left,
                            roiTop = rightResult.top,
                            roiWidth = rightResult.width,
                            roiHeight = rightResult.height,
                            regions = rightMarkerResult.regions
                        )

                    } else {

                        leftMarked
                    }

                if (
                    leftMarked !==
                    finalMarked &&
                    !leftMarked.isRecycled
                ) {
                    leftMarked.recycle()
                }

                lastResultBitmap =
                    finalMarked

                hasInspectionResult =
                    true

                runOnUiThread {

                    binding.sealImagePreview
                        .setImageBitmap(
                            finalMarked
                        )

                    binding.sealImagePreview.imageMatrix =
                        imageMatrixValue

                    binding.tvSealMetrics.text =
                        """
SEAL V2 - LEFT / RIGHT 동시 검사

LEFT
판정 : ${leftResult.baseline.judgment}
Baseline Deviation : ${String.format(Locale.getDefault(), "%.1f", leftResult.baseline.baselineDeviation)}
유효 주름/크랙 후보 : ${leftResult.baseline.qualifiedWrinkleCount}개
고위험 크랙 후보 : ${leftResult.baseline.severeWrinkleCount}개
${leftResult.baseline.reason}

RIGHT
판정 : ${rightResult.baseline.judgment}
Baseline Deviation : ${String.format(Locale.getDefault(), "%.1f", rightResult.baseline.baselineDeviation)}
유효 주름/크랙 후보 : ${rightResult.baseline.qualifiedWrinkleCount}개
고위험 크랙 후보 : ${rightResult.baseline.severeWrinkleCount}개
${rightResult.baseline.reason}

종합 판정 : $finalJudgment
종합 Quality Score : ${String.format(Locale.getDefault(), "%.1f", worstQuality)}

※ 좌/우 중 더 나쁜 판정을 종합 판정으로 사용합니다.
※ 현재 주의/불량 기준은 정상 10셀/20장 기반의 임시 현장 기준입니다.
※ 실제 불량 샘플 확보 시 기준을 다시 검증합니다.
                        """.trimIndent()

                    binding.tvSealStatus.text =
                        "SEAL 완료 - LEFT ${leftResult.baseline.judgment} / RIGHT ${rightResult.baseline.judgment} / 종합 $finalJudgment"
                }

            } catch (
                e: Exception
            ) {

                runOnUiThread {

                    binding.tvSealStatus.text =
                        "SEAL 좌/우 분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    /*
     * =========================================================
     * SEAL Telegram 자동 알림
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
         * 같은 검사 결과의 중복 발송을 방지합니다.
         * 네트워크 실패는 기존 재전송 기능이 처리합니다.
         */
        telegramAlertQueuedForCurrentResult =
            true

        TelegramSender.sendInspectionAlert(
            context = this,
            inspectionType = "SEAL",
            score = lastSealScore,
            judgment = lastJudgment,
            details = lastDetails,
            resultBitmap = lastResultBitmap
        ) { result ->

            runOnUiThread {

                val message =
                    if (
                        result.success
                    ) {

                        "SEAL Telegram 자동전송 완료\n" +
                            "성공 ${result.successCount}개 / " +
                            "실패 ${result.failureCount}개"

                    } else {

                        "SEAL Telegram 자동전송 실패\n" +
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
                "먼저 SEAL ROI 검사를 실행해주세요.",
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
                "SEAL 결과 사진이 없습니다. SEAL 검사를 다시 실행해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val success =
            InspectionHistoryStore.save(
                context = this,
                inspectionType = "SEAL",
                score = lastSealScore,
                judgment = lastJudgment,
                sensitivity = sensitivity,
                details = lastDetails,

                /*
                 * 새 기능:
                 * 빨간 NG 후보가 표시된 SEAL 결과 사진도 함께 저장
                 */
                imageBitmap = resultBitmap
            )

        if (
            success
        ) {

            /*
             * 검사 결과와 결과 사진이 정상 저장된 경우에만
             * Telegram 정책에 따라 1회 자동전송합니다.
             */
            sendTelegramAlertIfNeeded()

            Toast.makeText(
                this,
                String.format(
                    Locale.getDefault(),
                    "SEAL 검사 결과 + 사진 저장 완료\nScore %.1f / 100\n%s",
                    lastSealScore,
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

    private fun grayValue(
        color: Int
    ): Int {

        val r =
            android.graphics.Color.red(
                color
            )

        val g =
            android.graphics.Color.green(
                color
            )

        val b =
            android.graphics.Color.blue(
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
