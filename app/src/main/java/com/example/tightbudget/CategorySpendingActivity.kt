package com.example.tightbudget

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tightbudget.adapters.CategorySpendingAdapter
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.databinding.ActivityCategorySpendingBinding
import com.example.tightbudget.models.CategorySpendingItem
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.ui.CategoryDetailBottomSheet
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CategorySpendingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategorySpendingBinding
    private lateinit var adapter: CategorySpendingAdapter
    private lateinit var db: AppDatabase

    private var userId: Int = -1
    private var categoryItems = mutableListOf<CategorySpendingItem>()
    private var transactions = listOf<Transaction>()

    // Date range for filtering
    private var startDate: Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }.time

    private var endDate: Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
    }.time

    // Filtering and sorting state
    private var currentPeriod = "This Month"
    private var currentSortOption = "Amount (High-Low)"

    private val TAG = "CategorySpendingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategorySpendingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize database
        db = AppDatabase.getDatabase(this)

        // Get current user ID
        userId = getCurrentUserId()

        // Set up UI components
        setupRecyclerView()
        setupFilterButtons()
        setupBackButton()
        setupBottomNavigation()

        // Load data
        loadCategoryData()
    }

    /**
     * Set up RecyclerView and adapter
     */
    private fun setupRecyclerView() {
        adapter = CategorySpendingAdapter(categoryItems) { category ->
            // Show category detail when clicked
            showCategoryDetail(category)
        }

        binding.categoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CategorySpendingActivity)
            adapter = this@CategorySpendingActivity.adapter
        }
    }

    /**
     * Set up filter buttons for period and sorting
     */
    private fun setupFilterButtons() {
        // Period filter
        binding.periodFilter.setOnClickListener {
            val popup = PopupMenu(this, binding.periodFilter)
            popup.menu.add("Today")
            popup.menu.add("This Week")
            popup.menu.add("This Month")
            popup.menu.add("Previous Month")
            popup.menu.add("This Year")
            popup.menu.add("Custom Range...")

            popup.setOnMenuItemClickListener { item ->
                currentPeriod = item.title.toString()
                binding.periodFilter.text = currentPeriod

                when (currentPeriod) {
                    "Today" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        endDate = calendar.time
                    }
                    "This Week" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.add(Calendar.DAY_OF_WEEK, 6)
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        endDate = calendar.time
                    }
                    "This Month" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        endDate = calendar.time
                    }
                    "Previous Month" -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, -1)
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        endDate = calendar.time
                    }
                    "This Year" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_YEAR, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.set(Calendar.MONTH, 11) // December
                        calendar.set(Calendar.DAY_OF_MONTH, 31)
                        calendar.set(Calendar.HOUR_OF_DAY, 23)
                        calendar.set(Calendar.MINUTE, 59)
                        calendar.set(Calendar.SECOND, 59)
                        endDate = calendar.time
                    }
                    "Custom Range..." -> {
                        showDateRangePicker()
                        return@setOnMenuItemClickListener true
                    }
                }

                // Reload data with new date range
                loadCategoryData()
                true
            }

            popup.show()
        }

        // Sort filter
        binding.sortFilter.setOnClickListener {
            val popup = PopupMenu(this, binding.sortFilter)
            popup.menu.add("Amount (High-Low)")
            popup.menu.add("Amount (Low-High)")
            popup.menu.add("Name (A-Z)")
            popup.menu.add("Budget Usage (%)")

            popup.setOnMenuItemClickListener { item ->
                currentSortOption = item.title.toString()
                binding.sortFilter.text = currentSortOption

                // Sort existing data without reloading
                sortAndUpdateCategories()
                true
            }

            popup.show()
        }
    }

    /**
     * Sort categories based on current sort option and update the UI
     */
    private fun sortAndUpdateCategories() {
        val sorted = when (currentSortOption) {
            "Amount (High-Low)" -> categoryItems.sortedByDescending { it.amount }
            "Amount (Low-High)" -> categoryItems.sortedBy { it.amount }
            "Name (A-Z)" -> categoryItems.sortedBy { it.name }
            "Budget Usage (%)" -> categoryItems.sortedByDescending {
                if (it.budget > 0) (it.amount / it.budget) * 100 else 0.0
            }
            else -> categoryItems.sortedByDescending { it.amount }
        }

        categoryItems.clear()
        categoryItems.addAll(sorted)
        adapter.updateCategories(categoryItems)
    }

    /**
     * Show date range picker dialog
     */
    private fun showDateRangePicker() {
        val startCalendar = Calendar.getInstance()
        startCalendar.time = startDate

        val endCalendar = Calendar.getInstance()
        endCalendar.time = endDate

        // First pick start date
        DatePickerDialog(
            this,
            { _, year, month, day ->
                startCalendar.set(Calendar.YEAR, year)
                startCalendar.set(Calendar.MONTH, month)
                startCalendar.set(Calendar.DAY_OF_MONTH, day)
                startCalendar.set(Calendar.HOUR_OF_DAY, 0)
                startCalendar.set(Calendar.MINUTE, 0)
                startCalendar.set(Calendar.SECOND, 0)

                startDate = startCalendar.time

                // Then pick end date
                DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDay ->
                        endCalendar.set(Calendar.YEAR, endYear)
                        endCalendar.set(Calendar.MONTH, endMonth)
                        endCalendar.set(Calendar.DAY_OF_MONTH, endDay)
                        endCalendar.set(Calendar.HOUR_OF_DAY, 23)
                        endCalendar.set(Calendar.MINUTE, 59)
                        endCalendar.set(Calendar.SECOND, 59)

                        endDate = endCalendar.time

                        // Format the date range for display
                        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                        currentPeriod = "${dateFormat.format(startDate)} - ${dateFormat.format(endDate)}"
                        binding.periodFilter.text = currentPeriod

                        // Reload data with new date range
                        loadCategoryData()
                    },
                    endCalendar.get(Calendar.YEAR),
                    endCalendar.get(Calendar.MONTH),
                    endCalendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            startCalendar.get(Calendar.YEAR),
            startCalendar.get(Calendar.MONTH),
            startCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Load category data for the selected period
     */
    private fun loadCategoryData() {
        // Show loading indicator
        binding.loadingIndicator.visibility = View.VISIBLE

        // Format date for display
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val periodDisplay = if (currentPeriod == "This Month" || currentPeriod == "Previous Month") {
            dateFormat.format(startDate)
        } else {
            currentPeriod
        }
        binding.periodSummary.text = periodDisplay

        lifecycleScope.launch {
            try {
                // Load transactions for the selected period
                val transactionDao = db.transactionDao()
                transactions = if (userId != -1) {
                    transactionDao.getTransactionsForPeriod(userId, startDate, endDate)
                        .filter { it.isExpense } // Only include expenses
                } else {
                    // Generate sample data for users who aren't logged in
                    generateSampleTransactions()
                }

                // Load categories to get emoji and color information
                val categoryDao = db.categoryDao()
                val categories = categoryDao.getAllCategories()

                // Calculate spending by category
                val categoryGroups = transactions.groupBy { it.category }

                // Load budget information
                val (activeBudgetGoal, categoryBudgets) = loadBudgetInfo()

                // Create CategorySpendingItems
                categoryItems.clear()
                var totalSpent = 0.0

                categoryGroups.forEach { (categoryName, categoryTransactions) ->
                    // Find the category for emoji and color
                    val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }

                    // Find budget for this category
                    val budgetAmount = categoryBudgets.find {
                        it.categoryName.equals(categoryName, ignoreCase = true)
                    }?.allocation ?: 0.0

                    // Calculate spending amount
                    val spendingAmount = categoryTransactions.sumOf { it.amount }
                    totalSpent += spendingAmount

                    // Create CategorySpendingItem
                    val item = CategorySpendingItem(
                        id = categoryName,
                        name = categoryName,
                        emoji = category?.emoji ?: EmojiUtils.getCategoryEmoji(categoryName),
                        color = category?.color ?: "#CCCCCC",
                        amount = spendingAmount,
                        budget = budgetAmount,
                        transactionCount = categoryTransactions.size
                    )

                    categoryItems.add(item)
                }

                // Handle categories with budget but no spending
                categoryBudgets.forEach { budget ->
                    val exists = categoryItems.any {
                        it.name.equals(budget.categoryName, ignoreCase = true)
                    }

                    if (!exists) {
                        // Find the category for emoji and color
                        val category = categories.find {
                            it.name.equals(budget.categoryName, ignoreCase = true)
                        }

                        // Create CategorySpendingItem with zero spending
                        val item = CategorySpendingItem(
                            id = budget.categoryName,
                            name = budget.categoryName,
                            emoji = category?.emoji ?: EmojiUtils.getCategoryEmoji(budget.categoryName),
                            color = category?.color ?: "#CCCCCC",
                            amount = 0.0,
                            budget = budget.allocation,
                            transactionCount = 0
                        )

                        categoryItems.add(item)
                    }
                }

                // Sort by current sort option
                sortAndUpdateCategories()

                // Update UI
                runOnUiThread {
                    // Update summary text
                    binding.totalSpentText.text = "Total: R${String.format("%,.2f", totalSpent)}"
                    binding.categoryCount.text = "${categoryItems.size} categories"

                    // Update adapter
                    adapter.updateCategories(categoryItems)

                    // Hide loading indicator
                    binding.loadingIndicator.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading category data: ${e.message}", e)

                runOnUiThread {
                    // Hide loading indicator
                    binding.loadingIndicator.visibility = View.GONE

                    // Show error message
                    Toast.makeText(
                        this@CategorySpendingActivity,
                        "Error loading category data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Load active budget goal and category budgets
     */
    private suspend fun loadBudgetInfo(): Pair<BudgetGoal?, List<CategoryBudget>> {
        if (userId == -1) {
            return Pair(null, emptyList())
        }

        try {
            val budgetGoalDao = db.budgetGoalDao()

            // Get the month and year from the start date
            val calendar = Calendar.getInstance()
            calendar.time = startDate
            val month = calendar.get(Calendar.MONTH) + 1 // 0-based to 1-based
            val year = calendar.get(Calendar.YEAR)

            // Try to get budget for the period month/year
            var budgetGoal = budgetGoalDao.getBudgetGoalForMonth(userId, month, year)

            // If not found, try to get the active budget
            if (budgetGoal == null) {
                budgetGoal = budgetGoalDao.getActiveBudgetGoal(userId)
            }

            // Get category budgets if budget goal exists
            val categoryBudgets = if (budgetGoal != null) {
                db.categoryBudgetDao().getCategoryBudgetsForGoal(budgetGoal.id)
            } else {
                emptyList()
            }

            return Pair(budgetGoal, categoryBudgets)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading budget info: ${e.message}", e)
            return Pair(null, emptyList())
        }
    }

    /**
     * Generate sample transactions for users who aren't logged in
     */
    private fun generateSampleTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()

        // Create sample transactions
        return listOf(
            Transaction(
                id = 1,
                userId = -1,
                merchant = "Checkers",
                category = "Food",
                amount = 450.75,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -2) }.time,
                isExpense = true,
                description = "Weekly grocery shopping"
            ),
            Transaction(
                id = 2,
                userId = -1,
                merchant = "Engen",
                category = "Transport",
                amount = 350.50,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -5) }.time,
                isExpense = true,
                description = "Fuel"
            ),
            Transaction(
                id = 3,
                userId = -1,
                merchant = "Netflix",
                category = "Entertainment",
                amount = 159.00,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -10) }.time,
                isExpense = true,
                description = "Monthly subscription"
            ),
            Transaction(
                id = 4,
                userId = -1,
                merchant = "Vodacom",
                category = "Utilities",
                amount = 599.00,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -8) }.time,
                isExpense = true,
                description = "Mobile plan"
            ),
            Transaction(
                id = 5,
                userId = -1,
                merchant = "CoCT",
                category = "Housing",
                amount = 1200.00,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -15) }.time,
                isExpense = true,
                description = "Utilities"
            )
        )
    }

    /**
     * Show category detail bottom sheet with better error handling
     */
    private fun showCategoryDetail(category: CategorySpendingItem) {
        try {
            // Log action for debugging
            Log.d(TAG, "Showing category detail for: ${category.name}")

            // Filter transactions for this category
            val categoryTransactions = transactions.filter {
                it.category.equals(category.name, ignoreCase = true)
            }

            // Log transaction count
            Log.d(TAG, "Found ${categoryTransactions.size} transactions for category ${category.name}")

            // Create bottom sheet fragment
            val bottomSheet = CategoryDetailBottomSheet.newInstance(
                category = category,
                transactions = categoryTransactions,
                startDate = startDate,
                endDate = endDate
            )

            // Show bottom sheet with error handling
            try {
                bottomSheet.show(supportFragmentManager, "CategoryDetail")
            } catch (e: Exception) {
                Log.e(TAG, "Error showing bottom sheet: ${e.message}", e)
                Toast.makeText(
                    this,
                    "Error showing category details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            // Log and display any errors
            Log.e(TAG, "Error preparing category detail: ${e.message}", e)
            Toast.makeText(
                this,
                "Error showing category details: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Set up back button
     */
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            finish()
        }
    }

    /**
     * Get current user ID from shared preferences
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    /**
     * Set up bottom navigation
     */
    private fun setupBottomNavigation() {
        val bottomNavBar = binding.bottomNavBar
        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_reports -> {
                    startActivity(Intent(this, StatisticsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_add_transaction -> {
                    startActivity(Intent(this, AddTransactionActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_wallet -> {
                    startActivity(Intent(this, TransactionsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }
}