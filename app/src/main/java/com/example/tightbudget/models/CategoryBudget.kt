package com.example.tightbudget.models

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * This data class represents a budget goal in the app.
 * It is used to store information about each user's budget goals, including the month, year,
 * total budget, and whether the goal is active.
 * The class is annotated with @Entity to indicate that it is a Room database entity.
 */

@Entity(
    tableName = "category_budgets",
    foreignKeys = [
        ForeignKey(
            entity = BudgetGoal::class,
            parentColumns = ["id"],
            childColumns = ["budgetGoalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("budgetGoalId")]
)
data class CategoryBudget(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val budgetGoalId: Int,   // Links to the budget goal
    val categoryName: String,
    val allocation: Double   // Amount allocated to this category
)