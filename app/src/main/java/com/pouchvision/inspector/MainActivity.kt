package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var lastBitmap: Bitmap? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvStatus.text = "카메라 준비 중..."

        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnInspect.setOnClickListener {

            val bitmap = lastBitmap

            if (bitmap == null) {

                Toast.makeText(
                    this,
                    "먼저 사진을 촬영해주세요.",
                    Toast.LENGTH_SHORT
                ).show()

            } else {

                analyzeBottomCorner(bitmap)
            }
        }

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
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {

        val capture = imageCapture ?: return

        binding.tvStatus.text =
            "사진 촬영 중..."

        val fileName =
            "Pouch_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.getDefault()
                    ).format(System.currentTimeMillis()) +
                    ".jpg"

        val values =
            ContentValues().apply {

                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "image/jpeg"
                )

                if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {

                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/PouchVision"
                    )
                }
            }

        val output =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ).build()

        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),

            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {

                    val uri = result.savedUri

                    if (uri != null) {

                        contentResolver
                            .openInputStream(uri)
                            ?.use {

                                lastBitmap =
                                    BitmapFactory.decodeStream(it)
                            }
                    }

                    binding.tvStatus.text =
                        "촬영 완료 - 검사 시작을 눌러주세요."

                    Toast.makeText(
                        this@MainActivity,
                        "사진 저장 완료",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {

                    binding.tvStatus.text =
                        "촬영 실패"
                }
            }
        )
    }

    private fun analyzeBottomCorner(
        source: Bitmap
    ) {

        binding.tvStatus.text =
            "Bottom Corner 분석 중..."

        Thread {

            try {

                /*
                 * 1차 버전:
                 * 촬영 사진의 중앙 영역을 ROI로 사용합니다.
                 *
                 * 이후 ROI 가이드와 실제 카메라 좌표를
                 * 정확하게 일치시키는 기능을 추가합니다.
                 */

                val roiWidth =
                    (source.width * 0.60).toInt()

                val roiHeight =
                    (source.height * 0.45).toInt()

                val startX =
                    (source.width - roiWidth) / 2

                val startY =
                    (source.height - roiHeight) / 2

                val roi =
                    Bitmap.createBitmap(
                        source,
                        startX,
                        startY,
                        roiWidth,
                        roiHeight
                    )

                /*
                 * 계산량을 줄이기 위해 축소
                 */

                val small =
                    Bitmap.createScaledBitmap(
                        roi,
                        320,
                        240,
                        true
                    )

                var edgeCount = 0L
                var totalStrength = 0L
                var pixelCount = 0L

                for (y in 1 until small.height - 1) {

                    for (x in 1 until small.width - 1) {

                        val center =
                            gray(
                                small.getPixel(x, y)
                            )

                        val right =
                            gray(
                                small.getPixel(x + 1, y)
                            )

                        val bottom =
                            gray(
                                small.getPixel(x, y + 1)
                            )

                        val gradient =
                            abs(center - right) +
                                    abs(center - bottom)

                        totalStrength += gradient
                        pixelCount++

                        /*
                         * 임시 Edge threshold.
                         * 실제 기준사진으로 추후 보정합니다.
                         */

                        if (gradient > 45) {
                            edgeCount++
                        }
                    }
                }

                val edgeDensity =
                    if (pixelCount > 0) {
                        edgeCount.toDouble() /
                                pixelCount.toDouble() *
                                100.0
                    } else {
                        0.0
                    }

                val edgeStrength =
                    if (pixelCount > 0) {
                        totalStrength.toDouble() /
                                pixelCount.toDouble()
                    } else {
                        0.0
                    }

                /*
                 * 이 점수는 아직 품질 합격/불합격 기준이 아닙니다.
                 * 이미지 변화량을 보기 위한 초기 지표입니다.
                 */

                val wrinkleIndex =
                    (
                        edgeDensity * 3.0 +
                        edgeStrength * 0.7
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
                            "한계 후보"

                        else ->
                            "불량 후보"
                    }

                runOnUiThread {

                    binding.tvStatus.text =
                        """
Bottom Corner 분석 결과

Wrinkle Index : %.1f
Edge Density : %.1f%%
Edge Strength : %.1f

판정 : %s

※ 현재는 기준 학습 전 임시 판정
                        """.trimIndent()
                            .format(
                                wrinkleIndex,
                                edgeDensity,
                                edgeStrength,
                                level
                            )
                }

            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }

    private fun gray(
        color: Int
    ): Int {

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        return (
            0.299 * r +
            0.587 * g +
            0.114 * b
        ).toInt()
    }
}
