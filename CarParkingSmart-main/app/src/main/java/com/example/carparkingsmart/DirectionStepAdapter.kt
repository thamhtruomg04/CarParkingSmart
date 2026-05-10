package com.example.carparkingsmart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DirectionStepAdapter(
    private val steps: List<DirectionStep>
) : RecyclerView.Adapter<DirectionStepAdapter.StepViewHolder>() {

    class StepViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_step_icon)
        val tvInstruction: TextView = itemView.findViewById(R.id.tv_step_instruction)
        val tvDistance: TextView = itemView.findViewById(R.id.tv_step_distance)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_step_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_direction_step, parent, false)
        return StepViewHolder(view)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        val step = steps[position]
        holder.tvInstruction.text = step.instruction
        holder.tvDistance.text = step.distance
        holder.tvDuration.text = step.duration
        holder.ivIcon.setImageResource(getIconForManeuver(step.maneuver))
    }

    override fun getItemCount(): Int = steps.size

    private fun getIconForManeuver(maneuver: String): Int {
        return when {
            maneuver.contains("depart") -> R.drawable.ic_start
            maneuver.contains("arrive") -> R.drawable.ic_finish
            maneuver.contains("turn left") -> R.drawable.ic_turn_left
            maneuver.contains("turn right") -> R.drawable.ic_turn_right
            maneuver.contains("straight") -> R.drawable.ic_straight
            maneuver.contains("roundabout") -> R.drawable.ic_roundabout
            else -> R.drawable.ic_direction
        }
    }
}

data class DirectionStep(
    val instruction: String,
    val distance: String,
    val duration: String,
    val maneuver: String,
    val startLat: Double,
    val startLon: Double,
    val endLat: Double,
    val endLon: Double
)