package com.example.tightbudget.models

/**
 * Data class representing a category with its spending information
 * for display in the CategorySpendingActivity.
 */
data class CategorySpendingItem(
    val id: String,             // Unique identifier (can be the category name)
    val name: String,           // Category name (e.g., "Food", "Transport")
    val emoji: String,          // Category emoji (e.g., "🍔", "🚗")
    val color: String,          // Category color (e.g., "#FF9800")
    val amount: Double,         // Amount spent in this category
    val budget: Double,         // Budget allocated for this category
    val transactionCount: Int   // Number of transactions in this category
)