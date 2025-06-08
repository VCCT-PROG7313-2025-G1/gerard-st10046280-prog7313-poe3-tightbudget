package com.example.tightbudget.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.tightbudget.firebase.RecurringTransactionManager
import java.util.*

/**
 * Utility class to process recurring transactions in the background
 * This should be called when the app starts or when the user opens key activities
 */
object RecurringTransactionProcessor {
    private const val TAG = "RecurringTransactionProcessor"
    private const val PREF_NAME = "recurring_transaction_prefs"
    private const val LAST_PROCESSED_KEY = "last_processed_date"

    /**
     * Process recurring transactions if they haven't been processed today
     */
    fun processIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val today = Calendar.getInstance()
        val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.DAY_OF_YEAR)}"
        val lastProcessed = prefs.getString(LAST_PROCESSED_KEY, "")

        if (lastProcessed != todayString) {
            Log.d(TAG, "Processing recurring transactions for today: $todayString")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val recurringManager = RecurringTransactionManager.getInstance()
                    recurringManager.processDueRecurringTransactions()

                    // Mark as processed for today
                    prefs.edit().putString(LAST_PROCESSED_KEY, todayString).apply()
                    Log.d(TAG, "Recurring transactions processed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing recurring transactions: ${e.message}", e)
                }
            }
        } else {
            Log.d(TAG, "Recurring transactions already processed today")
        }
    }

    /**
     * Force process recurring transactions (useful for testing)
     */
    fun forceProcess(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val recurringManager = RecurringTransactionManager.getInstance()
                recurringManager.processDueRecurringTransactions()

                // Update last processed date
                val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                val today = Calendar.getInstance()
                val todayString = "${today.get(Calendar.YEAR)}-${today.get(Calendar.DAY_OF_YEAR)}"
                prefs.edit().putString(LAST_PROCESSED_KEY, todayString).apply()

                Log.d(TAG, "Force processed recurring transactions")
            } catch (e: Exception) {
                Log.e(TAG, "Error force processing recurring transactions: ${e.message}", e)
            }
        }
    }
}