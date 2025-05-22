package com.example.tightbudget.ui

/**
 * Data class representing a category budget item.
 * This class is used to store information about each category's budget allocation.
 *
 * @property categoryName The name of the category (e.g., "Food", "Transport").
 * @property emoji The emoji associated with the category (e.g., "🍔", "🚗").
 * @property color The color associated with the category (e.g., "#FF9800").
 * @property allocation The budget allocation for this category.
 * @property id The unique identifier for this category budget item in the database.
 */

data class CategoryBudgetItem(
    val categoryName: String,
    val emoji: String,      // Category emoji
    val color: String,      // Category color
    var allocation: Double, // Budget allocation
    val id: Int = 0         // Database ID
)