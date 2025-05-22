package com.example.tightbudget.adapters

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.models.CategoryItem

/**
 * Adapter to display a grid of category items in the category picker bottom sheet.
 */
class CategoryAdapter(
    private var categories: List<CategoryItem>,
    private val onCategorySelected: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_row, parent, false)
        return CategoryViewHolder(view)
    }

    override fun getItemCount(): Int = categories.size

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    fun updateCategories(newCategories: List<CategoryItem>) {
        this.categories = newCategories
        notifyDataSetChanged()
    }

    /**
     * ViewHolder class that holds references to the views in each category item layout.
     */
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: CardView = itemView.findViewById(R.id.categoryCard)
        private val emoji: TextView = itemView.findViewById(R.id.categoryEmoji)
        private val name: TextView = itemView.findViewById(R.id.categoryName)
        private val budget: TextView = itemView.findViewById(R.id.categoryBudget)

        fun bind(category: CategoryItem) {
            emoji.text = category.emoji
            name.text = category.name
            budget.text = "R${"%,.2f".format(category.budget)}"

            try {
                // Make sure color starts with #
                val colorStr = if (category.color.startsWith("#")) category.color else "#${category.color}"
                val backgroundColor = Color.parseColor(colorStr)
                card.setCardBackgroundColor(backgroundColor)

                // Set text color based on background brightness
                val isColorDark = isDarkColor(backgroundColor)
                val textColor = if (isColorDark) Color.WHITE else Color.BLACK

                // Apply the text color to both name and budget
                name.setTextColor(textColor)
                budget.setTextColor(if (isColorDark) Color.parseColor("#DDDDDD") else Color.parseColor("#666666"))
            } catch (e: IllegalArgumentException) {
                Log.e("CategoryAdapter", "Error parsing color: ${category.color}", e)
                card.setCardBackgroundColor(Color.LTGRAY)
                name.setTextColor(Color.BLACK)
                budget.setTextColor(Color.DKGRAY)
            }

            itemView.setOnClickListener {
                onCategorySelected(category)
            }
        }

        // This function determines if a color is dark or light based on its RGB values.
        private fun isDarkColor(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.5
        }
    }
}
