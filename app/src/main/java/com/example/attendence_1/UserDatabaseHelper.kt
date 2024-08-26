package com.example.attendence_1

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "attendance.db"
        private const val DATABASE_VERSION = 4 // Incremented version
        const val TABLE_NAME = "users"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_EMBEDDING = "embedding"
        const val COLUMN_QR_CODE_PATH = "qr_code_path"
        const val COLUMN_PHOTO_PATH = "photo_path"

        // Attendance table constants
        const val ATTENDANCE_TABLE_NAME = "attendance"
        const val COLUMN_ATTENDANCE_ID = "attendance_id"
        const val COLUMN_USER_ID_FK = "user_id"
        const val COLUMN_DATE = "date"
        const val COLUMN_STATUS = "status"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = ("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_USER_ID + " TEXT,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_EMAIL + " TEXT,"
                + COLUMN_EMBEDDING + " BLOB,"
                + COLUMN_QR_CODE_PATH + " TEXT,"
                + COLUMN_PHOTO_PATH + " TEXT)")
        db.execSQL(createUsersTable)

        // Create attendance table
        val createAttendanceTable = ("CREATE TABLE " + ATTENDANCE_TABLE_NAME + " ("
                + COLUMN_ATTENDANCE_ID + " INTEGER PRIMARY KEY,"
                + COLUMN_USER_ID_FK + " TEXT ,"
                + COLUMN_DATE + " TEXT,"
                + COLUMN_STATUS + " TEXT CHECK(" + COLUMN_STATUS + " IN ('Present', 'Absent', 'Not-taken')),"
                + "FOREIGN KEY(" + COLUMN_USER_ID_FK + ") REFERENCES " + TABLE_NAME + "(" + COLUMN_USER_ID + "))")
        db.execSQL(createAttendanceTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            // Alter existing table to change user_id to TEXT if needed
            db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
            db.execSQL("DROP TABLE IF EXISTS $ATTENDANCE_TABLE_NAME")
            onCreate(db)
        }
    }
}
