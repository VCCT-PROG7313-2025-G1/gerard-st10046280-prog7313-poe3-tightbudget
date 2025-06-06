package com.example.tightbudget.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.tightbudget.R

/**
 * Helper class for showing points notifications and achievements
 */
object PointsNotificationHelper {

    /**
     * Show a custom points earned notification
     */
    fun showPointsEarned(context: Context, points: Int, reason: String) {
        val message = when {
            points >= 100 -> "🎉 Amazing! +$points pts: $reason"
            points >= 50 -> "🔥 Great job! +$points pts: $reason"
            points >= 25 -> "⭐ Nice! +$points pts: $reason"
            else -> "✨ +$points pts: $reason"
        }

        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Show achievement unlocked notification
     */
    fun showAchievementUnlocked(context: Context, achievementTitle: String, points: Int) {
        val message = "🏆 Achievement Unlocked!\n$achievementTitle\n+$points bonus points!"

        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Show level up notification
     */
    fun showLevelUp(context: Context, newLevel: Int) {
        val message = "🎊 LEVEL UP! You're now Level $newLevel!"

        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Show daily challenge completed notification
     */
    fun showChallengeCompleted(context: Context, challengeTitle: String, points: Int) {
        val message = "💪 Challenge Complete!\n$challengeTitle\n+$points points earned!"

        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Show streak milestone notification
     */
    fun showStreakMilestone(context: Context, streakDays: Int, bonusPoints: Int) {
        val message = when {
            streakDays >= 30 -> "🔥 INCREDIBLE! ${streakDays}-day streak! +$bonusPoints pts"
            streakDays >= 14 -> "🚀 Amazing ${streakDays}-day streak! +$bonusPoints pts"
            streakDays >= 7 -> "⚡ Week-long streak! +$bonusPoints pts"
            else -> "🔥 ${streakDays}-day streak! +$bonusPoints pts"
        }

        val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
        toast.show()
    }

    /**
     * Create animated points floating text (for future enhancement)
     */
    fun createFloatingPointsText(parent: ViewGroup, points: Int): View {
        val inflater = LayoutInflater.from(parent.context)
        val pointsView = inflater.inflate(R.layout.floating_points_text, parent, false)

        val pointsText = pointsView.findViewById<TextView>(R.id.floatingPointsText)
        pointsText.text = "+$points"

        // Set color based on points amount
        val color = when {
            points >= 100 -> ContextCompat.getColor(parent.context, R.color.gold)
            points >= 50 -> ContextCompat.getColor(parent.context, R.color.purple_light)
            points >= 25 -> ContextCompat.getColor(parent.context, R.color.teal_light)
            else -> ContextCompat.getColor(parent.context, R.color.green_light)
        }
        pointsText.setTextColor(color)

        return pointsView
    }

    /**
     * Animate the floating points text
     */
    fun animateFloatingPoints(pointsView: View, onComplete: () -> Unit) {
        val animator = ObjectAnimator.ofFloat(pointsView, "translationY", 0f, -200f)
        animator.duration = 1500

        val fadeAnimator = ObjectAnimator.ofFloat(pointsView, "alpha", 1f, 0f)
        fadeAnimator.duration = 1500
        fadeAnimator.startDelay = 500

        animator.start()
        fadeAnimator.start()

        // Call completion callback after animation
        pointsView.postDelayed(onComplete, 2000)
    }
}

/**
 * Extension functions for easy notification usage
 */
fun Context.showPointsEarned(points: Int, reason: String) {
    PointsNotificationHelper.showPointsEarned(this, points, reason)
}

fun Context.showAchievementUnlocked(achievementTitle: String, points: Int) {
    PointsNotificationHelper.showAchievementUnlocked(this, achievementTitle, points)
}

fun Context.showLevelUp(newLevel: Int) {
    PointsNotificationHelper.showLevelUp(this, newLevel)
}

fun Context.showChallengeCompleted(challengeTitle: String, points: Int) {
    PointsNotificationHelper.showChallengeCompleted(this, challengeTitle, points)
}

fun Context.showStreakMilestone(streakDays: Int, bonusPoints: Int) {
    PointsNotificationHelper.showStreakMilestone(this, streakDays, bonusPoints)
}