package com.example.tightbudget.data

import androidx.room.*
import com.example.tightbudget.models.BudgetGoal

@Dao
interface BudgetGoalDao {
    @Insert
    suspend fun insertBudgetGoal(budgetGoal: BudgetGoal): Long

    @Update
    suspend fun updateBudgetGoal(budgetGoal: BudgetGoal)

    @Delete
    suspend fun deleteBudgetGoal(budgetGoal: BudgetGoal)

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND month = :month AND year = :year LIMIT 1")
    suspend fun getBudgetGoalForMonth(userId: Int, month: Int, year: Int): BudgetGoal?

    @Query("SELECT * FROM budget_goals WHERE userId = :userId AND isActive = 1 LIMIT 1")
    suspend fun getActiveBudgetGoal(userId: Int): BudgetGoal?

    @Query("UPDATE budget_goals SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAllBudgetGoals(userId: Int)

    @Query("SELECT * FROM budget_goals WHERE userId = :userId ORDER BY year DESC, month DESC")
    suspend fun getAllBudgetGoalsForUser(userId: Int): List<BudgetGoal>
}