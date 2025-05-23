package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.example.tightbudget.models.Transaction
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase Transaction Manager handles transaction storage in Firebase Realtime Database
 * Stores all user transactions online for Part 3 of the POE
 */
class FirebaseTransactionManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val transactionsRef: DatabaseReference = database.getReference("transactions")
    private val transactionCounterRef: DatabaseReference = database.getReference("transactionCounter")

    companion object {
        private const val TAG = "FirebaseTransactionManager"

        @Volatile
        private var INSTANCE: FirebaseTransactionManager? = null

        fun getInstance(): FirebaseTransactionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseTransactionManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates a new transaction in Firebase with incremental ID
     * @param transaction The transaction object to create
     * @return The created transaction with incremental ID
     */
    suspend fun createTransaction(transaction: Transaction): Transaction {
        return try {
            // Get next incremental ID
            val nextId = getNextTransactionId()

            // Generate a new key for the transaction
            val transactionKey = transactionsRef.push().key
                ?: throw Exception("Failed to generate transaction key")

            // Create transaction with incremental ID
            val firebaseTransaction = transaction.copy(id = nextId)

            // Store transaction in Firebase using the Firebase key
            transactionsRef.child(transactionKey).setValue(firebaseTransaction).await()

            Log.d(TAG, "Transaction created successfully with ID: $nextId")
            firebaseTransaction

        } catch (e: Exception) {
            Log.e(TAG, "Error creating transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get the next incremental transaction ID
     */
    private suspend fun getNextTransactionId(): Int {
        return suspendCoroutine { continuation ->
            transactionCounterRef.get().addOnSuccessListener { snapshot ->
                val currentCounter = snapshot.getValue(Int::class.java) ?: 0
                val nextId = currentCounter + 1

                // Update the counter in Firebase
                transactionCounterRef.setValue(nextId).addOnSuccessListener {
                    Log.d(TAG, "Transaction counter updated to: $nextId")
                    continuation.resume(nextId)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating transaction counter: ${exception.message}")
                    // Fallback to timestamp if counter update fails
                    continuation.resume(System.currentTimeMillis().toInt())
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting transaction counter: ${exception.message}")
                // Fallback to timestamp if counter fails
                continuation.resume(System.currentTimeMillis().toInt())
            }
        }
    }

    /**
     * Gets all transactions for a specific user
     * @param userId The user ID to get transactions for
     * @return List of transactions for the user
     */
    suspend fun getAllTransactionsForUser(userId: Int): List<Transaction> {
        return suspendCoroutine { continuation ->
            transactionsRef.orderByChild("userId").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val transactions = mutableListOf<Transaction>()
                            for (transactionSnapshot in snapshot.children) {
                                val transaction = transactionSnapshot.getValue(Transaction::class.java)
                                transaction?.let { transactions.add(it) }
                            }
                            // Sort by date (newest first)
                            transactions.sortByDescending { it.date }
                            continuation.resume(transactions)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing transactions: ${e.message}", e)
                            continuation.resume(emptyList())
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        continuation.resumeWithException(Exception(error.message))
                    }
                })
        }
    }

    /**
     * Gets transactions for a user within a specific date range
     * @param userId The user ID
     * @param startDate Start date of the range
     * @param endDate End date of the range
     * @return List of transactions within the date range
     */
    suspend fun getTransactionsForPeriod(userId: Int, startDate: Date, endDate: Date): List<Transaction> {
        return try {
            // Get all transactions for user first
            val allTransactions = getAllTransactionsForUser(userId)

            // Filter by date range
            val filteredTransactions = allTransactions.filter { transaction ->
                transaction.date >= startDate && transaction.date <= endDate
            }

            Log.d(TAG, "Found ${filteredTransactions.size} transactions for user $userId in period")
            filteredTransactions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions for period: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Gets total expenses for a category within a date range
     * @param userId The user ID
     * @param category The category name
     * @param startDate Start date
     * @param endDate End date
     * @return Total amount spent in the category
     */
    suspend fun getTotalExpenseForCategory(
        userId: Int,
        category: String,
        startDate: Date,
        endDate: Date
    ): Double {
        return try {
            val transactions = getTransactionsForPeriod(userId, startDate, endDate)
            val categoryExpenses = transactions.filter {
                it.isExpense && it.category.equals(category, ignoreCase = true)
            }

            val total = categoryExpenses.sumOf { it.amount }
            Log.d(TAG, "Total expenses for $category: $total")
            total

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating category expenses: ${e.message}", e)
            0.0
        }
    }

    /**
     * Gets total expenses for a user within a date range
     * @param userId The user ID
     * @param startDate Start date
     * @param endDate End date
     * @return Total expenses amount
     */
    suspend fun getTotalExpensesForPeriod(userId: Int, startDate: Date, endDate: Date): Double {
        return try {
            val transactions = getTransactionsForPeriod(userId, startDate, endDate)
            val expenses = transactions.filter { it.isExpense }

            val total = expenses.sumOf { it.amount }
            Log.d(TAG, "Total expenses for period: $total")
            total

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating total expenses: ${e.message}", e)
            0.0
        }
    }

    /**
     * Gets all transactions for a specific category
     * @param userId The user ID
     * @param category The category name
     * @return List of transactions in the category
     */
    suspend fun getTransactionsForCategory(userId: Int, category: String): List<Transaction> {
        return try {
            val allTransactions = getAllTransactionsForUser(userId)
            val categoryTransactions = allTransactions.filter {
                it.category.equals(category, ignoreCase = true)
            }

            Log.d(TAG, "Found ${categoryTransactions.size} transactions for category $category")
            categoryTransactions

        } catch (e: Exception) {
            Log.e(TAG, "Error getting transactions for category: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Updates an existing transaction
     * @param transaction The transaction with updated information
     */
    suspend fun updateTransaction(transaction: Transaction) {
        try {
            val transactionKey = findTransactionKeyById(transaction.id)
            if (transactionKey != null) {
                transactionsRef.child(transactionKey).setValue(transaction).await()
                Log.d(TAG, "Transaction updated successfully")
            } else {
                throw Exception("Transaction not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a transaction from Firebase
     * @param transaction The transaction to delete
     */
    suspend fun deleteTransaction(transaction: Transaction) {
        try {
            val transactionKey = findTransactionKeyById(transaction.id)
            if (transactionKey != null) {
                transactionsRef.child(transactionKey).removeValue().await()
                Log.d(TAG, "Transaction deleted successfully")
            } else {
                throw Exception("Transaction not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting transaction: ${e.message}", e)
            throw e
        }
    }

    /**
     * Helper function to find a transaction's Firebase key by its ID
     * @param transactionId The transaction ID to search for
     * @return Firebase key if found, null otherwise
     */
    private suspend fun findTransactionKeyById(transactionId: Int): String? {
        return suspendCoroutine { continuation ->
            transactionsRef.orderByChild("id").equalTo(transactionId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val transactionKey = snapshot.children.first().key
                            continuation.resume(transactionKey)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        continuation.resumeWithException(Exception(error.message))
                    }
                })
        }
    }

    /**
     * Gets a transaction by its ID
     * @param transactionId The transaction ID
     * @return Transaction if found, null otherwise
     */
    suspend fun getTransactionById(transactionId: Int): Transaction? {
        return suspendCoroutine { continuation ->
            transactionsRef.orderByChild("id").equalTo(transactionId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                val transactionSnapshot = snapshot.children.first()
                                val transaction = transactionSnapshot.getValue(Transaction::class.java)
                                continuation.resume(transaction)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing transaction data: ${e.message}", e)
                            continuation.resume(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        continuation.resumeWithException(Exception(error.message))
                    }
                })
        }
    }

    /**
     * Gets recent transactions for a user (for dashboard)
     * @param userId The user ID
     * @param limit Maximum number of transactions to return
     * @return List of recent transactions
     */
    suspend fun getRecentTransactions(userId: Int, limit: Int = 5): List<Transaction> {
        return try {
            val allTransactions = getAllTransactionsForUser(userId)
            allTransactions.take(limit) // Already sorted by date (newest first)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recent transactions: ${e.message}", e)
            emptyList()
        }
    }
}