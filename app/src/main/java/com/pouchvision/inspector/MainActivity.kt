package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private var lastBitmap: Bitmap? = null

    private var roiLastTouchX = 0f
    private var roiLastTouchY = 0f

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                startCamera()
            } else {
                binding.tvStatus.text = "카메라 권한이 필요합니다."
            }
        }

    private val galleryLauncher =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri != null) {
                loadGalleryImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding =
            ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        binding.tvStatus.text = "카메라 준비 중..."

        binding.btnCapture.setOnClickListener {
            showCameraMode()
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnInspect.setOnClickListener {

            val bitmap = lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 사진을 촬영하거나 갤러리에서 선택해주세요.",
                    Toast.LENGTH_LONG
                ).show()

            } else {

                analyzeSelectedRoi(bitmap)
            }
        }

        binding.btnRoiSmaller.setOnClickListener {
            resizeRoi(0.85f)
        }

        binding.btnRoiLarger.setOnClickListener {
            resizeRoi(1.15f)
        }

        binding.btnRoiReset.setOnClickListener {
            resetRoiPosition()
        }

        binding.btnCameraMode.setOnClickListener {
            showCameraMode()
        }

        setupRoiDrag()

        checkCameraPermission()
    }

    private fun checkCameraPermission() {

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
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
                                binding.previewView.surfaceProvider
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

        }, ContextCompat.getMainExecutor(this))
    }

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

        binding.tvStatus.text =
            "사진 촬영 중..."

        val fileName =
            "Pouch_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                    ).format(System.currentTimeMillis()) +
                    ".jpg"

        val contentValues =
            ContentValues().apply {

                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "image/jpeg"
                )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/PouchVision"
                    )
                }
            }

        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    val uri =
                        outputFileResults.savedUri

                    if (uri != null) {

                        val bitmap =
                            decodeBitmapFromUri(uri)

                        if (bitmap != null) {

                            lastBitmap = bitmap

                            showSelectedImage(bitmap)

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

    private fun loadGalleryImage(
        uri: Uri
    ) {

        binding.tvStatus.text =
            "갤러리 사진 불러오는 중..."

        try {

            val bitmap =
                decodeBitmapFromUri(uri)

            if (bitmap == null) {

                binding.tvStatus.text =
                    "사진을 불러올 수 없습니다."

                return
            }

            lastBitmap = bitmap

            showSelectedImage(bitmap)

            resetRoiPosition()

            binding.tvStatus.text =
                "사진 선택 완료 - ROI를 Bottom Corner에 맞춰주세요."

        } catch (e: Exception) {

            binding.tvStatus.text =
                "사진 불러오기 오류"
        }
    }

    private fun decodeBitmapFromUri(
        uri: Uri
    ): Bitmap? {

        val options =
            BitmapFactory.Options()

        options.inJustDecodeBounds = true

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

        var sampleSize = 1

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

    private fun showSelectedImage(
        bitmap: Bitmap
    ) {

        binding.previewView.visibility =
            View.GONE

        binding.imagePreview.visibility =
            View.VISIBLE

        binding.imagePreview.setImageBitmap(bitmap)
    }

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

        binding.roiGuide.setOnTouchListener { view, event ->

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
                        view.x + dx

                    var newY =
                        view.y + dy

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

                else -> true
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

        var newWidth =
            (view.width * scale).toInt()

        var newHeight =
            (view.height * scale).toInt()

        newWidth =
            newWidth.coerceIn(
                100,
                (parent.width * 0.9).toInt()
            )

        newHeight =
            newHeight.coerceIn(
                80,
                (parent.height * 0.9).toInt()
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
     * ROI 중앙 이동
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
     * 현재 화면의 ROI 위치를
     * 실제 Bitmap 좌표로 변환
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
            "선택 ROI 분석 중..."

        Thread {

            try {

                val imageView =
                    binding.imagePreview

                val roiView =
                    binding.roiGuide

                val imageViewWidth =
                    imageView.width.toFloat()

                val imageViewHeight =
                    imageView.height.toFloat()

                val bitmapWidth =
                    source.width.toFloat()

                val bitmapHeight =
                    source.height.toFloat()

                /*
                 * ImageView가 fitCenter이므로
                 * 실제 이미지 표시 크기 계산
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
                 * roiGuide는 imageArea 기준 위치
                 * imagePreview도 imageArea 전체 크기이므로
                 * 좌표를 바로 사용할 수 있습니다.
                 */
                val roiLeft =
                    roiView.x

                val roiTop =
                    roiView.y

                val roiRight =
                    roiLeft +
                            roiView.width

                val roiBottom =
                    roiTop +
                            roiView.height

                /*
                 * Bitmap 좌표 변환
                 */
                var bitmapLeft =
                    (
                        (roiLeft -
                                offsetX) /
                                scale
                        ).toInt()

                var bitmapTop =
                    (
                        (roiTop -
                                offsetY) /
                                scale
                        ).toInt()

                var bitmapRight =
                    (
                        (roiRight -
                                offsetX) /
                                scale
                        ).toInt()

                var bitmapBottom =
                    (
                        (roiBottom -
                                offsetY) /
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

                analyzeBitmapRoi(
                    roiBitmap
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
     * 실제 ROI 이미지 분석
     */
    private fun analyzeBitmapRoi(
        roi: Bitmap
    ) {

        val small =
            Bitmap.createScaledBitmap(
                roi,
                320,
                240,
                true
            )

        var edgeCount =
            0L

        var totalStrength =
            0L

        var pixelCount =
            0L

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

            binding.tvStatus.text =
                String.format(
                    Locale.getDefault(),

                    """
Bottom Corner ROI 분석 결과

Wrinkle Index : %.1f
Edge Density : %.1f%%
Edge Strength : %.1f

판정 : %s

※ 현재는 기준 학습 전 임시 판정
                    """.trimIndent(),

                    wrinkleIndex,
                    edgeDensity,
                    edgeStrength,
                    level
                )
        }
    }

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
            0.299 * r +
                    0.587 * g +
                    0.114 * b
            ).toInt()
    }
}
