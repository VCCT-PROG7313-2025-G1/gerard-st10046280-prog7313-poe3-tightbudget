package com.example.tightbudget.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * This data class represents a user in the app.
 * It is used to store information about each user, including their full name, email, password, and balance.
 * The class is annotated with @Entity to indicate that it is a Room database entity.
 */

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fullName: String,
    val email: String,
    val password: String,
    val balance: Double = 0.0
)
