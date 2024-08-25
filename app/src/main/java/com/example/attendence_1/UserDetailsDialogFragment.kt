package com.example.attendence_1

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class UserDetailsDialogFragment : DialogFragment(), CalendarAdapter.OnItemListener {

    private lateinit var monthYearText: TextView
    private lateinit var calendarRecyclerView: RecyclerView
    private var selectedDate: LocalDate = LocalDate.now()
    private lateinit var attendanceRepository: AttendanceRepository
    private var attendanceMap: Map<String, String> = emptyMap()
    private lateinit var userRepository: UserRepository

    companion object {
        fun newInstance(user: User): UserDetailsDialogFragment {
            val fragment = UserDetailsDialogFragment()
            val args = Bundle().apply {
                putString("userId", user.userId)
                putString("name", user.name)
                putString("email", user.email)
                putString("photoPath", user.photoPath)
                putString("qrCodePath", user.qrCodePath)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_user_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        attendanceRepository = AttendanceRepository(requireContext())
        userRepository = UserRepository(requireContext())

        // Set up user details
        val userId = arguments?.getString("userId")
        val name = arguments?.getString("name")
        val email = arguments?.getString("email")
        val photoPath = arguments?.getString("photoPath")
        val qrCodePath = arguments?.getString("qrCodePath")

        view.findViewById<TextView>(R.id.tvDialogUserId).text = userId
        view.findViewById<TextView>(R.id.tvDialogUserName).text = name
        view.findViewById<TextView>(R.id.tvDialogUserEmail).text = email

        val photoImageView = view.findViewById<ImageView>(R.id.ivDialogUserPhoto)
        val qrPhotoImageView = view.findViewById<ImageView>(R.id.ivDialogUserQrPhoto)

        Glide.with(this).load(photoPath).into(photoImageView)
        Glide.with(this).load(qrCodePath).into(qrPhotoImageView)

        photoImageView.setOnClickListener {
            FullScreenImageDialogFragment.newInstance(photoPath.orEmpty()).show(parentFragmentManager, "FullScreenImage")
        }

        qrPhotoImageView.setOnClickListener {
            FullScreenImageDialogFragment.newInstance(qrCodePath.orEmpty()).show(parentFragmentManager, "FullScreenImage")
        }

        // Set up the calendar
        initCalendarWidgets(view)
        setMonthView()

        view.findViewById<Button>(R.id.btnPreviousMonth).setOnClickListener {
            previousMonthAction()
        }

        view.findViewById<Button>(R.id.btnNextMonth).setOnClickListener {
            nextMonthAction()
        }

        userId?.let {
            loadAttendanceData(it, selectedDate)
        }

        view.findViewById<Button>(R.id.btnDeleteUser).setOnClickListener {
            userId?.let {
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete User")
                    .setMessage("Are you sure you want to delete this user?")
                    .setPositiveButton("Yes") { dialog, which ->
                        val rowsDeleted = userRepository.deleteUser(it)
                        if (rowsDeleted > 0) {
                            Toast.makeText(requireContext(), "User deleted successfully.", Toast.LENGTH_SHORT).show()
                            dismiss()  // Close the dialog after deletion
                        } else {
                            Toast.makeText(requireContext(), "Failed to delete user.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

    }

    private fun initCalendarWidgets(view: View) {
        calendarRecyclerView = view.findViewById(R.id.calendarRecyclerView)
        monthYearText = view.findViewById(R.id.tvMonthYear)
    }

    private fun setMonthView() {
        monthYearText.text = monthYearFromDate(selectedDate)
        val daysInMonth = daysInMonthArray(selectedDate)

        val calendarAdapter = CalendarAdapter(daysInMonth, this, attendanceMap, selectedDate)
        val layoutManager = GridLayoutManager(requireContext(), 7)
        calendarRecyclerView.layoutManager = layoutManager
        calendarRecyclerView.adapter = calendarAdapter
    }

    private fun daysInMonthArray(date: LocalDate): ArrayList<String> {
        val daysInMonthArray = ArrayList<String>()
        val yearMonth = YearMonth.from(date)

        val daysInMonth = yearMonth.lengthOfMonth()
        val firstOfMonth = selectedDate.withDayOfMonth(1)
        val dayOfWeek = firstOfMonth.dayOfWeek.value

        for (i in 1..42) {
            if (i <= dayOfWeek || i > daysInMonth + dayOfWeek) {
                daysInMonthArray.add("")
            } else {
                daysInMonthArray.add((i - dayOfWeek).toString())
            }
        }
        return daysInMonthArray
    }

    private fun monthYearFromDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        return date.format(formatter)
    }

    fun previousMonthAction() {
        selectedDate = selectedDate.minusMonths(1)
        setMonthView()
        arguments?.getString("userId")?.let {
            loadAttendanceData(it, selectedDate)
        }
    }

    fun nextMonthAction() {
        selectedDate = selectedDate.plusMonths(1)
        setMonthView()
        arguments?.getString("userId")?.let {
            loadAttendanceData(it, selectedDate)
        }
    }

    private fun loadAttendanceData(userId: String, date: LocalDate) {
        val startOfMonth = date.withDayOfMonth(1)
        val endOfMonth = date.withDayOfMonth(date.month.length(date.isLeapYear))

        val startDateStr = startOfMonth.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val endDateStr = endOfMonth.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))

        val attendanceRecords = attendanceRepository.getAttendanceForMonth(userId, startDateStr, endDateStr)
        attendanceMap = attendanceRecords.associate { it.date to it.status }

        setMonthView()
    }

    override fun onItemClick(position: Int, dayText: String) {
        if (dayText.isNotEmpty()) {
            val selectedDateStr = "$dayText-${selectedDate.format(DateTimeFormatter.ofPattern("MM-yyyy"))}"
            val currentDate = LocalDate.now()
            val dayOfMonthInt = dayText.toInt()
            val dateOfDay = LocalDate.of(selectedDate.year, selectedDate.month, dayOfMonthInt)
            val attendanceStatus  = when {
                dateOfDay.isBefore(currentDate) -> {
                    // If the date is in the past and not in the map, consider it as absent
                    attendanceMap[selectedDateStr] ?: "Absent"
                }
                dateOfDay.isEqual(currentDate) -> {
                    // If it's today, check the map
                    attendanceMap[selectedDateStr] ?: "Not-taken"
                }
                else -> "Future"  // For future dates
            }
            val message = "Selected Date: $selectedDateStr, Status: $attendanceStatus"
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
}
