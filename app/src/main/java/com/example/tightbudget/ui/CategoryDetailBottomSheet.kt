package com.example.tightbudget.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.R
import com.example.tightbudget.TransactionsActivity
import com.example.tightbudget.adapters.TransactionAdapter
import com.example.tightbudget.models.CategorySpendingItem
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.utils.ProgressBarUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.Serializable
import java.util.*

/**
 * BottomSheetDialogFragment that displays detailed information about a category's spending
 * and all transactions within that category.
 */
class CategoryDetailBottomSheet : BottomSheetDialogFragment() {

    private val TAG = "CategoryDetailSheet"

    // Data variables
    private var categoryName: String = ""
    private var categoryEmoji: String = ""
    private var categoryAmount: Double = 0.0
    private var categoryBudget: Double = 0.0
    private var transactions: List<Transaction> = listOf()
    private var startDate: Long = 0L
    private var endDate: Long = 0L

    // UI Components
    private lateinit var categoryEmojiText: TextView
    private lateinit var categoryNameText: TextView
    private lateinit var totalAmountText: TextView
    private lateinit var budgetAmountText: TextView
    private lateinit var remainingAmountText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var transactionsRecyclerView: RecyclerView
    private lateinit var emptyStateText: TextView
    private lateinit var viewAllButton: Button
    private lateinit var closeButton: ImageView

    companion object {
        // Static method to create a new instance with all required data
        fun newInstance(
            category: CategorySpendingItem,
            transactions: List<Transaction>,
            startDate: Date,
            endDate: Date
        ): CategoryDetailBottomSheet {
            val fragment = CategoryDetailBottomSheet()
            val args = Bundle()

            // Store simple data as primitives or strings - avoid serialization issues
            args.putString("category_name", category.name)
            args.putString("category_emoji", category.emoji)
            args.putDouble("category_amount", category.amount)
            args.putDouble("category_budget", category.budget)
            args.putLong("start_date", startDate.time)
            args.putLong("end_date", endDate.time)

            // Serialize the transactions list to pass it as a Serializable instead of Parcelable
            fragment.transactions = transactions

            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_category_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            // Find views
            categoryEmojiText = view.findViewById(R.id.categoryEmojiLarge)
            categoryNameText = view.findViewById(R.id.categoryNameLarge)
            totalAmountText = view.findViewById(R.id.detailTotalAmount)
            budgetAmountText = view.findViewById(R.id.detailBudgetAmount)
            remainingAmountText = view.findViewById(R.id.detailRemainingAmount)
            progressBar = view.findViewById(R.id.detailProgressBar)
            transactionsRecyclerView = view.findViewById(R.id.detailTransactionsRecyclerView)
            emptyStateText = view.findViewById(R.id.emptyStateText)
            viewAllButton = view.findViewById(R.id.viewAllTransactionsButton)
            closeButton = view.findViewById(R.id.closeButton)

            // Set up RecyclerView
            transactionsRecyclerView.layoutManager = LinearLayoutManager(context)

            // Set up close button
            closeButton.setOnClickListener {
                dismiss()
            }

            // Get data from arguments
            setupFromArguments()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onViewCreated: ${e.message}", e)
        }
    }

    private fun setupFromArguments() {
        try {
            // Get data from arguments
            arguments?.let { args ->
                categoryName = args.getString("category_name", "")
                categoryEmoji = args.getString("category_emoji", "")
                categoryAmount = args.getDouble("category_amount", 0.0)
                categoryBudget = args.getDouble("category_budget", 0.0)
                startDate = args.getLong("start_date", 0L)
                endDate = args.getLong("end_date", 0L)

                // Now initialize UI with the data
                initializeWithCategory()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupFromArguments: ${e.message}", e)
        }
    }

    private fun initializeWithCategory() {
        try {
            // Set category info
            categoryEmojiText.text = categoryEmoji
            categoryNameText.text = categoryName

            // Set amounts
            totalAmountText.text = "R${String.format("%,.2f", categoryAmount)}"
            budgetAmountText.text = "R${String.format("%,.2f", categoryBudget)}"

            // Calculate remaining amount
            val remaining = categoryBudget - categoryAmount
            remainingAmountText.text = "R${String.format("%,.2f", remaining)}"

            // Set text color based on remaining amount
            if (remaining < 0) {
                remainingAmountText.setTextColor(requireContext().getColor(R.color.red_light))
            } else {
                remainingAmountText.setTextColor(requireContext().getColor(R.color.green_light))
            }

            // Set progress bar
            val progressPercentage = if (categoryBudget > 0) {
                (categoryAmount / categoryBudget) * 100
            } else {
                0.0
            }

            progressBar.max = 100
            progressBar.progress = progressPercentage.toInt().coerceIn(0, 100)

            // Apply color to progress bar based on spending vs budget
            ProgressBarUtils.applyBudgetStatusProgressBar(
                progressBar,
                requireContext(),
                categoryAmount.toFloat(),
                categoryBudget.toFloat()
            )

            // Filter for transactions in this category
            val categoryTransactions = transactions.filter {
                it.category.equals(categoryName, ignoreCase = true)
            }

            // Set up transactions
            if (categoryTransactions.isEmpty()) {
                transactionsRecyclerView.visibility = View.GONE
                emptyStateText.visibility = View.VISIBLE
            } else {
                transactionsRecyclerView.visibility = View.VISIBLE
                emptyStateText.visibility = View.GONE

                // Set up transaction adapter
                val transactionAdapter = TransactionAdapter(categoryTransactions) { transaction ->
                    // Show transaction detail when clicked
                    val detailSheet = TransactionDetailBottomSheet.newInstance(transaction)
                    detailSheet.show(parentFragmentManager, "TransactionDetail")
                }

                transactionsRecyclerView.adapter = transactionAdapter
            }

            // Set up view all button
            viewAllButton.setOnClickListener {
                try {
                    val intent = Intent(requireContext(), TransactionsActivity::class.java).apply {
                        putExtra("FILTER_CATEGORY", categoryName)
                        putExtra("START_DATE", startDate)
                        putExtra("END_DATE", endDate)
                    }
                    startActivity(intent)
                    dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in viewAllButton click: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in initializeWithCategory: ${e.message}", e)
        }
    }
}