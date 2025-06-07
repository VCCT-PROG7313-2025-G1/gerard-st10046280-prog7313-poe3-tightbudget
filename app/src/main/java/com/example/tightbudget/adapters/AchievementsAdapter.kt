package com.example.tightbudget.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.AchievementDisplayData
import com.example.tightbudget.R
import com.example.tightbudget.utils.DrawableUtils

/**
 * Adapter for displaying achievements in a grid layout
 */
class AchievementsAdapter(
    private var allAchievements: List<AchievementDisplayData>,
    private val onAchievementClick: (AchievementDisplayData) -> Unit
) : RecyclerView.Adapter<AchievementsAdapter.AchievementViewHolder>() {

    private var filteredAchievements: List<AchievementDisplayData> = allAchievements

    class AchievementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val achievementIcon: TextView = view.findViewById(R.id.achievementIcon)
        val achievementTitle: TextView = view.findViewById(R.id.achievementTitle)
        val achievementDescription: TextView = view.findViewById(R.id.achievementDescription)
        val achievementProgress: ProgressBar = view.findViewById(R.id.achievementProgress)
        val achievementProgressText: TextView = view.findViewById(R.id.achievementProgressText)
        val achievementPoints: TextView = view.findViewById(R.id.achievementPoints)
        val achievementStatus: TextView = view.findViewById(R.id.achievementStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        val achievementData = filteredAchievements[position]
        val achievement = achievementData.achievement
        val context = holder.itemView.context

        // Set achievement details
        holder.achievementIcon.text = achievement.emoji
        holder.achievementTitle.text = achievement.title
        holder.achievementDescription.text = achievement.description
        holder.achievementPoints.text = "+${achievement.pointsRequired} pts"

        // Set progress
        holder.achievementProgress.progress = achievementData.progressPercentage
        holder.achievementProgressText.text = "${achievementData.currentProgress}/${achievement.targetValue}"

        if (achievementData.isUnlocked) {
            // Unlocked achievement styling
            holder.achievementIcon.alpha = 1f
            holder.achievementStatus.text = "UNLOCKED"
            holder.achievementStatus.setTextColor(ContextCompat.getColor(context, R.color.green_light))
            holder.achievementStatus.visibility = View.VISIBLE

            // Apply unlocked background
            DrawableUtils.applyCircleBackground(
                holder.achievementIcon,
                ContextCompat.getColor(context, R.color.achievement_unlocked)
            )

            // Hide progress for unlocked achievements
            holder.achievementProgress.visibility = View.GONE
            holder.achievementProgressText.visibility = View.GONE

            // Full opacity for unlocked
            holder.itemView.alpha = 1f

            // Gold border for unlocked achievements
            holder.itemView.setBackgroundResource(R.drawable.achievement_unlocked_background)

        } else {
            // Locked achievement styling
            holder.achievementIcon.alpha = 0.5f

            if (achievementData.currentProgress > 0) {
                // In progress
                holder.achievementStatus.text = "IN PROGRESS"
                holder.achievementStatus.setTextColor(ContextCompat.getColor(context, R.color.orange_light))
                holder.achievementStatus.visibility = View.VISIBLE
            } else {
                // Not started
                holder.achievementStatus.text = "LOCKED"
                holder.achievementStatus.setTextColor(ContextCompat.getColor(context, R.color.text_light))
                holder.achievementStatus.visibility = View.VISIBLE
            }

            // Apply locked background
            DrawableUtils.applyCircleBackground(
                holder.achievementIcon,
                ContextCompat.getColor(context, R.color.achievement_locked)
            )

            // Show progress for locked achievements
            holder.achievementProgress.visibility = View.VISIBLE
            holder.achievementProgressText.visibility = View.VISIBLE

            // Dim locked achievements
            holder.itemView.alpha = 0.8f

            // Default border for locked achievements
            holder.itemView.setBackgroundResource(R.drawable.achievement_locked_background)
        }

        // Set click listener
        holder.itemView.setOnClickListener {
            onAchievementClick(achievementData)
        }

        // Add subtle animation for unlocked achievements
        if (achievementData.isUnlocked) {
            holder.achievementIcon.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(1000)
                .withEndAction {
                    holder.achievementIcon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(1000)
                        .start()
                }
                .start()
        }
    }

    override fun getItemCount(): Int = filteredAchievements.size

    fun updateAchievements(newAchievements: List<AchievementDisplayData>) {
        allAchievements = newAchievements
        filteredAchievements = newAchievements
        notifyDataSetChanged()
    }

    fun filterAchievements(predicate: (AchievementDisplayData) -> Boolean) {
        filteredAchievements = allAchievements.filter(predicate)
        notifyDataSetChanged()
    }
}