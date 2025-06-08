package com.example.tightbudget.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.models.Category
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.utils.EmojiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.find

/**
 * Adapter for displaying a list of transactions (expenses or income)
 * in the Transactions screen's RecyclerView.
 * Now supports real category data with emojis instead of hardcoded emojis.
 */
class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    // Store loaded categories for emoji lookup
    private var loadedCategories: List<Category> = emptyList()

    /**
     * ViewHolder class that holds references to the views in each transaction item layout.
     */
    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: TextView = itemView.findViewById(R.id.transactionIcon)
        val merchant: TextView = itemView.findViewById(R.id.merchantName)
        val details: TextView = itemView.findViewById(R.id.transactionDetails)
        val amount: TextView = itemView.findViewById(R.id.transactionAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.transaction_item, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]

        // Set merchant name and amount
        holder.merchant.text = transaction.merchant
        holder.amount.text = formatAmount(transaction.amount, transaction.isExpense)

        // Set transaction details line (category and date)
        holder.details.text = "${transaction.category} • ${formatDate(transaction.date)}"

        // Set emoji using real category data or fallback to EmojiUtils
        holder.icon.text = getCategoryEmojiFromData(transaction.category)

        // Colour-code the amount: red for expense, green for income
        val context = holder.itemView.context
        val colour = if (transaction.isExpense)
            ContextCompat.getColor(context, R.color.red_light)
        else
            ContextCompat.getColor(context, R.color.green_light)
        holder.amount.setTextColor(colour)

        // Handle item clicks
        holder.itemView.setOnClickListener {
            onItemClick(transaction)
        }
    }

    override fun getItemCount(): Int = transactions.size

    /**
     * Update the transaction list
     */
    fun updateList(newList: List<Transaction>) {
        transactions = newList
        notifyDataSetChanged()
    }

    /**
     * Update the categories data for emoji lookup
     * Call this method to provide real category data with emojis
     */
    fun updateCategories(categories: List<Category>) {
        loadedCategories = categories
        notifyDataSetChanged() // Refresh to show updated emojis
    }

    /**
     * Returns a formatted currency string (e.g., -R342.50 or +R1200.00)
     */
    private fun formatAmount(amount: Double, isExpense: Boolean): String {
        val sign = if (isExpense) "-" else "+"
        return "${sign}R${"%,.2f".format(amount)}"
    }

    /**
     * Converts a Date object into a readable string format
     */
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * Gets the emoji for a category using real data or EmojiUtils fallback
     */
    private fun getCategoryEmojiFromData(categoryName: String): String {
        // First try to find the category in loaded data
        val category = loadedCategories.find { it.name.equals(categoryName, ignoreCase = true) }
        return if (category != null && category.emoji.isNotBlank()) {
            category.emoji // Use real stored emoji
        } else {
            EmojiUtils.getCategoryEmoji(categoryName) // Fallback to hardcoded
        }
    }

    /**
     * DEPRECATED: Old method - kept for backwards compatibility
     * Use getCategoryEmojiFromData instead
     */
    private fun getCategoryEmoji(category: String): String {
        return getCategoryEmojiFromData(category)
    }
}