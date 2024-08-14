package com.example.attendence_1

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.util.concurrent.Executors

class RegisterActivity : AppCompatActivity() {

    private lateinit var etUserId: EditText
    private lateinit var etName: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnBack: Button
    private lateinit var btnCaptureFace: Button
    private lateinit var btnRetake: Button
    private lateinit var btnOkay: Button
    private lateinit var previewView: PreviewView
    private lateinit var imageView: ImageView

    private lateinit var userRepository: UserRepository
    private lateinit var faceNetModel: FaceNetModel

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var capturedFaceEmbedding: FloatArray? = null
    private var capturedFaceBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUserId = findViewById(R.id.etUserId)
        etName = findViewById(R.id.etName)
        etEmail = findViewById(R.id.etEmail)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnBack = findViewById(R.id.btnBackToMain)
        btnCaptureFace = findViewById(R.id.btnCaptureFace)
        btnRetake = findViewById(R.id.btnRetake)
        btnOkay = findViewById(R.id.btnOkay)
        previewView = findViewById(R.id.previewView)
        imageView = findViewById(R.id.imageView)

        userRepository = UserRepository(this)
        faceNetModel = FaceNetModel(this, Models.FACENET, useGpu = true, useXNNPack = true)

        btnSubmit.setOnClickListener { submitUser() }
        btnBack.setOnClickListener { finish() }
        btnCaptureFace.setOnClickListener { startCameraPreview() }
        btnRetake.setOnClickListener { startCameraPreview() }
        btnOkay.setOnClickListener {
            processCapturedFace(capturedFaceBitmap)
            btnOkay.visibility = View.GONE
        }

        if (allPermissionsGranted()) {
            // Permissions are already granted, do nothing until Capture button is clicked
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCameraPreview() {
        previewView.visibility = View.VISIBLE
        imageView.visibility = View.GONE
        btnCaptureFace.visibility = View.GONE
        btnRetake.visibility = View.GONE
        btnOkay.visibility = View.GONE
        startCamera()
        previewView.postDelayed({
            captureImage()
        }, 5000) // Delay for 5 seconds
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return

        val outputDirectory = getOutputDirectory()
        val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val bitmap = BitmapUtils.getBitmapFromUri(contentResolver, savedUri)
                bitmap?.let {
                    capturedFaceBitmap = it
                    previewView.visibility = View.GONE
                    imageView.setImageBitmap(it)
                    imageView.visibility = View.VISIBLE
                    btnRetake.visibility = View.VISIBLE
                    btnOkay.visibility = View.VISIBLE
                }
                stopCamera()
            }
        })
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun processCapturedFace(bitmap: Bitmap?) {
        bitmap?.let {
            val inputImage = InputImage.fromBitmap(it, 0)
            val detectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
            val detector = FaceDetection.getClient(detectorOptions)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val faceBitmap = BitmapUtils.cropRectFromBitmap(it, face.boundingBox)
                        capturedFaceBitmap = faceBitmap
                        capturedFaceEmbedding = faceNetModel.getFaceEmbedding(faceBitmap)
                        Toast.makeText(this, "Face captured and processed successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Face detection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun submitUser() {
        val userIdString = etUserId.text.toString()
        val userName = etName.text.toString()
        val userEmail = etEmail.text.toString()

        if (userIdString.isBlank() || userName.isBlank() || userEmail.isBlank()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = userIdString.toIntOrNull()
        if (userId == null) {
            Toast.makeText(this, "Please enter a valid number for User ID", Toast.LENGTH_SHORT).show()
            return
        }

        if (userRepository.getUserById(userId) != null) {
            Toast.makeText(this, "User ID already exists. Please use a different ID.", Toast.LENGTH_SHORT).show()
            return
        }

        if (capturedFaceEmbedding == null) {
            Toast.makeText(this, "Please capture face first", Toast.LENGTH_SHORT).show()
            return
        }

        val user = User(userId = userId, name = userName, email = userEmail, embedding = capturedFaceEmbedding!!)
        userRepository.insertUser(user)
        Toast.makeText(this, "User Registered Successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // Permissions granted, do nothing until Capture button is clicked
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
