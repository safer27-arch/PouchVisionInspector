package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
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
     * 분석용 원본 이미지
     *
     * 빨간 표시가 추가되어도
     * 원본 분석 데이터는 유지합니다.
     */
    private var lastBitmap: Bitmap? = null

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
         * 사진 촬영
         */
        binding.btnCapture.setOnClickListener {

            showCameraMode()

            takePhoto()
        }


        /*
         * 갤러리
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
         * ROI 축소
         */
        binding.btnRoiSmaller.setOnClickListener {

            resizeRoi(
                0.85f
            )
        }


        /*
         * ROI 확대
         */
        binding.btnRoiLarger.setOnClickListener {

            resizeRoi(
                1.15f
            )
        }


        /*
         * ROI 중앙 복귀
         */
        binding.btnRoiReset.setOnClickListener {

            resetRoiPosition()
        }


        /*
         * 카메라 복귀
         */
        binding.btnCameraMode.setOnClickListener {

            showCameraMode()
        }


        setupRoiDrag()

        checkCameraPermission()
    }


    /*
     * 카메라 권한 확인
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


    /*
     * 카메라 시작
     */
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
     * 사진 촬영
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

                    Toast.makeText(
                        this@MainActivity,
                        "촬영 실패: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }


    /*
     * 갤러리 이미지 읽기
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


            /*
             * 새로운 사진을 선택하면
             * 빨간 표시 없는 원본으로 초기화
             */
            lastBitmap =
                bitmap


            showSelectedImage(
                bitmap
            )


            resetRoiPosition()


            binding.tvStatus.text =
                "사진 선택 완료 - ROI를 Bottom Corner에 맞춰주세요."


        } catch (e: Exception) {

            binding.tvStatus.text =
                "사진 불러오기 오류"

            Toast.makeText(
                this,
                "사진 오류: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
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
    }


    /*
     * 카메라 모드
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
     * ROI 손가락 이동
     */
    private fun setupRoiDrag() {

        binding.roiGuide.setOnTouchListener {

                view,
                event ->


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


                else ->
                    true
            }
        }
    }


    /*
     * ROI 크기 변경
     */
    private fun resizeRoi(
        scale: Float
    ) {

        val view =
            binding.roiGuide

        val parent =
            binding.imageArea


        if (
            parent.width <= 0 ||
            parent.height <= 0
        ) {

            return
        }


        var newWidth =
            (
                view.width *
                        scale
                ).toInt()

        var newHeight =
            (
                view.height *
                        scale
                ).toInt()


        newWidth =
            newWidth.coerceIn(
                100,
                max(
                    100,
                    (
                        parent.width *
                                0.9f
                        ).toInt()
                )
            )


        newHeight =
            newHeight.coerceIn(
                80,
                max(
                    80,
                    (
                        parent.height *
                                0.9f
                        ).toInt()
                )
            )


        val params =
            view.layoutParams


        params.width =
            newWidth

        params.height =
            newHeight


        view.layoutParams =
            params


        resetRoiPosition()
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
     * ROI 좌표 계산
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


        /*
         * View 좌표는 UI Thread에서 먼저 읽습니다.
         */
        val imageViewWidth =
            binding.imagePreview.width
                .toFloat()

        val imageViewHeight =
            binding.imagePreview.height
                .toFloat()


        val roiLeft =
            binding.roiGuide.x

        val roiTop =
            binding.roiGuide.y

        val roiRight =
            roiLeft +
                    binding.roiGuide.width

        val roiBottom =
            roiTop +
                    binding.roiGuide.height


        binding.tvStatus.text =
            "선택 ROI 분석 중..."


        Thread {

            try {

                val bitmapWidth =
                    source.width.toFloat()

                val bitmapHeight =
                    source.height.toFloat()


                /*
                 * ImageView scaleType = fitCenter
                 */
                val scale =
                    minOf(
                        imageViewWidth /
                                bitmapWidth,

                        imageViewHeight /
                                bitmapHeight
                    )


                val displayedWidth =
                    bitmapWidth *
                            scale

                val displayedHeight =
                    bitmapHeight *
                            scale


                val offsetX =
                    (
                        imageViewWidth -
                                displayedWidth
                        ) / 2f

                val offsetY =
                    (
                        imageViewHeight -
                                displayedHeight
                        ) / 2f


                /*
                 * 화면 ROI →
                 * 실제 이미지 좌표 변환
                 */
                var bitmapLeft =
                    (
                        (
                            roiLeft -
                                    offsetX
                            ) /
                                scale
                        ).toInt()


                var bitmapTop =
                    (
                        (
                            roiTop -
                                    offsetY
                            ) /
                                scale
                        ).toInt()


                var bitmapRight =
                    (
                        (
                            roiRight -
                                    offsetX
                            ) /
                                scale
                        ).toInt()


                var bitmapBottom =
                    (
                        (
                            roiBottom -
                                    offsetY
                            ) /
                                scale
                        ).toInt()


                bitmapLeft =
                    bitmapLeft.coerceIn(
                        0,
                        source.width - 1
                    )


                bitmapTop =
                    bitmapTop.coerceIn(
                        0,
                        source.height - 1
                    )


                bitmapRight =
                    bitmapRight.coerceIn(
                        bitmapLeft + 1,
                        source.width
                    )


                bitmapBottom =
                    bitmapBottom.coerceIn(
                        bitmapTop + 1,
                        source.height
                    )


                val roiWidth =
                    bitmapRight -
                            bitmapLeft

                val roiHeight =
                    bitmapBottom -
                            bitmapTop


                val roiBitmap =
                    Bitmap.createBitmap(
                        source,
                        bitmapLeft,
                        bitmapTop,
                        roiWidth,
                        roiHeight
                    )


                /*
                 * ROI 분석 +
                 * 빨간 위치 표시
                 */
                analyzeBitmapRoi(

                    source =
                    source,

                    roi =
                    roiBitmap,

                    roiStartX =
                    bitmapLeft,

                    roiStartY =
                    bitmapTop
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
     * ROI 실제 분석
     *
     * 강한 밝기 변화 위치는
     * 빨간색으로 표시
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


        /*
         * 원본 사진 복사
         *
         * 여기에 빨간 표시를 그립니다.
         */
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
                    210
            }


        /*
         * 작은 분석 이미지 좌표를
         * 실제 ROI 좌표로 환산하기 위한 비율
         */
        val xScale =
            roi.width.toFloat() /
                    analysisWidth.toFloat()

        val yScale =
            roi.height.toFloat() /
                    analysisHeight.toFloat()


        /*
         * 표시 점 크기
         */
        val markRadius =
            max(
                2f,
                roi.width.toFloat() /
                        170f
            )


        /*
         * Edge 분석
         */
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


                /*
                 * Wrinkle Index용 Edge
                 */
                if (
                    gradient > 45
                ) {

                    edgeCount++
                }


                /*
                 * 빨간 표시용 Threshold
                 *
                 * 점이 너무 많아지지 않도록
                 * 분석 점보다 높은 기준 사용
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


        /*
         * 현재 임시 Wrinkle Index
         */
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


        /*
         * 아직 임시 판정
         */
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
             * 빨간 표시 사진 출력
             */
            binding.imagePreview.setImageBitmap(
                markedBitmap
            )


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
