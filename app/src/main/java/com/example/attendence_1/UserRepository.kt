package com.example.attendence_1

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.nio.ByteBuffer

class UserRepository(context: Context) {
    private val dbHelper = UserDatabaseHelper(context)
    private val database: SQLiteDatabase = dbHelper.writableDatabase

    fun insertUser(user: User) {
        val values = ContentValues().apply {
            put(UserDatabaseHelper.COLUMN_USER_ID, user.userId)
            put(UserDatabaseHelper.COLUMN_NAME, user.name)
            put(UserDatabaseHelper.COLUMN_EMAIL, user.email)
            put(UserDatabaseHelper.COLUMN_EMBEDDING, user.embedding.toByteArray())
        }
        database.insert(UserDatabaseHelper.TABLE_NAME, null, values)
    }

    fun getUserById(userId: Int): User? {
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
            User(userId, name, email, embedding)
        } else {
            null
        }.also {
            cursor.close()
        }
    }

    private fun FloatArray.toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(this.size * 4)
        this.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this)
        val floatArray = FloatArray(this.size / 4)
        buffer.asFloatBuffer().get(floatArray)
        return floatArray
    }
}
