package com.example.tightbudget

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.databinding.ActivityBudgetGoalsBinding
import com.example.tightbudget.databinding.ItemBudgetCategoryBinding
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.CategoryItem
import com.example.tightbudget.ui.CategoryBudgetItem
import com.example.tightbudget.ui.CategoryPickerBottomSheet
import com.example.tightbudget.ui.CreateCategoryBottomSheet
import com.example.tightbudget.utils.CategoryAllocationManager
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * BudgetGoalsActivity handles the screen where users set their total monthly budget
 * and allocate amounts to different spending categories.
 */
class BudgetGoalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBudgetGoalsBinding
    private var currentBudget = 5000.0  // Default starting budget
    private var minimumSpendingGoal = 0.0 // Default minimum spending goal
    private val budgetIncrement = 500.0
    private val categoryItems = mutableListOf<CategoryBudgetItem>()
    private var currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1-12
    private var currentYear = Calendar.getInstance().get(Calendar.YEAR)
    private var existingBudgetGoalId = 0 // For updating existing goals
    private val TAG = "BudgetGoalsActivity"
    private var totalAllocated = 0.0
    private lateinit var categoryManager: CategoryAllocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initilise the category manager
        val db = AppDatabase.getDatabase(this)
        categoryManager = CategoryAllocationManager(db)

        setupUI()
        loadCurrentUserBudget() // Load the current user's budget goal from the database
    }

    /**
     * Retrieve the current user ID from shared preferences.
     * This is used to link the budget goal to the specific user.
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    /**
     * Load the current user's budget goal from the database.
     */
    private fun loadCurrentUserBudget() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to set budget goals", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@BudgetGoalsActivity)
                val budgetGoalDao = db.budgetGoalDao()
                val categoryDao = db.categoryDao()

                // Show loading indicator
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.VISIBLE
                }

                // Try to get budget for current month first
                var budgetGoal =
                    budgetGoalDao.getBudgetGoalForMonth(userId, currentMonth, currentYear)

                // If no budget for current month, get the most recent active budget
                if (budgetGoal == null) {
                    budgetGoal = budgetGoalDao.getActiveBudgetGoal(userId)
                }

                if (budgetGoal != null) {
                    // We found an existing budget
                    existingBudgetGoalId = budgetGoal.id
                    currentBudget = budgetGoal.totalBudget
                    minimumSpendingGoal = budgetGoal.minimumSpendingGoal

                    // Load category allocations using the manager
                    val loadedItems = categoryManager.loadCategoryAllocations(budgetGoal.id)

                    // Update our list
                    categoryItems.clear()
                    categoryItems.addAll(loadedItems)

                    // Calculate total allocated
                    totalAllocated = categoryItems.sumOf { it.allocation }

                    // Update UI
                    runOnUiThread {
                        updateDisplayedBudget()
                        updateCategoryList()
                        updateMonth()
                        binding.loadingIndicator.visibility = android.view.View.GONE
                    }
                } else {
                    // No existing budget, create default categories and allocations
                    val defaultItems = categoryManager.createDefaultAllocations(currentBudget)

                    categoryItems.clear()
                    categoryItems.addAll(defaultItems)

                    // Calculate total allocated
                    totalAllocated = categoryItems.sumOf { it.allocation }

                    // Update UI
                    runOnUiThread {
                        updateDisplayedBudget()
                        updateCategoryList()
                        updateMonth()
                        binding.loadingIndicator.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading budget: ${e.message}", e)
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.GONE
                    Toast.makeText(
                        this@BudgetGoalsActivity,
                        "Error loading budget: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Update the month display in the UI.
     */
    private fun updateMonth() {
        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, currentMonth - 1) // 0-based month in Calendar
        calendar.set(Calendar.YEAR, currentYear)

        binding.monthText.text = monthFormatter.format(calendar.time)
    }

    private fun updateCategoryList() {
        binding.categoryContainer.removeAllViews()

        for (item in categoryItems) {
            val categoryView = layoutInflater.inflate(
                R.layout.item_budget_category,
                binding.categoryContainer,
                false
            )

            // Find views
            val emoji = categoryView.findViewById<TextView>(R.id.categoryEmoji)
            val name = categoryView.findViewById<TextView>(R.id.categoryName)
            val average = categoryView.findViewById<TextView>(R.id.categoryAverage)
            val amountInput = categoryView.findViewById<EditText>(R.id.categoryAmountInput)
            val percentage = categoryView.findViewById<TextView>(R.id.categoryPercentage)

            // Set category info
            emoji.text = item.emoji
            name.text = item.categoryName

            // Set amount input
            amountInput.setText(String.format("%.2f", item.allocation))

            // Calculate and set percentage
            val percentValue =
                if (currentBudget > 0) (item.allocation / currentBudget) * 100 else 0.0
            percentage.text = "${percentValue.toInt()}%"

            // Add progress indicator for allocation
            val progressBar = categoryView.findViewById<SeekBar>(R.id.categoryAllocationSeekBar)
            progressBar?.progress = percentValue.toInt()

            // Set allocation slider change listener
            progressBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        // Calculate new allocation based on percentage
                        val newAllocation = (progress / 100.0) * currentBudget
                        item.allocation = newAllocation

                        // Update the display
                        amountInput.setText(String.format("%.2f", newAllocation))
                        percentage.text = "$progress%"

                        // Recalculate total
                        recalculateTotalAllocated()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            // Set up amount input change listener
            amountInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    try {
                        val newValue = amountInput.text.toString().toDoubleOrNull() ?: 0.0
                        item.allocation = newValue

                        // Update percentage and progress bar when amount changes
                        val newPercentage =
                            if (currentBudget > 0) (newValue / currentBudget) * 100 else 0.0
                        percentage.text = "${newPercentage.toInt()}%"
                        progressBar?.progress = newPercentage.toInt()

                        // Recalculate total
                        recalculateTotalAllocated()
                    } catch (e: Exception) {
                        // Handle parsing errors
                        Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT)
                            .show()
                        amountInput.setText(String.format("%.2f", item.allocation))
                    }
                }
            }

            // Set average spending (from past transactions)
            lifecycleScope.launch {
                val avg = getCategoryAverage(item.categoryName)
                runOnUiThread {
                    average.text = "Avg: $avg"
                }
            }

            binding.categoryContainer.addView(categoryView)
        }

        // Update total allocated
        updateTotalAllocated()
    }

    /// Helper function to get average spending for a category (from transaction history)
    private suspend fun getCategoryAverage(categoryName: String): String {
        val userId = getCurrentUserId()

        try {
            val db = AppDatabase.getDatabase(this)
            val transactionDao = db.transactionDao()

            // Get transactions for last 3 months
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -3)
            val threeMonthsAgo = calendar.time

            val currentDate = Calendar.getInstance().time

            // Get all transactions for the period
            val transactions = transactionDao.getTransactionsForPeriod(userId, threeMonthsAgo, currentDate)

            // Filter by category
            val categoryTransactions = transactions.filter { transaction ->
                transaction.category == categoryName && transaction.isExpense
            }

            if (categoryTransactions.isEmpty()) {
                return "R0.00"
            }

            // Calculate monthly average
            val totalSpent = categoryTransactions.sumOf { transaction -> transaction.amount }
            val avgPerMonth = totalSpent / 3.0

            return "R${"%,.2f".format(avgPerMonth)}"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category average: ${e.message}", e)
            return "R0.00"
        }
    }

    private fun recalculateTotalAllocated() {
        totalAllocated = categoryItems.sumOf { it.allocation }
        updateTotalAllocated()
    }

    private fun updateTotalAllocated() {
        binding.totalAllocated.text = "Total: R${"%,.2f".format(totalAllocated)}"

        // Calculate percentage of budget allocated
        val percentAllocated = if (currentBudget > 0) (totalAllocated / currentBudget) * 100 else 0.0
        binding.allocationPercentage.text = "${percentAllocated.toInt()}% allocated"

        // Update allocation progress
        binding.allocationProgress.progress = percentAllocated.toInt()

        // Highlight if over budget
        val textColor = when {
            totalAllocated > currentBudget -> R.color.red_light
            totalAllocated == currentBudget -> R.color.green_light
            else -> R.color.text_dark
        }

        binding.totalAllocated.setTextColor(getColor(textColor))
    }

    /**
     * Setup all the event listeners and initial UI values.
     */
    private fun setupUI() {
        // Display the initial budget value
        updateDisplayedBudget()

        // Handle back button
        binding.backButton.setOnClickListener {
            finish()  // Closes the activity and returns to previous screen
        }

        // Increase total budget
        binding.increaseBudget.setOnClickListener {
            val oldBudget = currentBudget
            currentBudget += budgetIncrement
            updateDisplayedBudget()

            // Proportionally adjust category allocations using the manager
            if (totalAllocated > 0) {
                val adjustedItems = categoryManager.adjustAllocationsByRatio(
                    categoryItems,
                    oldBudget,
                    currentBudget
                )

                categoryItems.clear()
                categoryItems.addAll(adjustedItems)

                updateCategoryList()
            }
        }

        // Decrease total budget
        binding.decreaseBudget.setOnClickListener {
            if (currentBudget > budgetIncrement) {
                val oldBudget = currentBudget
                currentBudget -= budgetIncrement
                updateDisplayedBudget()

                // Proportionally adjust category allocations
                if (totalAllocated > 0) {
                    val adjustedItems = categoryManager.adjustAllocationsByRatio(
                        categoryItems,
                        oldBudget,
                        currentBudget
                    )

                    categoryItems.clear()
                    categoryItems.addAll(adjustedItems)

                    updateCategoryList()
                }
            } else {
                Toast.makeText(this, "Budget cannot be less than R${budgetIncrement}", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle month change
        binding.changeDateButton.setOnClickListener {
            showMonthPicker()
        }

        // Add new category
        binding.addCategory.setOnClickListener {
            showCategoryPicker()
        }

        // Auto-balance allocations
        binding.balanceButton.setOnClickListener {
            autoBalanceAllocations()
        }

        // Handle save changes
        binding.saveChangesButton.setOnClickListener {
            saveBudgetGoal()
        }

        // Handle copy previous
        binding.copyPreviousButton.setOnClickListener {
            copyPreviousMonth()
        }

        // Setup minimum spending goal
        binding.minimumGoalSeekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    minimumSpendingGoal = (progress / 100.0) * currentBudget
                    updateMinimumGoalDisplay()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    /**
     * Auto-balance allocations to match the total budget
     */
    private fun autoBalanceAllocations() {
        if (categoryItems.isEmpty()) {
            Toast.makeText(this, "No categories to balance", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate the difference between total budget and current allocations
        val difference = currentBudget - totalAllocated

        if (Math.abs(difference) < 0.01) {
            Toast.makeText(this, "Allocations already balanced", Toast.LENGTH_SHORT).show()
            return
        }

        // Distribute the difference proportionally
        val ratio = currentBudget / totalAllocated

        for (item in categoryItems) {
            item.allocation *= ratio
        }

        // Update UI
        updateCategoryList()
        Toast.makeText(this, "Allocations balanced to match total budget", Toast.LENGTH_SHORT).show()
    }

    /**
     * Update the displayed minimum spending goal.
     */
    private fun updateMinimumGoalDisplay() {
        binding.minimumGoalValue.text = "R${"%,.2f".format(minimumSpendingGoal)}"

        // Calculate percentage of total budget
        val percentage = if (currentBudget > 0)
            (minimumSpendingGoal / currentBudget) * 100 else 0.0
        binding.minimumGoalPercentage.text = "${percentage.toInt()}% of budget"

        // Update seek bar
        binding.minimumGoalSeekBar.progress = percentage.toInt()
    }

    private fun showMonthPicker() {
        // Create a month/year picker dialog
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, currentMonth - 1)
        calendar.set(Calendar.YEAR, currentYear)

        val dialog = android.app.DatePickerDialog(
            this,
            android.app.DatePickerDialog.OnDateSetListener { _, year, month, _ ->
                // Update month and year
                currentMonth = month + 1 // Month is 0-based in Calendar
                currentYear = year

                // Update UI
                updateMonth()

                // Load budget for new month
                loadCurrentUserBudget()
            },
            currentYear,
            currentMonth - 1,
            1
        )

        // Hide the day part since we only care about month and year
        dialog.datePicker.findViewById<android.widget.NumberPicker>(
            resources.getIdentifier("day", "id", "android")
        )?.visibility = android.view.View.GONE

        dialog.show()
    }

    /**
     * Show a dialog to pick a category to add to the budget.
     */
    private fun showCategoryPicker() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@BudgetGoalsActivity)
                val categoryDao = db.categoryDao()

                // Get all categories
                val dbCategories = categoryDao.getAllCategories()

                // Filter out categories already in our list
                val existingCategoryNames = categoryItems.map { it.categoryName }
                val availableCategories = dbCategories.filter {
                    !existingCategoryNames.contains(it.name)
                }

                if (availableCategories.isEmpty()) {
                    runOnUiThread {
                        // Show create new category dialog directly
                        val createCategorySheet = CreateCategoryBottomSheet()
                        createCategorySheet.show(supportFragmentManager, "createCategorySheet")
                    }
                    return@launch
                }

                // Convert to CategoryItem for the picker
                val categoryItems = availableCategories.map {
                    CategoryItem(
                        name = it.name,
                        emoji = it.emoji,
                        color = it.color,
                        budget = it.budget
                    )
                }

                runOnUiThread {
                    // Show category picker
                    val categoryPicker = CategoryPickerBottomSheet(
                        categoryList = categoryItems,
                        onCategorySelected = { selectedCategory ->
                            // Add selected category to our budget allocations
                            addCategoryToBudget(selectedCategory)
                        },
                        onCreateNewClicked = {
                            // Show create new category dialog
                            val createCategorySheet = CreateCategoryBottomSheet()
                            createCategorySheet.show(supportFragmentManager, "createCategorySheet")
                        }
                    )

                    categoryPicker.show(supportFragmentManager, "categoryPicker")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing category picker: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@BudgetGoalsActivity,
                        "Error loading categories: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Add a selected category to the budget with an initial allocation.
     */
    private fun addCategoryToBudget(selectedCategory: CategoryItem) {
        // Calculate a default allocation (remaining budget / number of existing categories)
        val remainingBudget = currentBudget - totalAllocated
        val defaultAllocation = if (remainingBudget > 0) remainingBudget else 0.0

        // Create a new budget item
        val newItem = CategoryBudgetItem(
            categoryName = selectedCategory.name,
            emoji = selectedCategory.emoji,
            color = selectedCategory.color,
            allocation = defaultAllocation
        )

        // Add to our list
        categoryItems.add(newItem)

        // Update UI
        updateCategoryList()
        recalculateTotalAllocated()

        Toast.makeText(
            this,
            "${selectedCategory.name} added with R${"%,.2f".format(defaultAllocation)} allocation",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Copy the budget from the previous month.
     */
    private fun copyPreviousMonth() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to use this feature", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@BudgetGoalsActivity)
                val budgetGoalDao = db.budgetGoalDao()

                // Get all budget goals
                val allGoals = budgetGoalDao.getAllBudgetGoalsForUser(userId)

                // Find a previous month (not current)
                val previousGoal = allGoals.firstOrNull {
                    it.year != currentYear || it.month != currentMonth
                }

                if (previousGoal != null) {
                    // Load the previous goal's category budgets using the manager
                    val previousCategoryBudgets = categoryManager.loadCategoryAllocations(previousGoal.id)

                    // Update our current data
                    currentBudget = previousGoal.totalBudget
                    minimumSpendingGoal = previousGoal.minimumSpendingGoal

                    // Update category items
                    categoryItems.clear()
                    categoryItems.addAll(previousCategoryBudgets)

                    // Calculate total allocated
                    totalAllocated = categoryItems.sumOf { it.allocation }

                    // Update UI
                    runOnUiThread {
                        updateDisplayedBudget()
                        updateCategoryList()
                        updateMinimumGoalDisplay()
                        Toast.makeText(
                            this@BudgetGoalsActivity,
                            "Copied budget from ${previousGoal.month}/${previousGoal.year}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this@BudgetGoalsActivity,
                            "No previous budget found to copy",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying previous budget: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(
                        this@BudgetGoalsActivity,
                        "Error copying budget: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Save the budget goal to the database.
     */
    private fun saveBudgetGoal() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to save budget goals", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getDatabase(this@BudgetGoalsActivity)
                val budgetGoalDao = db.budgetGoalDao()

                // Show saving indicator
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.VISIBLE
                    binding.saveChangesButton.isEnabled = false
                }

                // First, deactivate all existing budget goals for this user
                budgetGoalDao.deactivateAllBudgetGoals(userId)

                // Create or update budget goal
                val budgetGoal = BudgetGoal(
                    id = if (existingBudgetGoalId > 0) existingBudgetGoalId else 0,
                    userId = userId,
                    month = currentMonth,
                    year = currentYear,
                    totalBudget = currentBudget,
                    minimumSpendingGoal = minimumSpendingGoal,
                    isActive = true
                )

                val budgetGoalId = if (existingBudgetGoalId > 0) {
                    budgetGoalDao.updateBudgetGoal(budgetGoal)
                    existingBudgetGoalId
                } else {
                    budgetGoalDao.insertBudgetGoal(budgetGoal).toInt()
                }

                // Save category budgets using the manager
                val success = categoryManager.saveCategoryAllocations(budgetGoalId, categoryItems)

                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.GONE
                    binding.saveChangesButton.isEnabled = true

                    if (success) {
                        Toast.makeText(
                            this@BudgetGoalsActivity,
                            "Budget goals saved successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        existingBudgetGoalId = budgetGoalId // Update ID for future updates
                    } else {
                        Toast.makeText(
                            this@BudgetGoalsActivity,
                            "Error saving category allocations",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving budget goal: ${e.message}", e)
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.GONE
                    binding.saveChangesButton.isEnabled = true

                    Toast.makeText(
                        this@BudgetGoalsActivity,
                        "Error saving budget: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Update the displayed budget text across relevant views.
     */
    private fun updateDisplayedBudget() {
        val budgetText = "R${"%,.2f".format(currentBudget)}"
        binding.monthlyBudgetText.text = budgetText
        binding.currentBudgetDisplay.text = budgetText

        // Update minimum goal as well
        updateMinimumGoalDisplay()
    }
}