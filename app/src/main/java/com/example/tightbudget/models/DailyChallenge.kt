package com.example.tightbudget.models

/**
 * Represents a daily challenge for users
 */
data class DailyChallenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val pointsReward: Int = 0,
    val type: ChallengeType = ChallengeType.TRANSACTION,
    val targetValue: Int = 0,
    val currentProgress: Int = 0,
    val isCompleted: Boolean = false,
    val dateAssigned: Long = 0L,
    val expiresAt: Long = 0L
) {
    // No-argument constructor for Firebase
    constructor() : this("", "", "", 0, ChallengeType.TRANSACTION, 0, 0, false, 0L, 0L)

    /**
     * Get progress percentage for UI display
     */
    fun getProgressPercentage(): Int {
        return if (targetValue > 0) {
            minOf(100, (currentProgress * 100) / targetValue)
        } else 0
    }

    /**
     * Get progress text for UI display
     */
    fun getProgressText(): String {
        return "$currentProgress/$targetValue"
    }
}