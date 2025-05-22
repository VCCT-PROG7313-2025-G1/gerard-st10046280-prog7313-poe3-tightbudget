package com.example.tightbudget.utils

import android.util.Log
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Manager class responsible for loading and processing data for the Dashboard
 */
class DashboardDataManager(private val database: AppDatabase) {
    private val TAG = "DashboardDataManager"

    /**
     * Loads the active budget goal for the current user
     */
    suspend fun loadActiveBudgetGoal(userId: Int): BudgetGoal? = withContext(Dispatchers.IO) {
        try {
            val budgetGoalDao = database.budgetGoalDao()
            val activeBudgetGoal = budgetGoalDao.getActiveBudgetGoal(userId)

            if (activeBudgetGoal == null) {
                // If no active budget goal, try to get one for the current month
                val calendar = Calendar.getInstance()
                val currentMonth = calendar.get(Calendar.MONTH) + 1 // +1 because Calendar months are 0-based
                val currentYear = calendar.get(Calendar.YEAR)

                return@withContext budgetGoalDao.getBudgetGoalForMonth(userId, currentMonth, currentYear)
            }

            return@withContext activeBudgetGoal
        } catch (e: Exception) {
            Log.e(TAG, "Error loading active budget goal: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Loads category budgets associated with a budget goal
     */
    suspend fun loadCategoryBudgets(budgetGoalId: Int): List<CategoryBudget> = withContext(Dispatchers.IO) {
        try {
            return@withContext database.categoryBudgetDao().getCategoryBudgetsForGoal(budgetGoalId)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading category budgets: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Loads recent transactions for a user
     */
    suspend fun loadRecentTransactions(userId: Int, limit: Int = 5): List<Transaction> = withContext(Dispatchers.IO) {
        try {
            val transactions = database.transactionDao().getAllTransactionsForUser(userId)
            return@withContext transactions.take(limit)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading recent transactions: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    /**
     * Gets current month spending by category
     */
    suspend fun getCurrentMonthSpendingByCategory(userId: Int): Map<String, Double> = withContext(Dispatchers.IO) {
        try {
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

            val transactionDao = database.transactionDao()
            val transactions = transactionDao.getTransactionsForPeriod(userId, startDate, endDate)

            // Group transactions by category and sum amounts (only expenses)
            return@withContext transactions
                .filter { it.isExpense }
                .groupBy { it.category }
                .mapValues { (_, transactions) ->
                    transactions.sumOf { it.amount }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating current month spending: ${e.message}", e)
            return@withContext emptyMap()
        }
    }

    /**
     * Calculates total spending for current month
     */
    suspend fun getCurrentMonthTotalSpending(userId: Int): Double = withContext(Dispatchers.IO) {
        try {
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

            val transactionDao = database.transactionDao()
            val totalExpense = transactionDao.getTotalExpensesForPeriod(userId, startDate, endDate) ?: 0.0

            return@withContext totalExpense
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total spending: ${e.message}", e)
            return@withContext 0.0
        }
    }

    /**
     * Combines category spending with budget limits to create dashboard data
     */
    suspend fun getDashboardCategoryData(userId: Int): Map<String, Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            // Get current budget goal
            val budgetGoal = loadActiveBudgetGoal(userId) ?: return@withContext emptyMap()

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

            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "Error creating dashboard category data: ${e.message}", e)
            return@withContext emptyMap()
        }
    }
}