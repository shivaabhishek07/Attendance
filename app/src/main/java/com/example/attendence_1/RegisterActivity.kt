package com.example.attendence_1

import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import android.Manifest


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
    private var photoFilePath: String? = null  // Store photo file path

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize UI elements
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

        // Initialize database and model
        userRepository = UserRepository(this)
        faceNetModel = FaceNetModel(this, Models.FACENET, useGpu = true, useXNNPack = true)

        // Set up button listeners
        btnSubmit.setOnClickListener { submitUser() }
        btnBack.setOnClickListener { finish() }
        btnCaptureFace.setOnClickListener { startCameraPreview() }
        btnRetake.setOnClickListener { startCameraPreview() }
        btnOkay.setOnClickListener {
            processCapturedFace(capturedFaceBitmap)
            btnOkay.visibility = View.GONE
        }

        // Check permissions
        if (!allPermissionsGranted()) {
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
        val userId = etUserId.text.toString()
        val imageCapture = imageCapture ?: return
        val photoFile = File(getPhotosDirectory(), "${userId}_photo.jpg") // Save in Photos folder
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    photoFilePath = photoFile.absolutePath
                    previewView.visibility = View.GONE

                    // Decode bitmap from file
                    capturedFaceBitmap = BitmapUtils.decodeBitmapFromFile(photoFilePath!!)

                    // Rotate the bitmap by 90 degrees
                    val matrix = android.graphics.Matrix().apply {
                        postRotate(-90f)
                    }
                    val rotatedBitmap = Bitmap.createBitmap(
                        capturedFaceBitmap!!, 0, 0,
                        capturedFaceBitmap!!.width, capturedFaceBitmap!!.height,
                        matrix, true
                    )

                    val orientation = resources.configuration.orientation

                    if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                        // Landscape mode: Set the image to capturedFaceBitmap
                        imageView.setImageBitmap(capturedFaceBitmap)
                    } else if (orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
                        // Portrait mode: Set the image to rotatedBitmap
                        imageView.setImageBitmap(rotatedBitmap)
                    }
                    imageView.visibility = View.VISIBLE
                    btnRetake.visibility = View.VISIBLE
                    btnOkay.visibility = View.VISIBLE
                }
            })
    }



    private fun processCapturedFace(bitmap: Bitmap?) {
        bitmap?.let {
            val inputImage = InputImage.fromBitmap(it, 0)
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
            val detector = FaceDetection.getClient(options)

            detector.process(inputImage)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val croppedBitmap = BitmapUtils.cropFace(it, face.boundingBox)
                        capturedFaceEmbedding = faceNetModel.getFaceEmbedding(croppedBitmap)
                        Toast.makeText(this, "Processed and saved the image, you can proceed and submit", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No face detected", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to process face", Toast.LENGTH_SHORT).show()
                    Log.e("FaceDetection", "Face detection failed: ${e.message}", e)
                }
        }
    }

    private fun submitUser() {
        val userId = etUserId.text.toString()
        val name = etName.text.toString()
        val email = etEmail.text.toString()

        if (name.isEmpty() || email.isEmpty() || capturedFaceEmbedding == null) {
            Toast.makeText(this, "Please complete all fields and capture a face image", Toast.LENGTH_SHORT).show()
            return
        }

        // Generate QR code and save it in QRs folder
        val qrCodeBitmap = generateQRCode(userId)
        val qrCodePath = saveBitmap(qrCodeBitmap, "${userId}_qr.jpg", getQRsDirectory())

        // Save user to database
        val user = User(userId, name, email, capturedFaceEmbedding!!, qrCodePath, photoFilePath!!)
        userRepository.insertUser(user)

        Toast.makeText(this, "User registered successfully", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun generateQRCode(data: String): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, 200, 200)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return bitmap
    }

    private fun saveBitmap(bitmap: Bitmap, filename: String, directory: File): String {
        val file = File(directory, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return file.absolutePath
    }


    private fun getOutputDirectory(baseFolderName: String): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name) + "/$baseFolderName").apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun getPhotosDirectory(): File {
        return getOutputDirectory("Photos")
    }

    private fun getQRsDirectory(): File {
        return getOutputDirectory("QRs")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}
