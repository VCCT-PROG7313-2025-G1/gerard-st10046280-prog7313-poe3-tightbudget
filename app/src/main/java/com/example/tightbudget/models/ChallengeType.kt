package com.example.tightbudget.models

enum class ChallengeType {
    TRANSACTION,        // Add X transactions
    RECEIPT,           // Upload X receipts
    BUDGET_COMPLIANCE, // Stay within budget for X categories
    SAVINGS,          // Save X amount compared to yesterday
    STREAK,           // Maintain logging streak
    CATEGORY_LIMIT    // Don't exceed limit in specific category
}