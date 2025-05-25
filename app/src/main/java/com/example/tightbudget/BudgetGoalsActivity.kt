package com.example.tightbudget

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityBudgetGoalsBinding
import com.example.tightbudget.firebase.FirebaseBudgetManager
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.ui.CategoryBudgetItem
import com.example.tightbudget.ui.CreateCategoryBottomSheet
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * BudgetGoalsActivity handles the screen where users set their total monthly budget
 * and allocate amounts to different spending categories using Firebase.
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

    // Firebase managers
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var firebaseBudgetManager: FirebaseBudgetManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase managers
        firebaseDataManager = FirebaseDataManager.getInstance()
        firebaseBudgetManager = FirebaseBudgetManager.getInstance()

        setupUI()
        loadCurrentUserBudget() // Load the current user's budget goal from Firebase
    }

    /**
     * Retrieve the current user ID from shared preferences.
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    /**
     * Load the current user's budget goal from Firebase.
     */
    private fun loadCurrentUserBudget() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to set budget goals", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Show loading indicator
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.VISIBLE
                }

                // Try to get budget for current month first
                var budgetGoal = firebaseBudgetManager.getBudgetGoalForMonth(userId, currentMonth, currentYear)

                // If no budget for current month, get the most recent active budget
                if (budgetGoal == null) {
                    budgetGoal = firebaseBudgetManager.getActiveBudgetGoal(userId)
                }

                if (budgetGoal != null) {
                    // We found an existing budget
                    existingBudgetGoalId = budgetGoal.id
                    currentBudget = budgetGoal.totalBudget
                    minimumSpendingGoal = budgetGoal.minimumSpendingGoal

                    // Load category allocations using Firebase
                    val loadedCategoryBudgets = firebaseBudgetManager.getCategoryBudgetsForGoal(budgetGoal.id)

                    // Convert CategoryBudget to CategoryBudgetItem with emoji and color
                    categoryItems.clear()
                    for (categoryBudget in loadedCategoryBudgets) {
                        val categoryItem = CategoryBudgetItem(
                            categoryName = categoryBudget.categoryName,
                            emoji = EmojiUtils.getCategoryEmoji(categoryBudget.categoryName),
                            color = getCategoryColor(categoryBudget.categoryName),
                            allocation = categoryBudget.allocation,
                            id = categoryBudget.id
                        )
                        categoryItems.add(categoryItem)
                    }

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
                    createDefaultAllocations()

                    // Update UI
                    runOnUiThread {
                        updateDisplayedBudget()
                        updateCategoryList()
                        updateMonth()
                        binding.loadingIndicator.visibility = android.view.View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading budget from Firebase: ${e.message}", e)
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
     * Helper method for category colors
     */
    private fun getCategoryColor(categoryName: String): String {
        return when (categoryName.lowercase()) {
            "housing" -> "#4CAF50"
            "food", "groceries" -> "#FF9800"
            "transport" -> "#2196F3"
            "entertainment" -> "#9C27B0"
            "utilities" -> "#FFC107"
            "health" -> "#E91E63"
            "shopping" -> "#00BCD4"
            "education" -> "#3F51B5"
            else -> "#9E9E9E"
        }
    }

    /**
     * Create default allocations for new users
     */
    private suspend fun createDefaultAllocations() {
        try {
            // Smart allocation algorithm for default categories
            val essentialCategories = listOf("Housing", "Groceries", "Utilities", "Transport")
            val essentialPercentage = 0.65 // 65% for essential categories

            categoryItems.clear()
            var remainingBudget = currentBudget

            // First, allocate to essentials
            val essentialBudget = currentBudget * essentialPercentage
            remainingBudget -= essentialBudget

            // Distribute essential budget among essential categories
            val perEssentialCat = essentialBudget / essentialCategories.size

            for (categoryName in essentialCategories) {
                categoryItems.add(
                    CategoryBudgetItem(
                        categoryName = categoryName,
                        emoji = EmojiUtils.getCategoryEmoji(categoryName),
                        color = getCategoryColor(categoryName),
                        allocation = perEssentialCat
                    )
                )
            }

            // Add some other common categories
            val otherCategories = listOf("Entertainment", "Health", "Shopping", "Education")
            val perOtherCat = remainingBudget / otherCategories.size

            for (categoryName in otherCategories) {
                categoryItems.add(
                    CategoryBudgetItem(
                        categoryName = categoryName,
                        emoji = EmojiUtils.getCategoryEmoji(categoryName),
                        color = getCategoryColor(categoryName),
                        allocation = perOtherCat
                    )
                )
            }

            // Calculate total allocated
            totalAllocated = categoryItems.sumOf { it.allocation }

        } catch (e: Exception) {
            Log.e(TAG, "Error creating default allocations: ${e.message}", e)
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

    /**
     * Update the category list UI
     */
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
                        Toast.makeText(this@BudgetGoalsActivity, "Please enter a valid amount", Toast.LENGTH_SHORT)
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

    /**
     * Helper function to get average spending for a category from Firebase
     */
    private suspend fun getCategoryAverage(categoryName: String): String {
        val userId = getCurrentUserId()

        try {
            // Get transactions for last 3 months from Firebase
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.MONTH, -3)
            val threeMonthsAgo = calendar.time

            val currentDate = Calendar.getInstance().time

            // Get all transactions for the period from Firebase
            val transactions = firebaseDataManager.getTransactionsForPeriod(userId, threeMonthsAgo, currentDate)

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
            Log.e(TAG, "Error getting category average from Firebase: ${e.message}", e)
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

            // Proportionally adjust category allocations
            if (totalAllocated > 0) {
                val ratio = currentBudget / oldBudget
                for (item in categoryItems) {
                    item.allocation *= ratio
                }
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
                    val ratio = currentBudget / oldBudget
                    for (item in categoryItems) {
                        item.allocation *= ratio
                    }
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
            showCreateCategoryDialog()
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
     * Show create category dialog (simplified since we're not managing Room categories anymore)
     */
    private fun showCreateCategoryDialog() {
        val createCategorySheet = CreateCategoryBottomSheet()
        createCategorySheet.show(supportFragmentManager, "createCategorySheet")
    }

    /**
     * Copy the budget from the previous month using Firebase.
     */
    private fun copyPreviousMonth() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to use this feature", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Get all budget goals from Firebase
                val allGoals = firebaseBudgetManager.getAllBudgetGoalsForUser(userId)

                // Find a previous month (not current)
                val previousGoal = allGoals.firstOrNull {
                    it.year != currentYear || it.month != currentMonth
                }

                if (previousGoal != null) {
                    // Load the previous goal's category budgets from Firebase
                    val previousCategoryBudgets = firebaseBudgetManager.getCategoryBudgetsForGoal(previousGoal.id)

                    // Update our current data
                    currentBudget = previousGoal.totalBudget
                    minimumSpendingGoal = previousGoal.minimumSpendingGoal

                    // Convert to CategoryBudgetItem
                    categoryItems.clear()
                    for (categoryBudget in previousCategoryBudgets) {
                        categoryItems.add(
                            CategoryBudgetItem(
                                categoryName = categoryBudget.categoryName,
                                emoji = EmojiUtils.getCategoryEmoji(categoryBudget.categoryName),
                                color = getCategoryColor(categoryBudget.categoryName),
                                allocation = categoryBudget.allocation,
                                id = 0 // Reset ID for new budget
                            )
                        )
                    }

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
                Log.e(TAG, "Error copying previous budget from Firebase: ${e.message}", e)
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
     * Save the budget goal to Firebase.
     */
    private fun saveBudgetGoal() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to save budget goals", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Show saving indicator
                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.VISIBLE
                    binding.saveChangesButton.isEnabled = false
                }

                // First, deactivate all existing budget goals for this user
                firebaseBudgetManager.deactivateAllBudgetGoals(userId)

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

                val savedBudgetGoal = if (existingBudgetGoalId > 0) {
                    firebaseBudgetManager.updateBudgetGoal(budgetGoal)
                    budgetGoal
                } else {
                    firebaseBudgetManager.createBudgetGoal(budgetGoal)
                }

                // Save category budgets to Firebase
                firebaseBudgetManager.deleteCategoryBudgetsForGoal(savedBudgetGoal.id)

                for (item in categoryItems) {
                    val categoryBudget = CategoryBudget(
                        id = 0, // Always insert new
                        budgetGoalId = savedBudgetGoal.id,
                        categoryName = item.categoryName,
                        allocation = item.allocation
                    )
                    firebaseBudgetManager.createCategoryBudget(categoryBudget)
                }

                runOnUiThread {
                    binding.loadingIndicator.visibility = android.view.View.GONE
                    binding.saveChangesButton.isEnabled = true

                    Toast.makeText(
                        this@BudgetGoalsActivity,
                        "Budget goals saved to Firebase successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    existingBudgetGoalId = savedBudgetGoal.id // Update ID for future updates
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving budget goal to Firebase: ${e.message}", e)
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