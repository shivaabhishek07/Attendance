package com.example.attendence_1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

class LiveActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var tvStartingCamera: TextView

    private lateinit var userRepository: UserRepository
    private lateinit var faceNetModel: FaceNetModel
    private lateinit var attendanceRepository: AttendanceRepository
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        previewView = findViewById(R.id.previewView)
        tvStartingCamera = findViewById(R.id.tvStartingCamera)
        userRepository = UserRepository(this)
        faceNetModel = FaceNetModel(this, Models.FACENET, useGpu = true, useXNNPack = true)
        attendanceRepository = AttendanceRepository(this)

        startProcess()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCameraPreview() {

        if (userId.isEmpty()) {
            Toast.makeText(this, "No content in the QR", Toast.LENGTH_SHORT).show()
            return
        }

        val user = userRepository.getUserById(userId)
        if (user != null) {
            // Start CameraX preview for 5 seconds
            previewView.visibility = View.VISIBLE
            tvStartingCamera.visibility = TextView.VISIBLE
            startCamera()
            Handler(Looper.getMainLooper()).postDelayed({
                tvStartingCamera.visibility = TextView.INVISIBLE
            }, 1500)
            previewView.postDelayed({
                captureImage(user.embedding)
            }, 5000) // Delay for 5 seconds
        } else {
            showCustomTopView("User not found")

        }
    }

    private fun startProcess(){
        val intent = Intent(this, QRScanActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_QR_SCAN)
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
                        processCapturedFace(it, storedEmbedding, photoFile)
                    }
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

    private fun processCapturedFace(bitmap: Bitmap, storedEmbedding: FloatArray, photoFile: File) {
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
                    startProcess()
                }
                // Delete the image file after processing
                if (photoFile.exists()) {
                    photoFile.delete()
                    Log.d("ImageFile", "Image file deleted: ${photoFile.name}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed: ${e.message}")
                Toast.makeText(this, "Face detection failed!", Toast.LENGTH_SHORT).show()
                startCameraPreview()
                // Ensure the image file is deleted even if processing fails
                if (photoFile.exists()) {
                    photoFile.delete()
                    Log.d("ImageFile", "Image file deleted: ${photoFile.name}")
                }
            }
            .addOnCompleteListener {
                // Stop the camera and hide the preview
                stopCamera()
                previewView.visibility = View.GONE
            }
    }

    private fun compareEmbeddings(faceEmbedding: FloatArray, storedEmbedding: FloatArray) {
        val distance = L2Norm(faceEmbedding, storedEmbedding)
        Log.d("EmbeddingComparison", "L2 Norm Distance: $distance")
        val cosineSim = cosineSimilarity(faceEmbedding, storedEmbedding)
        Log.d("EmbeddingComparison", "Cosine Similarity: $cosineSim")
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val currentDate = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        var status = ""

        if (attendanceRepository.hasAttendanceMarked(userId, currentDate)) {
            status = "Attendance has already been marked for UserID: ${userId}"
        } else {
            if (distance < THRESHOLD || cosineSim > COSINE_THRESHOLD) {
                attendanceRepository.insertAttendance(userId, currentDate, "Present")
                status = "Attendance marked for UserID: ${userId} at ${currentTime}"
            } else {
                status = "Face does not match with UserID: ${userId}"
            }
        }

        showCustomTopView(status)
        Handler(Looper.getMainLooper()).postDelayed({
            startProcess()
        }, 5000)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_QR_SCAN && resultCode == RESULT_OK) {
            val qrCodeResult = data?.getStringExtra(QRScanActivity.EXTRA_QR_CODE)
            qrCodeResult?.let {
                userId = it
                startCameraPreview()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val THRESHOLD = 4.0f
        private const val COSINE_THRESHOLD = 0.7f
        private const val REQUEST_CODE_QR_SCAN = 101
    }

    private fun showCustomTopView(message: String) {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val customView = inflater.inflate(R.layout.custom_snackbar, null)
        val textView = customView.findViewById<TextView>(R.id.custom_snackbar_text)
        textView.text = message

        val rootLayout = findViewById<FrameLayout>(android.R.id.content)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        rootLayout.addView(customView, params)

        // Remove the view after some time
        customView.postDelayed({
            rootLayout.removeView(customView)
        }, 5000) // Duration to show the custom view
    }

}