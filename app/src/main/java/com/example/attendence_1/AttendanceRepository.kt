package com.example.attendence_1

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

class AttendanceRepository(context: Context) {
    private val dbHelper = UserDatabaseHelper(context)
    private val database: SQLiteDatabase = dbHelper.writableDatabase

    // Insert a new attendance record into the database
    fun insertAttendance(userId: String, date: String, status: String) {
        val values = ContentValues().apply {
            put(UserDatabaseHelper.COLUMN_USER_ID_FK, userId)
            put(UserDatabaseHelper.COLUMN_DATE, date)
            put(UserDatabaseHelper.COLUMN_STATUS, status)
        }
        database.insert(UserDatabaseHelper.ATTENDANCE_TABLE_NAME, null, values)
    }

    // Retrieve attendance records for a specific user on a specific date
    fun getAttendanceByUserIdAndDate(userId: String, date: String): AttendanceRecord? {
        val cursor: Cursor = database.query(
            UserDatabaseHelper.ATTENDANCE_TABLE_NAME,
            null,
            "${UserDatabaseHelper.COLUMN_USER_ID_FK} = ? AND ${UserDatabaseHelper.COLUMN_DATE} = ?",
            arrayOf(userId, date),
            null,
            null,
            null
        )

        return if (cursor.moveToFirst()) {
            val attendanceId = cursor.getInt(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_ATTENDANCE_ID))
            val status = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_STATUS))
            AttendanceRecord(attendanceId, userId, date, status)
        } else {
            null
        }.also {
            cursor.close()
        }
    }

    // Retrieve attendance records for a specific user for the entire month
    fun getAttendanceForMonth(userId: String, startDate: String, endDate: String): List<AttendanceRecord> {
        val cursor: Cursor = database.query(
            UserDatabaseHelper.ATTENDANCE_TABLE_NAME,
            null,
            "${UserDatabaseHelper.COLUMN_USER_ID_FK} = ? AND ${UserDatabaseHelper.COLUMN_DATE} BETWEEN ? AND ?",
            arrayOf(userId, startDate, endDate),
            null,
            null,
            null
        )

        val attendanceRecords = mutableListOf<AttendanceRecord>()
        while (cursor.moveToNext()) {
            val attendanceId = cursor.getInt(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_ATTENDANCE_ID))
            val date = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_DATE))
            val status = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_STATUS))
            attendanceRecords.add(AttendanceRecord(attendanceId, userId, date, status))
        }
        cursor.close()
        return attendanceRecords
    }

    // Retrieve attendance records for a specific date
    fun getAttendanceByDate(date: String): List<AttendanceRecord> {
        val cursor: Cursor = database.query(
            UserDatabaseHelper.ATTENDANCE_TABLE_NAME,
            null,
            "${UserDatabaseHelper.COLUMN_DATE} = ?",
            arrayOf(date),
            null,
            null,
            null
        )

        val attendanceRecords = mutableListOf<AttendanceRecord>()
        while (cursor.moveToNext()) {
            val attendanceId = cursor.getInt(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_ATTENDANCE_ID))
            val userId = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_USER_ID_FK))
            val status = cursor.getString(cursor.getColumnIndexOrThrow(UserDatabaseHelper.COLUMN_STATUS))
            attendanceRecords.add(AttendanceRecord(attendanceId, userId, date, status))
        }
        cursor.close()
        return attendanceRecords
    }

    // Check if attendance has already been marked for a specific user on a specific date
    fun hasAttendanceMarked(userId: String, date: String): Boolean {
        val cursor: Cursor = database.query(
            UserDatabaseHelper.ATTENDANCE_TABLE_NAME,
            arrayOf(UserDatabaseHelper.COLUMN_ATTENDANCE_ID),
            "${UserDatabaseHelper.COLUMN_USER_ID_FK} = ? AND ${UserDatabaseHelper.COLUMN_DATE} = ?",
            arrayOf(userId, date),
            null,
            null,
            null
        )
        val hasRecord = cursor.moveToFirst()
        cursor.close()
        return hasRecord
    }



    data class AttendanceRecord(
        val attendanceId: Int,
        val userId: String,
        val date: String,
        val status: String
    )
}
