package com.example.tightbudget

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tightbudget.adapters.TransactionAdapter
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.firebase.GamificationManager
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.ui.TransactionDetailBottomSheet
import com.example.tightbudget.utils.CategoryConstants
import com.example.tightbudget.utils.ChartUtils
import com.example.tightbudget.utils.DashboardHelper
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.example.tightbudget.utils.ProgressBarUtils
import kotlinx.coroutines.launch
import java.util.Date
import android.util.TypedValue
import android.view.ViewGroup
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.models.Achievement
import com.example.tightbudget.models.Category
import com.example.tightbudget.models.UserProgress
import kotlin.collections.find
import kotlin.collections.forEachIndexed

/**
 * Dashboard screen showing financial summary, goals, charts and quick access buttons.
 * Updated to use Firebase instead of Room database.
 */
class DashboardActivity : AppCompatActivity() {
    private val TAG = "DashboardActivity"

    // Firebase data manager - replaces Room database
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var gamificationManager: GamificationManager

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
    private var loadedCategories: List<Category> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize Firebase data manager
        firebaseDataManager = FirebaseDataManager.getInstance()
        gamificationManager = GamificationManager.getInstance()
        // Load categories for emojis
        loadCategoriesForEmojis()

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
        setupGamificationComponents()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to the dashboard
        loadUserInformation()
        loadFinancialData()
        loadGamificationData()
    }

    /**
     * Load user information for the header
     */
    private fun loadUserInformation() {
        val currentUserId = getCurrentUserId()

        if (currentUserId == -1) {
            // Guest user
            setupGuestHeader()
            return
        }

        lifecycleScope.launch {
            try {
                // Get user from Firebase
                val user = firebaseDataManager.getUserById(currentUserId)

                // Get gamification data for level and streak
                val userProgress = gamificationManager.getUserProgress(currentUserId)
                val userLevel = gamificationManager.calculateLevel(userProgress.totalPoints)

                runOnUiThread {
                    updateHeaderWithUserData(user?.fullName, userLevel, userProgress.currentStreak)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user information: ${e.message}", e)
                runOnUiThread {
                    setupGuestHeader()
                }
            }
        }
    }

    /**
     * Update header with real user data
     */
    private fun updateHeaderWithUserData(userName: String?, level: Int, streak: Int) {
        val headerRoot = findViewById<View>(R.id.dashboardHeaderRoot)

        // Update welcome message
        val welcomeText = headerRoot.findViewById<TextView>(R.id.welcomeText)
        welcomeText?.text = if (userName != null) {
            "Welcome back, ${userName.split(" ").firstOrNull() ?: userName}!"
        } else {
            "Welcome back!"
        }

        // Update profile level badge
        val profileLevelBadge = headerRoot.findViewById<TextView>(R.id.profileLevelBadge)
        profileLevelBadge?.text = level.toString()

        // Update streak count
        val headerStreakCount = headerRoot.findViewById<TextView>(R.id.headerStreakCount)
        headerStreakCount?.text = streak.toString()

        Log.d(TAG, "Header updated - User: $userName, Level: $level, Streak: $streak")
    }

    /**
     * Setup header for guest users
     */
    private fun setupGuestHeader() {
        val headerRoot = findViewById<View>(R.id.dashboardHeaderRoot)

        // Set guest welcome message
        val welcomeText = headerRoot.findViewById<TextView>(R.id.welcomeText)
        welcomeText?.text = "Welcome to TightBudget!"

        // Set guest level and streak
        val profileLevelBadge = headerRoot.findViewById<TextView>(R.id.profileLevelBadge)
        profileLevelBadge?.text = "0"

        val headerStreakCount = headerRoot.findViewById<TextView>(R.id.headerStreakCount)
        headerStreakCount?.text = "0"
    }

    /**
     * Load all financial data for the dashboard using Firebase
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

                // Show loading indicator
                showLoadingState(true)

                Log.d(TAG, "Loading financial data from Firebase for user $currentUserId")

                // Load budget goal for the current user from Firebase
                currentBudgetGoal = firebaseDataManager.loadActiveBudgetGoal(currentUserId)

                if (currentBudgetGoal != null) {
                    Log.d(TAG, "Found active budget goal: ${currentBudgetGoal!!.totalBudget}")

                    // Initialize the budget goals section
                    initBudgetGoalsSection()

                    // Load category budgets for the current user's budget goal from Firebase
                    categoryBudgets = firebaseDataManager.loadCategoryBudgets(currentBudgetGoal!!.id)
                    Log.d(TAG, "Loaded ${categoryBudgets.size} category budgets from Firebase")

                    // Load spending data for the current user from Firebase
                    val allSpendingData = firebaseDataManager.getCurrentMonthSpendingByCategory(currentUserId)
                    val totalSpending = firebaseDataManager.getCurrentMonthTotalSpending(currentUserId)

                    Log.d(TAG, "Current month spending: $totalSpending, categories: ${allSpendingData.size}")

                    // Update UI with real data from Firebase
                    runOnUiThread {
                        updateBudgetSummary(currentBudgetGoal!!, totalSpending)
                        updateCategoryProgressBars(allSpendingData)
                        updateSpendingChart(allSpendingData)
                        showLoadingState(false)
                    }

                    Log.d(TAG, "Successfully loaded budget goal and spending data from Firebase")
                } else {
                    // No budget goal found - show placeholder or prompt to create one
                    runOnUiThread {
                        initBudgetGoalsSection()
                        showNoBudgetGoalUI()
                        showLoadingState(false)
                    }

                    Log.d(TAG, "No active budget goal found in Firebase for user $currentUserId")
                }

                // Load transactions for the current user from Firebase
                setupRecentTransactions()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading financial data from Firebase: ${e.message}", e)

                runOnUiThread {
                    showLoadingState(false)

                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again"
                        e.message?.contains("permission") == true ->
                            "Permission denied. Please check your Firebase configuration"
                        else -> "Error loading data: ${e.message}"
                    }

                    Toast.makeText(this@DashboardActivity, errorMessage, Toast.LENGTH_LONG).show()

                    // Show fallback UI
                    showNoBudgetGoalUI()
                }
            }
        }
    }

    /**
     * Show/hide loading indicators
     */
    private fun showLoadingState(show: Boolean) {
        Log.d(TAG, if (show) "Showing loading state" else "Hiding loading state")
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
        currentBudgetGoal = null

        // Get the categories container and clear it (for logged-in users)
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)
        val categoryContainer = root.findViewById<LinearLayout>(R.id.categoryContainer)
        if (categoryContainer != null && currentUserId != -1) {
            categoryContainer.removeAllViews()
        }
    }

    private fun showDefaultUserInfo() {
        welcomeTextView.text = "Welcome, Guest!"
        balanceAmountView.text = "R0.00"
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

            minGoalText?.visibility = View.VISIBLE
            minGoalAmount?.visibility = View.VISIBLE
            minGoalAmount?.text = "R%.2f".format(budgetGoal.minimumSpendingGoal)

            // Add visual indicator if below minimum goal
            if (totalSpending < budgetGoal.minimumSpendingGoal) {
                minGoalAmount?.setTextColor(getColor(R.color.orange))
            } else {
                minGoalAmount?.setTextColor(getColor(R.color.text_medium))
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
        if (currentUserId != -1) {
            categoryContainer.removeAllViews()
        }

        // Only show categories with transactions or budget for signed-in users
        if (currentUserId != -1) {
            // Limit to top 4 categories using DashboardHelper
            val topCategories = DashboardHelper.limitTopCategories(spendingData, 4)

            // Debug logging
            Log.d(TAG, "Categories to display: $topCategories")

            // Process each category
            for (categoryName in topCategories.keys) {
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
     * Creates a category view that uses real category data including emoji
     * Now uses actual category emoji instead of EmojiUtils fallback
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

        // Get proper emoji for this category from stored categories
        val emoji = getCategoryEmojiFromFirebase(categoryName)

        // Category name with emoji
        val nameView = TextView(this).apply {
            text = "$emoji $categoryName"
            textSize = 14f
            setTextColor(getColor(R.color.text_medium))
            layoutParams = RelativeLayout.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT
            )
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

    /**
     * Get category emoji from Firebase data or fallback to EmojiUtils
     * This method gets the real emoji from stored categories
     */
    private fun getCategoryEmojiFromFirebase(categoryName: String): String {
        return try {
            // Check if we have loaded categories with their emojis
            loadedCategories.find { it.name.equals(categoryName, ignoreCase = true) }?.emoji
                ?: EmojiUtils.getCategoryEmoji(categoryName) // Fallback to hardcoded mapping
        } catch (e: Exception) {
            Log.e(TAG, "Error getting category emoji: ${e.message}")
            EmojiUtils.getCategoryEmoji(categoryName) // Fallback
        }
    }

    /**
     * Load categories from Firebase to get real emojis
     */
    private fun loadCategoriesForEmojis() {
        lifecycleScope.launch {
            try {
                val categoryManager = FirebaseCategoryManager.getInstance()
                loadedCategories = categoryManager.getAllCategories()
                Log.d(TAG, "Loaded ${loadedCategories.size} categories for emoji mapping")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading categories for emojis: ${e.message}", e)
                loadedCategories = emptyList()
            }
        }
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

            // Get emoji for the category
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
        createBudgetMessage?.visibility = View.VISIBLE

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
        categoryContainer?.removeAllViews()
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
                text = "$emoji ${categoryName.lowercase().replaceFirstChar { it.uppercase() }}"
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
        val housingView = createCategoryView(CategoryConstants.HOUSING, 650.0, 800.0)
        container.addView(housingView)

        // Food sample placeholder
        val foodView = createCategoryView(CategoryConstants.FOOD, 425.75, 400.0)
        container.addView(foodView)

        // Transport sample placeholder
        val transportView = createCategoryView(CategoryConstants.TRANSPORT, 232.50, 250.0)
        container.addView(transportView)

        // Entertainment sample placeholder
        val entertainmentView = createCategoryView(CategoryConstants.ENTERTAINMENT, 205.02, 150.0)
        container.addView(entertainmentView)
    }

    /**
     * Sets up the recent transactions list using Firebase data.
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

        // Load actual transactions using Firebase data manager
        lifecycleScope.launch {
            try {
                // Get recent transactions for the current user from Firebase, limiting to top 4
                val transactions = firebaseDataManager.loadRecentTransactions(currentUserId, 4)

                runOnUiThread {
                    if (transactions.isEmpty()) {
                        // Handle empty state - maybe show a message
                        val emptyView = root.findViewById<TextView>(R.id.emptyTransactionsMessage)
                        if (emptyView != null) {
                            emptyView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        }
                        Log.d(TAG, "No transactions found in Firebase for user $currentUserId")
                    } else {
                        // Update adapter with real data from Firebase
                        val emptyView = root.findViewById<TextView>(R.id.emptyTransactionsMessage)
                        if (emptyView != null) {
                            emptyView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                        }
                        Log.d(TAG, "Loaded ${transactions.size} transactions from Firebase for user $currentUserId")
                        transactionAdapter.updateList(transactions)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions from Firebase: ${e.message}", e)
                runOnUiThread {
                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error loading transactions"
                        else -> "Error loading transactions: ${e.message}"
                    }

                    Toast.makeText(this@DashboardActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
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
        val bottomNavBar = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavBar)
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

        root.findViewById<TextView>(R.id.manageBudgetButton)?.setOnClickListener {
            startActivity(Intent(this, BudgetGoalsActivity::class.java))
        }

        root.findViewById<TextView>(R.id.seeAllTransactionsButton)?.setOnClickListener {
            startActivity(Intent(this, TransactionsActivity::class.java))
        }

        root.findViewById<TextView>(R.id.viewAllSpendingButton)?.setOnClickListener {
            startActivity(Intent(this, CategorySpendingActivity::class.java))
        }
    }

    /**
     * Setup gamification components (replaces setupAchievementPlaceholders)
     */
    private fun setupGamificationComponents() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Show the achievement section (remove the hiding)
        val achievementSection = root.findViewById<LinearLayout>(R.id.achievementsSection)
        achievementSection?.visibility = View.VISIBLE

        // Show the challenges card
        val challengesCard = root.findViewById<View>(R.id.cardChallenges)
        challengesCard?.visibility = View.VISIBLE

        // View all badges button
        root.findViewById<TextView>(R.id.allBadgesButton)?.setOnClickListener {
            startActivity(Intent(this, AchievementsActivity::class.java))
        }

        // View All Challenges button
        root.findViewById<TextView>(R.id.viewAllChallengesButton)?.setOnClickListener {
            val currentUserId = getCurrentUserId()
            if (currentUserId == -1) {
                Toast.makeText(this, "Please log in to view challenges", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, DailyChallengesActivity::class.java))
            }
        }

        // Setup click listeners for challenges layouts
        root.findViewById<View>(R.id.challenge1Layout)?.setOnClickListener {
            navigateToChallenges("Challenge 1 clicked")
        }

        root.findViewById<View>(R.id.challenge2Layout)?.setOnClickListener {
            navigateToChallenges("Challenge 2 clicked")
        }

        root.findViewById<View>(R.id.challenge3Layout)?.setOnClickListener {
            navigateToChallenges("Challenge 3 clicked")
        }

        // Setup click listeners
        setupGamificationClickListeners()

        // Load real gamification data
        loadGamificationData()
        // If no user is logged in, setup guest state
        setupGuestAchievementsState()
    }

    /**
     * Helper method to navigate to challenges
     */
    private fun navigateToChallenges(logMessage: String) {
        val currentUserId = getCurrentUserId()
        if (currentUserId == -1) {
            Toast.makeText(this, "Please log in to view challenges", Toast.LENGTH_SHORT).show()
        } else {
            Log.d(TAG, logMessage)
            startActivity(Intent(this, DailyChallengesActivity::class.java))
        }
    }

    /**
     * Load real gamification data for dashboard components
     */
    private fun loadGamificationData() {
        val currentUserId = getCurrentUserId()

        if (currentUserId == -1) {
            setupGuestGamificationState()
            return
        }

        lifecycleScope.launch {
            try {
                // Get real gamification data
                val userProgress = gamificationManager.getUserProgress(currentUserId)
                val userLevel = gamificationManager.calculateLevel(userProgress.totalPoints)

                // Get today's challenges
                val todaysChallenges = gamificationManager.getUserDailyChallenges(currentUserId)
                val activeChallenges = todaysChallenges.filter {
                    isToday(it.dateAssigned) && it.expiresAt > System.currentTimeMillis()
                }

                // If no challenges exist, generate new ones
                val challenges = if (activeChallenges.isEmpty()) {
                    gamificationManager.generateDailyChallenges(currentUserId)
                } else {
                    activeChallenges
                }

                // Calculate points earned today
                val pointsEarnedToday = calculateTodaysPoints(currentUserId)

                runOnUiThread {
                    updateAchievementsComponent(userLevel, userProgress.totalPoints, userProgress.currentStreak, userProgress)
                    updateChallengesComponent(challenges, pointsEarnedToday)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading gamification data: ${e.message}", e)
                runOnUiThread {
                    setupGuestGamificationState()
                }
            }
        }
    }

    /**
     * Setup guest user achievements state
     */
    private fun setupGuestAchievementsState() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Set guest values for level/points/streak
        root.findViewById<TextView>(R.id.dashboardUserLevel)?.text = "0"
        root.findViewById<TextView>(R.id.dashboardUserPoints)?.text = "0"
        root.findViewById<TextView>(R.id.dashboardUserStreak)?.text = "0"

        // Set achievement progress for guest
        root.findViewById<TextView>(R.id.achievementProgressText)?.text = "0/21"
        root.findViewById<ProgressBar>(R.id.achievementProgressBar)?.progress = 0
        root.findViewById<TextView>(R.id.achievementProgressSubtext)?.text = "Create an account to start unlocking achievements!"

        // Hide recent achievement for guest
        root.findViewById<LinearLayout>(R.id.recentAchievementLayout)?.visibility = View.GONE

        // Set guest state for top achievements
        val guestAchievements = listOf(
            Triple("🎯", "First Steps", false),
            Triple("📝", "Getting Started", false),
            Triple("📄", "Receipt Rookie", false)
        )

        val achievementViews = listOf(
            Pair(root.findViewById<TextView>(R.id.achievement1Icon), root.findViewById<TextView>(R.id.achievement1Title)),
            Pair(root.findViewById<TextView>(R.id.achievement2Icon), root.findViewById<TextView>(R.id.achievement2Title)),
            Pair(root.findViewById<TextView>(R.id.achievement3Icon), root.findViewById<TextView>(R.id.achievement3Title))
        )

        guestAchievements.forEachIndexed { index, (emoji, title, _) ->
            if (index < achievementViews.size) {
                val (iconView, titleView) = achievementViews[index]

                iconView?.text = emoji
                titleView?.text = title
                iconView?.alpha = 0.3f
                iconView?.background = ContextCompat.getDrawable(this, R.drawable.achievement_mini_background)
                titleView?.setTextColor(ContextCompat.getColor(this, R.color.text_light))
            }
        }
    }

    /**
     * Update the achievements component card
     */
    private fun updateAchievementsComponent(level: Int, points: Int, streak: Int, userProgress: com.example.tightbudget.models.UserProgress) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Update level, points, streak
        root.findViewById<TextView>(R.id.dashboardUserLevel)?.text = level.toString()
        root.findViewById<TextView>(R.id.dashboardUserPoints)?.text = points.toString()
        root.findViewById<TextView>(R.id.dashboardUserStreak)?.text = streak.toString()

        lifecycleScope.launch {
            try {
                // Get all achievements for progress calculation
                val allAchievements = gamificationManager.getAllAchievements()
                val unlockedCount = userProgress.achievementsUnlocked.size
                val totalCount = allAchievements.size

                // Calculate overall progress
                val progressPercentage = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0

                // Get recent achievement (most recently unlocked)
                val recentAchievement = if (userProgress.achievementsUnlocked.isNotEmpty()) {
                    allAchievements.find { it.id == userProgress.achievementsUnlocked.lastOrNull() }
                } else null

                // Get top 3 achievements to show (first 3 that are unlocked or closest to unlock)
                val topAchievements = getTopAchievementsForDashboard(allAchievements, userProgress)

                runOnUiThread {
                    updateAchievementProgress(root, unlockedCount, totalCount, progressPercentage, userProgress)
                    updateRecentAchievement(root, recentAchievement)
                    updateTopAchievements(root, topAchievements)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating achievements component: ${e.message}", e)
            }
        }

        Log.d(TAG, "Dashboard achievements updated - Level: $level, Points: $points, Streak: $streak")
    }

    /**
     * Update achievement progress section
     */
    private fun updateAchievementProgress(
        root: View,
        unlockedCount: Int,
        totalCount: Int,
        progressPercentage: Int,
        userProgress: com.example.tightbudget.models.UserProgress
    ) {
        root.findViewById<TextView>(R.id.achievementProgressText)?.text = "$unlockedCount/$totalCount"
        root.findViewById<ProgressBar>(R.id.achievementProgressBar)?.progress = progressPercentage

        val subtextMessage = when {
            unlockedCount == 0 -> "Add your first transaction to unlock achievements!"
            unlockedCount < 5 -> "Great start! Keep logging transactions to unlock more."
            unlockedCount < 10 -> "You're on fire! ${totalCount - unlockedCount} achievements remaining."
            unlockedCount < totalCount -> "Almost there! Only ${totalCount - unlockedCount} left to unlock."
            else -> "🎉 Achievement Master! All achievements unlocked!"
        }

        root.findViewById<TextView>(R.id.achievementProgressSubtext)?.text = subtextMessage
    }

    /**
     * Update recent achievement section
     */
    private fun updateRecentAchievement(root: View, recentAchievement: com.example.tightbudget.models.Achievement?) {
        val recentLayout = root.findViewById<LinearLayout>(R.id.recentAchievementLayout)

        if (recentAchievement != null) {
            recentLayout?.visibility = View.VISIBLE
            root.findViewById<TextView>(R.id.recentAchievementIcon)?.text = recentAchievement.emoji
            root.findViewById<TextView>(R.id.recentAchievementTitle)?.text = recentAchievement.title
            root.findViewById<TextView>(R.id.recentAchievementPoints)?.text = "+${recentAchievement.pointsRequired} pts"
        } else {
            recentLayout?.visibility = View.GONE
        }
    }

    /**
     * Update top 3 achievements preview
     */
    private fun updateTopAchievements(root: View, topAchievements: List<AchievementDisplayInfo>) {
        val achievementViews = listOf(
            Pair(root.findViewById<TextView>(R.id.achievement1Icon), root.findViewById<TextView>(R.id.achievement1Title)),
            Pair(root.findViewById<TextView>(R.id.achievement2Icon), root.findViewById<TextView>(R.id.achievement2Title)),
            Pair(root.findViewById<TextView>(R.id.achievement3Icon), root.findViewById<TextView>(R.id.achievement3Title))
        )

        topAchievements.forEachIndexed { index, achievementInfo ->
            if (index < achievementViews.size) {
                val (iconView, titleView) = achievementViews[index]

                iconView?.text = achievementInfo.achievement.emoji
                titleView?.text = achievementInfo.achievement.title

                if (achievementInfo.isUnlocked) {
                    iconView?.alpha = 1f
                    iconView?.background = ContextCompat.getDrawable(this, R.drawable.achievement_mini_unlocked)
                    titleView?.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                } else {
                    iconView?.alpha = 0.6f
                    iconView?.background = ContextCompat.getDrawable(this, R.drawable.achievement_mini_background)
                    titleView?.setTextColor(ContextCompat.getColor(this, R.color.text_medium))
                }
            }
        }
    }

    /**
     * Get top achievements for dashboard display
     */
    private suspend fun getTopAchievementsForDashboard(
        allAchievements: List<com.example.tightbudget.models.Achievement>,
        userProgress: com.example.tightbudget.models.UserProgress
    ): List<AchievementDisplayInfo> {
        return try {
            val transactions = firebaseDataManager.getAllTransactionsForUser(currentUserId)

            allAchievements.take(3).map { achievement ->
                val isUnlocked = userProgress.achievementsUnlocked.contains(achievement.id)
                val progress = calculateAchievementProgress(achievement, userProgress, transactions)

                AchievementDisplayInfo(
                    achievement = achievement,
                    isUnlocked = isUnlocked,
                    currentProgress = progress,
                    progressPercentage = if (achievement.targetValue > 0) {
                        minOf(100, (progress * 100) / achievement.targetValue)
                    } else 100
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting top achievements: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Calculate achievement progress for dashboard
     */
    private fun calculateAchievementProgress(
        achievement: Achievement,
        userProgress: UserProgress,
        transactions: List<Transaction>
    ): Int {
        return when (achievement.type) {
            com.example.tightbudget.models.AchievementType.TRANSACTIONS -> userProgress.transactionCount
            com.example.tightbudget.models.AchievementType.RECEIPTS -> userProgress.receiptsUploaded
            com.example.tightbudget.models.AchievementType.STREAK -> userProgress.longestStreak
            com.example.tightbudget.models.AchievementType.POINTS -> userProgress.totalPoints
            com.example.tightbudget.models.AchievementType.BUDGET_GOALS -> userProgress.budgetGoalsMet
            com.example.tightbudget.models.AchievementType.CATEGORIES -> {
                transactions.map { it.category }.distinct().size
            }
            else -> 0
        }
    }

    /**
     * Update dashboard badges display
     */
    private fun updateDashboardBadges(badges: List<Triple<String, String, Boolean>>) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        val badgeViews = listOf(
            Pair(root.findViewById<TextView>(R.id.saverBadgeIcon), root.findViewById<TextView>(R.id.saverBadgeLabel)),
            Pair(root.findViewById<TextView>(R.id.consistentBadgeIcon), root.findViewById<TextView>(R.id.consistentBadgeLabel)),
            Pair(root.findViewById<TextView>(R.id.transportBadgeIcon), root.findViewById<TextView>(R.id.transportBadgeLabel)),
            Pair(root.findViewById<TextView>(R.id.lockedBadgeIcon), root.findViewById<TextView>(R.id.lockedBadgeLabel))
        )

        badges.forEachIndexed { i, badge ->
            if (i < badgeViews.size) {
                val (achievementId, displayName, earned) = badge
                val (badgeIcon, badgeLabel) = badgeViews[i]

                if (badgeIcon != null && badgeLabel != null) {
                    // Set the emoji for the badge
                    badgeIcon.text = EmojiUtils.getAchievementEmoji(achievementId)
                    badgeLabel.text = displayName

                    // Apply styling
                    if (earned) {
                        DrawableUtils.applyCircleBackground(
                            badgeIcon,
                            ContextCompat.getColor(this, R.color.teal_light)
                        )
                        badgeIcon.alpha = 1f
                        badgeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_dark))
                    } else {
                        DrawableUtils.applyCircleBackground(
                            badgeIcon,
                            ContextCompat.getColor(this, R.color.background_gray)
                        )
                        badgeIcon.alpha = 0.5f
                        badgeLabel.setTextColor(ContextCompat.getColor(this, R.color.text_light))
                    }
                }
            }
        }
    }

    /**
     * Update the challenges component card
     */
    private fun updateChallengesComponent(challenges: List<com.example.tightbudget.models.DailyChallenge>, pointsToday: Int) {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Update header
        root.findViewById<TextView>(R.id.todayPointsEarned)?.text = "+$pointsToday pts today"

        // Update progress summary
        val completedCount = challenges.count { it.isCompleted }
        val totalCount = challenges.size
        val progressPercent = if (totalCount > 0) (completedCount * 100) / totalCount else 0
        val totalPointsEarned = challenges.filter { it.isCompleted }.sumOf { it.pointsReward }

        root.findViewById<TextView>(R.id.challengesSummaryText)?.text = "$completedCount of $totalCount challenges completed"
        root.findViewById<ProgressBar>(R.id.challengesProgressBar)?.progress = progressPercent
        root.findViewById<TextView>(R.id.challengesPointsTotal)?.text = "+$totalPointsEarned pts"

        // Update individual challenges
        updateChallengeItem(root, challenges.getOrNull(0), R.id.challenge1Checkbox, R.id.challenge1Text, R.id.challenge1Progress, true)
        updateChallengeItem(root, challenges.getOrNull(1), R.id.challenge2Checkbox, R.id.challenge2Text, R.id.challenge2Progress, false)
        updateChallengeItem(root, challenges.getOrNull(2), R.id.challenge3Checkbox, R.id.challenge3Text, R.id.challenge3Progress, false)

        Log.d(TAG, "Dashboard challenges updated - Completed: $completedCount/$totalCount, Points: $totalPointsEarned")
    }

    /**
     * Update individual challenge item
     */
    private fun updateChallengeItem(
        root: View,
        challenge: com.example.tightbudget.models.DailyChallenge?,
        checkboxId: Int,
        textId: Int,
        progressId: Int,
        showProgress: Boolean
    ) {
        val checkbox = root.findViewById<CheckBox>(checkboxId)
        val text = root.findViewById<TextView>(textId)
        val progress = root.findViewById<TextView>(progressId)

        if (challenge != null) {
            checkbox?.isChecked = challenge.isCompleted
            text?.text = challenge.description

            if (showProgress) {
                val currentProgress = getCurrentChallengeProgress(challenge)
                progress?.text = "$currentProgress/${challenge.targetValue}"
            } else {
                progress?.text = "+${challenge.pointsReward} pts"
            }
        } else {
            checkbox?.isChecked = false
            text?.text = "No more challenges today"
            progress?.text = if (showProgress) "0/0" else "+0 pts"
        }
    }

    /**
     * Setup guest user gamification state for dashboard
     */
    private fun setupGuestGamificationState() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // Set guest values for achievements component
        root.findViewById<TextView>(R.id.dashboardUserLevel)?.text = "0"
        root.findViewById<TextView>(R.id.dashboardUserPoints)?.text = "0"
        root.findViewById<TextView>(R.id.dashboardUserStreak)?.text = "0"

        // Set guest values for challenges component
        root.findViewById<TextView>(R.id.todayPointsEarned)?.text = "+0 pts today"
        root.findViewById<TextView>(R.id.challengesSummaryText)?.text = "Log in to see challenges"
        root.findViewById<ProgressBar>(R.id.challengesProgressBar)?.progress = 0
        root.findViewById<TextView>(R.id.challengesPointsTotal)?.text = "+0 pts"

        // Set guest challenge items
        root.findViewById<CheckBox>(R.id.challenge1Checkbox)?.isChecked = false
        root.findViewById<TextView>(R.id.challenge1Text)?.text = "Create account to unlock challenges"
        root.findViewById<TextView>(R.id.challenge1Progress)?.text = "0/0"

        root.findViewById<CheckBox>(R.id.challenge2Checkbox)?.isChecked = false
        root.findViewById<TextView>(R.id.challenge2Text)?.text = "Log in to start earning points"
        root.findViewById<TextView>(R.id.challenge2Progress)?.text = "+0 pts"

        root.findViewById<CheckBox>(R.id.challenge3Checkbox)?.isChecked = false
        root.findViewById<TextView>(R.id.challenge3Text)?.text = "Sign up to track your progress"
        root.findViewById<TextView>(R.id.challenge3Progress)?.text = "+0 pts"

        // Set guest badges
        val guestBadges = listOf(
            Triple("Locked", "Locked", false),
            Triple("Locked", "Locked", false),
            Triple("Locked", "Locked", false),
            Triple("Locked", "Locked", false)
        )
        updateDashboardBadges(guestBadges)
    }

    /**
     * Setup click listeners for gamification elements
     */
    private fun setupGamificationClickListeners() {
        val root = findViewById<View>(R.id.dashboardMainCardsRoot)

        // View Profile button (in achievements component)
        root.findViewById<TextView>(R.id.allBadgesButton)?.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // View All Challenges button
        root.findViewById<TextView>(R.id.viewAllChallengesButton)?.setOnClickListener {
            val currentUserId = getCurrentUserId()
            if (currentUserId == -1) {
                Toast.makeText(this, "Please log in to view challenges", Toast.LENGTH_SHORT).show()
            } else {
                // Navigate to challenges
                startActivity(Intent(this, DailyChallengesActivity::class.java))
                Log.d(TAG, "Navigating to DailyChallengesActivity")
            }
        }
    }

    /**
     * Calculate points earned today (simplified estimation)
     */
    private suspend fun calculateTodaysPoints(userId: Int): Int {
        return try {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val transactions = firebaseDataManager.getAllTransactionsForUser(userId)
            val todayTransactions = transactions.filter { transaction ->
                transaction.dateTimestamp >= todayStart.timeInMillis
            }

            // Estimate points: 10 per transaction + 15 for receipts
            var points = todayTransactions.size * 10
            points += todayTransactions.count { !it.receiptPath.isNullOrEmpty() } * 15

            points
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating today's points: ${e.message}", e)
            0
        }
    }

    /**
     * Get current progress for a challenge (simplified)
     */
    private fun getCurrentChallengeProgress(challenge: com.example.tightbudget.models.DailyChallenge): Int {
        // Simplified progress calculation
        return if (challenge.isCompleted) challenge.targetValue else kotlin.random.Random.nextInt(0, challenge.targetValue)
    }

    /**
     * Check if timestamp is today
     */
    private fun isToday(timestamp: Long): Boolean {
        val today = java.util.Calendar.getInstance()
        val checkDate = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }

        return today.get(java.util.Calendar.YEAR) == checkDate.get(java.util.Calendar.YEAR) &&
                today.get(java.util.Calendar.DAY_OF_YEAR) == checkDate.get(java.util.Calendar.DAY_OF_YEAR)
    }

    // Extension property to convert Int to dp
    private val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            resources.displayMetrics
        ).toInt()

    // Extension function to set margins for a View
    private fun View.setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        if (this.layoutParams is ViewGroup.MarginLayoutParams) {
            val p = this.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(left, top, right, bottom)
            this.requestLayout()
        }
    }

    /**
     * Data class for achievement display on dashboard
     */
    data class AchievementDisplayInfo(
        val achievement: com.example.tightbudget.models.Achievement,
        val isUnlocked: Boolean,
        val currentProgress: Int,
        val progressPercentage: Int
    )
}
