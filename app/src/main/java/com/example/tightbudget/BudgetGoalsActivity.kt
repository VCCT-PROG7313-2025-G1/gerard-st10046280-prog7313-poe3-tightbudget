package com.example.tightbudget

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.DatePicker
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityBudgetGoalsBinding
import com.example.tightbudget.firebase.FirebaseBudgetManager
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.ui.CategoryBudgetItem
import com.example.tightbudget.ui.CreateCategoryBottomSheet
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

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

    // State variable to track if auto-balancing is on or off
    private var isAutoBalanceEnabled: Boolean = false

    // A flag to prevent listeners from triggering during programmatic updates
    private var isUpdatingProgrammatically = false

    // Firebase managers
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var firebaseBudgetManager: FirebaseBudgetManager
    private lateinit var firebaseCategoryManager: FirebaseCategoryManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBudgetGoalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase managers
        firebaseDataManager = FirebaseDataManager.getInstance()
        firebaseBudgetManager = FirebaseBudgetManager.getInstance()
        firebaseCategoryManager = FirebaseCategoryManager.getInstance()

        setupUI()
    }

    override fun onResume() {
        super.onResume()
        // Reload the budget and categories whenever the screen becomes active.
        loadCurrentUserBudget()
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    private fun loadCurrentUserBudget() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to set budget goals", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                runOnUiThread { binding.loadingIndicator.visibility = View.VISIBLE }

                var budgetGoal = firebaseBudgetManager.getBudgetGoalForMonth(userId, currentMonth, currentYear)
                if (budgetGoal == null) {
                    budgetGoal = firebaseBudgetManager.getActiveBudgetGoal(userId)
                }

                if (budgetGoal != null) {
                    existingBudgetGoalId = budgetGoal.id
                    currentBudget = budgetGoal.totalBudget
                    minimumSpendingGoal = budgetGoal.minimumSpendingGoal

                    val allUserCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
                    val savedAllocations = firebaseBudgetManager.getCategoryBudgetsForGoal(budgetGoal.id)
                    val savedAllocationsMap = savedAllocations.associateBy { it.categoryName }

                    categoryItems.clear()
                    allUserCategories.forEach { userCategory ->
                        val savedAllocation = savedAllocationsMap[userCategory.name]
                        categoryItems.add(
                            CategoryBudgetItem(
                                categoryName = userCategory.name,
                                emoji = userCategory.emoji ?: "📁",
                                color = userCategory.color,
                                allocation = savedAllocation?.allocation ?: 0.0,
                                id = savedAllocation?.id ?: 0
                            )
                        )
                    }

                    totalAllocated = categoryItems.sumOf { it.allocation }
                    runOnUiThread { updateAllUI() }
                } else {
                    createDefaultAllocations()
                    runOnUiThread { updateAllUI() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading budget from Firebase: ${e.message}", e)
                runOnUiThread {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(this@BudgetGoalsActivity, "Error loading budget: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateAllUI() {
        updateDisplayedBudget()
        updateCategoryList()
        updateMonth()
        updateBalanceButtonUI() // Update the button's appearance
        binding.loadingIndicator.visibility = View.GONE
    }

    private fun rebalanceAllocations(editedItemIndex: Int, newAllocationForEditedItem: Double) {
        if (categoryItems.isEmpty() || editedItemIndex !in categoryItems.indices) return
        isUpdatingProgrammatically = true
        val cappedAllocation = newAllocationForEditedItem.coerceIn(0.0, currentBudget)
        categoryItems[editedItemIndex].allocation = cappedAllocation
        val budgetLeftForOthers = currentBudget - cappedAllocation
        val otherItems = categoryItems.filterIndexed { index, _ -> index != editedItemIndex }
        val totalAllocationOfOthers = otherItems.sumOf { it.allocation }

        if (totalAllocationOfOthers > 0.01) {
            val ratio = budgetLeftForOthers / totalAllocationOfOthers
            otherItems.forEach { it.allocation *= ratio }
        } else if (otherItems.isNotEmpty()) {
            val evenSplit = budgetLeftForOthers / otherItems.size
            otherItems.forEach { it.allocation = evenSplit }
        }

        val finalTotal = categoryItems.sumOf { it.allocation }
        val roundingDifference = currentBudget - finalTotal
        if (abs(roundingDifference) > 0.01 && categoryItems.isNotEmpty()) {
            categoryItems.last().allocation += roundingDifference
        }

        updateCategoryList()
        isUpdatingProgrammatically = false
    }

    // Other helper functions (getCategoryColor, createDefaultAllocations, etc.) remain the same...
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
            "fitness" -> "#FF5722"
            else -> "#9E9E9E"
        }
    }

    private suspend fun createDefaultAllocations() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            createFallbackAllocations()
            return
        }

        val categoryAllocationManager = com.example.tightbudget.utils.CategoryAllocationManager()
        val allocatedItems = categoryAllocationManager.createDefaultAllocations(currentBudget, userId)

        if (allocatedItems.isNotEmpty()) {
            categoryItems.clear()
            categoryItems.addAll(allocatedItems)
            totalAllocated = categoryItems.sumOf { it.allocation }
        } else {
            createFallbackAllocations()
        }
    }

    private fun createFallbackAllocations() {
        categoryItems.clear()
        val fallbackCategories = listOf("Housing", "Food", "Transport", "Other")
        val perFallbackCat = currentBudget / fallbackCategories.size
        fallbackCategories.forEach { categoryName ->
            categoryItems.add(
                CategoryBudgetItem(
                    categoryName = categoryName,
                    emoji = EmojiUtils.getCategoryEmoji(categoryName),
                    color = getCategoryColor(categoryName),
                    allocation = perFallbackCat
                )
            )
        }
        totalAllocated = categoryItems.sumOf { it.allocation }
    }

    private fun updateMonth() {
        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val calendar = Calendar.getInstance().apply {
            set(Calendar.MONTH, currentMonth - 1)
            set(Calendar.YEAR, currentYear)
        }
        binding.monthText.text = monthFormatter.format(calendar.time)
    }

    private fun updateCategoryList() {
        isUpdatingProgrammatically = true
        binding.categoryContainer.removeAllViews()

        categoryItems.forEachIndexed { index, item ->
            val categoryView = layoutInflater.inflate(R.layout.item_budget_category, binding.categoryContainer, false)
            val emoji = categoryView.findViewById<TextView>(R.id.categoryEmoji)
            val name = categoryView.findViewById<TextView>(R.id.categoryName)
            val average = categoryView.findViewById<TextView>(R.id.categoryAverage)
            val amountInput = categoryView.findViewById<EditText>(R.id.categoryAmountInput)
            val percentage = categoryView.findViewById<TextView>(R.id.categoryPercentage)
            val progressBar = categoryView.findViewById<SeekBar>(R.id.categoryAllocationSeekBar)

            emoji.text = item.emoji
            name.text = item.categoryName
            amountInput.setText(String.format("%.2f", item.allocation))

            val percentValue = if (currentBudget > 0) (item.allocation / currentBudget) * 100 else 0.0
            percentage.text = "${percentValue.toInt()}%"
            progressBar.progress = percentValue.toInt()

            progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && !isUpdatingProgrammatically) {
                        val newAllocation = (progress / 100.0) * currentBudget
                        if (isAutoBalanceEnabled) {
                            rebalanceAllocations(index, newAllocation)
                        } else {
                            item.allocation = newAllocation
                            amountInput.setText(String.format("%.2f", newAllocation))
                            recalculateTotalAllocated()
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            amountInput.doAfterTextChanged { editable ->
                if (!isUpdatingProgrammatically) {
                    val newValue = editable.toString().toDoubleOrNull()
                    if (newValue != null) {
                        if (isAutoBalanceEnabled) {
                            rebalanceAllocations(index, newValue)
                        } else {
                            item.allocation = newValue
                            recalculateTotalAllocated()
                        }
                    }
                }
            }

            lifecycleScope.launch {
                val avg = getCategoryAverage(item.categoryName)
                runOnUiThread { average.text = "Avg: $avg" }
            }

            binding.categoryContainer.addView(categoryView)
        }
        updateTotalAllocated()
        isUpdatingProgrammatically = false
    }

    private suspend fun getCategoryAverage(categoryName: String): String {
        val userId = getCurrentUserId()
        try {
            val calendar = Calendar.getInstance()
            val endDate = calendar.time
            calendar.add(Calendar.MONTH, -3)
            val startDate = calendar.time

            val transactions = firebaseDataManager.getTransactionsForPeriod(userId, startDate, endDate)
            val categoryTransactions = transactions.filter { it.category == categoryName && it.isExpense }

            if (categoryTransactions.isEmpty()) return "R0.00"

            val totalSpent = categoryTransactions.sumOf { it.amount }
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
        val percentAllocated = if (currentBudget > 0) (totalAllocated / currentBudget) * 100 else 0.0
        binding.allocationPercentage.text = "${percentAllocated.toInt()}% allocated"
        binding.allocationProgress.progress = percentAllocated.toInt()

        val textColor = when {
            totalAllocated > currentBudget + 0.01 -> getColor(R.color.red_light)
            totalAllocated < currentBudget - 0.01 -> getColor(R.color.text_medium)
            else -> getColor(R.color.green_light)
        }
        binding.totalAllocated.setTextColor(textColor)
    }

    private fun setupUI() {
        updateDisplayedBudget()
        binding.backButton.setOnClickListener { finish() }

        binding.increaseBudget.setOnClickListener {
            val oldBudget = currentBudget
            currentBudget += budgetIncrement
            updateDisplayedBudget()
            if (totalAllocated > 0) {
                val ratio = currentBudget / oldBudget
                categoryItems.forEach { it.allocation *= ratio }
                updateCategoryList()
            }
        }

        binding.decreaseBudget.setOnClickListener {
            if (currentBudget > budgetIncrement) {
                val oldBudget = currentBudget
                currentBudget -= budgetIncrement
                updateDisplayedBudget()
                if (totalAllocated > 0) {
                    val ratio = currentBudget / oldBudget
                    categoryItems.forEach { it.allocation *= ratio }
                    updateCategoryList()
                }
            } else {
                Toast.makeText(this, "Budget cannot be less than R$budgetIncrement", Toast.LENGTH_SHORT).show()
            }
        }

        binding.changeDateButton.setOnClickListener { showMonthPicker() }
        binding.addCategory.setOnClickListener { showCreateCategoryDialog() }

        // This button toggles the auto-balance feature
        binding.balanceButton.setOnClickListener {
            // Toggle the auto-balance state
            isAutoBalanceEnabled = !isAutoBalanceEnabled

            // If the user just enabled it, perform an initial balance
            if(isAutoBalanceEnabled) {
                autoBalanceAllocations(showToast = false) // Balance without showing toast
            }

            // Update the button's appearance and show a toast
            updateBalanceButtonUI()
            val status = if(isAutoBalanceEnabled) "enabled" else "disabled"
            Toast.makeText(this, "Auto-balance $status", Toast.LENGTH_SHORT).show()
        }

        binding.saveChangesButton.setOnClickListener { saveBudgetGoal() }
        binding.copyPreviousButton.setOnClickListener { copyPreviousMonth() }

        binding.minimumGoalSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

    private fun updateBalanceButtonUI() {
        if(isAutoBalanceEnabled) {
            binding.balanceButton.text = getString(R.string.auto_balance_on)
            binding.balanceButton.setTextColor(ContextCompat.getColor(this, R.color.green_light))
        } else {
            binding.balanceButton.text = getString(R.string.auto_balance)
            binding.balanceButton.setTextColor(ContextCompat.getColor(this, R.color.purple))
        }
    }

    private fun autoBalanceAllocations(showToast: Boolean = true) {
        if (categoryItems.isEmpty()) return
        val totalToAllocate = currentBudget
        val currentTotal = categoryItems.sumOf { it.allocation }

        if (abs(currentBudget - currentTotal) < 0.01) {
            if(showToast) Toast.makeText(this, "Allocations already balanced", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentTotal <= 0) return

        val ratio = totalToAllocate / currentTotal
        categoryItems.forEach { it.allocation *= ratio }
        updateCategoryList()
        if(showToast) Toast.makeText(this, "Allocations balanced", Toast.LENGTH_SHORT).show()
    }

    // ... Other functions (showMonthPicker, copyPreviousMonth, saveBudgetGoal, etc.) remain the same
    private fun updateMinimumGoalDisplay() {
        binding.minimumGoalValue.text = "R${"%,.2f".format(minimumSpendingGoal)}"
        val percentage = if (currentBudget > 0) (minimumSpendingGoal / currentBudget) * 100 else 0.0
        binding.minimumGoalPercentage.text = "${percentage.toInt()}% of budget"
        binding.minimumGoalSeekBar.progress = percentage.toInt()
    }

    private fun showMonthPicker() {
        val today = Calendar.getInstance()
        val dialog = DatePickerDialog(
            this,
            { _, year, month, _ ->
                if (year != currentYear || (month + 1) != currentMonth) {
                    currentYear = year
                    currentMonth = month + 1
                    existingBudgetGoalId = 0
                    loadCurrentUserBudget()
                }
            },
            currentYear,
            currentMonth - 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        dialog.datePicker.findViewById<View>(resources.getIdentifier("android:id/day", null, null))?.visibility = View.GONE
        dialog.show()
    }

    private fun showCreateCategoryDialog() {
        CreateCategoryBottomSheet().show(supportFragmentManager, "createCategorySheet")
    }

    private fun copyPreviousMonth() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to use this feature", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                val allGoals = firebaseBudgetManager.getAllBudgetGoalsForUser(userId)
                    .sortedWith(compareByDescending<BudgetGoal> { it.year }.thenByDescending { it.month })
                val previousGoal = allGoals.firstOrNull { it.year != currentYear || it.month != currentMonth }
                if (previousGoal != null) {
                    val previousCategoryBudgets = firebaseBudgetManager.getCategoryBudgetsForGoal(previousGoal.id)
                    currentBudget = previousGoal.totalBudget
                    minimumSpendingGoal = previousGoal.minimumSpendingGoal
                    existingBudgetGoalId = 0
                    categoryItems.clear()
                    previousCategoryBudgets.forEach { categoryBudget ->
                        categoryItems.add(
                            CategoryBudgetItem(
                                categoryName = categoryBudget.categoryName,
                                emoji = EmojiUtils.getCategoryEmoji(categoryBudget.categoryName),
                                color = getCategoryColor(categoryBudget.categoryName),
                                allocation = categoryBudget.allocation,
                                id = 0
                            )
                        )
                    }
                    runOnUiThread {
                        updateAllUI()
                        Toast.makeText(this@BudgetGoalsActivity, "Copied budget from ${previousGoal.month}/${previousGoal.year}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@BudgetGoalsActivity, "No previous budget found to copy", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying previous budget: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@BudgetGoalsActivity, "Error copying budget: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBudgetGoal() {
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(this, "Please log in to save budget goals", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            try {
                runOnUiThread {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.saveChangesButton.isEnabled = false
                }
                firebaseBudgetManager.deactivateAllBudgetGoals(userId)
                val budgetGoal = BudgetGoal(
                    id = if (existingBudgetGoalId > 0) existingBudgetGoalId else 0,
                    userId = userId, month = currentMonth, year = currentYear,
                    totalBudget = currentBudget, minimumSpendingGoal = minimumSpendingGoal, isActive = true
                )
                val savedBudgetGoal = if (existingBudgetGoalId > 0) {
                    firebaseBudgetManager.updateBudgetGoal(budgetGoal); budgetGoal
                } else {
                    firebaseBudgetManager.createBudgetGoal(budgetGoal)
                }
                existingBudgetGoalId = savedBudgetGoal.id
                firebaseBudgetManager.deleteCategoryBudgetsForGoal(savedBudgetGoal.id)
                categoryItems.forEach { item ->
                    val categoryBudget = CategoryBudget(
                        budgetGoalId = savedBudgetGoal.id,
                        categoryName = item.categoryName,
                        allocation = item.allocation
                    )
                    firebaseBudgetManager.createCategoryBudget(categoryBudget)
                }
                runOnUiThread {
                    Toast.makeText(this@BudgetGoalsActivity, "Budget saved successfully!", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                    binding.saveChangesButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving budget goal: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@BudgetGoalsActivity, "Error saving budget: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.loadingIndicator.visibility = View.GONE
                    binding.saveChangesButton.isEnabled = true
                }
            }
        }
    }

    private fun updateDisplayedBudget() {
        val budgetText = "R${"%,.2f".format(currentBudget)}"
        binding.monthlyBudgetText.text = budgetText
        binding.currentBudgetDisplay.text = budgetText
        updateMinimumGoalDisplay()
    }
}