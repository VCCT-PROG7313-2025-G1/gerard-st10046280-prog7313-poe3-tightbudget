package com.example.tightbudget.models

/**
 * This data class represents a user in the app.
 * It is used to store information about each user, including their full name, email, password, and balance.
 * Firebase-compatible version with no-argument constructor and proper defaults.
 */
data class User(
    val id: Int = 0,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val balance: Double = 0.0
) {
    // No-argument constructor required by Firebase
    constructor() : this(0, "", "", "", 0.0)
}