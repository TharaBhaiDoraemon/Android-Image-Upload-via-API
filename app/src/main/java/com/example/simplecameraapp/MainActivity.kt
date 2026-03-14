package com.example.simplecameraapp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var saveButton: Button
    private lateinit var imagePreview: ImageView
    private lateinit var selectPictureButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var takePictureButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var flipCameraButton: com.google.android.material.floatingactionbutton.FloatingActionButton
    private var tempImageUri: Uri? = null
    private var tempImageFile: File? = null
    private lateinit var discardButton: MaterialButton

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var cameraProvider: ProcessCameraProvider? = null

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted){
            startCamera()
        } else{
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        selectPictureButton = findViewById(R.id.fab_select_picture)

        takePictureButton = findViewById(R.id.fab_take_picture)
        flipCameraButton = findViewById(R.id.fab_flip_camera)
        cameraPreview = findViewById(R.id.camera_preview)
        saveButton = findViewById(R.id.btn_save)
        imagePreview = findViewById(R.id.image_view)
        discardButton = findViewById(R.id.btn_discard)

        cameraExecutor = Executors.newSingleThreadExecutor()

        saveButton.visibility = View.GONE
        imagePreview.visibility = View.GONE

        selectPictureButton.setOnClickListener {
            pickImage.launch("image/*")
        }
        takePictureButton.setOnClickListener { takePhoto() }
        saveButton.setOnClickListener { saveToGallery() }
        flipCameraButton.setOnClickListener {

            cameraSelector =
                if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

            startCamera()
        }
        discardButton.setOnClickListener {
            resetUI()
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                imagePreview.setImageURI(it)
                imagePreview.visibility = View.VISIBLE

                saveButton.visibility = View.GONE
                discardButton.visibility = View.VISIBLE
                takePictureButton.visibility = View.GONE
            }
        }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            this.cameraProvider = cameraProvider

            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(cameraPreview.surfaceProvider)

            val imageCapture = ImageCapture.Builder().build()
            this.imageCapture = imageCapture

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        tempImageFile = File.createTempFile("temp_image", ".jpg", cacheDir)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempImageFile!!).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    imagePreview.setImageURI(Uri.fromFile(tempImageFile))
                    imagePreview.visibility = View.VISIBLE
                    saveButton.visibility = View.VISIBLE
                    discardButton.visibility = View.VISIBLE
                    takePictureButton.visibility = View.GONE
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error capturing photo: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }


    private fun saveToGallery() {
        tempImageFile?.let { file ->
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                val outputStream = contentResolver.openOutputStream(it)
                val inputStream = file.inputStream()
                inputStream.use { input ->
                    outputStream?.use { output ->
                        input.copyTo(output)
                    }
                }
                Toast.makeText(this, "Saved to gallery!", Toast.LENGTH_SHORT).show()
            }

            resetUI()
        }
    }


    private fun resetUI() {
        imagePreview.visibility = View.GONE
        saveButton.visibility = View.GONE
        discardButton.visibility = View.GONE
        takePictureButton.visibility = View.VISIBLE
        tempImageFile = null
    }


    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
