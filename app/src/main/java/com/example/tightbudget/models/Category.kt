package com.example.tightbudget.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * This data class represents a category in the app.
 * It is used to store information about each category, including its name, emoji, color, and budget.
 * The class is annotated with @Entity to indicate that it is a Room database entity.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val emoji: String,
    val color: String,
    val budget: Double
)