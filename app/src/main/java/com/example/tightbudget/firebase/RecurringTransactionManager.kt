package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.models.RecurringTransaction
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages recurring transactions - creating, processing, and scheduling them
 */
class RecurringTransactionManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val recurringTransactionsRef: DatabaseReference = database.getReference("recurringTransactions")
    private val firebaseTransactionManager = FirebaseTransactionManager.getInstance()

    companion object {
        private const val TAG = "RecurringTransactionManager"

        @Volatile
        private var INSTANCE: RecurringTransactionManager? = null

        fun getInstance(): RecurringTransactionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = RecurringTransactionManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates a recurring transaction template and schedules the first occurrence
     */
    suspend fun createRecurringTransaction(baseTransaction: Transaction): String {
        return try {
            val recurringTransactionKey = recurringTransactionsRef.push().key
                ?: throw Exception("Failed to generate recurring transaction key")

            // Calculate next occurrence (monthly from the base transaction date)
            val nextOccurrence = calculateNextOccurrence(baseTransaction.date)

            val recurringTransaction = RecurringTransaction(
                id = recurringTransactionKey,
                userId = baseTransaction.userId,
                merchant = baseTransaction.merchant,
                category = baseTransaction.category,
                amount = baseTransaction.amount,
                isExpense = baseTransaction.isExpense,
                description = baseTransaction.description,
                receiptPath = baseTransaction.receiptPath,
                frequency = "MONTHLY", // Currently only supporting monthly
                startDateTimestamp = baseTransaction.dateTimestamp,
                nextOccurrenceTimestamp = nextOccurrence.time,
                lastProcessedTimestamp = 0L,
                isActive = true,
                createdAt = System.currentTimeMillis()
            )

            // Save to Firebase
            recurringTransactionsRef.child(recurringTransactionKey).setValue(recurringTransaction).await()

            Log.d(TAG, "Recurring transaction created with ID: $recurringTransactionKey")
            Log.d(TAG, "Next occurrence: $nextOccurrence")

            recurringTransactionKey
        } catch (e: Exception) {
            Log.e(TAG, "Error creating recurring transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Process all due recurring transactions for all users
     * This should be called daily (ideally via background job or when app starts)
     */
    suspend fun processDueRecurringTransactions() {
        try {
            Log.d(TAG, "Processing due recurring transactions...")

            val allRecurringTransactions = getAllActiveRecurringTransactions()
            val today = Calendar.getInstance().time
            var processedCount = 0

            for (recurringTransaction in allRecurringTransactions) {
                if (recurringTransaction.nextOccurrence <= today) {
                    processRecurringTransaction(recurringTransaction)
                    processedCount++
                }
            }

            Log.d(TAG, "Processed $processedCount recurring transactions")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing due recurring transactions: ${e.message}", e)
        }
    }

    /**
     * Process a single recurring transaction by creating the actual transaction
     * and updating the next occurrence date
     */
    private suspend fun processRecurringTransaction(recurringTransaction: RecurringTransaction) {
        try {
            // Create the actual transaction
            val actualTransaction = Transaction(
                id = 0, // Will be auto-generated
                userId = recurringTransaction.userId,
                merchant = recurringTransaction.merchant,
                category = recurringTransaction.category,
                amount = recurringTransaction.amount,
                date = recurringTransaction.nextOccurrence,
                isExpense = recurringTransaction.isExpense,
                description = "${recurringTransaction.description} (Recurring)".takeIf {
                    !recurringTransaction.description.isNullOrEmpty()
                } ?: "(Recurring)",
                receiptPath = null, // Don't copy receipts for recurring transactions
                isRecurring = false // The generated transaction itself is not recurring
            )

            // Save the actual transaction
            val savedTransaction = firebaseTransactionManager.createTransaction(actualTransaction)
            Log.d(TAG, "Created recurring transaction instance: ${savedTransaction.id}")

            // Update next occurrence
            val nextOccurrence = calculateNextOccurrence(recurringTransaction.nextOccurrence)
            val updatedRecurringTransaction = recurringTransaction.copy(
                nextOccurrenceTimestamp = nextOccurrence.time,
                lastProcessedTimestamp = System.currentTimeMillis()
            )

            // Save updated recurring transaction
            recurringTransactionsRef.child(recurringTransaction.id)
                .setValue(updatedRecurringTransaction).await()

            Log.d(TAG, "Updated next occurrence to: $nextOccurrence")

        } catch (e: Exception) {
            Log.e(TAG, "Error processing recurring transaction ${recurringTransaction.id}: ${e.message}", e)
        }
    }

    /**
     * Get all active recurring transactions across all users
     */
    private suspend fun getAllActiveRecurringTransactions(): List<RecurringTransaction> = suspendCoroutine { continuation ->
        recurringTransactionsRef.orderByChild("isActive").equalTo(true)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val recurringTransactions = mutableListOf<RecurringTransaction>()
                        snapshot.children.forEach { child ->
                            child.getValue(RecurringTransaction::class.java)?.let { recurringTransaction ->
                                recurringTransactions.add(recurringTransaction)
                            }
                        }
                        continuation.resume(recurringTransactions)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing recurring transactions: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}")
                    continuation.resume(emptyList())
                }
            })
    }

    /**
     * Get recurring transactions for a specific user
     */
    suspend fun getRecurringTransactionsForUser(userId: Int): List<RecurringTransaction> = suspendCoroutine { continuation ->
        recurringTransactionsRef.orderByChild("userId").equalTo(userId.toDouble())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val recurringTransactions = mutableListOf<RecurringTransaction>()
                        snapshot.children.forEach { child ->
                            child.getValue(RecurringTransaction::class.java)?.let { recurringTransaction ->
                                recurringTransactions.add(recurringTransaction)
                            }
                        }
                        continuation.resume(recurringTransactions)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user recurring transactions: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}")
                    continuation.resume(emptyList())
                }
            })
    }

    /**
     * Cancel a recurring transaction
     */
    suspend fun cancelRecurringTransaction(recurringTransactionId: String) {
        try {
            recurringTransactionsRef.child(recurringTransactionId).child("isActive").setValue(false).await()
            Log.d(TAG, "Recurring transaction $recurringTransactionId cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling recurring transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Calculate the next occurrence date (monthly)
     */
    private fun calculateNextOccurrence(currentDate: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = currentDate
        calendar.add(Calendar.MONTH, 1) // Add one month
        return calendar.time
    }

    /**
     * Check if there are any due recurring transactions for a user (for UI notifications)
     */
    suspend fun hasDueRecurringTransactions(userId: Int): Boolean {
        return try {
            val userRecurringTransactions = getRecurringTransactionsForUser(userId)
            val today = Calendar.getInstance().time

            userRecurringTransactions.any { recurringTransaction ->
                recurringTransaction.isActive && recurringTransaction.nextOccurrence <= today
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking due recurring transactions: ${e.message}", e)
            false
        }
    }
}