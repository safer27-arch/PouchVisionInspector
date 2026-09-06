package com.pouchvision.inspector

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null

    private val requestCameraPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                startCamera()

            } else {

                Toast.makeText(
                    this,
                    "카메라 권한이 필요합니다.",
                    Toast.LENGTH_LONG
                ).show()

                binding.tvStatus.text =
                    "카메라 권한이 필요합니다."
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)


        binding.tvStatus.text =
            "카메라 준비 중..."


        binding.btnCapture.setOnClickListener {

            takePhoto()
        }


        binding.btnInspect.setOnClickListener {

            Toast.makeText(
                this,
                "촬영 이미지 분석 기능을 준비 중입니다.",
                Toast.LENGTH_SHORT
            ).show()

            binding.tvStatus.text =
                "검사 준비 완료"
        }


        checkCameraPermission()
    }


    private fun checkCameraPermission() {

        when {

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {

                startCamera()
            }

            else -> {

                requestCameraPermission.launch(
                    Manifest.permission.CAMERA
                )
            }
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


                val cameraSelector =
                    CameraSelector.DEFAULT_BACK_CAMERA


                cameraProvider.unbindAll()


                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
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
                    "카메라 실행 오류: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun takePhoto() {

        val imageCapture =
            imageCapture ?: run {

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


        val outputOptions =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ).build()


        imageCapture.takePicture(

            outputOptions,

            ContextCompat.getMainExecutor(this),

            object :
                ImageCapture.OnImageSavedCallback {


                override fun onImageSaved(
                    outputFileResults:
                    ImageCapture.OutputFileResults
                ) {

                    binding.tvStatus.text =
                        "촬영 완료"

                    Toast.makeText(
                        this@MainActivity,
                        "사진 저장 완료\n$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                }


                override fun onError(
                    exception: ImageCaptureException
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
}
