package com.example.tightbudget.models

/**
 * Represents user's gamification progress
 */
data class UserProgress(
    var userId: Int = 0,
    var totalPoints: Int = 0,
    var currentLevel: Int = 1,
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var lastLoginDate: Long = System.currentTimeMillis(),
    var transactionCount: Int = 0,
    var receiptsUploaded: Int = 0,
    var budgetGoalsMet: Int = 0,
    var achievementsUnlocked: List<String> = emptyList(),
    var dailyChallenges: List<String> = emptyList(), // Challenge IDs
    var lastChallengeRefresh: Long = 0
) {
    constructor() : this(0, 0, 1, 0, 0, System.currentTimeMillis(), 0, 0, 0, emptyList(), emptyList(), 0)
}