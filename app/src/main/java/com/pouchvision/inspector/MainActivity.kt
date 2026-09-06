package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
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
import kotlin.math.abs
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    /*
     * 분석용 원본 사진
     */
    private var lastBitmap: Bitmap? = null

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
     * 카메라 권한
     */
    private val requestCameraPermission =
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


    /*
     * 갤러리 선택
     */
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
            ActivityMainBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)


        binding.tvStatus.text =
            "카메라 준비 중..."


        /*
         * 이미지 줌 기능 준비
         */
        setupImageZoom()


        /*
         * ROI 드래그 준비
         */
        setupRoiDrag()


        /*
         * 사진 촬영
         */
        binding.btnCapture.setOnClickListener {

            showCameraMode()

            takePhoto()
        }


        /*
         * 갤러리 선택
         */
        binding.btnGallery.setOnClickListener {

            galleryLauncher.launch(
                "image/*"
            )
        }


        /*
         * ROI 검사
         */
        binding.btnInspect.setOnClickListener {

            val bitmap =
                lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 사진을 촬영하거나 갤러리에서 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedRoi(
                    bitmap
                )
            }
        }


        /*
         * ROI 가로 축소
         */
        binding.btnRoiWidthSmaller.setOnClickListener {

            resizeRoiWidth(
                0.85f
            )
        }


        /*
         * ROI 가로 확대
         */
        binding.btnRoiWidthLarger.setOnClickListener {

            resizeRoiWidth(
                1.15f
            )
        }


        /*
         * ROI 세로 축소
         */
        binding.btnRoiHeightSmaller.setOnClickListener {

            resizeRoiHeight(
                0.85f
            )
        }


        /*
         * ROI 세로 확대
         */
        binding.btnRoiHeightLarger.setOnClickListener {

            resizeRoiHeight(
                1.15f
            )
        }


        /*
         * ROI 중앙
         */
        binding.btnRoiReset.setOnClickListener {

            resetRoiPosition()
        }


        /*
         * 사진 확대 / 이동 초기화
         */
        binding.btnImageReset.setOnClickListener {

            resetImageMatrix()
        }


        /*
         * 카메라 화면 복귀
         */
        binding.btnCameraMode.setOnClickListener {

            showCameraMode()
        }


        checkCameraPermission()
    }


    /*
     * =========================================================
     * 이미지 확대 / 축소 / 이동
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
                        detector: ScaleGestureDetector
                    ): Boolean {

                        val requestedScale =
                            detector.scaleFactor

                        val oldZoom =
                            zoomFactor

                        val newZoom =
                            (
                                zoomFactor *
                                        requestedScale
                                ).coerceIn(
                                1f,
                                8f
                            )

                        val realScale =
                            newZoom /
                                    oldZoom

                        zoomFactor =
                            newZoom

                        imageMatrixValue.postScale(
                            realScale,
                            realScale,
                            detector.focusX,
                            detector.focusY
                        )

                        binding.imagePreview.imageMatrix =
                            imageMatrixValue

                        return true
                    }
                }
            )


        binding.imagePreview.setOnTouchListener {
                view,
                event ->

            /*
             * ScrollView가 사진 드래그를
             * 빼앗지 않도록 처리
             */
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

                    /*
                     * 두 손가락 확대 중이 아닐 때만
                     * 사진 이동
                     */
                    if (
                        !scaleGestureDetector
                            .isInProgress &&
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
     * 사진을 화면 안에 맞춤
     */
    private fun resetImageMatrix() {

        val bitmap =
            lastBitmap
                ?: return


        binding.imagePreview.post {

            val viewWidth =
                binding.imagePreview.width
                    .toFloat()

            val viewHeight =
                binding.imagePreview.height
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


            binding.imagePreview.imageMatrix =
                imageMatrixValue
        }
    }


    /*
     * =========================================================
     * 카메라
     * =========================================================
     */
    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            startCamera()

        } else {

            requestCameraPermission.launch(
                Manifest.permission.CAMERA
            )
        }
    }


    private fun startCamera() {

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
                    "카메라 준비 완료"


            } catch (e: Exception) {

                binding.tvStatus.text =
                    "카메라 실행 오류"

                Toast.makeText(
                    this,
                    "카메라 오류: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        },
            ContextCompat.getMainExecutor(
                this
            )
        )
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


        binding.tvStatus.text =
            "사진 촬영 중..."


        val fileName =
            "Pouch_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                    ).format(
                        System.currentTimeMillis()
                    ) +
                    ".jpg"


        val contentValues =
            ContentValues().apply {

                put(
                    MediaStore
                        .MediaColumns
                        .DISPLAY_NAME,
                    fileName
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
                    ImageCapture
                        .OutputFileResults
                ) {

                    val uri =
                        outputFileResults.savedUri


                    if (uri != null) {

                        val bitmap =
                            decodeBitmapFromUri(
                                uri
                            )


                        if (bitmap != null) {

                            lastBitmap =
                                bitmap

                            showSelectedImage(
                                bitmap
                            )

                            resetRoiPosition()
                        }
                    }


                    binding.tvStatus.text =
                        "촬영 완료 - ROI를 맞춘 후 검사하세요."
                }


                override fun onError(
                    exception:
                    ImageCaptureException
                ) {

                    binding.tvStatus.text =
                        "촬영 실패"
                }
            }
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

        binding.tvStatus.text =
            "갤러리 사진 불러오는 중..."


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


            lastBitmap =
                bitmap


            showSelectedImage(
                bitmap
            )


            resetRoiPosition()


            binding.tvStatus.text =
                "사진 선택 완료 - ROI를 검사 위치에 맞춰주세요."


        } catch (e: Exception) {

            binding.tvStatus.text =
                "사진 불러오기 오류: ${e.message}"
        }
    }


    /*
     * 고해상도 사진 메모리 보호
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
            maxSize / sampleSize >
            1600
        ) {

            sampleSize *= 2
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
     * 사진 화면 표시
     */
    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        binding.previewView.visibility =
            View.GONE


        binding.imagePreview.visibility =
            View.VISIBLE


        binding.imagePreview.setImageBitmap(
            bitmap
        )


        resetImageMatrix()
    }


    /*
     * 카메라 화면
     */
    private fun showCameraMode() {

        binding.imagePreview.visibility =
            View.GONE


        binding.previewView.visibility =
            View.VISIBLE


        binding.tvStatus.text =
            "카메라 모드"
    }


    /*
     * =========================================================
     * ROI 이동
     * =========================================================
     */
    private fun setupRoiDrag() {

        binding.roiGuide.setOnTouchListener {
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
                        binding.imageArea


                    val maxX =
                        parent.width -
                                view.width

                    val maxY =
                        parent.height -
                                view.height


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
     * ROI 가로만 조절
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
                    roi.width / 2f


        val params =
            roi.layoutParams


        params.width =
            newWidth


        roi.layoutParams =
            params


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
                        ).coerceAtLeast(
                        0
                    ).toFloat()
                )


            roi.x =
                newX
        }
    }


    /*
     * =========================================================
     * ROI 세로만 조절
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
                    roi.height / 2f


        val params =
            roi.layoutParams


        params.height =
            newHeight


        roi.layoutParams =
            params


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
                        ).coerceAtLeast(
                        0
                    ).toFloat()
                )


            roi.y =
                newY
        }
    }


    /*
     * ROI 중앙
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
                    ) / 2f


            roi.y =
                (
                    parent.height -
                            roi.height
                    ) / 2f
        }
    }


    /*
     * =========================================================
     * ROI → 실제 Bitmap 좌표 변환
     *
     * 핵심:
     * 이미지가 확대되고 이동되어도
     * Matrix 역변환을 사용하여
     * ROI가 실제 가리키는 부분을 찾습니다.
     * =========================================================
     */
    private fun analyzeSelectedRoi(
        source: Bitmap
    ) {

        if (
            binding.imagePreview.visibility !=
            View.VISIBLE
        ) {

            Toast.makeText(
                this,
                "촬영 또는 갤러리 사진을 먼저 선택해주세요.",
                Toast.LENGTH_LONG
            ).show()

            return
        }


        binding.tvStatus.text =
            "ROI 분석 중..."


        /*
         * 현재 ROI 화면 좌표
         */
        val screenRect =
            RectF(
                binding.roiGuide.x,
                binding.roiGuide.y,
                binding.roiGuide.x +
                        binding.roiGuide.width,
                binding.roiGuide.y +
                        binding.roiGuide.height
            )


        /*
         * 현재 이미지 Matrix 복사
         */
        val currentMatrix =
            Matrix(
                binding.imagePreview.imageMatrix
            )


        Thread {

            try {

                /*
                 * 화면 좌표 →
                 * Bitmap 좌표 역변환
                 */
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


                /*
                 * ROI가 사진 밖의 검은 부분에
                 * 대부분 위치한 경우 방지
                 */
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


                analyzeBitmapRoi(
                    source,
                    roiBitmap,
                    left,
                    top
                )


            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvStatus.text =
                        "ROI 분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    /*
     * =========================================================
     * 실제 Bottom Corner 분석
     * =========================================================
     */
    private fun analyzeBitmapRoi(

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


        var edgeCount =
            0L

        var totalStrength =
            0L

        var pixelCount =
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
            roi.width.toFloat() /
                    analysisWidth.toFloat()


        val yScale =
            roi.height.toFloat() /
                    analysisHeight.toFloat()


        val markRadius =
            max(
                2f,
                roi.width.toFloat() /
                        170f
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


                val right =
                    gray(
                        small.getPixel(
                            x + 1,
                            y
                        )
                    )


                val bottom =
                    gray(
                        small.getPixel(
                            x,
                            y + 1
                        )
                    )


                val gradient =
                    abs(
                        center -
                                right
                    ) +
                            abs(
                                center -
                                        bottom
                            )


                totalStrength +=
                    gradient


                pixelCount++


                if (
                    gradient > 45
                ) {

                    edgeCount++
                }


                /*
                 * 강한 변화만 빨간색 표시
                 */
                if (
                    gradient > 70 &&
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


        val edgeStrength =
            if (
                pixelCount > 0
            ) {

                totalStrength.toDouble() /
                        pixelCount.toDouble()

            } else {

                0.0
            }


        val wrinkleIndex =
            (
                edgeDensity *
                        3.0 +
                        edgeStrength *
                        0.7
                ).coerceIn(
                0.0,
                100.0
            )


        val level =
            when {

                wrinkleIndex < 25 ->
                    "정상 후보"

                wrinkleIndex < 45 ->
                    "주의 후보"

                wrinkleIndex < 65 ->
                    "한계정상 후보"

                else ->
                    "불량 후보"
            }


        runOnUiThread {

            /*
             * 중요한 부분:
             * 결과 사진으로 바꾸더라도
             * 현재 확대 위치는 유지됩니다.
             */
            binding.imagePreview.setImageBitmap(
                markedBitmap
            )


            binding.imagePreview.imageMatrix =
                imageMatrixValue


            binding.tvStatus.text =
                String.format(
                    Locale.getDefault(),

                    """
Bottom Corner ROI 분석 결과

Wrinkle Index : %.1f
Edge Density : %.1f%%
Edge Strength : %.1f

판정 : %s

빨간 표시 : 주름 의심 위치

※ 현재는 기준 학습 전 임시 판정
                    """.trimIndent(),

                    wrinkleIndex,
                    edgeDensity,
                    edgeStrength,
                    level
                )
        }
    }


    /*
     * RGB → Gray
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
            0.299 * r +
                    0.587 * g +
                    0.114 * b
            ).toInt()
    }
}
