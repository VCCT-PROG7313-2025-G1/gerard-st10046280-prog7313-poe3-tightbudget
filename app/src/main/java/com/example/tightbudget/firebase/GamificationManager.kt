package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.example.tightbudget.models.*
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError

/**
 * Manages gamification features including points, achievements, and daily challenges
 */
class GamificationManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val userProgressRef: DatabaseReference = database.getReference("userProgress")
    private val achievementsRef: DatabaseReference = database.getReference("achievements")
    private val dailyChallengesRef: DatabaseReference = database.getReference("dailyChallenges")

    companion object {
        private const val TAG = "GamificationManager"

        @Volatile
        private var INSTANCE: GamificationManager? = null

        fun getInstance(): GamificationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = GamificationManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Awards points to user and updates their progress
     */
    suspend fun awardPoints(userId: Int, points: Int, reason: String): Boolean {
        return try {
            Log.d(TAG, "Awarding $points points to user $userId for: $reason")

            val userProgress = getUserProgress(userId)
            val newPoints = userProgress.totalPoints + points
            val newLevel = calculateLevel(newPoints)

            val updatedProgress = userProgress.copy(
                totalPoints = newPoints,
                currentLevel = newLevel
            )

            saveUserProgress(userId, updatedProgress)
            Log.d(TAG, "Points awarded successfully. New total: $newPoints")

            // Only check for achievements if this isn't already an achievement reward
            if (!reason.contains("Unlocked achievement")) {
                Log.d(TAG, "Checking for new achievements after point award...")
                // Get the LATEST user progress after all updates
                val latestProgress = getUserProgress(userId)
                checkAndUnlockAchievements(userId, latestProgress)
            } else {
                Log.d(TAG, "Skipping achievement check - this was an achievement reward")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error awarding points: ${e.message}", e)
            false
        }
    }

    /**
     * Handles transaction addition and awards appropriate points
     */
    suspend fun onTransactionAdded(userId: Int, transaction: Transaction): Int {
        var totalPointsEarned = 0

        try {
            Log.d(TAG, "=== TRANSACTION GAMIFICATION START ===")
            Log.d(TAG, "Processing transaction for user $userId")

            // Get FRESH user progress
            val userProgress = getUserProgress(userId)
            Log.d(TAG, "Current user progress: $userProgress")

            // Update transaction count FIRST (before awarding points)
            val hasReceipt = !transaction.receiptPath.isNullOrEmpty()
            val updatedProgress = userProgress.copy(
                transactionCount = userProgress.transactionCount + 1,
                receiptsUploaded = if (hasReceipt) userProgress.receiptsUploaded + 1 else userProgress.receiptsUploaded
            )

            // Save updated progress immediately
            saveUserProgress(userId, updatedProgress)
            Log.d(TAG, "Updated progress saved: transactionCount=${updatedProgress.transactionCount}, receiptsUploaded=${updatedProgress.receiptsUploaded}")

            // Base points for adding transaction
            val basePoints = PointsSystem.ADD_TRANSACTION
            awardPoints(userId, basePoints, "Added transaction")
            totalPointsEarned += basePoints

            // Bonus points for receipt
            if (hasReceipt) {
                val receiptPoints = PointsSystem.ADD_RECEIPT
                awardPoints(userId, receiptPoints, "Added receipt")
                totalPointsEarned += receiptPoints
            }

            // Check if it's first transaction of the day
            if (isFirstTransactionToday(userId)) {
                val bonusPoints = PointsSystem.FIRST_TRANSACTION_OF_DAY
                awardPoints(userId, bonusPoints, "First transaction today")
                totalPointsEarned += bonusPoints
            }

            // Update daily challenges progress
            updateChallengeProgress(userId, ChallengeType.TRANSACTION)

            // Update streak
            updateStreak(userId)

            Log.d(TAG, "=== TRANSACTION GAMIFICATION END ===")
            Log.d(TAG, "Total points earned: $totalPointsEarned")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error processing transaction gamification: ${e.message}", e)
        }

        return totalPointsEarned
    }

    /**
     * Generate realistic daily challenges
     */
    suspend fun generateDailyChallenges(userId: Int): List<DailyChallenge> {
        val challenges = mutableListOf<DailyChallenge>()
        val currentTime = System.currentTimeMillis()
        val endOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        // Always generate these 3 daily challenges
        challenges.add(
            DailyChallenge(
                id = "daily_transaction_${currentTime}",
                title = "Transaction Logger",
                description = "Add 3 transactions today",
                pointsReward = 75,
                type = ChallengeType.TRANSACTION,
                targetValue = 3,
                dateAssigned = currentTime,
                expiresAt = endOfDay
            )
        )

        challenges.add(
            DailyChallenge(
                id = "daily_receipt_${currentTime}",
                title = "Receipt Collector",
                description = "Upload 2 receipts today",
                pointsReward = 60,
                type = ChallengeType.RECEIPT,
                targetValue = 2,
                dateAssigned = currentTime,
                expiresAt = endOfDay
            )
        )

        challenges.add(
            DailyChallenge(
                id = "daily_budget_${currentTime}",
                title = "Smart Spender",
                description = "Keep expenses under 200 today",
                pointsReward = 100,
                type = ChallengeType.BUDGET_COMPLIANCE,
                targetValue = 1,
                dateAssigned = currentTime,
                expiresAt = endOfDay
            )
        )

        // Save challenges to Firebase
        challenges.forEach { challenge ->
            saveDailyChallenge(userId, challenge)
        }

        return challenges
    }

    /**
     * Check and update challenge progress
     */
    suspend fun updateChallengeProgress(userId: Int, challengeType: ChallengeType) {
        try {
            val challenges = getUserDailyChallenges(userId)
            val relevantChallenges = challenges.filter {
                it.type == challengeType && !it.isCompleted && it.expiresAt > System.currentTimeMillis()
            }

            relevantChallenges.forEach { challenge ->
                val currentProgress = getCurrentChallengeProgress(userId, challenge)

                if (currentProgress >= challenge.targetValue) {
                    // Complete the challenge
                    val completedChallenge = challenge.copy(isCompleted = true)
                    saveDailyChallenge(userId, completedChallenge)

                    // Award points
                    awardPoints(userId, challenge.pointsReward, "Completed daily challenge: ${challenge.title}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating challenge progress: ${e.message}", e)
        }
    }

    /**
     * Get current progress for a specific challenge
     */
    private suspend fun getCurrentChallengeProgress(userId: Int, challenge: DailyChallenge): Int {
        return when (challenge.type) {
            ChallengeType.TRANSACTION -> getTodayTransactionCount(userId)
            ChallengeType.RECEIPT -> getTodayReceiptCount(userId)
            ChallengeType.BUDGET_COMPLIANCE -> getTodayBudgetComplianceCount(userId)
            ChallengeType.SAVINGS -> getTodaySavingsPercentage(userId)
            ChallengeType.STREAK -> getUserProgress(userId).currentStreak
            ChallengeType.CATEGORY_LIMIT -> getTodayCategoryComplianceCount(userId)
        }
    }

    /**
     * Calculate user level based on total points
     */
    fun calculateLevel(totalPoints: Int): Int {
        return when {
            totalPoints >= 10000 -> 10  // Grandmaster (was 5000)
            totalPoints >= 7500 -> 9    // Finance Master (was 3000)
            totalPoints >= 5000 -> 8    // Savings Specialist (was 2000)
            totalPoints >= 3500 -> 7    // Budget Expert (was 1500)
            totalPoints >= 2500 -> 6    // Financial Strategist (was 1000)
            totalPoints >= 1800 -> 5    // Money Manager (was 750)
            totalPoints >= 1200 -> 4    // Budget Apprentice (was 500)
            totalPoints >= 800 -> 3     // Smart Spender (was 300)
            totalPoints >= 400 -> 2     // Penny Tracker (was 150)
            else -> 1                   // Budget Newbie (0-399)
        }
    }

    /**
     * Check and unlock achievements
     */
    private suspend fun checkAndUnlockAchievements(userId: Int, userProgress: UserProgress) {
        try {
            val allAchievements = getAllAchievements()
            val unlockedAchievementIds = userProgress.achievementsUnlocked

            Log.d(TAG, "=== ACHIEVEMENT CHECK START ===")
            Log.d(TAG, "User $userId current stats:")
            Log.d(TAG, "- Total Points: ${userProgress.totalPoints}")
            Log.d(TAG, "- Transaction Count: ${userProgress.transactionCount}")
            Log.d(TAG, "- Receipts Uploaded: ${userProgress.receiptsUploaded}")
            Log.d(TAG, "- Longest Streak: ${userProgress.longestStreak}")
            Log.d(TAG, "- Already unlocked: $unlockedAchievementIds")

            allAchievements.forEach { achievement ->
                Log.d(TAG, "Checking achievement: ${achievement.id} (${achievement.title})")
                Log.d(TAG, "- Type: ${achievement.type}, Target: ${achievement.targetValue}")
                Log.d(TAG, "- Already unlocked: ${unlockedAchievementIds.contains(achievement.id)}")
                Log.d(TAG, "- Meets criteria: ${meetsAchievementCriteria(userProgress, achievement)}")

                // Only check achievements that haven't been unlocked yet
                if (!unlockedAchievementIds.contains(achievement.id) &&
                    meetsAchievementCriteria(userProgress, achievement)) {

                    Log.d(TAG, "🏆 UNLOCKING achievement: ${achievement.title} for user $userId")

                    // Mark achievement as unlocked in user progress
                    val updatedAchievementsList = unlockedAchievementIds.toMutableList()
                    updatedAchievementsList.add(achievement.id)

                    val updatedUserProgress = userProgress.copy(
                        achievementsUnlocked = updatedAchievementsList
                    )

                    // Save updated user progress first
                    saveUserProgress(userId, updatedUserProgress)

                    // Award bonus points for achievement (but don't recursively check achievements)
                    awardPointsDirectly(userId, achievement.pointsRequired, "Unlocked achievement: ${achievement.title}")

                    Log.d(TAG, "✅ Achievement ${achievement.title} unlocked successfully for user $userId")
                } else {
                    Log.d(TAG, "⏭️ Skipping achievement ${achievement.title} - already unlocked or criteria not met")
                }
            }
            Log.d(TAG, "=== ACHIEVEMENT CHECK END ===")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking achievements: ${e.message}", e)
        }
    }

    /**
     * Award points directly without triggering achievement checks (prevents infinite loop)
     */
    private suspend fun awardPointsDirectly(userId: Int, points: Int, reason: String): Boolean {
        return try {
            val userProgress = getUserProgress(userId)
            val newPoints = userProgress.totalPoints + points
            val newLevel = calculateLevel(newPoints)

            val updatedProgress = userProgress.copy(
                totalPoints = newPoints,
                currentLevel = newLevel
            )

            saveUserProgress(userId, updatedProgress)
            Log.d(TAG, "Awarded $points points to user $userId for: $reason")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error awarding points directly: ${e.message}", e)
            false
        }
    }

    /**
     * Check if user meets achievement criteria
     */
    private fun meetsAchievementCriteria(userProgress: UserProgress, achievement: Achievement): Boolean {
        val meets = when (achievement.type) {
            AchievementType.POINTS -> {
                val meets = userProgress.totalPoints >= achievement.targetValue
                Log.d(TAG, "Points check: ${userProgress.totalPoints} >= ${achievement.targetValue} = $meets")
                meets
            }
            AchievementType.STREAK -> {
                val meets = userProgress.longestStreak >= achievement.targetValue
                Log.d(TAG, "Streak check: ${userProgress.longestStreak} >= ${achievement.targetValue} = $meets")
                meets
            }
            AchievementType.TRANSACTIONS -> {
                val meets = userProgress.transactionCount >= achievement.targetValue
                Log.d(TAG, "Transaction check: ${userProgress.transactionCount} >= ${achievement.targetValue} = $meets")
                meets
            }
            AchievementType.BUDGET_GOALS -> {
                val meets = userProgress.budgetGoalsMet >= achievement.targetValue
                Log.d(TAG, "Budget goals check: ${userProgress.budgetGoalsMet} >= ${achievement.targetValue} = $meets")
                meets
            }
            AchievementType.RECEIPTS -> {
                val meets = userProgress.receiptsUploaded >= achievement.targetValue
                Log.d(TAG, "Receipts check: ${userProgress.receiptsUploaded} >= ${achievement.targetValue} = $meets")
                meets
            }
            else -> {
                Log.d(TAG, "Unknown achievement type: ${achievement.type}")
                false
            }
        }
        return meets
    }

    // Helper methods for Firebase operations
    suspend fun getUserProgress(userId: Int): UserProgress = suspendCoroutine { continuation ->
        userProgressRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val progress = snapshot.getValue(UserProgress::class.java) ?: UserProgress(userId = userId)
                continuation.resume(progress)
            }
            override fun onCancelled(error: DatabaseError) {
                continuation.resume(UserProgress(userId = userId))
            }
        })
    }

    private suspend fun saveUserProgress(userId: Int, progress: UserProgress) {
        userProgressRef.child(userId.toString()).setValue(progress).await()
    }

    private suspend fun saveDailyChallenge(userId: Int, challenge: DailyChallenge) {
        dailyChallengesRef.child(userId.toString()).child(challenge.id).setValue(challenge).await()
    }

    suspend fun getUserDailyChallenges(userId: Int): List<DailyChallenge> = suspendCoroutine { continuation ->
        dailyChallengesRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val challenges = mutableListOf<DailyChallenge>()
                snapshot.children.forEach {
                    it.getValue(DailyChallenge::class.java)?.let { challenge -> challenges.add(challenge) }
                }
                continuation.resume(challenges)
            }
            override fun onCancelled(error: DatabaseError) {
                continuation.resume(emptyList())
            }
        })
    }

    private suspend fun saveAchievement(userId: Int, achievement: Achievement) {
        achievementsRef.child(userId.toString()).child(achievement.id).setValue(achievement).await()
    }

    fun getAllAchievements(): List<Achievement> {
        // Return predefined achievements with consistent IDs
        return listOf(
            // === BEGINNER ACHIEVEMENTS ===
            Achievement(
                id = "first_transaction",
                title = "First Steps",
                description = "Add your first transaction",
                emoji = "🎯",
                pointsRequired = 50,
                type = AchievementType.TRANSACTIONS,
                targetValue = 1
            ),
            Achievement(
                id = "transactions_5",
                title = "Getting Started",
                description = "Add 5 transactions",
                emoji = "📝",
                pointsRequired = 75,
                type = AchievementType.TRANSACTIONS,
                targetValue = 5
            ),
            Achievement(
                id = "first_receipt",
                title = "Receipt Rookie",
                description = "Upload your first receipt",
                emoji = "📄",
                pointsRequired = 40,
                type = AchievementType.RECEIPTS,
                targetValue = 1
            ),

            // === INTERMEDIATE ACHIEVEMENTS ===
            Achievement(
                id = "transactions_25",
                title = "Transaction Pro",
                description = "Add 25 transactions",
                emoji = "💼",
                pointsRequired = 150,
                type = AchievementType.TRANSACTIONS,
                targetValue = 25
            ),
            Achievement(
                id = "receipts_10",
                title = "Receipt Collector",
                description = "Upload 10 receipts",
                emoji = "📋",
                pointsRequired = 200,
                type = AchievementType.RECEIPTS,
                targetValue = 10
            ),
            Achievement(
                id = "streak_3",
                title = "Three Day Hero",
                description = "Log transactions for 3 days straight",
                emoji = "🔥",
                pointsRequired = 100,
                type = AchievementType.STREAK,
                targetValue = 3
            ),
            Achievement(
                id = "streak_7",
                title = "Week Warrior",
                description = "Log transactions for 7 days straight",
                emoji = "⚡",
                pointsRequired = 200,
                type = AchievementType.STREAK,
                targetValue = 7
            ),
            Achievement(
                id = "points_500",
                title = "Point Collector",
                description = "Earn 500 total points",
                emoji = "⭐",
                pointsRequired = 100,
                type = AchievementType.POINTS,
                targetValue = 500
            ),

            // === ADVANCED ACHIEVEMENTS ===
            Achievement(
                id = "transactions_50",
                title = "Budget Master",
                description = "Add 50 transactions",
                emoji = "👑",
                pointsRequired = 300,
                type = AchievementType.TRANSACTIONS,
                targetValue = 50
            ),
            Achievement(
                id = "receipts_25",
                title = "Receipt Master",
                description = "Upload 25 receipts",
                emoji = "🗂️",
                pointsRequired = 400,
                type = AchievementType.RECEIPTS,
                targetValue = 25
            ),
            Achievement(
                id = "streak_14",
                title = "Two Week Champion",
                description = "Log transactions for 14 days straight",
                emoji = "🏆",
                pointsRequired = 500,
                type = AchievementType.STREAK,
                targetValue = 14
            ),
            Achievement(
                id = "points_1500",
                title = "Point Millionaire",
                description = "Earn 1500 total points",
                emoji = "💎",
                pointsRequired = 300,
                type = AchievementType.POINTS,
                targetValue = 1500
            ),
            Achievement(
                id = "categories_10",
                title = "Category Explorer",
                description = "Use 10 different categories",
                emoji = "🗺️",
                pointsRequired = 250,
                type = AchievementType.CATEGORIES,
                targetValue = 10
            ),

            // === EXPERT ACHIEVEMENTS ===
            Achievement(
                id = "transactions_100",
                title = "Transaction Legend",
                description = "Add 100 transactions",
                emoji = "🌟",
                pointsRequired = 600,
                type = AchievementType.TRANSACTIONS,
                targetValue = 100
            ),
            Achievement(
                id = "receipts_50",
                title = "Receipt Royalty",
                description = "Upload 50 receipts",
                emoji = "👑",
                pointsRequired = 800,
                type = AchievementType.RECEIPTS,
                targetValue = 50
            ),
            Achievement(
                id = "streak_30",
                title = "Month Master",
                description = "Log transactions for 30 days straight",
                emoji = "🚀",
                pointsRequired = 1000,
                type = AchievementType.STREAK,
                targetValue = 30
            ),
            Achievement(
                id = "points_5000",
                title = "Point Grandmaster",
                description = "Earn 5000 total points",
                emoji = "💫",
                pointsRequired = 1000,
                type = AchievementType.POINTS,
                targetValue = 5000
            ),

            // === LEGENDARY ACHIEVEMENTS ===
            Achievement(
                id = "transactions_250",
                title = "Budget Grandmaster",
                description = "Add 250 transactions",
                emoji = "🏅",
                pointsRequired = 1500,
                type = AchievementType.TRANSACTIONS,
                targetValue = 250
            ),
            Achievement(
                id = "receipts_100",
                title = "Receipt Emperor",
                description = "Upload 100 receipts",
                emoji = "🎖️",
                pointsRequired = 2000,
                type = AchievementType.RECEIPTS,
                targetValue = 100
            ),
            Achievement(
                id = "streak_60",
                title = "Two Month Legend",
                description = "Log transactions for 60 days straight",
                emoji = "🔮",
                pointsRequired = 2500,
                type = AchievementType.STREAK,
                targetValue = 60
            ),
            Achievement(
                id = "points_10000",
                title = "Financial Deity",
                description = "Earn 10000 total points",
                emoji = "✨",
                pointsRequired = 2000,
                type = AchievementType.POINTS,
                targetValue = 10000
            )
        )
    }

    /**
     * Check if this is the user's first transaction today
     */
    private suspend fun isFirstTransactionToday(userId: Int): Boolean {
        return try {
            val today = Calendar.getInstance()
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val transactionManager = FirebaseTransactionManager.getInstance()
            val allTransactions = transactionManager.getAllTransactionsForUser(userId)

            val todayTransactions = allTransactions.filter { transaction ->
                val transactionDate = Calendar.getInstance().apply {
                    timeInMillis = transaction.dateTimestamp
                }
                transactionDate.timeInMillis >= todayStart.timeInMillis
            }

            todayTransactions.size <= 1 // This transaction is the first (or second, close enough)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking first transaction: ${e.message}", e)
            false
        }
    }

    /**
     * Update user's logging streak
     */
    private suspend fun updateStreak(userId: Int) {
        try {
            val userProgress = getUserProgress(userId)
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }

            val lastLoginCal = Calendar.getInstance().apply {
                timeInMillis = userProgress.lastLoginDate
            }

            val newStreak = when {
                isSameDay(lastLoginCal, yesterday) -> {
                    // Logged in yesterday, continue streak
                    userProgress.currentStreak + 1
                }
                isSameDay(lastLoginCal, today) -> {
                    // Already logged in today, maintain streak
                    userProgress.currentStreak
                }
                else -> {
                    // Missed days, reset streak
                    1
                }
            }

            val updatedProgress = userProgress.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(userProgress.longestStreak, newStreak),
                lastLoginDate = System.currentTimeMillis()
            )

            saveUserProgress(userId, updatedProgress)

            // Award streak bonuses
            if (newStreak > userProgress.currentStreak) {
                when (newStreak) {
                    7 -> awardPoints(userId, 100, "7-day streak bonus!")
                    14 -> awardPoints(userId, 200, "14-day streak bonus!")
                    30 -> awardPoints(userId, 500, "30-day streak bonus!")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error updating streak: ${e.message}", e)
        }
    }

    /**
     * Get today's transaction count for challenge progress
     */
    private suspend fun getTodayTransactionCount(userId: Int): Int {
        return try {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val transactionManager = FirebaseTransactionManager.getInstance()
            val allTransactions = transactionManager.getAllTransactionsForUser(userId)

            allTransactions.count { transaction ->
                transaction.dateTimestamp >= todayStart.timeInMillis
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's transaction count: ${e.message}", e)
            0
        }
    }

    /**
     * Get today's receipt count for challenge progress
     */
    private suspend fun getTodayReceiptCount(userId: Int): Int {
        return try {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val transactionManager = FirebaseTransactionManager.getInstance()
            val allTransactions = transactionManager.getAllTransactionsForUser(userId)

            allTransactions.count { transaction ->
                transaction.dateTimestamp >= todayStart.timeInMillis &&
                        !transaction.receiptPath.isNullOrEmpty()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting today's receipt count: ${e.message}", e)
            0
        }
    }

    /**
     * Check budget compliance for today (simplified version)
     */
    private suspend fun getTodayBudgetComplianceCount(userId: Int): Int {
        return try {
            // Simplified: assume user is compliant if they haven't overspent massively
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val transactionManager = FirebaseTransactionManager.getInstance()
            val allTransactions = transactionManager.getAllTransactionsForUser(userId)

            val todayExpenses = allTransactions.filter { transaction ->
                transaction.dateTimestamp >= todayStart.timeInMillis && transaction.isExpense
            }.sumOf { it.amount }

            // Simple logic: if spent less than 500, consider it compliant
            if (todayExpenses < 500.0) 2 else 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking budget compliance: ${e.message}", e)
            0
        }
    }

    /**
     * Calculate savings percentage compared to yesterday (simplified)
     */
    private suspend fun getTodaySavingsPercentage(userId: Int): Int {
        return try {
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            val transactionManager = FirebaseTransactionManager.getInstance()
            val allTransactions = transactionManager.getAllTransactionsForUser(userId)

            val todayExpenses = allTransactions.filter { transaction ->
                val transCal = Calendar.getInstance().apply { timeInMillis = transaction.dateTimestamp }
                isSameDay(transCal, today) && transaction.isExpense
            }.sumOf { it.amount }

            val yesterdayExpenses = allTransactions.filter { transaction ->
                val transCal = Calendar.getInstance().apply { timeInMillis = transaction.dateTimestamp }
                isSameDay(transCal, yesterday) && transaction.isExpense
            }.sumOf { it.amount }

            if (yesterdayExpenses > 0) {
                val savings = ((yesterdayExpenses - todayExpenses) / yesterdayExpenses * 100).toInt()
                maxOf(0, savings) // Return 0 if negative (spent more)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating savings: ${e.message}", e)
            0
        }
    }

    /**
     * Category compliance check (simplified)
     */
    private suspend fun getTodayCategoryComplianceCount(userId: Int): Int {
        // Simplified implementation - return random compliance for now
        return kotlin.random.Random.nextInt(0, 3)
    }

    /**
     * Helper function to check if two calendars are on the same day
     */
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun getEndOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        return calendar.timeInMillis
    }
}