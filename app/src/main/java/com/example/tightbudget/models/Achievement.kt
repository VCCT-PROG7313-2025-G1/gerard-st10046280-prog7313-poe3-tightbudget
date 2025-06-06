package com.example.tightbudget.models

/**
 * Represents an achievement/badge
 */
data class Achievement(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var emoji: String = "",
    var pointsRequired: Int = 0,
    var type: AchievementType = AchievementType.POINTS,
    var targetValue: Int = 0,
    var isUnlocked: Boolean = false,
    var unlockedDate: Long? = null
) {
    constructor() : this("", "", "", "", 0, AchievementType.POINTS, 0, false, null)
}