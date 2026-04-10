package com.example.carparkingsmart // Kiểm tra lại tên package của bạn

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

// Model đơn giản để hứng dữ liệu từ Django
data class ChargingSlot(
    val id: Int,
    val slot_code: String,
    val is_available: Boolean
)

class SlotAdapter(
    private val slots: List<ChargingSlot>,
    private val onSlotSelected: (ChargingSlot) -> Unit // Callback khi click
) : RecyclerView.Adapter<SlotAdapter.SlotViewHolder>() {

    private var selectedPosition = -1

    class SlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.card_slot)
        val text: TextView = view.findViewById(R.id.tv_slot_code)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        // Sử dụng file item_charging_slot.xml mà chúng ta đã nói ở bước trước
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_charging_slot, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = slots[position]
        holder.text.text = slot.slot_code

        // 1. Xử lý màu sắc dựa trên trạng thái Is_Available và Selected
        if (!slot.is_available) {
            holder.card.setCardBackgroundColor(Color.LTGRAY) // Màu xám: Đã có xe
            holder.text.setTextColor(Color.DKGRAY)
            holder.itemView.isEnabled = false
        } else {
            holder.itemView.isEnabled = true
            holder.text.setTextColor(Color.WHITE)

            if (selectedPosition == position) {
                holder.card.setCardBackgroundColor(Color.parseColor("#FFD600")) // Màu Vàng: Đang chọn
                holder.text.setTextColor(Color.BLACK)
            } else {
                holder.card.setCardBackgroundColor(Color.parseColor("#4CAF50")) // Màu Xanh: Ô trống
            }
        }

        // 2. Xử lý sự kiện Click
        holder.itemView.setOnClickListener {
            val previousSelected = selectedPosition
            selectedPosition = holder.bindingAdapterPosition

            // Làm mới 2 ô: ô cũ và ô vừa chọn để đổi màu
            notifyItemChanged(previousSelected)
            notifyItemChanged(selectedPosition)

            // Gửi dữ liệu ô đã chọn về cho Activity
            onSlotSelected(slot)
        }
    }

    override fun getItemCount() = slots.size
}