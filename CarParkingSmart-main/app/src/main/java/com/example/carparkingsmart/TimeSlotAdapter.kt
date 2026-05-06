package com.example.carparkingsmart

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class TimeSlot(
    val hour: Int,           // 0..23
    val label: String,       // "06:00 - 07:00"
    val isBooked: Boolean    // true = đã có người đặt → mờ
)

class TimeSlotAdapter(
    private val timeSlots: List<TimeSlot>,
    private val onTimeSelected: (TimeSlot) -> Unit
) : RecyclerView.Adapter<TimeSlotAdapter.TimeViewHolder>() {

    private var selectedPosition = -1

    inner class TimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLabel:  TextView = itemView.findViewById(R.id.tv_time_label)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_time_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_time_slot, parent, false)
        return TimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val slot = timeSlots[position]
        holder.tvLabel.text  = slot.label
        holder.tvStatus.text = if (slot.isBooked) "Đã đặt" else "Còn trống"

        when {
            slot.isBooked -> {
                // Mờ — không cho chọn
                holder.itemView.setBackgroundColor(Color.parseColor("#EEEEEE"))
                holder.tvLabel.setTextColor(Color.parseColor("#BDBDBD"))
                holder.tvStatus.setTextColor(Color.parseColor("#BDBDBD"))
                holder.itemView.alpha = 0.5f
                holder.itemView.isClickable = false
                holder.itemView.isEnabled = false
            }
            position == selectedPosition -> {
                // Đang chọn
                holder.itemView.setBackgroundColor(Color.parseColor("#4CAF50"))
                holder.tvLabel.setTextColor(Color.WHITE)
                holder.tvStatus.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1f
                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }
            else -> {
                // Trống bình thường
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9"))
                holder.tvLabel.setTextColor(Color.parseColor("#1B5E20"))
                holder.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                holder.itemView.alpha = 1f
                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }
        }

        holder.itemView.setOnClickListener {
            if (slot.isBooked) return@setOnClickListener
            val old = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(old)
            notifyItemChanged(selectedPosition)
            onTimeSelected(slot)
        }
    }

    override fun getItemCount() = timeSlots.size
}