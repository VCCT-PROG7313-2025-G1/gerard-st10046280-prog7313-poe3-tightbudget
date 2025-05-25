package com.example.tightbudget.models

/**
 * This data class represents a budget goal in the app.
 * It is used to store information about each user's budget goals, including the month, year,
 * total budget, and whether the goal is active.
 * Firebase-compatible version with no-argument constructor and proper defaults.
 */
data class BudgetGoal(
    val id: Int = 0,
    val userId: Int = 0,                  // User reference
    val month: Int = 1,                   // Month (1-12)
    val year: Int = 2025,                 // Year
    val totalBudget: Double = 0.0,        // Total budget amount
    val minimumSpendingGoal: Double = 0.0,// Minimum spending goal
    val isActive: Boolean = true          // Whether this goal is active
) {
    // No-argument constructor required by Firebase
    constructor() : this(0, 0, 1, 2025, 0.0, 0.0, true)
}