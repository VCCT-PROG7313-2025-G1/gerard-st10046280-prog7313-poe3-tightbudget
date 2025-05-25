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
import com.example.tightbudget.databinding.ActivityProfileBinding
import com.example.tightbudget.firebase.FirebaseDataManager
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
 * Updated to use Firebase instead of Room database.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var firebaseDataManager: FirebaseDataManager
    private val TAG = "ProfileActivity"
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase data manager
        firebaseDataManager = FirebaseDataManager.getInstance()

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
     * Loads and displays the user profile information using Firebase
     */
    private fun setupProfileData() {
        if (userId == -1) {
            // Guest user - show default data
            binding.usernameText.text = "Guest User"
            binding.profileLevelBadge.text = "0"
            binding.streakCount.text = "0"

            // Set default profile avatar
            DrawableUtils.applyWhiteCircleBackground(binding.profileAvatar, this)
            binding.profileAvatar.setImageResource(R.drawable.app_icon)

            Log.d(TAG, "Showing guest user profile")
            return
        }

        // Get actual user data from Firebase
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading user profile from Firebase for user: $userId")

                // Get user information from Firebase
                val user = firebaseDataManager.getUserById(userId)

                // Update UI with user data on main thread
                runOnUiThread {
                    if (user != null) {
                        binding.usernameText.text = user.fullName

                        // Calculate user level and streak based on activity
                        // In a real app, these would come from user stats or transaction analysis
                        calculateUserStats()

                        // Set profile avatar
                        DrawableUtils.applyWhiteCircleBackground(binding.profileAvatar, this@ProfileActivity)
                        binding.profileAvatar.setImageResource(R.drawable.app_icon)

                        Log.d(TAG, "Successfully loaded user profile from Firebase: ${user.fullName}")
                    } else {
                        Log.e(TAG, "User not found in Firebase with ID: $userId")
                        Toast.makeText(
                            this@ProfileActivity,
                            "User profile not found",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Show default guest data
                        binding.usernameText.text = "User Not Found"
                        binding.profileLevelBadge.text = "0"
                        binding.streakCount.text = "0"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user profile from Firebase: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error loading profile"
                        e.message?.contains("permission") == true ->
                            "Permission denied. Please check your Firebase configuration"
                        else -> "Error loading profile: ${e.message}"
                    }

                    Toast.makeText(this@ProfileActivity, errorMessage, Toast.LENGTH_SHORT).show()

                    // Show fallback data
                    binding.usernameText.text = "Profile Error"
                    binding.profileLevelBadge.text = "0"
                    binding.streakCount.text = "0"
                }
            }
        }
    }

    /**
     * Calculate user level and streak based on their transaction history from Firebase
     */
    private fun calculateUserStats() {
        if (userId == -1) return

        lifecycleScope.launch {
            try {
                // Get user's transaction history from Firebase to calculate stats
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)

                // Calculate level based on number of transactions (simple algorithm)
                val transactionCount = transactions.size
                val calculatedLevel = when {
                    transactionCount >= 100 -> 15
                    transactionCount >= 75 -> 12
                    transactionCount >= 50 -> 10
                    transactionCount >= 25 -> 7
                    transactionCount >= 10 -> 5
                    transactionCount >= 5 -> 3
                    transactionCount > 0 -> 1
                    else -> 0
                }

                // Calculate streak based on recent transaction activity
                val recentTransactions = firebaseDataManager.loadRecentTransactions(userId, 10)
                val calculatedStreak = calculateTransactionStreak(recentTransactions)

                // Calculate total points based on activity
                val totalPoints = (transactionCount * 25) + (calculatedLevel * 100)

                runOnUiThread {
                    binding.profileLevelBadge.text = calculatedLevel.toString()
                    binding.streakCount.text = calculatedStreak.toString()
                    binding.totalPoints.text = "$totalPoints pts"

                    Log.d(TAG, "Calculated user stats - Level: $calculatedLevel, Streak: $calculatedStreak, Points: $totalPoints")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error calculating user stats: ${e.message}", e)
                runOnUiThread {
                    // Use default values if calculation fails
                    binding.profileLevelBadge.text = "5"
                    binding.streakCount.text = "3"
                    binding.totalPoints.text = "450 pts"
                }
            }
        }
    }

    /**
     * Calculate transaction streak based on recent activity
     */
    private fun calculateTransactionStreak(recentTransactions: List<com.example.tightbudget.models.Transaction>): Int {
        if (recentTransactions.isEmpty()) return 0

        // Simple streak calculation - count consecutive days with transactions
        val today = java.util.Calendar.getInstance()
        var streak = 0

        // Group transactions by date
        val transactionsByDate = recentTransactions.groupBy { transaction ->
            val calendar = java.util.Calendar.getInstance()
            calendar.time = transaction.date
            "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
        }

        // Count consecutive days starting from today
        for (i in 0..30) { // Check last 30 days
            today.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateKey = "${today.get(java.util.Calendar.YEAR)}-${today.get(java.util.Calendar.DAY_OF_YEAR)}"

            if (transactionsByDate.containsKey(dateKey)) {
                streak++
            } else if (streak > 0) {
                break // Stop counting if we hit a day without transactions
            }

            // Reset calendar for next iteration
            today.add(java.util.Calendar.DAY_OF_YEAR, i)
        }

        return minOf(streak, 15) // Cap at 15 days
    }

    /**
     * Applies XP bar styling and sets dynamic text based on user activity
     */
    private fun setupLevelProgress() {
        if (userId == -1) {
            // Guest user - show minimal progress
            binding.pointsText.text = "0 pts"
            binding.totalPoints.text = "0 pts"
            binding.levelProgressBar.progress = 0
            binding.nextLevelText.text = "Log in to track your progress"
            return
        }

        lifecycleScope.launch {
            try {
                // Get user transactions to calculate dynamic progress
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)
                val transactionCount = transactions.size

                // Calculate current level and points
                val currentLevel = when {
                    transactionCount >= 100 -> 15
                    transactionCount >= 75 -> 12
                    transactionCount >= 50 -> 10
                    transactionCount >= 25 -> 7
                    transactionCount >= 10 -> 5
                    transactionCount >= 5 -> 3
                    transactionCount > 0 -> 1
                    else -> 0
                }

                val currentPoints = transactionCount * 25 + (currentLevel * 50)
                val nextLevelThreshold = when {
                    currentLevel >= 15 -> currentPoints + 500 // Max level
                    currentLevel >= 10 -> (currentLevel + 1) * 100 + ((currentLevel + 1) * 25 * 10)
                    else -> (currentLevel + 1) * 100 + ((currentLevel + 1) * 25 * 5)
                }
                val totalPoints = currentPoints + (currentLevel * 100)

                runOnUiThread {
                    // Set progress values
                    binding.pointsText.text = "$currentPoints pts"
                    binding.totalPoints.text = "$totalPoints pts"

                    val pointsToNextLevel = nextLevelThreshold - currentPoints
                    val progressPercentage = if (nextLevelThreshold > currentPoints) {
                        ((currentPoints.toFloat() / nextLevelThreshold) * 100).toInt()
                    } else {
                        100
                    }

                    binding.levelProgressBar.progress = progressPercentage

                    // Apply styled progress bar
                    ProgressBarUtils.applyColoredProgressBar(
                        binding.levelProgressBar,
                        this@ProfileActivity,
                        ContextCompat.getColor(this@ProfileActivity, R.color.teal_light)
                    )

                    // Set text for next level info
                    if (currentLevel >= 15) {
                        binding.nextLevelText.text = "Maximum level reached! Keep tracking your expenses."
                    } else if (pointsToNextLevel > 0) {
                        binding.nextLevelText.text = "Earn $pointsToNextLevel more points to reach Level ${currentLevel + 1}"
                    } else {
                        binding.nextLevelText.text = "Ready to advance to Level ${currentLevel + 1}!"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up level progress: ${e.message}", e)
                runOnUiThread {
                    // Fallback to default values
                    binding.pointsText.text = "680 pts"
                    binding.totalPoints.text = "2450 pts"
                    binding.levelProgressBar.progress = 68
                    binding.nextLevelText.text = "Keep tracking expenses to level up!"
                }
            }
        }
    }

    /**
     * Sets up the achievement badges based on user activity from Firebase
     */
    private fun setupAchievementBadges() {
        if (userId == -1) {
            // Guest user - show all locked badges
            val badges = listOf(
                "Locked" to false,
                "Locked" to false,
                "Locked" to false,
                "Locked" to false
            )
            displayBadges(badges)
            return
        }

        lifecycleScope.launch {
            try {
                // Get user data from Firebase to determine achievements
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)
                val budgetGoal = firebaseDataManager.loadActiveBudgetGoal(userId)

                // Calculate achievements based on real data
                val badges = calculateAchievements(transactions, budgetGoal)

                runOnUiThread {
                    displayBadges(badges)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading achievements: ${e.message}", e)
                runOnUiThread {
                    // Show default badges on error
                    val defaultBadges = listOf(
                        "Saver" to true,
                        "Consistent" to false,
                        "Transport" to false,
                        "Locked" to false
                    )
                    displayBadges(defaultBadges)
                }
            }
        }
    }

    /**
     * Calculate achievements based on user's Firebase data
     */
    private fun calculateAchievements(
        transactions: List<com.example.tightbudget.models.Transaction>,
        budgetGoal: com.example.tightbudget.models.BudgetGoal?
    ): List<Pair<String, Boolean>> {

        val achievements = mutableListOf<Pair<String, Boolean>>()

        // Saver badge - has a budget goal set
        val hasSaver = budgetGoal != null
        achievements.add("Saver" to hasSaver)

        // Consistent badge - has transactions in last 7 days
        val recentTransactions = transactions.filter { transaction ->
            val daysDiff = (System.currentTimeMillis() - transaction.date.time) / (24 * 60 * 60 * 1000)
            daysDiff <= 7
        }
        val hasConsistent = recentTransactions.size >= 3
        achievements.add("Consistent" to hasConsistent)

        // Transport badge - has transport transactions
        val hasTransportTransactions = transactions.any {
            it.category.equals("Transport", ignoreCase = true)
        }
        achievements.add("Transport" to hasTransportTransactions)

        // Budget Master badge - spending within budget for current month
        val hasBudgetMaster = if (budgetGoal != null) {
            val currentMonthSpending = getCurrentMonthSpending(transactions)
            currentMonthSpending <= budgetGoal.totalBudget
        } else {
            false
        }
        achievements.add("Budget Master" to hasBudgetMaster)

        return achievements
    }

    /**
     * Calculate current month spending from transactions
     */
    private fun getCurrentMonthSpending(transactions: List<com.example.tightbudget.models.Transaction>): Double {
        val calendar = java.util.Calendar.getInstance()
        val currentMonth = calendar.get(java.util.Calendar.MONTH)
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        return transactions.filter { transaction ->
            calendar.time = transaction.date
            transaction.isExpense &&
                    calendar.get(java.util.Calendar.MONTH) == currentMonth &&
                    calendar.get(java.util.Calendar.YEAR) == currentYear
        }.sumOf { it.amount }
    }

    /**
     * Display badges in the UI
     */
    private fun displayBadges(badges: List<Pair<String, Boolean>>) {
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
                    badgeView.alpha = 1f
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
        if (userId == -1) {
            // Guest user - show locked challenges
            binding.challenge2Checkbox.isChecked = false
            return
        }

        lifecycleScope.launch {
            try {
                // Get user transactions to determine challenge completion
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)

                // Check if user has added a transaction today
                val today = java.util.Calendar.getInstance()
                val todayTransactions = transactions.filter { transaction ->
                    val transactionCal = java.util.Calendar.getInstance()
                    transactionCal.time = transaction.date

                    transactionCal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                            transactionCal.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
                }

                runOnUiThread {
                    // Mark challenge as completed if user has today's transactions
                    binding.challenge2Checkbox.isChecked = todayTransactions.isNotEmpty()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up challenges: ${e.message}", e)
                runOnUiThread {
                    // Default to incomplete challenge on error
                    binding.challenge2Checkbox.isChecked = false
                }
            }
        }
    }

    /**
     * Displays a points chart using ChartUtils
     * For logged-in users, this uses transaction-based data from Firebase
     */
    private fun setupPointsChart() {
        val container: FrameLayout = binding.pointsChartContainer

        if (userId == -1) {
            // Guest user - show sample data
            val samplePoints = mapOf(
                "Mon" to 0f,
                "Tue" to 0f,
                "Wed" to 0f,
                "Thu" to 0f,
                "Fri" to 0f,
                "Sat" to 0f,
                "Sun" to 0f
            )

            container.removeAllViews()
            val lineChart = ChartUtils.EnhancedLineChartView(this, samplePoints, false)
            container.addView(
                lineChart,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            return
        }

        lifecycleScope.launch {
            try {
                // Get recent transactions from Firebase to create points chart
                val recentTransactions = firebaseDataManager.loadRecentTransactions(userId, 20)

                // Create points data based on transaction activity over last 7 days
                val pointsData = createPointsDataFromTransactions(recentTransactions)

                runOnUiThread {
                    // Create and add the chart to the container
                    container.removeAllViews()
                    val lineChart = ChartUtils.EnhancedLineChartView(this@ProfileActivity, pointsData, false)
                    container.addView(
                        lineChart,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating points chart: ${e.message}", e)
                runOnUiThread {
                    // Fallback to sample data
                    val samplePoints = mapOf(
                        "Mon" to 25f,
                        "Tue" to 50f,
                        "Wed" to 30f,
                        "Thu" to 75f,
                        "Fri" to 15f,
                        "Sat" to 60f,
                        "Sun" to 40f
                    )

                    container.removeAllViews()
                    val lineChart = ChartUtils.EnhancedLineChartView(this@ProfileActivity, samplePoints, false)
                    container.addView(
                        lineChart,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    )
                }
            }
        }

        // Set up month selector
        binding.viewByMonth.setOnClickListener {
            Toast.makeText(this, "Monthly view coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Create points data based on user's recent transactions
     */
    private fun createPointsDataFromTransactions(
        transactions: List<com.example.tightbudget.models.Transaction>
    ): Map<String, Float> {

        val pointsData = mutableMapOf<String, Float>()
        val calendar = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())

        // Initialize last 7 days with 0 points
        for (i in 6 downTo 0) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dayName = dateFormat.format(calendar.time)
            pointsData[dayName] = 0f
            calendar.add(java.util.Calendar.DAY_OF_YEAR, i) // Reset
        }

        // Add points based on transaction activity
        transactions.forEach { transaction ->
            val transactionCal = java.util.Calendar.getInstance()
            transactionCal.time = transaction.date
            val dayName = dateFormat.format(transaction.date)

            // Check if transaction is within last 7 days
            val daysDiff = (System.currentTimeMillis() - transaction.date.time) / (24 * 60 * 60 * 1000)
            if (daysDiff <= 7 && pointsData.containsKey(dayName)) {
                // Award points for transaction activity (25 points per transaction)
                pointsData[dayName] = pointsData[dayName]!! + 25f
            }
        }

        return pointsData
    }
}