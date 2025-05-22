package com.example.tightbudget.models

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Represents a single transaction (income or expense) in the app.
 * This class is used to display items in the transaction list,
 * and is also passed to the transaction detail overlay when clicked.
 *
 * The @Parcelize annotation automatically generates the code
 * needed to send this object between Android components.
 */
@Parcelize
@Entity(
    tableName = "transactions",
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
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val userId: Int,           // Foreign key to link to user
    val merchant: String,      // Who you paid or were paid by
    val category: String,      // Category like 'Food', 'Salary', etc.
    val amount: Double,        // How much was spent or earned
    val date: Date,            // Date and time of the transaction
    val isExpense: Boolean,    // True = expense, False = income
    val description: String? = null,
    val receiptPath: String? = null,
    val isRecurring: Boolean = false
) : Parcelable