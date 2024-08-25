package com.example.attendence_1

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.nio.ByteBuffer

class UserRepository(context: Context) {
    private val dbHelper = UserDatabaseHelper(context)
    private val database: SQLiteDatabase = dbHelper.writableDatabase

    // Insert a new user into the database
    fun insertUser(user: User) {
        val values = ContentValues().apply {
            put(UserDatabaseHelper.COLUMN_USER_ID, user.userId)
            put(UserDatabaseHelper.COLUMN_NAME, user.name)
            put(UserDatabaseHelper.COLUMN_EMAIL, user.email)
            put(UserDatabaseHelper.COLUMN_EMBEDDING, user.embedding.toByteArray())
            put(UserDatabaseHelper.COLUMN_QR_CODE_PATH, user.qrCodePath)
            put(UserDatabaseHelper.COLUMN_PHOTO_PATH, user.photoPath)
        }
        database.insert(UserDatabaseHelper.TABLE_NAME, null, values)
    }

    // Retrieve a user by their ID
    fun getUserById(userId: String): User? {
        val cursor: Cursor = database.query(
            UserDatabaseHelper.TABLE_NAME,
            null,
            "${UserDatabaseHelper.COLUMN_USER_ID} = ?",
            arrayOf(userId.toString()),
            null,
            null,
            null
        )

        return if (cursor.moveToFirst()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_NAME))
            val email = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_EMAIL))
            val embedding = cursor.getBlob(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_EMBEDDING)).toFloatArray()
            val qrCodePath = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_QR_CODE_PATH))
            val photoPath = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_PHOTO_PATH))
            User(userId, name, email, embedding, qrCodePath, photoPath)
        } else {
            null
        }.also {
            cursor.close()
        }
    }

    // Retrieve all users from the database
    fun getAllUsers(): List<User> {
        val users = mutableListOf<User>()
        val cursor: Cursor = database.query(
            UserDatabaseHelper.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                val userId = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_USER_ID))
                val name = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_NAME))
                val email = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_EMAIL))
                val embedding = cursor.getBlob(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_EMBEDDING)).toFloatArray()
                val qrCodePath = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_QR_CODE_PATH))
                val photoPath = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_PHOTO_PATH))

                val user = User(userId, name, email, embedding, qrCodePath, photoPath)
                users.add(user)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return users
    }

    fun deleteUser(userId: String): Int {
        val db = dbHelper.writableDatabase
        val whereClause = "${UserDatabaseHelper.COLUMN_USER_ID}=?"
        val whereArgs = arrayOf(userId)
        val rowsDeleted = db.delete(UserDatabaseHelper.TABLE_NAME, whereClause, whereArgs)
        db.close()
        return rowsDeleted
    }


    // Convert FloatArray to ByteArray
    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(this.size * 4)
        this.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    // Convert ByteArray to FloatArray
    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this)
        val floatArray = FloatArray(this.size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
