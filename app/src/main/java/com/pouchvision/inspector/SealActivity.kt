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

                resetRoiPosition()
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
        resetRoiPosition()
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

    private fun setupRoiDrag() {

        binding.sealRoiGuide
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

    /*
     * =========================================================
     * ROI 가로
     * =========================================================
     */

    private fun resizeRoiWidth(
        scale: Float
    ) {

        val roi =
            binding.sealRoiGuide

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

    /*
     * =========================================================
     * ROI 세로
     * =========================================================
     */

    private fun resizeRoiHeight(
        scale: Float
    ) {

        val roi =
            binding.sealRoiGuide

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
        }
    }

    /*
     * =========================================================
     * ROI 중앙
     * =========================================================
     */

    private fun resetRoiPosition() {

        binding.sealImageArea.post {

            val roi =
                binding.sealRoiGuide

            val parent =
                binding.sealImageArea

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

    private fun getBitmapRoi(
        bitmap: Bitmap
    ): RectF? {

        if (
            binding.sealImagePreview.visibility !=
            View.VISIBLE
        ) {

            return null
        }

        val roi =
            binding.sealRoiGuide

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
            "SEAL ROI 분석 중..."

        invalidateInspectionResult()
        restoreOriginalImage()

        val bitmapRoi =
            getBitmapRoi(
                bitmap
            )

        if (
            bitmapRoi ==
            null
        ) {

            binding.tvSealStatus.text =
                "ROI 영역을 계산하지 못했습니다."

            return
        }

        val left =
            bitmapRoi.left
                .toInt()
                .coerceIn(
                    0,
                    bitmap.width -
                        1
                )

        val top =
            bitmapRoi.top
                .toInt()
                .coerceIn(
                    0,
                    bitmap.height -
                        1
                )

        val right =
            bitmapRoi.right
                .toInt()
                .coerceIn(
                    left +
                        1,
                    bitmap.width
                )

        val bottom =
            bitmapRoi.bottom
                .toInt()
                .coerceIn(
                    top +
                        1,
                    bitmap.height
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

            binding.tvSealStatus.text =
                "ROI 영역이 너무 작습니다."

            return
        }

        val source =
            bitmap

        /*
         * 분석은 Background Thread
         */
        Thread {

            try {

                val roiBitmap =
                    Bitmap.createBitmap(
                        source,
                        left,
                        top,
                        roiWidth,
                        roiHeight
                    )

                /*
                 * 촬영 이미지 품질 점검
                 * 검사 Score / 판정에는 영향을 주지 않습니다.
                 */
                val photoQuality =
                    ImageQualityChecker.analyzeBitmap(
                        roiBitmap
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

                val analysisWidth =
                    minOf(
                        320,
                        roiWidth
                    )
                        .coerceAtLeast(
                            20
                        )

                val analysisHeight =
                    max(
                        40,
                        (
                            analysisWidth *
                                roiBitmap.height
                                    .toFloat() /
                                roiBitmap.width
                            )
                            .toInt()
                    )
                        .coerceAtMost(
                            240
                        )

                val smallBitmap =
                    Bitmap.createScaledBitmap(
                        roiBitmap,
                        analysisWidth,
                        analysisHeight,
                        true
                    )

                val width =
                    smallBitmap.width

                val height =
                    smallBitmap.height

                val gray =
                    IntArray(
                        width *
                            height
                    )

                for (
                    y in 0 until
                        height
                ) {

                    for (
                        x in 0 until
                            width
                    ) {

                        gray[
                            y *
                                width +
                                x
                        ] =
                            grayValue(
                                smallBitmap.getPixel(
                                    x,
                                    y
                                )
                            )
                    }
                }

                /*
                 * 기존 SEAL 민감도 기반 Threshold
                 */
                val edgeThreshold =
                    (
                        75 -
                            sensitivity *
                            0.50
                        )
                        .toInt()
                        .coerceIn(
                            20,
                            70
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
                    0.0

                var sampleCount =
                    0L

                for (
                    y in 1 until
                        height -
                        1
                ) {

                    for (
                        x in 1 until
                            width -
                            1
                    ) {

                        val center =
                            gray[
                                y *
                                    width +
                                    x
                            ]

                        val leftValue =
                            gray[
                                y *
                                    width +
                                    x -
                                    1
                            ]

                        val rightValue =
                            gray[
                                y *
                                    width +
                                    x +
                                    1
                            ]

                        val topValue =
                            gray[
                                (
                                    y -
                                        1
                                    ) *
                                    width +
                                    x
                            ]

                        val bottomValue =
                            gray[
                                (
                                    y +
                                        1
                                    ) *
                                    width +
                                    x
                            ]

                        val gx =
                            abs(
                                rightValue -
                                    leftValue
                            )

                        val gy =
                            abs(
                                bottomValue -
                                    topValue
                            )

                        val local =
                            abs(
                                center -
                                    (
                                        leftValue +
                                            rightValue +
                                            topValue +
                                            bottomValue
                                        ) /
                                    4
                            )

                        val gradient =
                            gx +
                                gy +
                                local

                        totalGradient +=
                            gradient

                        sampleCount++

                        if (
                            gradient >=
                            edgeThreshold
                        ) {

                            edgeCount++
                        }

                        if (
                            gradient >=
                            strongThreshold
                        ) {

                            strongEdgeCount++
                        }
                    }
                }

                if (
                    sampleCount <=
                    0
                ) {

                    runOnUiThread {

                        binding.tvSealStatus.text =
                            "SEAL 분석에 실패했습니다."
                    }

                    return@Thread
                }

                /*
                 * 기존 SEAL 점수 계산
                 */
                val edgeDensity =
                    edgeCount
                        .toDouble() /
                        sampleCount
                            .toDouble() *
                        100.0

                val strongEdgeDensity =
                    strongEdgeCount
                        .toDouble() /
                        sampleCount
                            .toDouble() *
                        100.0

                val averageStrength =
                    totalGradient /
                        sampleCount
                            .toDouble()

                val sensitivityFactor =
                    0.55 +
                        sensitivity /
                        133.3

                val sealUniformity =
                    (
                        100.0 -
                            (
                                edgeDensity *
                                    1.6 +
                                    strongEdgeDensity *
                                    2.0
                                ) *
                            sensitivityFactor
                        )
                        .coerceIn(
                            0.0,
                            100.0
                        )

                val defectLevel =
                    (
                        (
                            edgeDensity *
                                2.0 +
                                strongEdgeDensity *
                                3.0 +
                                averageStrength *
                                0.45
                            ) *
                            sensitivityFactor
                        )
                        .coerceIn(
                            0.0,
                            100.0
                        )

                val sealScore =
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

                        sealScore >=
                            85.0 ->
                            "정상 후보"

                        sealScore >=
                            70.0 ->
                            "주의 후보"

                        sealScore >=
                            50.0 ->
                            "한계정상 후보"

                        else ->
                            "불량 후보"
                    }

                /*
                 * NG 후보 영역 표시
                 *
                 * 현재 공용 DefectMarker를 그대로 사용합니다.
                 * SEAL 알고리즘 자체는 이번 단계에서 변경하지 않습니다.
                 */
                val markerResult =
                    DefectMarker.markDefectRegions(
                        sourceBitmap = source,
                        roiLeft = left,
                        roiTop = top,
                        roiWidth = roiWidth,
                        roiHeight = roiHeight,
                        sensitivity = sensitivity,
                        maxRegions = 5
                    )

                val regionSummary =
                    DefectMarker.buildRegionSummary(
                        markerResult.regions
                    )

                val regionCount =
                    markerResult.regions.size

                /*
                 * 결과 저장용 수치
                 */
                lastSealScore =
                    sealScore

                lastSealUniformity =
                    sealUniformity

                lastEdgeDensity =
                    edgeDensity

                lastStrongEdgeDensity =
                    strongEdgeDensity

                lastEdgeStrength =
                    averageStrength

                lastJudgment =
                    judgment

                lastDetails =
                    String.format(
                        Locale.getDefault(),

                        """
Seal Uniformity : %.1f / 100
Seal Score : %.1f / 100
Edge Density : %.1f%%
Strong Edge : %.1f%%
Edge Strength : %.1f
Sensitivity : %d%%
NG 후보 영역 : %d개
%s
                        """.trimIndent(),

                        sealUniformity,
                        sealScore,
                        edgeDensity,
                        strongEdgeDensity,
                        averageStrength,
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
                 * =================================================
                 * 핵심 추가:
                 * 빨간 NG 후보가 표시된 결과 사진을 저장해 둡니다.
                 * lastBitmap 원본은 변경하지 않습니다.
                 * =================================================
                 */
                /*
                 * 표시 방식만 공통 Renderer로 변경합니다.
                 *
                 * - SEAL 판정 / 후보 검출 알고리즘은 그대로 유지
                 * - "NG 후보 N" 라벨은 ROI 바깥으로 이동
                 * - 빨간 원 선 굵기는 기존의 약 50%
                 */
                val displayBitmap =
                    MarkerDisplayRenderer.renderGeneric(
                        sourceBitmap = source,
                        roiLeft = left,
                        roiTop = top,
                        roiWidth = roiWidth,
                        roiHeight = roiHeight,
                        regions = markerResult.regions
                    )

                lastResultBitmap =
                    displayBitmap

                hasInspectionResult =
                    true

                /*
                 * Telegram 자동 알림
                 *
                 * 설정한 전송 기준에 해당하는 판정이면
                 * 결과 이미지 + Model / Line / 검사 항목 / Score를
                 * 자동으로 전송합니다.
                 */
                sendTelegramAlertIfNeeded()

                /*
                 * 화면 결과
                 */
                runOnUiThread {

                    binding.sealImagePreview
                        .setImageBitmap(
                            displayBitmap
                        )

                    binding.sealImagePreview.imageMatrix =
                        imageMatrixValue

                    binding.tvSealMetrics.text =
                        String.format(
                            Locale.getDefault(),

                            """
Seal Uniformity : %.1f / 100
Edge Density    : %.1f%%
Strong Edge     : %.1f%%
Edge Strength   : %.1f
Seal Score      : %.1f / 100

판정 : %s

민감도 : %d%%

NG 후보 영역 : %d개
%s

빨간 원/박스 = 국부 변화가 큰 검사 후보 영역

※ 빨간 표시는 확정 NG가 아닙니다.
※ 인쇄문자, 반사광, Pouch 경계선도 후보로 검출될 수 있습니다.
※ 민감도는 영상 검출 수준이며 실제 품질 Spec과 별도입니다.
※ 실제 Seal Width(mm)는 Calibration이 필요합니다.
                            """.trimIndent(),

                            sealUniformity,
                            edgeDensity,
                            strongEdgeDensity,
                            averageStrength,
                            sealScore,
                            judgment,
                            sensitivity,
                            regionCount,
                            regionSummary
                        )

                    binding.tvSealMetrics.append(
                        "\n\n" +
                            photoQualityText +
                            "\n※ 사진 품질은 검사 판정과 별도의 촬영 상태 보조지표입니다."
                    )

                    binding.tvSealStatus.text =
                        "SEAL 검사 완료 - $judgment"

                    if (!photoQuality.isUsable) {
                        Toast.makeText(
                            this,
                            "촬영 상태 재확인 권고\n${photoQuality.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvSealStatus.text =
                        "SEAL 분석 오류: ${e.message}"
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
