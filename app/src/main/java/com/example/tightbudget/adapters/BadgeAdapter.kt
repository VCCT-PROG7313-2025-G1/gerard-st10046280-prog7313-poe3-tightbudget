package com.example.tightbudget.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.models.Badge
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils

/**
 * Adapter for displaying achievement badges in a grid.
 */
class BadgeAdapter(
    private val context: Context,
    private var badgeList: List<Badge>
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    /**
     * ViewHolder pattern to reference views inside each badge item
     */
    inner class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emoji: TextView = itemView.findViewById(R.id.badgeEmoji)
        val title: TextView = itemView.findViewById(R.id.badgeTitle)
        val subtitle: TextView = itemView.findViewById(R.id.badgeSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badgeList[position]

        // Set emoji based on badge name (fallback to locked if unknown)
        val emoji = EmojiUtils.getAchievementEmoji(badge.name)
        holder.emoji.text = emoji

        // Set circle background colour based on whether badge is earned
        val backgroundColor = if (badge.earned) {
            ContextCompat.getColor(context, R.color.teal_light)
        } else {
            ContextCompat.getColor(context, R.color.background_gray)
        }
        DrawableUtils.applyCircleBackground(holder.emoji, backgroundColor)

        // Badge name
        holder.title.text = badge.name

        // Optional subtitle like level or condition
        holder.subtitle.text = badge.subtitle

        // Dimmed styling for locked badges
        if (!badge.earned) {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.text_light))
            holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.text_light))
            holder.emoji.alpha = 0.4f
        } else {
            holder.title.setTextColor(ContextCompat.getColor(context, R.color.text_dark))
            holder.subtitle.setTextColor(ContextCompat.getColor(context, R.color.text_light))
            holder.emoji.alpha = 1f
        }
    }

    override fun getItemCount(): Int = badgeList.size

    /**
     * Updates the list of badges (e.g., when filtered)
     */
    fun updateList(newList: List<Badge>) {
        badgeList = newList
        notifyDataSetChanged()
    }
}