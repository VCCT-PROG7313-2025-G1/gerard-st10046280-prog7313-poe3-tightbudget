package com.example.tightbudget.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Represents a single transaction (income or expense) in the app.
 * This class is used to display items in the transaction list,
 * and is also passed to the transaction detail overlay when clicked.
 *
 * Firebase-compatible version with timestamp handling and no-argument constructor.
 * The @Parcelize annotation automatically generates the code
 * needed to send this object between Android components.
 */
@Parcelize
data class Transaction(
    val id: Int = 0,
    val userId: Int = 0,           // Foreign key to link to user
    val merchant: String = "",      // Who you paid or were paid by
    val category: String = "",      // Category like 'Food', 'Salary', etc.
    val amount: Double = 0.0,        // How much was spent or earned
    val dateTimestamp: Long = System.currentTimeMillis(), // Timestamp for Firebase compatibility
    val isExpense: Boolean = true,    // True = expense, False = income
    val description: String? = null,
    val receiptPath: String? = null,
    val isRecurring: Boolean = false
) : Parcelable {

    // No-argument constructor required by Firebase
    constructor() : this(0, 0, "", "", 0.0, System.currentTimeMillis(), true, null, null, false)

    // Convenience property to convert timestamp to Date for backward compatibility
    val date: Date
        get() = Date(dateTimestamp)

    // Helper constructor that accepts Date and converts to timestamp
    constructor(
        id: Int,
        userId: Int,
        merchant: String,
        category: String,
        amount: Double,
        date: Date,
        isExpense: Boolean,
        description: String? = null,
        receiptPath: String? = null,
        isRecurring: Boolean = false
    ) : this(
        id = id,
        userId = userId,
        merchant = merchant,
        category = category,
        amount = amount,
        dateTimestamp = date.time,
        isExpense = isExpense,
        description = description,
        receiptPath = receiptPath,
        isRecurring = isRecurring
    )
}