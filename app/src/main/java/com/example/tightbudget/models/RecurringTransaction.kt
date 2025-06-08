package com.example.tightbudget.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Represents a recurring transaction template that generates actual transactions
 * at specified intervals (monthly in this case)
 */
@Parcelize
data class RecurringTransaction(
    var id: String = "",
    var userId: Int = 0,
    var merchant: String = "",
    var category: String = "",
    var amount: Double = 0.0,
    var isExpense: Boolean = true,
    var description: String? = null,
    var receiptPath: String? = null,
    var frequency: String = "MONTHLY", // DAILY, WEEKLY, MONTHLY, YEARLY
    var startDateTimestamp: Long = System.currentTimeMillis(),
    var nextOccurrenceTimestamp: Long = System.currentTimeMillis(),
    var lastProcessedTimestamp: Long = 0L,
    var isActive: Boolean = true,
    var createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    // No-argument constructor for Firebase
    constructor() : this("", 0, "", "", 0.0, true, null, null, "MONTHLY",
        System.currentTimeMillis(), System.currentTimeMillis(), 0L, true, System.currentTimeMillis())

    // Convenience properties to convert timestamps to Dates
    val startDate: Date
        get() = Date(startDateTimestamp)

    val nextOccurrence: Date
        get() = Date(nextOccurrenceTimestamp)

    val lastProcessed: Date?
        get() = if (lastProcessedTimestamp > 0L) Date(lastProcessedTimestamp) else null

    // Helper constructor that accepts Dates
    constructor(
        id: String,
        userId: Int,
        merchant: String,
        category: String,
        amount: Double,
        isExpense: Boolean,
        description: String?,
        receiptPath: String?,
        frequency: String,
        startDate: Date,
        nextOccurrence: Date,
        isActive: Boolean,
        createdAt: Long
    ) : this(
        id = id,
        userId = userId,
        merchant = merchant,
        category = category,
        amount = amount,
        isExpense = isExpense,
        description = description,
        receiptPath = receiptPath,
        frequency = frequency,
        startDateTimestamp = startDate.time,
        nextOccurrenceTimestamp = nextOccurrence.time,
        lastProcessedTimestamp = 0L,
        isActive = isActive,
        createdAt = createdAt
    )

    /**
     * Check if this recurring transaction is due for processing
     */
    fun isDue(): Boolean {
        val now = System.currentTimeMillis()
        return isActive && nextOccurrenceTimestamp <= now
    }

    /**
     * Get a human-readable description of the frequency
     */
    fun getFrequencyDescription(): String {
        return when (frequency) {
            "DAILY" -> "Daily"
            "WEEKLY" -> "Weekly"
            "MONTHLY" -> "Monthly"
            "YEARLY" -> "Yearly"
            else -> frequency
        }
    }

    /**
     * Get days until next occurrence
     */
    fun getDaysUntilNext(): Int {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance()
        next.timeInMillis = nextOccurrenceTimestamp

        val diffInMillis = next.timeInMillis - now.timeInMillis
        return (diffInMillis / (1000 * 60 * 60 * 24)).toInt()
    }
}