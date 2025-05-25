package com.example.tightbudget.utils

import android.util.Log
import com.example.tightbudget.firebase.FirebaseBudgetManager
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.ui.CategoryBudgetItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages category budget allocations, including loading, saving,
 * and distribution algorithms using Firebase instead of Room database.
 * This class is responsible for:
 * - Loading category allocations for a specific budget goal from Firebase
 * - Creating default allocations for a new budget goal
 * - Saving category allocations to Firebase
 * - Adjusting allocations when the total budget changes
 * - Distributing additional amounts across categories
 * - Calculating spending statistics by category from Firebase
 * Updated to use Firebase Realtime Database.
 */
class CategoryAllocationManager {
    private val TAG = "CategoryAllocationManager"

    // Firebase managers
    private val firebaseBudgetManager = FirebaseBudgetManager.getInstance()
    private val firebaseDataManager = FirebaseDataManager.getInstance()

    /**
     * Loads category allocations for a specific budget goal from Firebase
     */
    suspend fun loadCategoryAllocations(budgetGoalId: Int): List<CategoryBudgetItem> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading category allocations from Firebase for budget goal: $budgetGoalId")

                // Get budget allocations for this goal from Firebase
                val categoryBudgets = firebaseBudgetManager.getCategoryBudgetsForGoal(budgetGoalId)

                // Convert to UI items
                val results = categoryBudgets.map { categoryBudget ->
                    CategoryBudgetItem(
                        categoryName = categoryBudget.categoryName,
                        emoji = EmojiUtils.getCategoryEmoji(categoryBudget.categoryName),
                        color = getCategoryColor(categoryBudget.categoryName),
                        allocation = categoryBudget.allocation,
                        id = categoryBudget.id
                    )
                }

                Log.d(TAG, "Loaded ${results.size} category allocations from Firebase")
                return@withContext results

            } catch (e: Exception) {
                Log.e(TAG, "Error loading category allocations from Firebase: ${e.message}", e)
                return@withContext emptyList<CategoryBudgetItem>()
            }
        }

    /**
     * Creates default allocations for a new budget goal
     */
    suspend fun createDefaultAllocations(totalBudget: Double): List<CategoryBudgetItem> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating default allocations for budget: $totalBudget")

                // Use predefined categories for POE demonstration
                val predefinedCategories = getPredefinedCategories()

                // Smart allocation algorithm:
                // 1. Essential categories get higher percentage
                // 2. Other categories get even distribution of remaining budget

                val essentialCategories = listOf("Housing", "Groceries", "Utilities", "Transport")
                val essentialPercentage = 0.65 // 65% for essential categories

                val results = mutableListOf<CategoryBudgetItem>()
                var remainingBudget = totalBudget

                // First, allocate to essentials
                val essentialCats = predefinedCategories.filter {
                    essentialCategories.contains(it.name)
                }

                if (essentialCats.isNotEmpty()) {
                    val essentialBudget = totalBudget * essentialPercentage
                    remainingBudget -= essentialBudget

                    // Distribute essential budget among essential categories
                    val perEssentialCat = essentialBudget / essentialCats.size

                    for (cat in essentialCats) {
                        results.add(
                            CategoryBudgetItem(
                                categoryName = cat.name,
                                emoji = cat.emoji,
                                color = cat.color,
                                allocation = perEssentialCat
                            )
                        )
                    }
                }

                // Distribute remaining budget to other categories
                val otherCats = predefinedCategories.filter {
                    !essentialCategories.contains(it.name)
                }

                if (otherCats.isNotEmpty()) {
                    val perOtherCat = remainingBudget / otherCats.size

                    for (cat in otherCats) {
                        results.add(
                            CategoryBudgetItem(
                                categoryName = cat.name,
                                emoji = cat.emoji,
                                color = cat.color,
                                allocation = perOtherCat
                            )
                        )
                    }
                }

                Log.d(TAG, "Created ${results.size} default allocations")
                return@withContext results

            } catch (e: Exception) {
                Log.e(TAG, "Error creating default allocations: ${e.message}", e)
                return@withContext emptyList<CategoryBudgetItem>()
            }
        }

    /**
     * Get predefined categories for POE demonstration
     */
    private fun getPredefinedCategories(): List<PredefinedCategory> {
        return listOf(
            PredefinedCategory("Housing", EmojiUtils.getCategoryEmoji("Housing"), "#4CAF50"),
            PredefinedCategory("Groceries", EmojiUtils.getCategoryEmoji("Groceries"), "#FF9800"),
            PredefinedCategory("Utilities", EmojiUtils.getCategoryEmoji("Utilities"), "#FFC107"),
            PredefinedCategory("Transport", EmojiUtils.getCategoryEmoji("Transport"), "#2196F3"),
            PredefinedCategory("Entertainment", EmojiUtils.getCategoryEmoji("Entertainment"), "#9C27B0"),
            PredefinedCategory("Health", EmojiUtils.getCategoryEmoji("Health"), "#E91E63"),
            PredefinedCategory("Shopping", EmojiUtils.getCategoryEmoji("Shopping"), "#00BCD4"),
            PredefinedCategory("Education", EmojiUtils.getCategoryEmoji("Education"), "#3F51B5")
        )
    }

    /**
     * Helper data class for predefined categories
     */
    private data class PredefinedCategory(
        val name: String,
        val emoji: String,
        val color: String
    )

    /**
     * Get category color based on category name
     */
    private fun getCategoryColor(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "housing" -> "#4CAF50"
            "food", "groceries" -> "#FF9800"
            "transport" -> "#2196F3"
            "entertainment" -> "#9C27B0"
            "utilities" -> "#FFC107"
            "health" -> "#E91E63"
            "shopping" -> "#00BCD4"
            "education" -> "#3F51B5"
            else -> "#9E9E9E"
        }
    }

    /**
     * Saves category allocations for a budget goal to Firebase
     */
    suspend fun saveCategoryAllocations(
        budgetGoalId: Int,
        categoryItems: List<CategoryBudgetItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving ${categoryItems.size} category allocations to Firebase for budget goal: $budgetGoalId")

            // First delete existing allocations from Firebase
            firebaseBudgetManager.deleteCategoryBudgetsForGoal(budgetGoalId)

            // Then insert new ones to Firebase
            for (item in categoryItems) {
                val categoryBudget = CategoryBudget(
                    id = 0, // Always insert new
                    budgetGoalId = budgetGoalId,
                    categoryName = item.categoryName,
                    allocation = item.allocation
                )
                firebaseBudgetManager.createCategoryBudget(categoryBudget)
            }

            Log.d(TAG, "Successfully saved category allocations to Firebase")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error saving category allocations to Firebase: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Adjusts category allocations proportionally when total budget changes
     */
    fun adjustAllocationsByRatio(
        categoryItems: List<CategoryBudgetItem>,
        oldTotal: Double,
        newTotal: Double
    ): List<CategoryBudgetItem> {
        if (oldTotal <= 0 || categoryItems.isEmpty()) return categoryItems

        val ratio = newTotal / oldTotal
        Log.d(TAG, "Adjusting allocations by ratio: $ratio (old: $oldTotal, new: $newTotal)")

        return categoryItems.map { item ->
            item.copy(allocation = item.allocation * ratio)
        }
    }

    /**
     * Distributes an additional amount across categories
     */
    fun distributeAdditionalAmount(
        categoryItems: List<CategoryBudgetItem>,
        additionalAmount: Double
    ): List<CategoryBudgetItem> {
        if (categoryItems.isEmpty()) return categoryItems

        val perCategoryAmount = additionalAmount / categoryItems.size
        Log.d(TAG, "Distributing additional amount: $additionalAmount across ${categoryItems.size} categories")

        return categoryItems.map { item ->
            item.copy(allocation = item.allocation + perCategoryAmount)
        }
    }

    /**
     * Calculates spending statistics by category using Firebase data
     */
    suspend fun getCategorySpendingStats(
        userId: Int,
        month: Int,
        year: Int
    ): Map<String, Double> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Calculating category spending stats from Firebase for user: $userId, month: $month, year: $year")

            // Create date range for the specified month
            val calendar = Calendar.getInstance()
            calendar.set(year, month - 1, 1, 0, 0, 0) // First day of month
            val startDate = calendar.time

            calendar.set(
                year,
                month - 1,
                calendar.getActualMaximum(Calendar.DAY_OF_MONTH),
                23,
                59,
                59
            ) // Last day of month
            val endDate = calendar.time

            // Get transactions for the period from Firebase
            val transactions = firebaseDataManager.getTransactionsForPeriod(userId, startDate, endDate)

            // Group transactions by category and sum amounts (only expenses)
            val results = transactions
                .filter { it.isExpense }
                .groupBy { it.category }
                .mapValues { entry ->
                    entry.value.sumOf { it.amount }
                }

            Log.d(TAG, "Calculated spending stats for ${results.size} categories from Firebase")
            return@withContext results

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating category spending from Firebase: ${e.message}", e)
            return@withContext emptyMap<String, Double>()
        }
    }

    /**
     * Get category spending for current month from Firebase
     */
    suspend fun getCurrentMonthSpending(userId: Int): Map<String, Double> {
        val calendar = Calendar.getInstance()
        return getCategorySpendingStats(
            userId,
            calendar.get(Calendar.MONTH) + 1, // 0-based to 1-based
            calendar.get(Calendar.YEAR)
        )
    }

    /**
     * Get total allocated budget for a budget goal from Firebase
     */
    suspend fun getTotalAllocated(budgetGoalId: Int): Double {
        return try {
            val categoryBudgets = firebaseBudgetManager.getCategoryBudgetsForGoal(budgetGoalId)
            categoryBudgets.sumOf { it.allocation }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting total allocated from Firebase: ${e.message}", e)
            0.0
        }
    }
}