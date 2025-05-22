package com.example.tightbudget.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.models.CategorySpendingItem
import com.example.tightbudget.utils.ProgressBarUtils

/**
 * Adapter for displaying category spending items in the category spending screen.
 */
class CategorySpendingAdapter(
    private var categories: List<CategorySpendingItem>,
    private val onCategoryClicked: (CategorySpendingItem) -> Unit
) : RecyclerView.Adapter<CategorySpendingAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_spending, parent, false)
        return CategoryViewHolder(view)
    }

    override fun getItemCount(): Int = categories.size

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    fun updateCategories(newCategories: List<CategorySpendingItem>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }

    /**
     * ViewHolder class that holds references to the views in each category spending item layout.
     */
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val emoji: TextView = itemView.findViewById(R.id.categoryEmoji)
        private val name: TextView = itemView.findViewById(R.id.categoryName)
        private val progressText: TextView = itemView.findViewById(R.id.progressText)
        private val amount: TextView = itemView.findViewById(R.id.categoryAmount)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.categoryProgressBar)
        private val transactionCount: TextView = itemView.findViewById(R.id.transactionCountText)
        private val viewDetails: TextView = itemView.findViewById(R.id.viewDetailsText)

        init {
            // Set click listener for the whole item
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCategoryClicked(categories[position])
                }
            }

            // Set click listener for the "View Details" text
            viewDetails.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCategoryClicked(categories[position])
                }
            }
        }

        fun bind(category: CategorySpendingItem) {
            // Set the emoji and name
            emoji.text = category.emoji
            name.text = category.name

            // Set the amount
            amount.text = "R${String.format("%,.2f", category.amount)}"

            // Calculate progress percentage
            val progressPercentage = if (category.budget > 0) {
                (category.amount / category.budget) * 100
            } else {
                0.0
            }

            // Set progress text
            progressText.text = "R${String.format("%,.2f", category.amount)} / R${
                String.format(
                    "%,.2f",
                    category.budget
                )
            } (${progressPercentage.toInt()}%)"

            // Set progress bar
            progressBar.max = 100
            progressBar.progress = progressPercentage.toInt().coerceIn(0, 100)

            // Apply color to progress bar based on spending vs budget
            ProgressBarUtils.applyBudgetStatusProgressBar(
                progressBar,
                itemView.context,
                category.amount.toFloat(),
                category.budget.toFloat()
            )

            // Set transaction count text
            val transactionText = if (category.transactionCount == 1) {
                "1 transaction"
            } else {
                "${category.transactionCount} transactions"
            }
            transactionCount.text = transactionText
        }
    }
}