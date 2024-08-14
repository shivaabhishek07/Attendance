package com.example.attendence_1

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class UserDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "attendance.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "users"
        const val COLUMN_USER_ID = "user_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_EMBEDDING = "embedding"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_NAME + " ("
                + COLUMN_USER_ID + " INTEGER PRIMARY KEY,"
                + COLUMN_NAME + " TEXT,"
                + COLUMN_EMAIL + " TEXT,"
                + COLUMN_EMBEDDING + " BLOB)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }
}
