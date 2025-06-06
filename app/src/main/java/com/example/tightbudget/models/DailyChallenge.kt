package com.example.tightbudget.models

/**
 * Represents a daily challenge for users
 */
data class DailyChallenge(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var pointsReward: Int = 0,
    var type: ChallengeType = ChallengeType.TRANSACTION,
    var targetValue: Int = 1, // Number of transactions, days under budget, etc.
    var isCompleted: Boolean = false,
    var dateAssigned: Long = System.currentTimeMillis(),
    var expiresAt: Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
) {
    constructor() : this("", "", "", 0, ChallengeType.TRANSACTION, 1, false, System.currentTimeMillis(), System.currentTimeMillis() + (24 * 60 * 60 * 1000))
}