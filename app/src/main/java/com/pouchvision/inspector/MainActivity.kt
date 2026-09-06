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

                Toast.makeText(
                    this,
                    "카메라 권한을 허용해주세요.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }


    /*
     * 갤러리 사진 선택
     *
     * 별도의 저장소 권한 없이
     * Android 기본 사진 선택창을 사용합니다.
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
         * 갤러리 선택
         */
        binding.btnGallery.setOnClickListener {

            galleryLauncher.launch(
                "image/*"
            )
        }


        /*
         * 검사 시작
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

                analyzeBottomCorner(
                    bitmap
                )
            }
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
     * CameraX 시작
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


            lastBitmap =
                bitmap


            showSelectedImage(
                bitmap
            )


            binding.tvStatus.text =
                "갤러리 사진 선택 완료 - 검사 시작을 눌러주세요."


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
     * 큰 사진으로 인한 메모리 문제 방지
     *
     * 최대 약 1600px 크기로
     * 적절하게 줄여서 불러옵니다.
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
     * 선택한 사진 화면 표시
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
     * 카메라 화면으로 돌아가기
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
     * Bottom Corner 1차 분석
     */
    private fun analyzeBottomCorner(
        source: Bitmap
    ) {

        binding.tvStatus.text =
            "Bottom Corner 분석 중..."


        Thread {

            try {

                /*
                 * 현재 버전에서는
                 * 사진 중앙 60% x 45%를
                 * 검사 ROI로 사용합니다.
                 */

                val roiWidth =
                    (source.width * 0.60)
                        .toInt()

                val roiHeight =
                    (source.height * 0.45)
                        .toInt()


                val startX =
                    (source.width -
                            roiWidth) / 2

                val startY =
                    (source.height -
                            roiHeight) / 2


                val roi =
                    Bitmap.createBitmap(
                        source,
                        startX,
                        startY,
                        roiWidth,
                        roiHeight
                    )


                /*
                 * 분석 속도를 위해 축소
                 */
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


                /*
                 * 밝기 변화량 계산
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
                         * 임시 Edge threshold
                         */
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

                        edgeCount
                            .toDouble() /
                                pixelCount
                                    .toDouble() *
                                100.0

                    } else {

                        0.0
                    }


                val edgeStrength =

                    if (
                        pixelCount > 0
                    ) {

                        totalStrength
                            .toDouble() /
                                pixelCount
                                    .toDouble()

                    } else {

                        0.0
                    }


                /*
                 * 1차 Wrinkle Index
                 *
                 * 실제 정상/한계/불량 사진을
                 * 확보한 후 기준값을 보정합니다.
                 */
                val wrinkleIndex =
                    (
                            edgeDensity *
                                    3.0 +
                                    edgeStrength *
                                    0.7
                            )
                        .coerceIn(
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
Bottom Corner 분석 결과

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


            } catch (e: Exception) {

                runOnUiThread {

                    binding.tvStatus.text =
                        "분석 오류: ${e.message}"
                }
            }

        }.start()
    }


    /*
     * RGB → Gray 변환
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
