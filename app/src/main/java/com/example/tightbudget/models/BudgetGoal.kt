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
    tableName = "budget_goals",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("userId")]
)
data class BudgetGoal(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,                 // Links to the user
    val month: Int,                  // 1-12 for Jan-Dec
    val year: Int,                   // e.g., 2025
    val totalBudget: Double,         // Total monthly budget
    val minimumSpendingGoal: Double, // Minimum spending goal (new requirement)
    val isActive: Boolean = true     // Whether this is the current active budget
)