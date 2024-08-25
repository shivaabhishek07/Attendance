package com.example.attendence_1

data class User(
    val userId: String,
    val name: String,
    val email: String,
    val embedding: FloatArray,
    val qrCodePath: String,      // Add this field for storing QR code path
    val photoPath: String        // Add this field for storing photo path
)
