package com.example.tightbudget

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.tightbudget.adapters.AchievementsAdapter
import com.example.tightbudget.databinding.ActivityAchievementsBinding
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.firebase.GamificationManager
import com.example.tightbudget.models.Achievement
import com.example.tightbudget.models.UserProgress
import kotlinx.coroutines.launch

/**
 * Activity to display all user achievements with progress tracking
 */
class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private lateinit var gamificationManager: GamificationManager
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var achievementsAdapter: AchievementsAdapter
    private var userId: Int = -1

    companion object {
        private const val TAG = "AchievementsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        gamificationManager = GamificationManager.getInstance()
        firebaseDataManager = FirebaseDataManager.getInstance()
        userId = getCurrentUserId()

        setupUI()
        setupRecyclerView()
        loadAchievements()
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // Set up filter buttons
        binding.allFilterButton.setOnClickListener {
            filterAchievements("all")
        }

        binding.unlockedFilterButton.setOnClickListener {
            filterAchievements("unlocked")
        }

        binding.lockedFilterButton.setOnClickListener {
            filterAchievements("locked")
        }

        // Set initial filter state
        setActiveFilter(binding.allFilterButton)
    }

    private fun setupRecyclerView() {
        achievementsAdapter = AchievementsAdapter(emptyList()) { achievement ->
            // Handle achievement click - could show details
            showAchievementDetails(achievement)
        }

        binding.achievementsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@AchievementsActivity, 2)
            adapter = achievementsAdapter
        }
    }

    private fun loadAchievements() {
        if (userId == -1) {
            showGuestState()
            return
        }

        binding.loadingProgressBar.visibility = View.VISIBLE
        binding.achievementsRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Get all available achievements
                val allAchievements = gamificationManager.getAllAchievements()

                // Get user progress to determine unlocked achievements
                val userProgress = gamificationManager.getUserProgress(userId)

                // Get user transactions for progress calculation
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)

                // Create achievement display data with progress
                val achievementData = allAchievements.map { achievement ->
                    val isUnlocked = userProgress.achievementsUnlocked.contains(achievement.id)
                    val progress = calculateAchievementProgress(achievement, userProgress, transactions)

                    AchievementDisplayData(
                        achievement = achievement,
                        isUnlocked = isUnlocked,
                        currentProgress = progress,
                        progressPercentage = if (achievement.targetValue > 0) {
                            minOf(100, (progress * 100) / achievement.targetValue)
                        } else 100
                    )
                }

                runOnUiThread {
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.achievementsRecyclerView.visibility = View.VISIBLE

                    achievementsAdapter.updateAchievements(achievementData)
                    updateStatistics(achievementData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading achievements: ${e.message}", e)
                runOnUiThread {
                    binding.loadingProgressBar.visibility = View.GONE
                    showErrorState()
                }
            }
        }
    }

    private fun calculateAchievementProgress(
        achievement: Achievement,
        userProgress: UserProgress,
        transactions: List<com.example.tightbudget.models.Transaction>
    ): Int {
        return when (achievement.type) {
            com.example.tightbudget.models.AchievementType.TRANSACTIONS -> userProgress.transactionCount
            com.example.tightbudget.models.AchievementType.RECEIPTS -> userProgress.receiptsUploaded
            com.example.tightbudget.models.AchievementType.STREAK -> userProgress.longestStreak
            com.example.tightbudget.models.AchievementType.POINTS -> userProgress.totalPoints
            com.example.tightbudget.models.AchievementType.BUDGET_GOALS -> userProgress.budgetGoalsMet
            com.example.tightbudget.models.AchievementType.CATEGORIES -> {
                // Count unique categories used
                transactions.map { it.category }.distinct().size
            }
            else -> 0
        }
    }

    private fun updateStatistics(achievementData: List<AchievementDisplayData>) {
        val unlockedCount = achievementData.count { it.isUnlocked }
        val totalCount = achievementData.size
        val totalPointsEarned = achievementData.filter { it.isUnlocked }.sumOf { it.achievement.pointsRequired }

        binding.achievementProgress.text = "$unlockedCount / $totalCount"
        binding.totalPointsEarned.text = "$totalPointsEarned"

        val progressPercentage = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0
        binding.overallProgressBar.progress = progressPercentage
    }

    private fun filterAchievements(filter: String) {
        when (filter) {
            "all" -> {
                setActiveFilter(binding.allFilterButton)
                achievementsAdapter.filterAchievements { true }
            }
            "unlocked" -> {
                setActiveFilter(binding.unlockedFilterButton)
                achievementsAdapter.filterAchievements { it.isUnlocked }
            }
            "locked" -> {
                setActiveFilter(binding.lockedFilterButton)
                achievementsAdapter.filterAchievements { !it.isUnlocked }
            }
        }
    }

    private fun setActiveFilter(activeButton: View) {
        // Reset all buttons
        listOf(binding.allFilterButton, binding.unlockedFilterButton, binding.lockedFilterButton).forEach { button ->
            button.setBackgroundColor(getColor(R.color.background_light))
            if (true) { // Check if the button is a TextView or its subclass
                button.setTextColor(getColor(R.color.text_medium))
            }
        }

        // Set active button
        activeButton.setBackgroundColor(getColor(R.color.teal_light))
        if (activeButton is TextView) { // Check if the active button is a TextView or its subclass
            activeButton.setTextColor(getColor(R.color.white))
        }
    }

    private fun showAchievementDetails(achievementData: AchievementDisplayData) {
        val achievement = achievementData.achievement
        val message = if (achievementData.isUnlocked) {
            "🏆 ${achievement.title}\n\n${achievement.description}\n\nCongratulations! You earned ${achievement.pointsRequired} points!"
        } else {
            "${achievement.emoji} ${achievement.title}\n\n${achievement.description}\n\nProgress: ${achievementData.currentProgress}/${achievement.targetValue}\nReward: ${achievement.pointsRequired} points"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (achievementData.isUnlocked) "Achievement Unlocked!" else "Achievement Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showGuestState() {
        binding.loadingProgressBar.visibility = View.GONE
        binding.achievementsRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Log in to view achievements"
        binding.emptyStateSubtext.text = "Create an account to start earning badges and tracking your progress!"
    }

    private fun showErrorState() {
        binding.achievementsRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.emptyStateText.text = "Failed to load achievements"
        binding.emptyStateSubtext.text = "Please check your connection and try again"
    }
}

/**
 * Data class for achievement display with progress information
 */
data class AchievementDisplayData(
    val achievement: Achievement,
    val isUnlocked: Boolean,
    val currentProgress: Int,
    val progressPercentage: Int
)