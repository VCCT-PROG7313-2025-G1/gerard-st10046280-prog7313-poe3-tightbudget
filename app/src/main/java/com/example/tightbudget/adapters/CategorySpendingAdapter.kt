import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
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

    private var totalSpending: Double = 0.0

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
        // Calculate total spending for percentage calculations
        this.totalSpending = newCategories.sumOf { it.amount }
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

        // New UI elements
        private val percentageText: TextView? = itemView.findViewById(R.id.percentageText)
        private val budgetStatusIcon: TextView? = itemView.findViewById(R.id.budgetStatusIcon)
        private val budgetWarningStrip: LinearLayout? = itemView.findViewById(R.id.budgetWarningStrip)
        private val budgetWarningText: TextView? = itemView.findViewById(R.id.budgetWarningText)

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

            // Set percentage of total spending
            val totalPercentage = if (totalSpending > 0) {
                (category.amount / totalSpending) * 100
            } else {
                0.0
            }
            percentageText?.text = "${totalPercentage.toInt()}% of total"

            // Update budget status icon
            updateBudgetStatusIcon(progressPercentage.toInt())

            // Update warning strip for over-budget categories
            updateWarningStrip(progressPercentage, category.amount, category.budget)

            // Set transaction count text
            val transactionText = if (category.transactionCount == 1) {
                "1 transaction"
            } else {
                "${category.transactionCount} transactions"
            }
            transactionCount.text = transactionText
        }

        /**
         * Updates the budget status icon based on spending percentage
         */
        private fun updateBudgetStatusIcon(percentage: Int) {
            budgetStatusIcon?.apply {
                when {
                    percentage < 50 -> {
                        text = "✅"
                        visibility = View.VISIBLE
                    }
                    percentage < 80 -> {
                        text = "⚠️"
                        visibility = View.VISIBLE
                    }
                    percentage >= 100 -> {
                        text = "🚨"
                        visibility = View.VISIBLE
                    }
                    else -> {
                        text = "⚠️"
                        visibility = View.VISIBLE
                    }
                }
            }

            // Also update the amount text color based on budget status
            updateAmountTextColor(percentage)
        }

        /**
         * Updates the amount text color based on spending percentage
         */
        private fun updateAmountTextColor(percentage: Int) {
            val color = when {
                percentage < 50 -> {
                    // Good spending - keep purple or use teal
                    ContextCompat.getColor(itemView.context, R.color.teal_light)
                }
                percentage < 80 -> {
                    // Warning - orange
                    ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark)
                }
                else -> {
                    // Danger - red
                    ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                }
            }
            amount.setTextColor(color)
        }

        /**
         * Shows/hides warning strip for over-budget categories
         */
        private fun updateWarningStrip(percentage: Double, spent: Double, budget: Double) {
            if (percentage >= 100 && budget > 0) {
                val overAmount = spent - budget
                budgetWarningStrip?.visibility = View.VISIBLE
                budgetWarningText?.text = "Over budget by R${String.format("%.2f", overAmount)}"

                // Update warning strip background color based on how much over budget
                val overPercentage = ((spent - budget) / budget) * 100
                updateWarningStripColor(overPercentage)
            } else {
                budgetWarningStrip?.visibility = View.GONE
            }
        }

        /**
         * Updates warning strip background color based on overage severity
         */
        private fun updateWarningStripColor(overPercentage: Double) {
            budgetWarningStrip?.apply {
                when {
                    overPercentage < 10 -> {
                        // Slightly over budget - light orange
                        setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
                    }
                    overPercentage < 25 -> {
                        // Moderately over budget - orange
                        setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                    }
                    else -> {
                        // Severely over budget - red
                        setBackgroundColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
                    }
                }
            }
        }
    }
}