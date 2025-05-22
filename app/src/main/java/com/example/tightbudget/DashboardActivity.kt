package com.example.tightbudget

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.adapters.TransactionAdapter
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.ui.TransactionDetailBottomSheet
import com.example.tightbudget.utils.CategoryConstants
import com.example.tightbudget.utils.ChartUtils
import com.example.tightbudget.utils.DashboardDataManager
import com.example.tightbudget.utils.DashboardHelper
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.example.tightbudget.utils.ProgressBarUtils
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Dashboard screen showing financial summary, goals, charts and quick access buttons.
 */
class DashboardActivity : AppCompatActivity() {
    private val TAG = "DashboardActivity"

    // Database and data manager
    private lateinit var db: AppDatabase
    private lateinit var dataManager: DashboardDataManager

    // UI components for easy access
    private lateinit var welcomeTextView: TextView
    private lateinit var balanceAmountView: TextView
    private lateinit var totalBudgetView: TextView
    private lateinit var spentSoFarView: TextView
    private lateinit var remainingView: TextView
    private lateinit var chartContainer: FrameLayout
    private lateinit var legendContainer: LinearLayout

    private var currentUserId: Int = -1
    private var currentBudgetGoal: BudgetGoal? = null
    private var categoryBudgets: List<CategoryBudget> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize the database and data manager
        db = AppDatabase.getDatabase(this)
        dataManager = DashboardDataManager(db)

        // Find UI components
        welcomeTextView = findViewById(R.id.welcomeText)
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)
        balanceAmountView = root.findViewById(R.id.balanceAmount)
        totalBudgetView = root.findViewById(R.id.totalBudgetAmount)
        spentSoFarView = root.findViewById(R.id.spentSoFarAmount)
        remainingView = root.findViewById(R.id.remainingAmount)
        chartContainer = root.findViewById(R.id.chartContainer)
        legendContainer = root.findViewById(R.id.legendContainer)

        // Get current user ID
        currentUserId = getCurrentUserId()

        // Load user information and financial data
        loadUserInformation()
        loadFinancialData()

        // Open ProfileActivity when the user taps the profile icon
        findViewById<FrameLayout>(R.id.profileButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        setupBottomNavigation()
        setupQuickActions()
        setupNavigationButtons()
        setupAchievementPlaceholders()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to the dashboard
        loadFinancialData()
    }

    /**
     * Load all financial data for the dashboard
     */
    private fun loadFinancialData() {
        if (currentUserId == -1) {
            // Show placeholder data if no user is logged in
            showPlaceholderData()
            return
        }

        lifecycleScope.launch {
            try {
                // Clear any existing data displays first
                clearFinancialDisplays()

                // Show loading indicators if needed
                // binding.dataLoadingIndicator.visibility = View.VISIBLE

                // Load budget goal for the current user
                currentBudgetGoal = dataManager.loadActiveBudgetGoal(currentUserId)

                if (currentBudgetGoal != null) {
                    // Initialize the budget goals section
                    initBudgetGoalsSection()

                    // Load category budgets for the current user's budget goal
                    categoryBudgets = dataManager.loadCategoryBudgets(currentBudgetGoal!!.id)

                    // Load spending data for the current user
                    val allSpendingData = dataManager.getCurrentMonthSpendingByCategory(currentUserId)
                    val totalSpending = dataManager.getCurrentMonthTotalSpending(currentUserId)

                    // Update UI with real data
                    updateBudgetSummary(currentBudgetGoal!!, totalSpending)
                    updateCategoryProgressBars(allSpendingData)
                    updateSpendingChart(allSpendingData) // Keep all categories in the chart

                    Log.d(
                        TAG,
                        "Loaded budget goal ($${currentBudgetGoal!!.totalBudget}) and " +
                                "${categoryBudgets.size} category budgets, " +
                                "${allSpendingData.size} spending categories for user $currentUserId"
                    )
                } else {
                    // Initialize the budget goals section (clear or add placeholders)
                    initBudgetGoalsSection()

                    // No budget goal found - show placeholder or prompt to create one
                    showNoBudgetGoalUI()

                    Log.d(TAG, "No budget goal found for user $currentUserId")
                }

                // Load transactions for the current user
                setupRecentTransactions()

                // Hide loading indicators if needed
                // binding.dataLoadingIndicator.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "Error loading financial data", e)

                // Hide loading indicators if needed
                // binding.dataLoadingIndicator.visibility = View.GONE

                Toast.makeText(
                    this@DashboardActivity,
                    "Error loading financial data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()

                // Show fallback UI
                showNoBudgetGoalUI()
            }
        }
    }

    /**
     * Clears all financial data displays to prepare for new data
     */
    private fun clearFinancialDisplays() {
        // Reset budget summary
        totalBudgetView.text = "R0.00"
        spentSoFarView.text = "R0.00"
        remainingView.text = "R0.00"

        // Clear the chart
        chartContainer.removeAllViews()

        // Clear the legend
        legendContainer.removeAllViews()

        // Reset categoryBudgets
        categoryBudgets = emptyList()

        // Clear the current budget goal
        currentBudgetGoal = null;

        // Get the categories container and clear it (for logged-in users)
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)
        if (categoryContainer != null && currentUserId != -1) {
            categoryContainer.removeAllViews()
        }
    }

    /**
     * Load user information with proper fallback strategy
     */
    private fun loadUserInformation() {
        lifecycleScope.launch {
            try {
                val previousUserId = currentUserId  // Store the previous user ID

                currentUserId = getCurrentUserId()

                if (currentUserId != previousUserId) {
                    clearFinancialDisplays()  // Clear data if user has changed
                }

                if (currentUserId != -1) {
                    // User is logged in via ID from SharedPreferences
                    val user = db.userDao().getUserById(currentUserId)

                    if (user != null) {
                        // Set welcome message and balance
                        welcomeTextView.text = "Welcome back, ${user.fullName}!"
                        balanceAmountView.text = "R${String.format("%,.2f", user.balance)}"
                        Log.d(TAG, "Loaded user from SharedPreferences ID: ${user.fullName}")
                    } else {
                        // If user not found by ID, try the email from intent
                        val userEmail = intent.getStringExtra("USER_EMAIL")
                        if (!userEmail.isNullOrEmpty()) {
                            val userByEmail = db.userDao().getUserByEmail(userEmail)
                            if (userByEmail != null) {
                                welcomeTextView.text = "Welcome back, ${userByEmail.fullName}!"
                                balanceAmountView.text = "R${String.format("%,.2f", userByEmail.balance)}"
                                // Save the user ID since we found them by email
                                saveUserSession(userByEmail.id)
                                currentUserId = userByEmail.id
                                Log.d(TAG, "Loaded user from email and saved ID: ${userByEmail.id}")

                                // Refresh financial data with newly found user ID
                                loadFinancialData()
                            } else {
                                showDefaultUserInfo()
                            }
                        } else {
                            showDefaultUserInfo()
                        }
                    }
                } else {
                    // If no user ID in preferences, try the email from intent
                    val userEmail = intent.getStringExtra("USER_EMAIL")
                    if (!userEmail.isNullOrEmpty()) {
                        val userByEmail = db.userDao().getUserByEmail(userEmail)
                        if (userByEmail != null) {
                            welcomeTextView.text = "Welcome back, ${userByEmail.fullName}!"
                            balanceAmountView.text = "R${String.format("%,.2f", userByEmail.balance)}"
                            // Save the user ID since we found them by email
                            saveUserSession(userByEmail.id)
                            currentUserId = userByEmail.id
                            Log.d(TAG, "Loaded user from email and saved ID: ${userByEmail.id}")

                            // Refresh financial data with newly found user ID
                            loadFinancialData()
                        } else {
                            showDefaultUserInfo()
                        }
                    } else {
                        showDefaultUserInfo()
                    }
                }

                // Properly initialize the UI based on user login status
                if (currentUserId == -1) {
                    // User not logged in - show placeholder data
                    showPlaceholderData()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading user information", e)
                showDefaultUserInfo()

                // Still show placeholders if there's an error
                showPlaceholderData()
            }
        }
    }

    private fun showDefaultUserInfo() {
        welcomeTextView.text = "Welcome, Guest!"
        balanceAmountView.text = "R0.00"

        // Reset current user ID to ensure guest experience
        currentUserId = -1
    }

    private fun updateBudgetSummary(budgetGoal: BudgetGoal, totalSpending: Double) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Update budget summary numbers
        totalBudgetView.text = "R%.2f".format(budgetGoal.totalBudget)
        spentSoFarView.text = "R%.2f".format(totalSpending)

        val remaining = budgetGoal.totalBudget - totalSpending
        remainingView.text = "R%.2f".format(remaining)

        // Update the overall budget progress bar
        val overallProgressBar = root.findViewById<ProgressBar>(R.id.overallBudgetProgress)
        ProgressBarUtils.setProgress(overallProgressBar, totalSpending, budgetGoal.totalBudget)

        // Update minimum spending goal if available
        if (budgetGoal.minimumSpendingGoal > 0) {
            val minGoalText = root.findViewById<TextView>(R.id.minSpendingGoalText)
            val minGoalAmount = root.findViewById<TextView>(R.id.minSpendingGoalAmount)

            minGoalText.visibility = View.VISIBLE
            minGoalAmount.visibility = View.VISIBLE
            minGoalAmount.text = "R%.2f".format(budgetGoal.minimumSpendingGoal)

            // Add visual indicator if below minimum goal
            if (totalSpending < budgetGoal.minimumSpendingGoal) {
                minGoalAmount.setTextColor(getColor(R.color.orange))
            } else {
                minGoalAmount.setTextColor(getColor(R.color.text_medium))
            }
        }

        // Update the available balance and budget percentage
        calculateAndUpdateBalance(budgetGoal, totalSpending)
    }

    /**
     * Calculate available balance and budget percentage
     */
    private fun calculateAndUpdateBalance(budgetGoal: BudgetGoal, totalSpending: Double) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Calculate available budget balance
        val availableBalance = budgetGoal.totalBudget - totalSpending
        balanceAmountView.text = "R%.2f".format(availableBalance)

        // Calculate percentage of budget used
        val percentUsed = if (budgetGoal.totalBudget > 0) {
            (totalSpending / budgetGoal.totalBudget) * 100
        } else {
            0.0
        }

        // Update percentage text
        val budgetPercentage = root.findViewById<TextView>(R.id.budgetPercentage)
        budgetPercentage?.text = "${percentUsed.toInt()} %"

        // Update progress bar
        val budgetProgressBar = root.findViewById<ProgressBar>(R.id.budgetProgressBar)
        budgetProgressBar?.progress = percentUsed.toInt().coerceIn(0, 100)

        // Set color based on percentage
        if (budgetProgressBar != null) {
            val progressColor = when {
                percentUsed > 90 -> getColor(R.color.red_light)
                percentUsed > 75 -> getColor(R.color.orange)
                else -> getColor(R.color.primary_purple_light)
            }
            budgetProgressBar.progressTintList = ColorStateList.valueOf(progressColor)
        }
    }

    /**
     * Updates category progress bars and dynamically creates UI for custom categories
     */
    private fun updateCategoryProgressBars(spendingData: Map<String, Double>) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)
        val categoryBudgetMap = categoryBudgets.associateBy { it.categoryName }

        // Get the container where category items should be displayed
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)

        if (categoryContainer == null) {
            Log.e(TAG, "Category container not found in layout")
            return
        }

        // Clear ALL existing category views when user is logged in
        // Only keep the container itself
        if (currentUserId != -1) {
            categoryContainer.removeAllViews()
        }

        // Only show categories with transactions or budget for signed-in users
        if (currentUserId != -1) {
            // Get all categories that either have spending or a budget
            val allCategoriesToShow = (spendingData.keys + categoryBudgetMap.keys).toSet()

            // Limit to top 4 categories using DashboardHelper
            val topCategories = DashboardHelper.limitTopCategories(spendingData, 4)

            // Debug logging
            Log.d(TAG, "Categories to display: $topCategories")

            // Process each category
            for (categoryName in topCategories.keys) { // Iterate only through the top categories
                // Find spending for this category (default to 0 if none)
                val amount = spendingData[categoryName] ?: 0.0

                // Find budget for this category (default to 0 if none)
                val budget = categoryBudgetMap[categoryName]?.allocation ?: 0.0

                // Create and add a dynamic category UI
                try {
                    val categoryView = createCategoryView(categoryName, amount, budget)
                    categoryContainer.addView(categoryView)
                    Log.d(TAG, "Added category view for: $categoryName")
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating view for category $categoryName: ${e.message}", e)
                }
            }
        } else {
            // For non-logged in users, add placeholder categories
            addPlaceholderCategories(categoryContainer)
        }
    }


    /**
     * Get emoji for a category using the central EmojiUtils
     */
    private fun getCategoryEmoji(categoryName: String): String {
        return EmojiUtils.getCategoryEmoji(categoryName)
    }

    /**
     * Helper function to find category amount by checking multiple possible category names
     */
    private fun findCategoryAmount(
        spendingData: Map<String, Double>,
        vararg categoryNames: String
    ): Double {
        for (name in categoryNames) {
            spendingData[name.lowercase()]?.let { return it }
        }
        return 0.0
    }

    /**
     * Helper function to find category budget by checking multiple possible category names
     */
    private fun findCategoryBudget(
        budgetMap: Map<String, CategoryBudget>,
        vararg categoryNames: String
    ): Double {
        for (name in categoryNames) {
            budgetMap[name]?.let { return it.allocation }
            // Try case-insensitive match if needed
            budgetMap.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.let {
                return it.value.allocation
            }
        }
        return 0.0
    }

    /**
     * Creates a category view that matches the hardcoded design
     */
    private fun createCategoryView(categoryName: String, spending: Double, budget: Double): View {
        // Create a simple LinearLayout container
        val categoryView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                WRAP_CONTENT
            )
            setPadding(0, 0, 0, 12.dp)
        }

        // Top row with category name and amount
        val topRow = RelativeLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                WRAP_CONTENT
            )
        }

        // Get proper emoji for this category - FIXED
        val emoji = EmojiUtils.getCategoryEmoji(categoryName)

        // Category name with emoji
        val nameView = TextView(this).apply {
            text = "$emoji $categoryName"
            textSize = 14f
            setTextColor(getColor(R.color.text_medium))
            layoutParams = RelativeLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
            }
        }

        // Amount text (spent/budget)
        val amountView = TextView(this).apply {
            text = "R%.2f/R%.2f".format(spending, budget)
            textSize = 14f
            setTextColor(getColor(R.color.text_medium))
            layoutParams = RelativeLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
        }

        // Progress bar
        val percentUsed = if (budget > 0) (spending / budget) * 100 else 0.0
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                6.dp
            ).apply {
                topMargin = 4.dp
            }
            max = 100
            progress = percentUsed.toInt().coerceIn(0, 100)

            // Set color based on spending percentage
            val progressColor = when {
                percentUsed > 100 -> getColor(R.color.red_light)
                percentUsed > 85 -> getColor(R.color.orange)
                else -> getColor(R.color.teal_light)
            }
            progressTintList = ColorStateList.valueOf(progressColor)
            setBackgroundColor(getColor(R.color.background_gray))
        }

        // Assemble the view
        topRow.addView(nameView)
        topRow.addView(amountView)
        categoryView.addView(topRow)
        categoryView.addView(progressBar)

        return categoryView
    }

    private fun updateSpendingChart(spendingData: Map<String, Double>) {
        // Limit the data to the top 4 categories
        val topSpendingData = DashboardHelper.limitTopCategories(spendingData, 4)

        // Convert to the format needed by ChartUtils
        val chartData = topSpendingData.mapValues { it.value.toFloat() }

        // Create and display the chart
        val donutChart = ChartUtils.createDonutChartView(this, chartData)
        chartContainer.removeAllViews()
        chartContainer.addView(donutChart)

        // Update the spending legend
        updateSpendingLegend(topSpendingData)
    }

    private fun updateSpendingLegend(spendingData: Map<String, Double>) {
        // Clear existing items
        legendContainer.removeAllViews()

        if (spendingData.isEmpty()) {
            // Show a "No data" message if there's no spending
            val noDataText = TextView(this).apply {
                text = "No spending data for this period"
                textSize = 14f
                setTextColor(getColor(R.color.text_light))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 16.dp, 0, 16.dp)
            }
            legendContainer.addView(noDataText)
            return
        }

        // Create a row for each category
        for ((categoryName, amount) in spendingData) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val colorView = View(this).apply {
                val params = LinearLayout.LayoutParams(12.dp, 12.dp)
                params.setMargins(0, 0, 6.dp, 0)
                layoutParams = params
                background = DrawableUtils.getCategoryCircle(this@DashboardActivity, categoryName)
            }

            // Get emoji for the category - FIXED
            val emoji = EmojiUtils.getCategoryEmoji(categoryName)

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = "$emoji $categoryName"
                setTextColor(getColor(R.color.text_medium))
                textSize = 14f
            }

            val value = TextView(this).apply {
                text = "R${"%,.2f".format(amount)}"
                setTextColor(getColor(R.color.text_dark))
                textSize = 14f
            }

            row.addView(colorView)
            row.addView(label)
            row.addView(value)

            legendContainer.addView(row)
        }
    }

    private fun showNoBudgetGoalUI() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Update budget summary with zeros
        totalBudgetView.text = "R0.00"
        spentSoFarView.text = "R0.00"
        remainingView.text = "R0.00"

        // Show a message prompting the user to create a budget
        val createBudgetMessage = root.findViewById<TextView>(R.id.createBudgetMessage)
        if (createBudgetMessage != null) {
            createBudgetMessage.visibility = View.VISIBLE
        }

        // Show empty chart
        chartContainer.removeAllViews()
        val emptyChart = ChartUtils.createDonutChartView(this, emptyMap())
        chartContainer.addView(emptyChart)

        // Clear legend
        legendContainer.removeAllViews()
        val noDataText = TextView(this).apply {
            text = "No budget set for this month. Create one to get started!"
            textSize = 14f
            setTextColor(getColor(R.color.text_light))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 16.dp, 0, 16.dp)
        }
        legendContainer.addView(noDataText)

        // Clear the category container
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)
        if (categoryContainer != null) {
            categoryContainer.removeAllViews()
        }
    }

    private fun showPlaceholderData() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Sample budget values
        val sampleBudget = 5000.0
        val sampleSpending = 1513.47

        // Show sample budget data
        totalBudgetView.text = "R%.2f".format(sampleBudget)
        spentSoFarView.text = "R%.2f".format(sampleSpending)
        remainingView.text = "R%.2f".format(sampleBudget - sampleSpending)

        // Update available balance and percentage
        val availableBalance = sampleBudget - sampleSpending
        balanceAmountView.text = "R%.2f".format(availableBalance)

        // Update budget percentage
        val percentUsed = (sampleSpending / sampleBudget) * 100
        val budgetPercentage = root.findViewById<TextView>(R.id.budgetPercentage)
        budgetPercentage?.text = "${percentUsed.toInt()} %"

        // Update budget progress bar
        val budgetProgressBar = root.findViewById<ProgressBar>(R.id.budgetProgressBar)
        budgetProgressBar?.progress = percentUsed.toInt()

        // Get the categories container
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)
        if (categoryContainer != null) {
            // Clear existing items
            categoryContainer.removeAllViews()

            // Add placeholder categories
            addPlaceholderCategories(categoryContainer)
        }

        // Show sample chart
        val categoryData = mapOf(
            CategoryConstants.HOUSING to 650.0f,
            CategoryConstants.FOOD to 425.75f,
            CategoryConstants.TRANSPORT to 232.50f,
            CategoryConstants.ENTERTAINMENT to 205.02f
        )

        val donutChart = ChartUtils.createDonutChartView(this, categoryData)
        chartContainer.removeAllViews()
        chartContainer.addView(donutChart)

        // Populate sample spending legend
        populateSpendingLegend()
    }

    private fun populateSpendingLegend() {
        // Clear existing items
        legendContainer.removeAllViews()

        val categoryData = mapOf(
            CategoryConstants.HOUSING to 650.0,
            CategoryConstants.FOOD to 425.75,
            CategoryConstants.TRANSPORT to 232.50,
            CategoryConstants.ENTERTAINMENT to 205.02
        )

        for ((categoryName, amount) in categoryData) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER_VERTICAL
            }

            val colorView = View(this).apply {
                val params = LinearLayout.LayoutParams(12.dp, 12.dp)
                params.setMargins(0, 0, 6.dp, 0)
                layoutParams = params
                background = DrawableUtils.getCategoryCircle(this@DashboardActivity, categoryName)
            }

            val emoji = EmojiUtils.getCategoryEmoji(categoryName)

            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
                text = "$emoji ${
                    categoryName.lowercase().replaceFirstChar { it.uppercase() }
                }"
                setTextColor(getColor(R.color.text_medium))
                textSize = 14f
            }

            val value = TextView(this).apply {
                text = "R${"%,.2f".format(amount)}"
                setTextColor(getColor(R.color.text_dark))
                textSize = 14f
            }

            row.addView(colorView)
            row.addView(label)
            row.addView(value)

            legendContainer.addView(row)
        }
    }

    /**
     * Adds placeholder categories for guest users
     */
    private fun addPlaceholderCategories(container: LinearLayout) {
        // Housing sample placeholder
        val housingView = createCategoryView(
            CategoryConstants.HOUSING,
            650.0,
            800.0
        )
        container.addView(housingView)

        // Food sample placeholder
        val foodView = createCategoryView(
            CategoryConstants.FOOD,
            425.75,
            400.0
        )
        container.addView(foodView)

        // Transport sample placeholder
        val transportView = createCategoryView(
            CategoryConstants.TRANSPORT,
            232.50,
            250.0
        )
        container.addView(transportView)

        // Entertainment sample placeholder
        val entertainmentView = createCategoryView(
            CategoryConstants.ENTERTAINMENT,
            205.02,
            150.0
        )
        container.addView(entertainmentView)
    }

    /**
     * Sets up the recent transactions list.
     */
    private fun setupRecentTransactions() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)
        val recyclerView = root.findViewById<RecyclerView>(R.id.recentTransactionsRecyclerView)

        // Set up RecyclerView with empty adapter initially
        val transactionAdapter = TransactionAdapter(emptyList()) { clickedTransaction ->
            TransactionDetailBottomSheet.newInstance(clickedTransaction)
                .show(supportFragmentManager, "TransactionDetail")
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = transactionAdapter

        // Check if user is logged in
        if (currentUserId == -1) {
            // Show placeholder data for not logged in users
            val dummyTransactions = listOf(
                Transaction(
                    id = 1,
                    userId = -1,
                    merchant = "Checkers",
                    category = "Food",
                    amount = 98.00,
                    date = Date(),
                    isExpense = true
                ),
                Transaction(
                    id = 2,
                    userId = -1,
                    merchant = "Uber",
                    category = "Transport",
                    amount = 45.50,
                    date = Date(),
                    isExpense = true
                ),
                Transaction(
                    id = 3,
                    userId = -1,
                    merchant = "Salary",
                    category = "Income",
                    amount = 2500.00,
                    date = Date(),
                    isExpense = false
                )
            )
            transactionAdapter.updateList(dummyTransactions)
            return
        }

        // Load actual transactions using the data manager
        lifecycleScope.launch {
            try {
                // Get recent transactions for the current user, limiting to top 4
                val transactions = dataManager.loadRecentTransactions(currentUserId, 4)

                runOnUiThread {
                    if (transactions.isEmpty()) {
                        // Handle empty state - maybe show a message
                        val emptyView = root.findViewById<TextView>(R.id.emptyTransactionsMessage)
                        if (emptyView != null) {
                            emptyView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                        Log.d(TAG, "No transactions found for user $currentUserId")
                    } else {
                        // Update adapter with real data
                        val emptyView = root.findViewById<TextView>(R.id.emptyTransactionsMessage)
                        if (emptyView != null) {
                            emptyView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                        Log.d(
                            TAG,
                            "Loaded ${transactions.size} transactions for user $currentUserId"
                        )
                        transactionAdapter.updateList(transactions)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions", e)
                Toast.makeText(
                    this@DashboardActivity,
                    "Error loading transactions: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveUserSession(userId: Int) {
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        sharedPreferences.edit().putInt("current_user_id", userId).apply()
        Log.d(TAG, "Saved user session with ID: $userId")
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
        val userId = sharedPreferences.getInt("current_user_id", -1)
        Log.d(TAG, "Retrieved userId from SharedPreferences: $userId")
        return userId
    }

    /**
     * Ensures proper initialization of the budget goals section based on user login status
     */
    private fun initBudgetGoalsSection() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Get the container where category items should be displayed
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)

        if (categoryContainer == null) {
            Log.e(TAG, "Category container not found in layout")
            return
        }

        // Clear all existing views in the container
        categoryContainer.removeAllViews()

        // For logged-in users with no budget or spending, show empty state
        if (currentUserId != -1 && currentBudgetGoal == null) {
            showNoBudgetGoalUI()
        }
        // Otherwise, categories will be added dynamically in updateCategoryProgressBars
    }

    /**
     * Handles bottom navigation bar.
     */
    private fun setupBottomNavigation() {
        val bottomNavBar =
            findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavBar)
        bottomNavBar.selectedItemId = R.id.nav_dashboard

        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
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

    /**
     * Quick Actions: Add Expense, View Budget, and Goals.
     */
    private fun setupQuickActions() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        root.findViewById<LinearLayout>(R.id.addExpenseButton).setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        root.findViewById<LinearLayout>(R.id.viewBudgetButton).setOnClickListener {
            startActivity(Intent(this, BudgetGoalsActivity::class.java))
        }

        root.findViewById<LinearLayout>(R.id.goalsButton).setOnClickListener {
            startActivity(Intent(this, BudgetGoalsActivity::class.java))
        }
    }

    /**
     * Navigates to Budget Goals or Transactions screen.
     */
    private fun setupNavigationButtons() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        root.findViewById<TextView>(R.id.manageBudgetButton).setOnClickListener {
            startActivity(Intent(this, BudgetGoalsActivity::class.java))
        }

        root.findViewById<TextView>(R.id.seeAllTransactionsButton).setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        root.findViewById<TextView>(R.id.viewAllSpendingButton)?.setOnClickListener {
            startActivity(Intent(this, CategorySpendingActivity::class.java))
        }

        // Hide badges button (Gamification feature not implemented yet)
        val allBadgesButton = root.findViewById<TextView>(R.id.allBadgesButton)
        allBadgesButton?.visibility = View.GONE

    }

    /**
     * Setup simple static placeholders for dashboard icons - no actual achievements yet
     */
    private fun setupAchievementPlaceholders() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Simply hide the achievement section for Part 2 (Gamification feature not implemented yet)
        val achievementSection = root.findViewById<LinearLayout>(R.id.achievementsSection)
        achievementSection?.visibility = View.GONE
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
