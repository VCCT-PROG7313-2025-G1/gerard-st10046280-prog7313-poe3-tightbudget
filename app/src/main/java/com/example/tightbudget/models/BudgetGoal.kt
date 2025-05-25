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
    val userId: Int = 0,                  // <--- Default
    val month: Int = 1,                   // <--- Default (e.g., January)
    val year: Int = 2025,                 // <--- Default
    val totalBudget: Double = 0.0,        // <--- Default
    val minimumSpendingGoal: Double = 0.0,// <--- Default
    val isActive: Boolean = true          // <--- Default
)