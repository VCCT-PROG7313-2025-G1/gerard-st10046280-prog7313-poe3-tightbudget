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
            val userProgress = getUserProgress(userId)
            val newPoints = userProgress.totalPoints + points
            val newLevel = calculateLevel(newPoints)

            val updatedProgress = userProgress.copy(
                totalPoints = newPoints,
                currentLevel = newLevel
            )

            saveUserProgress(userId, updatedProgress)
            Log.d(TAG, "Awarded $points points to user $userId for: $reason")

            // Check for new achievements
            checkAndUnlockAchievements(userId, updatedProgress)

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error awarding points: ${e.message}", e)
            false
        }
    }

    /**
     * Handles transaction addition and awards appropriate points
     */
    suspend fun onTransactionAdded(userId: Int, transaction: Transaction): Int {
        var totalPointsEarned = 0

        try {
            val userProgress = getUserProgress(userId)

            // Base points for adding transaction
            awardPoints(userId, PointsSystem.ADD_TRANSACTION, "Added transaction")
            totalPointsEarned += PointsSystem.ADD_TRANSACTION

            // Bonus points for receipt
            if (!transaction.receiptPath.isNullOrEmpty()) {
                awardPoints(userId, PointsSystem.ADD_RECEIPT, "Added receipt")
                totalPointsEarned += PointsSystem.ADD_RECEIPT

                // Update receipts count
                val updatedProgress = userProgress.copy(
                    receiptsUploaded = userProgress.receiptsUploaded + 1,
                    transactionCount = userProgress.transactionCount + 1
                )
                saveUserProgress(userId, updatedProgress)
            }

            // Check if it's first transaction of the day
            if (isFirstTransactionToday(userId)) {
                awardPoints(userId, PointsSystem.FIRST_TRANSACTION_OF_DAY, "First transaction today")
                totalPointsEarned += PointsSystem.FIRST_TRANSACTION_OF_DAY
            }

            // Update daily challenges progress
            updateChallengeProgress(userId, ChallengeType.TRANSACTION)

            // Update streak
            updateStreak(userId)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing transaction gamification: ${e.message}", e)
        }

        return totalPointsEarned
    }

    /**
     * Generate daily challenges for user
     */
    suspend fun generateDailyChallenges(userId: Int): List<DailyChallenge> {
        val challenges = mutableListOf<DailyChallenge>()
        val random = Random()

        val challengeTemplates = listOf(
            DailyChallenge(
                id = "transaction_${System.currentTimeMillis()}",
                title = "Transaction Logger",
                description = "Add 3 transactions today",
                pointsReward = 75,
                type = ChallengeType.TRANSACTION,
                targetValue = 3
            ),
            DailyChallenge(
                id = "receipt_${System.currentTimeMillis()}",
                title = "Receipt Collector",
                description = "Upload 2 receipts today",
                pointsReward = 60,
                type = ChallengeType.RECEIPT,
                targetValue = 2
            ),
            DailyChallenge(
                id = "budget_${System.currentTimeMillis()}",
                title = "Budget Master",
                description = "Stay within budget in 2 categories",
                pointsReward = 100,
                type = ChallengeType.BUDGET_COMPLIANCE,
                targetValue = 2
            ),
            DailyChallenge(
                id = "saver_${System.currentTimeMillis()}",
                title = "Smart Saver",
                description = "Spend 20% less than yesterday",
                pointsReward = 80,
                type = ChallengeType.SAVINGS,
                targetValue = 20
            )
        )

        // Select 2-3 random challenges
        val selectedChallenges = challengeTemplates.shuffled().take(random.nextInt(2) + 2)

        selectedChallenges.forEach { challenge ->
            challenge.dateAssigned = System.currentTimeMillis()
            challenge.expiresAt = getEndOfDay()
            saveDailyChallenge(userId, challenge)
            challenges.add(challenge)
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
    private fun calculateLevel(totalPoints: Int): Int {
        return when {
            totalPoints >= 5000 -> 10
            totalPoints >= 3000 -> 9
            totalPoints >= 2000 -> 8
            totalPoints >= 1500 -> 7
            totalPoints >= 1000 -> 6
            totalPoints >= 750 -> 5
            totalPoints >= 500 -> 4
            totalPoints >= 300 -> 3
            totalPoints >= 150 -> 2
            else -> 1
        }
    }

    /**
     * Check and unlock achievements
     */
    private suspend fun checkAndUnlockAchievements(userId: Int, userProgress: UserProgress) {
        val achievements = getAllAchievements()

        achievements.forEach { achievement ->
            if (!achievement.isUnlocked && meetsAchievementCriteria(userProgress, achievement)) {
                val unlockedAchievement = achievement.copy(
                    isUnlocked = true,
                    unlockedDate = System.currentTimeMillis()
                )
                saveAchievement(userId, unlockedAchievement)

                // Award bonus points for achievement
                awardPoints(userId, achievement.pointsRequired, "Unlocked achievement: ${achievement.title}")
            }
        }
    }

    /**
     * Check if user meets achievement criteria
     */
    private fun meetsAchievementCriteria(userProgress: UserProgress, achievement: Achievement): Boolean {
        return when (achievement.type) {
            AchievementType.POINTS -> userProgress.totalPoints >= achievement.targetValue
            AchievementType.STREAK -> userProgress.longestStreak >= achievement.targetValue
            AchievementType.TRANSACTIONS -> userProgress.transactionCount >= achievement.targetValue
            AchievementType.BUDGET_GOALS -> userProgress.budgetGoalsMet >= achievement.targetValue
            AchievementType.RECEIPTS -> userProgress.receiptsUploaded >= achievement.targetValue
            else -> false
        }
    }

    // Helper methods for Firebase operations
    private suspend fun getUserProgress(userId: Int): UserProgress = suspendCoroutine { continuation ->
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

    private suspend fun getAllAchievements(): List<Achievement> {
        // Return predefined achievements - you can also load from Firebase
        return listOf(
            Achievement("first_transaction", "First Steps", "Add your first transaction", "🎯", 50, AchievementType.TRANSACTIONS, 1),
            Achievement("streak_7", "Week Warrior", "Log transactions for 7 days straight", "🔥", 200, AchievementType.STREAK, 7),
            Achievement("points_1000", "Point Master", "Earn 1000 total points", "⭐", 1000, AchievementType.POINTS, 1000),
            Achievement("receipts_10", "Receipt Collector", "Upload 10 receipts", "📄", 150, AchievementType.RECEIPTS, 10)
        )
    }

    // Helper methods for challenge progress tracking
    private suspend fun getTodayTransactionCount(userId: Int): Int {
        // Implementation to count today's transactions
        return 0 // Placeholder
    }

    private suspend fun getTodayReceiptCount(userId: Int): Int {
        // Implementation to count today's receipts
        return 0 // Placeholder
    }

    private suspend fun getTodayBudgetComplianceCount(userId: Int): Int {
        // Implementation to check budget compliance
        return 0 // Placeholder
    }

    private suspend fun getTodaySavingsPercentage(userId: Int): Int {
        // Implementation to calculate savings percentage
        return 0 // Placeholder
    }

    private suspend fun getTodayCategoryComplianceCount(userId: Int): Int {
        // Implementation to check category compliance
        return 0 // Placeholder
    }

    private fun isFirstTransactionToday(userId: Int): Boolean {
        // Implementation to check if this is first transaction today
        return false // Placeholder
    }

    private fun updateStreak(userId: Int) {
        // Implementation to update user's streak
    }

    private fun getEndOfDay(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        return calendar.timeInMillis
    }
}