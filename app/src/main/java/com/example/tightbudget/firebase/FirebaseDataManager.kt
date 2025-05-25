package com.example.tightbudget.firebase

import android.util.Log
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.models.User
import java.util.*

/**
 * Unified Firebase Data Manager that coordinates all Firebase operations
 * This replaces the DashboardDataManager for Part 3 of the POE
 * All data now comes from Firebase instead of local Room database
 */
class FirebaseDataManager {

    private val userManager = FirebaseUserManager.getInstance()
    private val transactionManager = FirebaseTransactionManager.getInstance()
    private val budgetManager = FirebaseBudgetManager.getInstance()

    companion object {
        private const val TAG = "FirebaseDataManager"

        @Volatile
        private var INSTANCE: FirebaseDataManager? = null

        fun getInstance(): FirebaseDataManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseDataManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Loads the active budget goal for the current user from Firebase
     */
    suspend fun loadActiveBudgetGoal(userId: Int): BudgetGoal? {
        return try {
            val activeBudgetGoal = budgetManager.getActiveBudgetGoal(userId)

            if (activeBudgetGoal == null) {
                // If no active budget goal, try to get one for the current month
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH) + 1 // +1 because Calendar months are 0-based
                val currentYear = calendar.get(Calendar.YEAR)

                return budgetManager.getBudgetGoalForMonth(userId, currentMonth, currentYear)
            }

            activeBudgetGoal

        } catch (e: Exception) {
            Log.e(TAG, "Error loading active budget goal from Firebase: ${e.message}", e)
            null
        }
    }

    /**
     * Loads category budgets associated with a budget goal from Firebase
     */
    suspend fun loadCategoryBudgets(budgetGoalId: Int): List<CategoryBudget> {
        return try {
            budgetManager.getCategoryBudgetsForGoal(budgetGoalId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading category budgets from Firebase: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Loads recent transactions for a user from Firebase
     */
    suspend fun loadRecentTransactions(userId: Int, limit: Int = 5): List<Transaction> {
        return try {
            transactionManager.getRecentTransactions(userId, limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent transactions from Firebase: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets current month spending by category from Firebase
     */
    suspend fun getCurrentMonthSpendingByCategory(userId: Int): Map<String, Double> {
        return try {
            val calendar = Calendar.getInstance()

            // Start of month
            val startCalendar = Calendar.getInstance()
            startCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), 1, 0, 0, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            val startDate = startCalendar.time

            // End of month
            val endCalendar = Calendar.getInstance()
            endCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            endCalendar.set(Calendar.MILLISECOND, 999)
            val endDate = endCalendar.time

            val transactions = transactionManager.getTransactionsForPeriod(userId, startDate, endDate)

            // Group transactions by category and sum amounts (only expenses)
            transactions
                .filter { it.isExpense }
                .groupBy { it.category }
                .mapValues { (_, transactions) ->
                    transactions.sumOf { it.amount }
                }

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating current month spending from Firebase: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Calculates total spending for current month from Firebase
     */
    suspend fun getCurrentMonthTotalSpending(userId: Int): Double {
        return try {
            val calendar = Calendar.getInstance()

            // Start of month
            val startCalendar = Calendar.getInstance()
            startCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), 1, 0, 0, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            val startDate = startCalendar.time

            // End of month
            val endCalendar = Calendar.getInstance()
            endCalendar.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
            endCalendar.set(Calendar.MILLISECOND, 999)
            val endDate = endCalendar.time

            transactionManager.getTotalExpensesForPeriod(userId, startDate, endDate)

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total spending from Firebase: ${e.message}", e)
            0.0
        }
    }

    /**
     * Combines category spending with budget limits to create dashboard data from Firebase
     */
    suspend fun getDashboardCategoryData(userId: Int): Map<String, Pair<Double, Double>> {
        return try {
            // Get current budget goal
            val budgetGoal = loadActiveBudgetGoal(userId) ?: return emptyMap()

            // Get category budgets
            val categoryBudgets = loadCategoryBudgets(budgetGoal.id)

            // Get current spending by category
            val categorySpending = getCurrentMonthSpendingByCategory(userId)

            // Combine into a map of category -> (spending, budget)
            val result = mutableMapOf<String, Pair<Double, Double>>()

            // Add categories with budgets
            for (categoryBudget in categoryBudgets) {
                val spending = categorySpending[categoryBudget.categoryName] ?: 0.0
                result[categoryBudget.categoryName] = Pair(spending, categoryBudget.allocation)
            }

            // Add categories with spending but no budget
            for ((category, spending) in categorySpending) {
                if (!result.containsKey(category)) {
                    result[category] = Pair(spending, 0.0)
                }
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Error creating dashboard category data from Firebase: ${e.message}", e)
            emptyMap()
        }
    }

    /**
     * Gets user information from Firebase
     */
    suspend fun getUserById(userId: Int): User? {
        return try {
            userManager.getUserById(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user from Firebase: ${e.message}", e)
            null
        }
    }

    /**
     * Gets user information by email from Firebase
     */
    suspend fun getUserByEmail(email: String): User? {
        return try {
            userManager.getUserByEmail(email)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user by email from Firebase: ${e.message}", e)
            null
        }
    }

    /**
     * Gets all transactions for a user from Firebase
     */
    suspend fun getAllTransactionsForUser(userId: Int): List<Transaction> {
        return try {
            transactionManager.getAllTransactionsForUser(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all transactions from Firebase: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets transactions for a specific period from Firebase
     */
    suspend fun getTransactionsForPeriod(userId: Int, startDate: Date, endDate: Date): List<Transaction> {
        return try {
            transactionManager.getTransactionsForPeriod(userId, startDate, endDate)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions for period from Firebase: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets transactions for a specific category from Firebase
     */
    suspend fun getTransactionsForCategory(userId: Int, category: String): List<Transaction> {
        return try {
            transactionManager.getTransactionsForCategory(userId, category)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions for category from Firebase: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Saves a budget goal to Firebase
     */
    suspend fun saveBudgetGoal(budgetGoal: BudgetGoal): BudgetGoal {
        return try {
            if (budgetGoal.id > 0) {
                budgetManager.updateBudgetGoal(budgetGoal)
                budgetGoal
            } else {
                budgetManager.createBudgetGoal(budgetGoal)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving budget goal to Firebase: ${e.message}", e)
            throw e
        }
    }

    /**
     * Saves category budgets to Firebase
     */
    suspend fun saveCategoryBudgets(budgetGoalId: Int, categoryBudgets: List<CategoryBudget>): Boolean {
        return try {
            // First delete existing category budgets
            budgetManager.deleteCategoryBudgetsForGoal(budgetGoalId)

            // Then create new ones
            for (categoryBudget in categoryBudgets) {
                budgetManager.createCategoryBudget(categoryBudget.copy(budgetGoalId = budgetGoalId))
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving category budgets to Firebase: ${e.message}", e)
            false
        }
    }

    /**
     * Deletes a transaction from Firebase
     */
    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            transactionManager.deleteTransaction(transaction)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction from Firebase: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates a transaction in Firebase
     */
    suspend fun updateTransaction(transaction: Transaction) {
        try {
            transactionManager.updateTransaction(transaction)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating transaction in Firebase: ${e.message}", e)
            throw e
        }
    }
}