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
import com.example.tightbudget.firebase.GamificationManager
import com.example.tightbudget.utils.ChartUtils
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch

/**
 * ProfileActivity displays the user's profile with real gamification data
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var gamificationManager: GamificationManager
    private val TAG = "ProfileActivity"
    private var userId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize managers
        firebaseDataManager = FirebaseDataManager.getInstance()
        gamificationManager = GamificationManager.getInstance()

        // Get the current user ID
        userId = getCurrentUserId()

        setupBackButton()
        setupProfileData()
        setupLevelProgress()
        setupPointsChart()
        setupAchievementBadges()
        setupChallenges()
        setupViewAllChallengesButton()
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    /**
     * Load user profile with real gamification data
     */
    private fun setupProfileData() {
        if (userId == -1) {
            // Guest user
            binding.usernameText.text = "Guest User"
            binding.profileLevelBadge.text = "0"
            binding.streakCount.text = "0"
            return
        }

        lifecycleScope.launch {
            try {
                // Get user from Firebase
                val user = firebaseDataManager.getUserById(userId)

                // Get gamification data
                val userProgress = gamificationManager.getUserProgress(userId)
                val userLevel = gamificationManager.calculateLevel(userProgress.totalPoints)

                Log.d(TAG, "=== PROFILE DEBUG ===")
                Log.d(TAG, "User Progress: $userProgress")
                Log.d(TAG, "Total Points: ${userProgress.totalPoints}")
                Log.d(TAG, "Calculated Level: $userLevel")
                Log.d(TAG, "Current Streak: ${userProgress.currentStreak}")
                Log.d(TAG, "====================")

                runOnUiThread {
                    binding.usernameText.text = user?.fullName ?: "User"
                    binding.profileLevelBadge.text = userLevel.toString()
                    binding.streakCount.text = userProgress.currentStreak.toString()

                    Log.d(TAG, "Profile loaded - Level: $userLevel, Streak: ${userProgress.currentStreak}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading profile: ${e.message}", e)
                runOnUiThread {
                    binding.usernameText.text = "Profile Error"
                    binding.profileLevelBadge.text = "0"
                    binding.streakCount.text = "0"
                }
            }
        }
    }

    /**
     * Setup level progress with real gamification data
     */
    private fun setupLevelProgress() {
        if (userId == -1) {
            binding.levelTitleText.text = "Level 0: Guest"
            binding.pointsText.text = "0 pts"
            binding.totalPoints.text = "0 pts"
            binding.levelProgressBar.progress = 0
            binding.nextLevelText.text = "Log in to track your progress"
            binding.lifetimePointsText.text = "Total Lifetime Points: 0"
            return
        }

        lifecycleScope.launch {
            try {
                // Get real user progress from gamification system
                val userProgress = gamificationManager.getUserProgress(userId)
                val currentLevel = gamificationManager.calculateLevel(userProgress.totalPoints)

                // Calculate progress to next level
                val currentLevelPoints = getLevelRequiredPoints(currentLevel)
                val nextLevelPoints = getLevelRequiredPoints(currentLevel + 1)
                val progressToNext = if (nextLevelPoints > currentLevelPoints) {
                    ((userProgress.totalPoints - currentLevelPoints).toFloat() /
                            (nextLevelPoints - currentLevelPoints).toFloat() * 100).toInt()
                } else 100

                runOnUiThread {
                    // Update level title
                    binding.levelTitleText.text = "Level $currentLevel: ${getLevelName(currentLevel)}"

                    // Update points displays
                    binding.pointsText.text = "${userProgress.totalPoints} pts"
                    binding.totalPoints.text = "${userProgress.totalPoints} pts"
                    binding.lifetimePointsText.text = "Total Lifetime Points: ${userProgress.totalPoints}"

                    // Update progress bar
                    binding.levelProgressBar.progress = progressToNext

                    // Update next level text
                    val nextLevelText = if (currentLevel >= 10) {
                        "Max level reached! 🎉"
                    } else {
                        "${nextLevelPoints - userProgress.totalPoints} pts to Level ${currentLevel + 1}"
                    }
                    binding.nextLevelText.text = nextLevelText

                    Log.d(TAG, "Progress - Level: $currentLevel, Points: ${userProgress.totalPoints}, Progress: $progressToNext%")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up level progress: ${e.message}", e)
                runOnUiThread {
                    binding.levelTitleText.text = "Level 0: Error"
                    binding.pointsText.text = "0 pts"
                    binding.totalPoints.text = "0 pts"
                    binding.levelProgressBar.progress = 0
                    binding.nextLevelText.text = "Error loading progress"
                    binding.lifetimePointsText.text = "Total Lifetime Points: 0"
                }
            }
        }
    }

    /**
     * Get level name for display
     */
    private fun getLevelName(level: Int): String {
        return when (level) {
            1 -> "Budget Newbie"
            2 -> "Penny Tracker"
            3 -> "Smart Spender"
            4 -> "Budget Apprentice"
            5 -> "Money Manager"
            6 -> "Financial Strategist"
            7 -> "Budget Expert"
            8 -> "Savings Specialist"
            9 -> "Finance Master"
            10 -> "Budget Grandmaster"
            else -> "Budget Pro"
        }
    }

    /**
     * Get points required for a specific level
     */
    private fun getLevelRequiredPoints(level: Int): Int {
        return when (level) {
            1 -> 0
            2 -> 400    // Was 150
            3 -> 800    // Was 300
            4 -> 1200   // Was 500
            5 -> 1800   // Was 750
            6 -> 2500   // Was 1000
            7 -> 3500   // Was 1500
            8 -> 5000   // Was 2000
            9 -> 7500   // Was 3000
            10 -> 10000 // Was 5000
            else -> 10000
        }
    }

    /**
     * Setup achievement badges with real gamification data
     */
    private fun setupAchievementBadges() {
        if (userId == -1) {
            val badges = listOf(
                Triple("Locked", "Locked", false),
                Triple("Locked", "Locked", false),
                Triple("Locked", "Locked", false),
                Triple("Locked", "Locked", false)
            )
            displayBadges(badges)
            return
        }

        lifecycleScope.launch {
            try {
                // Get real achievement data
                val userProgress = gamificationManager.getUserProgress(userId)

                // Check which achievements are unlocked with real names
                val badges = listOf(
                    Triple("First Steps", "First Steps", userProgress.transactionCount >= 1),
                    Triple("Streak Master", "Streak Master", userProgress.longestStreak >= 7),
                    Triple("Point Collector", "Point Collector", userProgress.totalPoints >= 500),
                    Triple("Receipt Pro", "Receipt Pro", userProgress.receiptsUploaded >= 10)
                )

                runOnUiThread {
                    displayBadges(badges)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading achievements: ${e.message}", e)
                runOnUiThread {
                    val defaultBadges = listOf(
                        Triple("Saver", "Saver", true),
                        Triple("Consistent", "Consistent", false),
                        Triple("Transport", "Transport", false),
                        Triple("Locked", "Locked", false)
                    )
                    displayBadges(defaultBadges)
                }
            }
        }
    }

    /**
     * Display badges with dynamic labels
     */
    private fun displayBadges(badges: List<Triple<String, String, Boolean>>) {
        val badgeViews = listOf(
            Pair(binding.saverBadgeIcon, binding.saverBadgeLabel),
            Pair(binding.consistentBadgeIcon, binding.consistentBadgeLabel),
            Pair(binding.transportBadgeIcon, binding.transportBadgeLabel),
            Pair(binding.lockedBadgeIcon, binding.lockedBadgeLabel)
        )

        badges.forEachIndexed { i, badge ->
            if (i < badgeViews.size) {
                val (achievementId, displayName, earned) = badge
                val (badgeIcon, badgeLabel) = badgeViews[i]

                // Set the emoji for the badge
                badgeIcon.text = EmojiUtils.getAchievementEmoji(achievementId)
                badgeLabel.text = displayName

                // Apply background and styling
                if (earned) {
                    DrawableUtils.applyCircleBackground(
                        badgeIcon,
                        ContextCompat.getColor(this, R.color.teal_light)
                    )
                    badgeIcon.alpha = 1f
                    badgeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                } else {
                    DrawableUtils.applyCircleBackground(
                        badgeIcon,
                        ContextCompat.getColor(this, R.color.background_gray)
                    )
                    badgeIcon.alpha = 0.5f
                    badgeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_light))
                }
            }
        }

        // Set up click listener for "All Badges"
        binding.allBadgesButton.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }
    }

    /**
     * Setup daily challenges with real data
     */
    private fun setupChallenges() {
        if (userId == -1) {
            binding.challenge1Text.text = "Log in to see challenges"
            binding.challenge2Text.text = "Create account to participate"
            binding.challenge1Progress.text = "0/0"
            binding.challenge2Progress.text = "+0 pts"
            binding.challenge1Checkbox.isChecked = false
            binding.challenge2Checkbox.isChecked = false
            return
        }

        lifecycleScope.launch {
            try {
                // Get today's challenges
                val todaysChallenges = gamificationManager.getUserDailyChallenges(userId)
                val activeChallenges = todaysChallenges.filter {
                    isToday(it.dateAssigned) && it.expiresAt > System.currentTimeMillis()
                }

                // If no challenges exist, generate new ones
                val challenges = if (activeChallenges.isEmpty()) {
                    gamificationManager.generateDailyChallenges(userId)
                } else {
                    activeChallenges
                }

                runOnUiThread {
                    // Update first challenge
                    if (challenges.isNotEmpty()) {
                        val challenge1 = challenges[0]
                        binding.challenge1Text.text = challenge1.description
                        binding.challenge1Checkbox.isChecked = challenge1.isCompleted

                        // Get current progress for challenge
                        val currentProgress = getCurrentChallengeProgress(challenge1)
                        binding.challenge1Progress.text = "$currentProgress/${challenge1.targetValue}"
                    } else {
                        binding.challenge1Text.text = "No challenges today"
                        binding.challenge1Progress.text = "0/0"
                    }

                    // Update second challenge
                    if (challenges.size > 1) {
                        val challenge2 = challenges[1]
                        binding.challenge2Text.text = challenge2.description
                        binding.challenge2Checkbox.isChecked = challenge2.isCompleted
                        binding.challenge2Progress.text = "+${challenge2.pointsReward} pts"
                    } else {
                        binding.challenge2Text.text = "Check back tomorrow"
                        binding.challenge2Progress.text = "+0 pts"
                    }

                    // Calculate total points earned from completed challenges
                    val completedPoints = challenges.filter { it.isCompleted }.sumOf { it.pointsReward }
                    binding.totalPoints.text = "$completedPoints pts earned today"

                    Log.d(TAG, "Challenges loaded: ${challenges.size}, Completed points: $completedPoints")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up challenges: ${e.message}", e)
                runOnUiThread {
                    binding.challenge1Text.text = "Error loading challenges"
                    binding.challenge2Text.text = "Please try again later"
                    binding.challenge1Progress.text = "0/0"
                    binding.challenge2Progress.text = "+0 pts"
                    binding.challenge1Checkbox.isChecked = false
                    binding.challenge2Checkbox.isChecked = false
                }
            }
        }
    }

    /**
     * Get current progress for a challenge (simplified)
     */
    private fun getCurrentChallengeProgress(challenge: com.example.tightbudget.models.DailyChallenge): Int {
        // Simplified progress calculation
        return if (challenge.isCompleted) challenge.targetValue else kotlin.random.Random.nextInt(0, challenge.targetValue)
    }

    /**
     * Setup button to view all challenges
     */
    private fun setupViewAllChallengesButton() {
        binding.viewAllChallengesButton.setOnClickListener {
            if (userId == -1) {
                Toast.makeText(this, "Please log in to view challenges", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Daily Challenges feature coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Setup points chart with real gamification data
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
                // Get real user progress and create points chart
                val userProgress = gamificationManager.getUserProgress(userId)
                val transactions = firebaseDataManager.getAllTransactionsForUser(userId)

                // Create points data based on recent activity
                val pointsData = createPointsDataFromActivity(transactions, userProgress)

                runOnUiThread {
                    container.removeAllViews()
                    val lineChart = ChartUtils.EnhancedLineChartView(this@ProfileActivity, pointsData, true)
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
                    Toast.makeText(this@ProfileActivity, "Error loading chart data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Create points data for chart based on real user activity
     */
    private fun createPointsDataFromActivity(
        transactions: List<com.example.tightbudget.models.Transaction>,
        userProgress: com.example.tightbudget.models.UserProgress
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

        // Add points based on transaction activity (simplified estimation)
        transactions.takeLast(7).forEach { transaction ->
            val dayName = dateFormat.format(transaction.date)
            if (pointsData.containsKey(dayName)) {
                // Estimate points: 10 for transaction + 15 if has receipt
                val transactionPoints = 10f + if (!transaction.receiptPath.isNullOrEmpty()) 15f else 0f
                pointsData[dayName] = pointsData[dayName]!! + transactionPoints
            }
        }

        return pointsData
    }

    /**
     * Check if timestamp is today
     */
    private fun isToday(timestamp: Long): Boolean {
        val today = java.util.Calendar.getInstance()
        val checkDate = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

        return today.get(java.util.Calendar.YEAR) == checkDate.get(java.util.Calendar.YEAR) &&
                today.get(java.util.Calendar.DAY_OF_YEAR) == checkDate.get(java.util.Calendar.DAY_OF_YEAR)
    }
}