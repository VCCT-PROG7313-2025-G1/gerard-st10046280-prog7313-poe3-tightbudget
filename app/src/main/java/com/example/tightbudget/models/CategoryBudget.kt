package com.example.tightbudget.models

/**
 * This data class represents a category budget in the app.
 * It is used to store information about budget allocations for each category within a budget goal.
 * Firebase-compatible version with no-argument constructor and proper defaults.
 */
data class CategoryBudget(
    val id: Int = 0,                // Unique identifier
    val budgetGoalId: Int = 0,      // Reference to parent budget goal
    val categoryName: String = "",   // Name of the category
    val allocation: Double = 0.0     // Amount allocated to this category
) {
    // No-argument constructor required by Firebase
    constructor() : this(0, 0, "", 0.0)
}