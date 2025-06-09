package com.example.tightbudget.utils

import android.util.Log
import com.example.tightbudget.firebase.FirebaseBudgetManager
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.ui.CategoryBudgetItem
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manages category budget allocations, including loading, saving,
 * and distribution algorithms using Firebase instead of Room database.
 * This class is responsible for:
 * - Loading category allocations for a specific budget goal from Firebase
 * - Creating default allocations for a new budget goal using custom categories
 * - Saving category allocations to Firebase
 * - Adjusting allocations when the total budget changes
 * - Distributing additional amounts across categories
 * - Calculating spending statistics by category from Firebase
 * Updated to use Firebase Realtime Database and custom categories.
 */
class CategoryAllocationManager {
    private val TAG = "CategoryAllocationManager"

    // Firebase managers
    private val firebaseBudgetManager = FirebaseBudgetManager.getInstance()
    private val firebaseDataManager = FirebaseDataManager.getInstance()
    private val firebaseCategoryManager = FirebaseCategoryManager.getInstance()

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
     * Creates default allocations for a new budget goal using user-specific categories from Firebase
     */
    suspend fun createDefaultAllocations(totalBudget: Double, userId: Int): List<CategoryBudgetItem> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Creating default allocations for budget: $totalBudget, userId: $userId")

                if (userId == -1) {
                    Log.w(TAG, "Invalid userId, using predefined categories")
                    val predefinedCategories = getPredefinedCategories()
                    return@withContext createAllocationsFromCategories(predefinedCategories, totalBudget)
                }

                // Load user-specific categories from Firebase
                val userCategories = try {
                    val categories = firebaseCategoryManager.getAllCategoriesForUser(userId)
                    Log.d(TAG, "Loaded ${categories.size} categories from Firebase for user $userId")
                    categories
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load user categories from Firebase: ${e.message}")
                    emptyList()
                }

                // Use user categories if available, otherwise seed defaults and use them
                val categoriesToUse = if (userCategories.isNotEmpty()) {
                    Log.d(TAG, "Using ${userCategories.size} user-specific categories")
                    userCategories.map {
                        Log.d(TAG, "Converting category: ${it.name}")
                        CategoryData(it.name, it.emoji ?: "📝", it.color, false)
                    }
                } else {
                    Log.d(TAG, "No user categories found, seeding defaults")
                    try {
                        // Seed default categories for this user
                        firebaseCategoryManager.seedDefaultCategoriesForUser(userId)
                        // Try loading again after seeding
                        val seededCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
                        Log.d(TAG, "After seeding, loaded ${seededCategories.size} categories")

                        if (seededCategories.isNotEmpty()) {
                            seededCategories.map {
                                Log.d(TAG, "Converting seeded category: ${it.name}")
                                CategoryData(it.name, it.emoji ?: "📝", it.color, false)
                            }
                        } else {
                            Log.d(TAG, "Seeding failed, falling back to predefined categories")
                            getPredefinedCategories()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error seeding categories: ${e.message}", e)
                        getPredefinedCategories()
                    }
                }

                Log.d(TAG, "Final categories to use: ${categoriesToUse.size}")
                for (cat in categoriesToUse) {
                    Log.d(TAG, "Category to allocate: ${cat.name}")
                }

                return@withContext createAllocationsFromCategories(categoriesToUse, totalBudget)

            } catch (e: Exception) {
                Log.e(TAG, "Error creating default allocations: ${e.message}", e)
                return@withContext emptyList<CategoryBudgetItem>()
            }
        }

    /**
     * Helper method to create allocations from a list of categories
     */
    private fun createAllocationsFromCategories(categories: List<CategoryData>, totalBudget: Double): List<CategoryBudgetItem> {
        // Smart allocation algorithm:
        // 1. Essential categories get higher percentage
        // 2. Non-essential categories get remaining amount
        val essentialCategoryNames = listOf("housing", "groceries", "food", "utilities", "transport")
        val essentialPercentage = 0.65 // 65% for essential categories

        // Separate essential and non-essential categories
        val essentialCategories = categories.filter { cat ->
            essentialCategoryNames.any { essential ->
                cat.name.lowercase().contains(essential)
            }
        }

        val nonEssentialCategories = categories.filter { cat ->
            !essentialCategoryNames.any { essential ->
                cat.name.lowercase().contains(essential)
            }
        }

        val results = mutableListOf<CategoryBudgetItem>()

        // Allocate to essential categories first
        if (essentialCategories.isNotEmpty()) {
            val essentialBudget = totalBudget * essentialPercentage
            val perEssentialCat = essentialBudget / essentialCategories.size

            for (cat in essentialCategories) {
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

        // Allocate remaining to non-essential categories
        if (nonEssentialCategories.isNotEmpty()) {
            val remainingBudget = totalBudget * (1.0 - essentialPercentage)
            val perOtherCat = remainingBudget / nonEssentialCategories.size

            for (cat in nonEssentialCategories) {
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
        return results
    }

    /**
     * Get predefined categories as fallback when no custom categories exist
     */
    private fun getPredefinedCategories(): List<CategoryData> {
        return listOf(
            CategoryData("Housing", EmojiUtils.getCategoryEmoji("Housing"), "#4CAF50", true),
            CategoryData("Groceries", EmojiUtils.getCategoryEmoji("Groceries"), "#FF9800", true),
            CategoryData("Utilities", EmojiUtils.getCategoryEmoji("Utilities"), "#FFC107", true),
            CategoryData("Transport", EmojiUtils.getCategoryEmoji("Transport"), "#2196F3", true),
            CategoryData("Entertainment", EmojiUtils.getCategoryEmoji("Entertainment"), "#9C27B0", false),
            CategoryData("Health", EmojiUtils.getCategoryEmoji("Health"), "#E91E63", false),
            CategoryData("Shopping", EmojiUtils.getCategoryEmoji("Shopping"), "#00BCD4", false),
            CategoryData("Education", EmojiUtils.getCategoryEmoji("Education"), "#3F51B5", false)
        )
    }

    /**
     * Helper data class for category data
     */
    private data class CategoryData(
        val name: String,
        val emoji: String,
        val color: String,
        val isEssential: Boolean = false
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
            "fitness" -> "#FF5722"
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
                23, 59, 59
            ) // Last day of month
            val endDate = calendar.time

            // Get transactions for the month from Firebase
            val transactions = firebaseDataManager.getTransactionsForPeriod(userId, startDate, endDate)

            // Group by category and sum amounts
            val categorySpending = transactions
                .filter { it.isExpense } // Only include expenses
                .groupBy { it.category }
                .mapValues { (_, transactions) ->
                    transactions.sumOf { it.amount }
                }

            Log.d(TAG, "Calculated spending for ${categorySpending.size} categories")
            return@withContext categorySpending

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating category spending stats: ${e.message}", e)
            return@withContext emptyMap()
        }
    }
}