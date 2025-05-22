package com.example.tightbudget.data

import androidx.room.*
import com.example.tightbudget.models.Transaction
import java.util.Date

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    suspend fun getAllTransactionsForUser(userId: Int): List<Transaction>

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getTransactionsForPeriod(userId: Int, startDate: Date, endDate: Date): List<Transaction>

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND isExpense = 1 AND category = :category AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalExpenseForCategory(userId: Int, category: String, startDate: Date, endDate: Date): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND isExpense = 1 AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalExpensesForPeriod(userId: Int, startDate: Date, endDate: Date): Double?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND category = :category ORDER BY date DESC")
    suspend fun getTransactionsForCategory(userId: Int, category: String): List<Transaction>
}