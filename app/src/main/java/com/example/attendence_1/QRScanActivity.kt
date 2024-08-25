package com.example.attendence_1

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var switchCameraButton: ImageButton
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val barcodeScannerOptions = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        previewView = findViewById(R.id.previewView)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        cameraExecutor = Executors.newSingleThreadExecutor()

        startCamera()

        // Set up the listener for the switch camera button
        switchCameraButton.setOnClickListener {
            toggleCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer())
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("QRScanActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        startCamera() // Restart the camera with the new selector
    }

    @OptIn(ExperimentalGetImage::class)
    private inner class BarcodeAnalyzer : ImageAnalysis.Analyzer {
        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                val scanner = BarcodeScanning.getClient(barcodeScannerOptions)

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                val intent = Intent()
                                intent.putExtra(EXTRA_QR_CODE, rawValue)
                                setResult(RESULT_OK, intent)
                                finish()
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("QRScanActivity", "Barcode scanning failed", e)
                        Toast.makeText(this@QRScanActivity, "Barcode scanning failed", Toast.LENGTH_SHORT).show()
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    companion object {
        const val EXTRA_QR_CODE = "com.example.attendence_1.EXTRA_QR_CODE"
    }
}
