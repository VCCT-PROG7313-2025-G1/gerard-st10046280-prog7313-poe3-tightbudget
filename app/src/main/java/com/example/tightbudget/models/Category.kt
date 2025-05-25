package com.example.tightbudget.models

/**
 * This data class represents a category in the app.
 * It is used to store information about each category, including its name, emoji, color, and budget.
 * Firebase-compatible version with no-argument constructor and proper defaults.
 */
data class Category(
    val id: Int = 0,                // Unique identifier
    val name: String = "",          // Category name (e.g., "Food", "Transport")
    val emoji: String = "",         // Category emoji (e.g., "🍔", "🚗")
    val color: String = "",         // Category color (e.g., "#FF9800")
    val budget: Double = 0.0        // Default budget for this category
) {
    // No-argument constructor required by Firebase
    constructor() : this(0, "", "", "", 0.0)
}