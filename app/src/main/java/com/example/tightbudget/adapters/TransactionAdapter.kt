package com.example.tightbudget.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.utils.EmojiUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying a list of transactions (expenses or income)
 * in the Transactions screen's RecyclerView.
 */
class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

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

        // Set an emoji icon (simple fallback for now)
        holder.icon.text = getCategoryEmoji(transaction.category)

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

    fun updateList(newList: List<Transaction>) {
        transactions = newList
        notifyDataSetChanged()
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
     * Gets the emoji for a category using the central EmojiUtils
     */
    private fun getCategoryEmoji(category: String): String {
        return EmojiUtils.getCategoryEmoji(category)
    }
}