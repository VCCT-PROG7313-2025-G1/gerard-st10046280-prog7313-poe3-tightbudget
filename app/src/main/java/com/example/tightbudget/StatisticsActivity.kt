package com.example.tightbudget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityStatisticsBinding
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.firebase.FirebaseBudgetManager
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import com.example.tightbudget.utils.ChartUtils
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.example.tightbudget.utils.ProgressBarUtils
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

class StatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatisticsBinding
    private lateinit var firebaseDataManager: FirebaseDataManager
    private lateinit var firebaseBudgetManager: FirebaseBudgetManager
    private var userId: Int = -1

    // List of period buttons to update styling dynamically
    private lateinit var periodButtons: List<MaterialButton>

    // Chart state tracking
    private var showPieChart = true
    private var selectedPeriod = "Month"

    // Data for current period
    private var totalSpent = 0.0
    private var budgetAmount = 0.0
    private var categoryDataList = listOf<ChartUtils.CategorySpendingData>()
    private var dailySpendingData = mapOf<String, Float>()
    private var forecastAmount = 0.0f
    private var daysRemaining = 0
    private var previousPeriodSpent = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val highView = findViewById<View>(R.id.highSpendIndicator)
        val mediumView = findViewById<View>(R.id.mediumSpendIndicator)
        val lowView = findViewById<View>(R.id.lowSpendIndicator)

        DrawableUtils.applyCircleBackground(highView, ContextCompat.getColor(this, R.color.red_light))
        DrawableUtils.applyCircleBackground(mediumView, ContextCompat.getColor(this, R.color.orange))
        DrawableUtils.applyCircleBackground(lowView, ContextCompat.getColor(this, R.color.teal_light))

        // Initialize Firebase managers
        firebaseDataManager = FirebaseDataManager.getInstance()
        firebaseBudgetManager = FirebaseBudgetManager.getInstance()
        userId = getCurrentUserId()

        setupBottomNavigation()
        setupPeriodButtons()
        setupChartToggle()
        setupBackButton()

        // Load default period stats
        loadStatsForPeriod("Month")
    }

    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    /**
     * Highlight and respond to each period button (Week, Month, Quarter, Year)
     */
    private fun setupPeriodButtons() {
        periodButtons = listOf(
            binding.weekButton,
            binding.monthButton,
            binding.quarterButton,
            binding.yearButton
        )

        periodButtons.forEach { button ->
            button.setOnClickListener {
                highlightSelected(button)
                selectedPeriod = button.text.toString()
                loadStatsForPeriod(selectedPeriod)
            }
        }

        // Set Month as default selected
        highlightSelected(binding.monthButton)
    }

    /**
     * Visually highlight the selected period button
     */
    private fun highlightSelected(selectedButton: MaterialButton) {
        periodButtons.forEach { button ->
            val selected = button == selectedButton

            button.setBackgroundTintList(ContextCompat.getColorStateList(
                this, if (selected) R.color.teal_light else android.R.color.white))

            button.setTextColor(ContextCompat.getColor(
                this, if (selected) R.color.white else R.color.text_medium))

            button.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /**
     * Set up chart toggle functionality
     */
    private fun setupChartToggle() {
        binding.chartToggle.setOnClickListener {
            showPieChart = !showPieChart
            updateChartType()

            // Add subtle animation feedback
            binding.chartToggle.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.chartToggle.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    /**
     * Load statistics for the selected period using Firebase
     */
    private fun loadStatsForPeriod(period: String) {
        if (userId == -1) {
            // Show sample data for non-logged in users
            loadSampleStatsForPeriod(period)
            return
        }

        // Show loading state
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                Log.d("StatisticsActivity", "Loading stats for period: $period using Firebase")

                // Get date range for the selected period
                val (startDate, endDate, periodTitle, previousPeriodTitle) = getDateRangeForPeriod(period)

                // Update period title
                val displayTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(endDate)

                // Calculate days remaining in period
                val calendar = Calendar.getInstance()
                val today = calendar.time
                val daysTotal = getDaysInPeriod(period)
                daysRemaining = if (endDate.after(today)) {
                    ((endDate.time - today.time) / (24 * 60 * 60 * 1000)).toInt()
                } else {
                    0
                }
                val daysPassed = daysTotal - daysRemaining
                val daysRatio = daysPassed.toFloat() / daysTotal.toFloat()

                // Get transactions for this period from Firebase
                val transactions = firebaseDataManager.getTransactionsForPeriod(userId, startDate, endDate)
                    .filter { it.isExpense }

                Log.d("StatisticsActivity", "Loaded ${transactions.size} transactions from Firebase")

                // Get active budget goal from Firebase
                val monthYear = Calendar.getInstance().apply {
                    time = endDate
                }
                val currentMonth = monthYear.get(Calendar.MONTH) + 1 // 0-based to 1-based
                val currentYear = monthYear.get(Calendar.YEAR)

                val budgetGoal = firebaseBudgetManager.getBudgetGoalForMonth(userId, currentMonth, currentYear)
                    ?: firebaseBudgetManager.getActiveBudgetGoal(userId)

                budgetAmount = budgetGoal?.totalBudget ?: 0.0

                // Calculate total spent
                totalSpent = transactions.sumOf { it.amount }

                // Get previous period transactions for comparison from Firebase
                val (prevStartDate, prevEndDate) = getPreviousPeriodDates(period, startDate)
                val previousTransactions = firebaseDataManager.getTransactionsForPeriod(userId, prevStartDate, prevEndDate)
                    .filter { it.isExpense }
                previousPeriodSpent = previousTransactions.sumOf { it.amount }

                // Get category budgets from Firebase
                val categoryBudgets = if (budgetGoal != null) {
                    firebaseBudgetManager.getCategoryBudgetsForGoal(budgetGoal.id)
                } else {
                    emptyList()
                }

                Log.d("StatisticsActivity", "Loaded ${categoryBudgets.size} category budgets from Firebase")

                // Group transactions by category
                val categorySpending = transactions
                    .groupBy { it.category }
                    .mapValues { (_, txns) -> txns.sumOf { it.amount }.toFloat() }

                // Create category data list for charts
                categoryDataList = categorySpending.map { (categoryName, amount) ->
                    // Get emoji for category
                    val emoji = EmojiUtils.getCategoryEmoji(categoryName)

                    // Get color for category
                    val color = getCategoryColor(categoryName)

                    // Find budget allocation
                    val budgetLimit = categoryBudgets
                        .find { it.categoryName == categoryName }
                        ?.allocation?.toFloat() ?: 0f

                    ChartUtils.CategorySpendingData(
                        name = categoryName,
                        amount = amount,
                        budgetLimit = budgetLimit,
                        color = color,
                        emoji = emoji
                    )
                }.sortedByDescending { it.amount }

                // Get daily spending data for line chart
                dailySpendingData = getDailySpendingData(transactions, startDate, endDate)

                // Calculate spending forecast
                forecastAmount = if (daysRatio > 0) {
                    (totalSpent / daysRatio).toFloat()
                } else {
                    totalSpent.toFloat()
                }

                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    updateStatisticsUI(displayTitle)
                    binding.loadingOverlay.visibility = View.GONE
                }

            } catch (e: Exception) {
                Log.e("StatisticsActivity", "Error loading statistics from Firebase: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again"
                        e.message?.contains("permission") == true ->
                            "Permission denied. Please check your Firebase configuration"
                        else -> "Error loading statistics: ${e.message}"
                    }

                    Toast.makeText(this@StatisticsActivity, errorMessage, Toast.LENGTH_LONG).show()
                    binding.loadingOverlay.visibility = View.GONE

                    // Fall back to sample data
                    loadSampleStatsForPeriod(period)
                }
            }
        }
    }

    /**
     * Load sample statistics for non-logged in users or error fallback
     */
    private fun loadSampleStatsForPeriod(period: String) {
        Log.d("StatisticsActivity", "Loading sample stats for period: $period")

        // Show sample data
        val sampleTransactions = generateSampleTransactions()
        totalSpent = sampleTransactions.sumOf { it.amount }
        budgetAmount = 5000.0
        previousPeriodSpent = 1200.0
        daysRemaining = 15

        // Create sample category data
        val categorySpending = sampleTransactions
            .groupBy { it.category }
            .mapValues { (_, txns) -> txns.sumOf { it.amount }.toFloat() }

        categoryDataList = categorySpending.map { (categoryName, amount) ->
            ChartUtils.CategorySpendingData(
                name = categoryName,
                amount = amount,
                budgetLimit = amount * 1.2f, // 20% higher budget
                color = getCategoryColor(categoryName),
                emoji = EmojiUtils.getCategoryEmoji(categoryName)
            )
        }.sortedByDescending { it.amount }

        // Sample daily spending data
        dailySpendingData = mapOf(
            "1 Jan" to 50f,
            "2 Jan" to 80f,
            "3 Jan" to 60f,
            "4 Jan" to 90f,
            "5 Jan" to 30f,
            "6 Jan" to 100f,
            "7 Jan" to 70f
        )

        forecastAmount = (totalSpent * 1.5).toFloat()

        // Update UI
        val displayTitle = "January 2025 (Sample Data)"
        updateStatisticsUI(displayTitle)
        binding.loadingOverlay.visibility = View.GONE
    }

    /**
     * Generate sample transactions for non-logged in users
     */
    private fun generateSampleTransactions(): List<Transaction> {
        val calendar = Calendar.getInstance()
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
                merchant = "City of Cape Town",
                category = "Housing",
                amount = 1200.00,
                date = calendar.apply { add(Calendar.DAY_OF_MONTH, -15) }.time,
                isExpense = true,
                description = "Municipal services"
            )
        )
    }

    /**
     * Helper method to get category colors
     */
    private fun getCategoryColor(categoryName: String): Int {
        return when (categoryName.lowercase()) {
            "housing" -> Color.parseColor("#4CAF50")
            "food", "groceries" -> Color.parseColor("#FF9800")
            "transport" -> Color.parseColor("#2196F3")
            "entertainment" -> Color.parseColor("#9C27B0")
            "utilities" -> Color.parseColor("#FFC107")
            "health" -> Color.parseColor("#E91E63")
            "shopping" -> Color.parseColor("#00BCD4")
            "education" -> Color.parseColor("#3F51B5")
            else -> Color.parseColor("#9E9E9E")
        }
    }

    /**
     * Get date range based on selected period
     */
    private fun getDateRangeForPeriod(period: String): DateRangeResult {
        val calendar = Calendar.getInstance()
        val endDate = calendar.time
        val periodTitle: String
        val previousPeriodTitle: String

        // Set start date based on period
        when (period) {
            "Week" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                periodTitle = "This Week"
                previousPeriodTitle = "Last Week"
            }
            "Month" -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                val startOfMonth = calendar.time

                calendar.add(Calendar.MONTH, 1)
                calendar.add(Calendar.SECOND, -1)
                val endOfMonth = calendar.time

                periodTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startOfMonth)

                calendar.add(Calendar.MONTH, -2)
                previousPeriodTitle = SimpleDateFormat("MMMM", Locale.getDefault()).format(calendar.time)

                return DateRangeResult(startOfMonth, endOfMonth, periodTitle, previousPeriodTitle)
            }
            "Quarter" -> {
                val month = calendar.get(Calendar.MONTH)
                val quarterStart = month - (month % 3)
                calendar.set(Calendar.MONTH, quarterStart)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                periodTitle = "This Quarter"
                previousPeriodTitle = "Last Quarter"
            }
            "Year" -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                periodTitle = "This Year"
                previousPeriodTitle = "Last Year"
            }
            else -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                periodTitle = "Last 30 Days"
                previousPeriodTitle = "Previous 30 Days"
            }
        }

        val startDate = calendar.time
        return DateRangeResult(startDate, endDate, periodTitle, previousPeriodTitle)
    }

    /**
     * Get date range for the previous period
     */
    private fun getPreviousPeriodDates(period: String, currentStartDate: Date): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.time = currentStartDate

        when (period) {
            "Week" -> {
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val prevEndDate = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val prevStartDate = calendar.time
                return Pair(prevStartDate, prevEndDate)
            }
            "Month" -> {
                calendar.add(Calendar.MONTH, -1)
                val prevEndDate = calendar.time
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val prevStartDate = calendar.time
                return Pair(prevStartDate, prevEndDate)
            }
            "Quarter" -> {
                calendar.add(Calendar.MONTH, -3)
                val prevEndDate = calendar.time
                calendar.add(Calendar.MONTH, -3)
                val prevStartDate = calendar.time
                return Pair(prevStartDate, prevEndDate)
            }
            "Year" -> {
                calendar.add(Calendar.YEAR, -1)
                val prevEndDate = calendar.time
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                val prevStartDate = calendar.time
                return Pair(prevStartDate, prevEndDate)
            }
            else -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val prevEndDate = calendar.time
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                val prevStartDate = calendar.time
                return Pair(prevStartDate, prevEndDate)
            }
        }
    }

    /**
     * Get total days in the selected period
     */
    private fun getDaysInPeriod(period: String): Int {
        return when (period) {
            "Week" -> 7
            "Month" -> {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            }
            "Quarter" -> 91 // Approximate
            "Year" -> 365
            else -> 30
        }
    }

    /**
     * Get daily spending data for line chart
     */
    private fun getDailySpendingData(
        transactions: List<Transaction>,
        startDate: Date,
        endDate: Date
    ): Map<String, Float> {
        val result = mutableMapOf<String, Float>()
        val dateFormat = SimpleDateFormat("d MMM", Locale.getDefault())

        // Create entries for all days in range
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        val endMillis = endDate.time

        // Limit to last 7 days for weekly view
        var daysToShow = 7
        if (selectedPeriod == "Week") {
            calendar.add(Calendar.DAY_OF_YEAR, -7 + daysToShow)
        }

        while (calendar.time.time <= endMillis && daysToShow > 0) {
            val dateStr = dateFormat.format(calendar.time)
            result[dateStr] = 0f
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            daysToShow--
        }

        // Fill with actual transaction data
        for (transaction in transactions) {
            val dateStr = dateFormat.format(transaction.date)
            if (result.containsKey(dateStr)) {
                result[dateStr] = result[dateStr]!! + transaction.amount.toFloat()
            }
        }

        return result
    }

    /**
     * Update all UI components with loaded statistics
     */
    private fun updateStatisticsUI(periodTitle: String) {
        // Update period title
        binding.periodTitle.text = periodTitle

        // Update days remaining
        binding.daysRemaining.text = "$daysRemaining days left"
        binding.daysRemaining.visibility = if (daysRemaining > 0) View.VISIBLE else View.GONE

        // Update total spent
        binding.totalSpentText.text = "R${String.format("%,.2f", totalSpent)}"

        // Calculate and update comparison with previous period
        val percentChange = if (previousPeriodSpent > 0) {
            ((totalSpent - previousPeriodSpent) / previousPeriodSpent) * 100
        } else {
            0.0
        }

        val changeText = if (percentChange > 0) {
            "↑ ${String.format("%.1f", percentChange)}% from previous"
        } else if (percentChange < 0) {
            "↓ ${String.format("%.1f", Math.abs(percentChange))}% from previous"
        } else {
            "No change from previous period"
        }

        binding.periodComparison.text = changeText
        binding.periodComparison.setTextColor(
            ContextCompat.getColor(
                this,
                if (percentChange > 0) R.color.red_light else R.color.green_light
            )
        )

        // Update budget progress
        val percentUsed = if (budgetAmount > 0) (totalSpent / budgetAmount) * 100 else 0.0
        binding.budgetUsageText.text =
            "Budget: R${String.format("%,.2f", budgetAmount)} – ${percentUsed.toInt()}% used"

        binding.budgetProgress.progress = min(percentUsed.toInt(), 100)
        ProgressBarUtils.applyBudgetStatusProgressBar(
            binding.budgetProgress,
            this,
            totalSpent.toFloat(),
            budgetAmount.toFloat()
        )

        // Update charts
        updateChartType()

        // Update daily spending chart
        updateDailySpendingChart()

        // Update forecast
        updateForecastSection()

        // Update tips based on spending patterns
        updateSpendingTips()
    }

    /**
     * Update chart based on current toggle state
     */
    private fun updateChartType() {
        binding.categoryChartPlaceholder.removeAllViews()

        if (categoryDataList.isEmpty()) {
            binding.noDataMessage.visibility = View.VISIBLE
            binding.chartToggle.visibility = View.GONE

            // Make the no data message more helpful
            binding.noDataMessage.text = "📊 No spending data available for this period.\n\nStart adding transactions to see your spending breakdown!"
            binding.noDataMessage.textAlignment = View.TEXT_ALIGNMENT_CENTER

            return
        }

        binding.noDataMessage.visibility = View.GONE
        binding.chartToggle.visibility = View.VISIBLE

        val chartView = if (showPieChart) {
            // Show what chart will come next
            binding.chartToggleIcon.setImageResource(R.drawable.ic_bar_chart)


            ChartUtils.createEnhancedDonutChartView(
                this,
                categoryDataList,
                totalSpent.toFloat()
            )
        } else {
            binding.chartToggleIcon.setImageResource(R.drawable.ic_pie_chart)


            ChartUtils.createCategoryBarChartView(
                this,
                categoryDataList
            )
        }

        binding.categoryChartPlaceholder.addView(
            chartView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * Update daily spending line chart
     */
    private fun updateDailySpendingChart() {
        binding.lineChartPlaceholder.removeAllViews()

        if (dailySpendingData.isEmpty()) {
            binding.noDailyDataMessage.visibility = View.VISIBLE
            return
        }

        binding.noDailyDataMessage.visibility = View.GONE
        binding.lastDaysText.text = "Last ${dailySpendingData.size} days"

        val lineChart = ChartUtils.EnhancedLineChartView(
            this,
            dailySpendingData
        )

        binding.lineChartPlaceholder.addView(
            lineChart,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * Update forecast section with projected spending
     */
    private fun updateForecastSection() {
        val FORECAST_HIGH_VARIANCE = 1.1f
        val FORECAST_LOW_VARIANCE = 0.9f
        val MIN_DAYS_FOR_FORECAST = 3
        val MAX_PROJECTION_MULTIPLIER = 3.0 // Cap projections at 3x budget

        val totalDays = getDaysInPeriod(selectedPeriod).takeIf { it > 0 } ?: 30
        val daysPassed = totalDays - daysRemaining

        // Launch a coroutine to handle the suspend functions
        lifecycleScope.launch {
            // Get historical average if available (for logged-in users)
            val historicalMonthlyAverage = if (userId != -1) {
                getHistoricalAverage() ?: budgetAmount.toFloat()
            } else {
                budgetAmount.toFloat()
            }

            // Calculate base projection with different methods based on days passed
            val projectedAmount = when {
                // For very early days (0-2), use a weighted blend of historical and current pace
                daysPassed < MIN_DAYS_FOR_FORECAST -> {
                    val currentWeight = 0.3f * (daysPassed / MIN_DAYS_FOR_FORECAST.toFloat())
                    val historicalWeight = 1.0f - currentWeight

                    val currentPaceProjection = if (daysPassed > 0) {
                        (totalSpent / daysPassed) * totalDays
                    } else {
                        0.0 // No days passed means no projection from current pace
                    }

                    (currentPaceProjection * currentWeight) + (historicalMonthlyAverage * historicalWeight)
                }

                // For early-mid month (3-10 days), gradually increase reliance on current pace
                daysPassed < 10 -> {
                    val currentWeight = 0.5f + (0.5f * ((daysPassed - MIN_DAYS_FOR_FORECAST) / (10 - MIN_DAYS_FOR_FORECAST).toFloat()))
                    val historicalWeight = 1.0f - currentWeight

                    // Calculate current pace projection
                    val currentPaceProjection = (totalSpent / daysPassed) * totalDays

                    (currentPaceProjection * currentWeight) + (historicalMonthlyAverage * historicalWeight)
                }

                // For most of the month (10+ days), primarily use current pace
                else -> {
                    // Standard projection calculation
                    (totalSpent / daysPassed) * totalDays
                }
            }

            // Cap projection at a reasonable maximum to prevent absurd values
            val cappedProjection = minOf(projectedAmount, budgetAmount * MAX_PROJECTION_MULTIPLIER)

            // Forecast scenarios (+10%, base, -10%)
            val highSpend = cappedProjection * FORECAST_HIGH_VARIANCE
            val mediumSpend = cappedProjection
            val lowSpend = cappedProjection * FORECAST_LOW_VARIANCE

            // Update UI on the main thread
            withContext(Dispatchers.Main) {
                binding.highSpendText.text = "High spend: R${String.format("%,.0f", highSpend)}"
                binding.mediumSpendText.text = "Medium spend: R${String.format("%,.0f", mediumSpend)}"
                binding.lowSpendText.text = "Low spend: R${String.format("%,.0f", lowSpend)}"

                // Show forecast confidence indicator if early in period
                val confidenceLevel = when {
                    daysPassed < MIN_DAYS_FOR_FORECAST -> "Low"
                    daysPassed < 10 -> "Medium"
                    else -> "High"
                }

                if (daysPassed < 10) {
                    binding.forecastConfidence.visibility = View.VISIBLE
                    binding.forecastConfidence.text = "Forecast confidence: $confidenceLevel"
                } else {
                    binding.forecastConfidence.visibility = View.GONE
                }

                // Display OVER/UNDER budget forecast
                if (cappedProjection > budgetAmount) {
                    val overAmount = cappedProjection - budgetAmount
                    binding.forecastStatus.text = "Projected to be OVER budget by R${String.format("%,.0f", overAmount)}"
                    binding.forecastStatus.setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.red_light))
                } else {
                    val underAmount = budgetAmount - cappedProjection
                    binding.forecastStatus.text = "Projected to be UNDER budget by R${String.format("%,.0f", underAmount)}"
                    binding.forecastStatus.setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.green_light))
                }

                // Create and display forecast bar chart
                binding.trendContainer.removeAllViews()
                val forecastChart = ChartUtils.createForecastChartView(
                    this@StatisticsActivity,
                    totalSpent.toFloat(),
                    budgetAmount.toFloat(),
                    cappedProjection.toFloat(),
                    (daysPassed.toFloat() / totalDays.toFloat()).coerceIn(0.01f, 0.99f)
                )

                binding.trendContainer.addView(
                    forecastChart,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }
    }

    /**
     * Get historical average monthly spending (from past 3 months) using Firebase
     * Returns null if no historical data is available
     */
    private suspend fun getHistoricalAverage(): Float? {
        return try {
            // Get current calendar info
            val calendar = Calendar.getInstance()
            val currentMonth = calendar.get(Calendar.MONTH) + 1 // 0-based to 1-based
            val currentYear = calendar.get(Calendar.YEAR)

            // Create a list to store past 3 months
            val pastMonths = mutableListOf<Pair<Int, Int>>() // Month, Year pairs

            // Calculate past 3 months
            for (i in 1..3) {
                calendar.set(currentYear, currentMonth - 1, 1)
                calendar.add(Calendar.MONTH, -i)
                pastMonths.add(Pair(calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.YEAR)))
            }

            // Try to get total spending for each of the past 3 months using Firebase
            var totalPastSpending = 0.0
            var monthsWithData = 0

            pastMonths.forEach { (month, year) ->
                val budgetGoal = firebaseBudgetManager.getBudgetGoalForMonth(userId, month, year)

                if (budgetGoal != null) {
                    // Get start and end date for this month
                    calendar.set(year, month - 1, 1, 0, 0, 0)
                    val startDate = calendar.time

                    calendar.set(year, month - 1, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    val endDate = calendar.time

                    // Get transactions for this month from Firebase
                    val transactions = firebaseDataManager.getTransactionsForPeriod(userId, startDate, endDate)
                        .filter { it.isExpense }

                    if (transactions.isNotEmpty()) {
                        totalPastSpending += transactions.sumOf { it.amount }
                        monthsWithData++
                    }
                }
            }

            // Calculate average if we have data
            if (monthsWithData > 0) {
                (totalPastSpending / monthsWithData).toFloat()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("StatisticsActivity", "Error calculating historical average from Firebase: ${e.message}", e)
            null
        }
    }

    /**
     * Generate spending tips based on category analysis
     */
    private fun updateSpendingTips() {
        // Find most overspent category
        val overspentCategories = categoryDataList.filter {
            it.amount > it.budgetLimit && it.budgetLimit > 0
        }.sortedByDescending { it.amount - it.budgetLimit }

        if (overspentCategories.isNotEmpty()) {
            val worstCategory = overspentCategories.first()
            val overAmount = worstCategory.amount - worstCategory.budgetLimit

            binding.tipText.text = "💡 Tip: Reduce ${worstCategory.name} spending " +
                    "by R${String.format("%,.0f", overAmount)} to stay on budget"
        } else {
            binding.tipText.text = "💡 Tip: You're doing great! All categories are within budget."
        }
    }

    /**
     * Handles bottom navigation bar.
     */
    private fun setupBottomNavigation() {
        val bottomNavBar = binding.bottomNavBar
        bottomNavBar.selectedItemId = R.id.nav_reports

        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    overridePendingTransition(0, 0)
                    true
                }

                R.id.nav_reports -> true // Already on this screen

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

    // Handles the back button click
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Handle back button press
     */
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    /**
     * Data class to hold date range information
     */
    data class DateRangeResult(
        val startDate: Date,
        val endDate: Date,
        val periodTitle: String,
        val previousPeriodTitle: String
    )
}