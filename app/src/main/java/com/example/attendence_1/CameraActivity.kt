package com.example.attendence_1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class CameraActivity : AppCompatActivity() {

    private val REQUEST_IMAGE_CAPTURE = 1
    private lateinit var imageFilePath: String
    private val REQUEST_CAMERA_PERMISSION = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION)
        } else {
            // Permission already granted, proceed with camera functionality
            dispatchTakePictureIntent()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, proceed with camera functionality
                    dispatchTakePictureIntent()
                } else {
                    // Permission denied, show a message to the user
                    Toast.makeText(this, "Camera permission is required to use the camera", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val extras = data?.extras
            val imageBitmap = extras?.get("data") as Bitmap

            // Define the file path and save the image
            imageFilePath = "${filesDir.absolutePath}/captured_face.png"
            saveImageToFile(imageBitmap, imageFilePath)

            // Return the file path to RegisterActivity
            val resultIntent = Intent()
            resultIntent.putExtra("faceBitmapPath", imageFilePath)
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    private fun saveImageToFile(bitmap: Bitmap, filePath: String) {
        val file = File(filePath)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
    }
}
