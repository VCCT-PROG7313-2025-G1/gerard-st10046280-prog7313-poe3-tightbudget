package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.databinding.ActivityProfileBinding
import com.example.tightbudget.utils.ChartUtils
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.example.tightbudget.utils.ProgressBarUtils
import kotlinx.coroutines.launch

/**
 * ProfileActivity displays the user's profile, including:
 * - User information and avatar
 * - Experience level and progress
 * - Achievement badges
 * - Daily challenges
 * - Points history chart
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val TAG = "ProfileActivity"
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get the current user ID
        userId = getCurrentUserId()

        setupBackButton()
        setupProfileData()
        setupLevelProgress()
        setupPointsChart()
        setupAchievementBadges()
        setupChallenges()
    }

    /**
     * Retrieves the current user ID from SharedPreferences
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    /**
     * Closes this screen and returns to the previous one
     */
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Loads and displays the user profile information
     */
    private fun setupProfileData() {
        if (userId == -1) {
            // Guest user - show default data
            binding.usernameText.text = "Guest User"
            binding.profileLevelBadge.text = "0"
            binding.streakCount.text = "0"
            return
        }

        // Get actual user data
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@ProfileActivity)
                val user = db.userDao().getUserById(userId)

                // Update UI with user data
                runOnUiThread {
                    if (user != null) {
                        binding.usernameText.text = user.fullName

                        // Set level and streak based on activity (these would come from user stats)
                        binding.profileLevelBadge.text = "12"
                        binding.streakCount.text = "8"

                        // Set profile avatar (this would be based on user preferences)
                        DrawableUtils.applyWhiteCircleBackground(binding.profileAvatar, this@ProfileActivity)
                        binding.profileAvatar.setImageResource(R.drawable.app_icon)
                    } else {
                        Log.e(TAG, "User not found with ID: $userId")
                        Toast.makeText(this@ProfileActivity, "User data not found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user profile: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@ProfileActivity,
                        "Error loading profile: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Applies XP bar styling and sets dynamic text
     */
    private fun setupLevelProgress() {
        // Get from user stats in a real app
        val currentPoints = 680
        val nextLevelThreshold = 1000
        val totalPoints = 2450

        // Set progress values
        binding.pointsText.text = "$currentPoints pts"
        binding.totalPoints.text = "$totalPoints pts"

        val pointsToNextLevel = nextLevelThreshold - currentPoints
        binding.levelProgressBar.progress = ((currentPoints.toFloat() / nextLevelThreshold) * 100).toInt()

        // Apply styled progress bar
        ProgressBarUtils.applyColoredProgressBar(
            binding.levelProgressBar,
            this,
            ContextCompat.getColor(this, R.color.teal_light)
        )

        // Set text for next level info
        if (pointsToNextLevel > 0) {
            binding.nextLevelText.text = "Earn $pointsToNextLevel more points to reach Level 13"
        } else {
            binding.nextLevelText.text = "Ready to advance to Level 13!"
        }
    }

    /**
     * Sets up the achievement badges
     */
    private fun setupAchievementBadges() {
        // In Part 3, these would be loaded from a database
        val badges = listOf(
            "Saver" to true,
            "Consistent" to true,
            "Transport" to true,
            "Locked" to false
        )

        // Apply badges to UI
        var index = 0
        val badgeLayouts = listOf(
            binding.saverBadgeIcon,
            binding.consistentBadgeIcon,
            binding.transportBadgeIcon,
            binding.lockedBadgeIcon
        )

        badges.forEachIndexed { i, badge ->
            if (i < badgeLayouts.size) {
                val (name, earned) = badge
                val badgeView = badgeLayouts[i]

                // Set the emoji for the badge
                badgeView.text = EmojiUtils.getAchievementEmoji(name)

                // Apply background
                if (earned) {
                    DrawableUtils.applyCircleBackground(
                        badgeView,
                        ContextCompat.getColor(this, R.color.teal_light)
                    )
                } else {
                    DrawableUtils.applyCircleBackground(
                        badgeView,
                        ContextCompat.getColor(this, R.color.background_gray)
                    )
                    badgeView.alpha = 0.5f
                }
            }
        }

        // Set up click listener for "All Badges"
        binding.allBadgesButton.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
    }

    /**
     * Sets up the daily challenges section
     */
    private fun setupChallenges() {
        // These would be loaded from a challenge system in Part 3

        // For demo purposes, set one challenge as completed
        binding.challenge2Checkbox.isChecked = true
    }

    /**
     * Displays a points chart using ChartUtils
     * For now it uses sample data, but will be replaced with actual data in Part 3
     */
    private fun setupPointsChart() {
        val container: FrameLayout = binding.pointsChartContainer

        // Sample data for demo purposes. This will be replaced with actual data in Part 3.
        val samplePoints = mapOf(
            "Mon" to 50f,
            "Tue" to 80f,
            "Wed" to 60f,
            "Thu" to 90f,
            "Fri" to 30f,
            "Sat" to 100f,
            "Sun" to 70f
        )

        // Create and add the chart to the container
        container.removeAllViews()
        val lineChart = ChartUtils.EnhancedLineChartView(this, samplePoints, false)
        container.addView(
            lineChart,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Set up month selector
        binding.viewByMonth.setOnClickListener {
            Toast.makeText(this, "Would show month selector", Toast.LENGTH_SHORT).show()
        }
    }
}