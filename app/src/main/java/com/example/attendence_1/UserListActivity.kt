package com.example.attendence_1

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import androidx.appcompat.widget.Toolbar
import java.util.Arrays

@Suppress("DEPRECATION")
class UserListActivity : AppCompatActivity() {

    private lateinit var userRepository: UserRepository
    private lateinit var attendanceRepository: AttendanceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_list)

        userRepository = UserRepository(this)
        attendanceRepository = AttendanceRepository(this)

        val btnViewUsers: Button = findViewById(R.id.btnViewUsers)
        val btnViewAttendance: Button = findViewById(R.id.btnViewAttendance)
        val userListView: ListView = findViewById(R.id.userListView)
        val attendanceLayout: LinearLayout = findViewById(R.id.attendanceLayout)

        btnViewUsers.setOnClickListener {
            userListView.visibility = View.VISIBLE
            attendanceLayout.visibility = View.GONE
            loadUsers()
        }

        btnViewAttendance.setOnClickListener {
            userListView.visibility = View.GONE
            attendanceLayout.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.btnFetchAttendance).setOnClickListener {
            val dateInput = findViewById<EditText>(R.id.etDateInput).text.toString().trim()
            Log.d("DateInput", "Date: $dateInput")
            if (dateInput.isNotEmpty()) {
                loadAttendance(dateInput)
            } else {
                Toast.makeText(this, "Please enter a date", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Load and display users when the activity resumes
        loadUsers()
    }

    private fun loadUsers() {
        val users = userRepository.getAllUsers()
        val userListView: ListView = findViewById(R.id.userListView)
        val noUsersFoundTextView: TextView = findViewById(R.id.tvNoUsersFound)

        if (users.isEmpty()) {
            noUsersFoundTextView.visibility = TextView.VISIBLE
            userListView.visibility = ListView.GONE
        } else {
            noUsersFoundTextView.visibility = TextView.GONE
            userListView.visibility = ListView.VISIBLE

            val userList = users.map { user ->
                mapOf(
                    "userId" to user.userId,
                    "name" to user.name,
                    "email" to user.email,
                    "photo" to user.photoPath,
                    "qrPhoto" to user.qrCodePath
                )
            }

            val from = arrayOf("userId", "name", "email", "photo", "qrPhoto")
            val to = intArrayOf(R.id.tvUserId, R.id.tvUserName, R.id.tvUserEmail, R.id.ivUserPhoto, R.id.ivUserQrPhoto)

            val adapter = object : SimpleAdapter(
                this, userList, R.layout.list_item_user, from, to
            ) {
                override fun setViewImage(view: ImageView, value: String?) {
                    Glide.with(this@UserListActivity)
                        .load(value)
                        .into(view)
                }
            }

            userListView.adapter = adapter

            userListView.setOnItemClickListener { _, _, position, _ ->
                val selectedUser = users[position]
                val dialog = UserDetailsDialogFragment.newInstance(selectedUser)
                dialog.show(supportFragmentManager, "UserDetailsDialog")
            }
        }
    }

    private fun loadAttendance(date: String) {
        Log.d("LoadAttendance", "It is loading")
        val attendanceRepository = AttendanceRepository(this)
        val users = userRepository.getAllUsers()
        val attendanceRecords = attendanceRepository.getAttendanceByDate(date)
        val attListView: ListView = findViewById(R.id.attendanceListView)
        val noAttFoTextView: TextView = findViewById(R.id.tvNoAttFound)

        Log.d("LoadAttendance", "Att $attendanceRecords , $users")
//        attListView.visibility = ListView.VISIBLE
        val attendanceMap = mutableMapOf<String, String>()
        attendanceRecords.forEach { record ->
            attendanceMap[record.userId] = record.status
        }

        val attendanceList = users.map { user ->
            val userName = user.name ?: "Unknown"
            val status = attendanceMap[user.userId] ?: "Absent"  // Default to "Absent" if not found
            mapOf(
                "userId" to user.userId.toString(),  // Convert userId to String
                "name" to userName,
                "status" to status
            )
        }

        if (attendanceList.isEmpty()) {
            noAttFoTextView.visibility = TextView.VISIBLE
            attListView.visibility = ListView.GONE
        } else {
            noAttFoTextView.visibility = TextView.GONE
            attListView.visibility = ListView.VISIBLE

            Log.d("LoadAttendance", "Att $attendanceList")

            val from = arrayOf("userId", "name", "status")
            val to = intArrayOf(R.id.tvUserId, R.id.tvUserName, R.id.tvStatus)

            val adapter = object : SimpleAdapter(
                this, attendanceList, R.layout.list_item_attendance, from, to
            ) {
                override fun setViewText(view: TextView, text: String?) {
                    super.setViewText(view, text ?: "")
                    view.text = text
                }
            }

            Log.d("LoadAttendance", "Changing")
            attListView.adapter = adapter
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed() // Go back to the previous activity
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
