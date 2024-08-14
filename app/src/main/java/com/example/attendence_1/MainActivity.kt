// MainActivity.kt
package com.example.attendence_1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etUserId: EditText
    private lateinit var btnFetch: Button
    private lateinit var tvUserDetails: TextView
    private lateinit var btnMarkAttendance: Button
    private lateinit var btnRegister: Button
    private lateinit var previewView: PreviewView

    private lateinit var userRepository: UserRepository
    private lateinit var faceNetModel: FaceNetModel

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUserId = findViewById(R.id.etUserId)
        btnFetch = findViewById(R.id.btnFetch)
        tvUserDetails = findViewById(R.id.tvUserDetails)
        btnMarkAttendance = findViewById(R.id.btnMarkAttendance)
        btnRegister = findViewById(R.id.btnRegister)
        previewView = findViewById(R.id.previewView)

        userRepository = UserRepository(this)
        faceNetModel = FaceNetModel(this, Models.FACENET, useGpu = true, useXNNPack = true)

        btnFetch.setOnClickListener {
            fetchUserDetails()
        }

        btnMarkAttendance.setOnClickListener {
            startCameraPreview()
        }

        btnRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun fetchUserDetails() {
        val userIdText = etUserId.text.toString()

        if (userIdText.isEmpty()) {
            Toast.makeText(this, "Please enter the User ID", Toast.LENGTH_SHORT).show()
            return
        }

        val userId: Int
        try {
            userId = userIdText.toInt()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid number format for User ID", Toast.LENGTH_SHORT).show()
            return
        }

        val user = userRepository.getUserById(userId)
        if (user != null) {
            tvUserDetails.text = "Name: ${user.name}\nEmail: ${user.email}"
        } else {
            tvUserDetails.text = "User not found"
        }
    }

    private fun startCameraPreview() {
        val userIdText = etUserId.text.toString()

        if (userIdText.isEmpty()) {
            Toast.makeText(this, "Please enter the User ID", Toast.LENGTH_SHORT).show()
            return
        }

        val userId: Int
        try {
            userId = userIdText.toInt()
        } catch (e: NumberFormatException) {
            Toast.makeText(this, "Please enter a valid number format for User ID", Toast.LENGTH_SHORT).show()
            return
        }

        val user = userRepository.getUserById(userId)
        if (user != null) {
            // Hide all other UI elements
            etUserId.visibility = View.GONE
            btnFetch.visibility = View.GONE
            tvUserDetails.visibility = View.GONE
            btnMarkAttendance.visibility = View.GONE
            btnRegister.visibility = View.GONE

            // Start CameraX preview for 5 seconds
            previewView.visibility = View.VISIBLE
            startCamera()
            previewView.postDelayed({
                captureImage(user.embedding)
            }, 5000) // Delay for 5 seconds
        } else {
            tvUserDetails.text = "User not found"
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
        }
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
                cameraProvider?.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage(storedEmbedding: FloatArray) {
        val imageCapture = imageCapture ?: return

        val outputDirectory = getOutputDirectory()
        val photoFile = File(
            outputDirectory,
            "${System.currentTimeMillis()}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val bitmap = BitmapUtils.getBitmapFromUri(contentResolver, savedUri)
                    bitmap?.let {
                        processCapturedFace(it, storedEmbedding)
                    }
                    // Stop the camera and hide the preview
                    stopCamera()
                    previewView.visibility = View.GONE
                    // Show all other UI elements
                    etUserId.visibility = View.VISIBLE
                    btnFetch.visibility = View.VISIBLE
                    tvUserDetails.visibility = View.VISIBLE
                    btnMarkAttendance.visibility = View.VISIBLE
                    btnRegister.visibility = View.VISIBLE
                }
            }
        )
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun processCapturedFace(bitmap: Bitmap, storedEmbedding: FloatArray) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val detectorOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        val detector = FaceDetection.getClient(detectorOptions)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val faceBitmap = BitmapUtils.cropRectFromBitmap(bitmap, face.boundingBox)
                    val faceEmbedding = faceNetModel.getFaceEmbedding(faceBitmap)

                    Log.d("FaceEmbedding", "Captured Embedding: ${faceEmbedding.joinToString()}")
                    compareEmbeddings(faceEmbedding, storedEmbedding)
                } else {
                    Log.d("FaceDetection", "No face detected")
                    Toast.makeText(this, "No face detected, try again", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed: ${e.message}")
                Toast.makeText(this, "Face detection failed!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun compareEmbeddings(faceEmbedding: FloatArray, storedEmbedding: FloatArray) {
        val distance = L2Norm(faceEmbedding, storedEmbedding)
        Log.d("EmbeddingComparison", "L2 Norm Distance: $distance")
        val cosineSim = cosineSimilarity(faceEmbedding, storedEmbedding)
        Log.d("EmbeddingComparison", "Cosine Similarity: $cosineSim")
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val status = if (distance < THRESHOLD || cosineSim > COSINE_THRESHOLD) {
            "Attendance marked for UserID: ${etUserId.text.toString()} at $currentTime"
        } else {
            "Face does not match with UserID: ${etUserId.text.toString()}"
        }
        tvUserDetails.append("\n$status")
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
    }

    private fun L2Norm(x1: FloatArray, x2: FloatArray): Float {
        return sqrt(x1.mapIndexed { i, xi -> (xi - x2[i]).pow(2) }.sum())
    }

    private fun cosineSimilarity(x1: FloatArray, x2: FloatArray): Float {
        val mag1 = sqrt(x1.map { it * it }.sum())
        val mag2 = sqrt(x2.map { it * it }.sum())
        val dot = x1.mapIndexed { i, xi -> xi * x2[i] }.sum()
        return dot / (mag1 * mag2)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val THRESHOLD = 1.0f
        private const val COSINE_THRESHOLD = 0.58f
    }
}
