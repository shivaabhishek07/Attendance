package com.example.attendence_1

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CalendarAdapter(
    private val daysOfMonth: ArrayList<String>,
    private val onItemListener: OnItemListener,
    private val attendanceMap: Map<String, String>,
    private val selectedDate: LocalDate
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.calendar_day_item, parent, false)
        return CalendarViewHolder(view, onItemListener)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val dayText = daysOfMonth[position]
        holder.dayOfMonth.text = dayText

        if (dayText.isNotEmpty()) {
            // Format the date string
            val dateStr = "$dayText-${selectedDate.format(DateTimeFormatter.ofPattern("MM-yyyy"))}"

            // Get the current year and month
            val currentDate = LocalDate.now()
            val dayOfMonthInt = dayText.toInt()
            val dateOfDay = LocalDate.of(selectedDate.year, selectedDate.month, dayOfMonthInt)

            // Determine the status based on whether the date is in the past or future
            val status = when {
                dateOfDay.isBefore(currentDate) -> {
                    // If the date is in the past and not in the map, consider it as absent
                    attendanceMap[dateStr] ?: "Absent"
                }
                dateOfDay.isEqual(currentDate) -> {
                    // If it's today, check the map
                    attendanceMap[dateStr] ?: "Not-taken"
                }
                else -> "Future"  // For future dates
            }

            // Set background color based on the status
            holder.itemView.setBackgroundColor(
                when (status) {
                    "Present" -> ContextCompat.getColor(holder.itemView.context, R.color.green)
                    "Absent" -> ContextCompat.getColor(holder.itemView.context, R.color.red)
                    else -> Color.TRANSPARENT
                }
            )
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    override fun getItemCount(): Int {
        return daysOfMonth.size
    }

    inner class CalendarViewHolder(itemView: View, private val onItemListener: OnItemListener) :
        RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val dayOfMonth: TextView = itemView.findViewById(R.id.tvDay)

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(view: View?) {
            onItemListener.onItemClick(adapterPosition, dayOfMonth.text.toString())
        }
    }

    interface OnItemListener {
        fun onItemClick(position: Int, dayText: String)
    }
}
