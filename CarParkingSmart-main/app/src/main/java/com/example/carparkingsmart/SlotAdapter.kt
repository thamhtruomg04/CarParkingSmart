package com.example.carparkingsmart

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChargingSlot(
    val id: Int,
    val slot_code: String,
    val is_available: Boolean
)

class SlotAdapter(
    private val slots: List<ChargingSlot>,
    private val onSlotSelected: (ChargingSlot) -> Unit
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private var selectedPosition = -1

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvSlotCode: TextView = itemView.findViewById(R.id.tv_slot_code)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_charging_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        holder.tvSlotCode.text = slot.slot_code

        when {
            // ✅ Ô đã bị đặt — làm mờ, không cho chọn
            !slot.is_available -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#CCCCCC"))
                holder.tvSlotCode.setTextColor(Color.parseColor("#888888"))
                holder.itemView.alpha = 0.5f
                holder.itemView.isClickable = false
                holder.itemView.isEnabled = false
            }
            // ✅ Ô đang được chọn
            position == selectedPosition -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#4CAF50"))
                holder.tvSlotCode.setTextColor(Color.WHITE)
                holder.itemView.alpha = 1f
                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }
            // ✅ Ô trống bình thường
            else -> {
                holder.itemView.setBackgroundColor(Color.parseColor("#E8F5E9"))
                holder.tvSlotCode.setTextColor(Color.parseColor("#1B5E20"))
                holder.itemView.alpha = 1f
                holder.itemView.isClickable = true
                holder.itemView.isEnabled = true
            }
        }

        holder.itemView.setOnClickListener {
            if (!slot.is_available) return@setOnClickListener
            val oldPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            onSlotSelected(slot)
        }
    }

    override fun getItemCount() = slots.size
}