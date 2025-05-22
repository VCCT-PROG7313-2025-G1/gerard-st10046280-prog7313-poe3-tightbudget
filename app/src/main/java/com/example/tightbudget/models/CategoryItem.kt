package com.example.tightbudget.models

/*
 * This data class represents a category item in the app.
 * It is used to store information about each category, including its name, emoji, color, and budget.
 * The class is not annotated with @Entity as it is not directly used for database operations.
 */

data class CategoryItem(
    val name: String,
    val emoji: String,
    val color: String,
    val budget: Double
)