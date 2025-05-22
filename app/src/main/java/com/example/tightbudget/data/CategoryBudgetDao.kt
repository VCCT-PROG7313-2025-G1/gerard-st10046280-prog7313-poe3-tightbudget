package com.example.tightbudget.data

import androidx.room.*
import com.example.tightbudget.models.CategoryBudget

@Dao
interface CategoryBudgetDao {
    @Insert
    suspend fun insertCategoryBudget(categoryBudget: CategoryBudget): Long

    @Update
    suspend fun updateCategoryBudget(categoryBudget: CategoryBudget)

    @Delete
    suspend fun deleteCategoryBudget(categoryBudget: CategoryBudget)

    @Query("SELECT * FROM category_budgets WHERE budgetGoalId = :budgetGoalId")
    suspend fun getCategoryBudgetsForGoal(budgetGoalId: Int): List<CategoryBudget>

    @Query("DELETE FROM category_budgets WHERE budgetGoalId = :budgetGoalId")
    suspend fun deleteCategoryBudgetsForGoal(budgetGoalId: Int)
}