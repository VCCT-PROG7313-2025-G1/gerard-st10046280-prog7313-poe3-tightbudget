package com.example.tightbudget

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tightbudget.adapters.TransactionAdapter
import com.example.tightbudget.data.AppDatabase
import com.example.tightbudget.databinding.ActivityTransactionsBinding
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.ui.TransactionDetailBottomSheet
import com.example.tightbudget.utils.CategoryConstants
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * Activity that displays a scrollable list of all transactions (expenses and income).
 * It uses a RecyclerView with TransactionAdapter and supports filter controls.
 */
class TransactionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionsBinding
    private lateinit var transactionAdapter: TransactionAdapter
    private val TAG = "TransactionsActivity"

    // Filter state variables
    private var allTransactions: List<Transaction> = emptyList()
    private var currentPeriod = "All Time"
    private var currentCategory = "All Categories"
    private var currentSortOption = "Date (Newest)"

    // Date range for filtering
    private var startDate: Date? = null
    private var endDate: Date? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val header = findViewById<View>(R.id.header)

        ViewCompat.setOnApplyWindowInsetsListener(header) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                insets.top, // Correct dynamic top padding
                view.paddingRight,
                view.paddingBottom
            )
            WindowInsetsCompat.CONSUMED
        }

        // Check if launched with a specific category filter
        if (intent.hasExtra("FILTER_CATEGORY")) {
            val filterCategory = intent.getStringExtra("FILTER_CATEGORY")

            // Set the category filter text
            if (!filterCategory.isNullOrEmpty()) {
                currentCategory = filterCategory
                binding.categoryFilter.text = filterCategory

                Log.d(TAG, "Filtering by category: $filterCategory")
            }

            // Check for date filters
            if (intent.hasExtra("START_DATE") && intent.hasExtra("END_DATE")) {
                val startDateLong = intent.getLongExtra("START_DATE", 0L)
                val endDateLong = intent.getLongExtra("END_DATE", 0L)

                if (startDateLong > 0 && endDateLong > 0) {
                    startDate = Date(startDateLong)
                    endDate = Date(endDateLong)

                    // Format dates for display
                    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val dateRangeText = "${dateFormat.format(startDate!!)} - ${dateFormat.format(endDate!!)}"
                    binding.periodFilter.text = dateRangeText

                    Log.d(TAG, "Date range filter: $dateRangeText")
                }
            }

            // Apply filters after UI is fully initialized
            binding.root.post {
                applyAllFilters()
            }
        }

        // Set up bottom navigation
        setupBottomNavigation()

        // Set up the RecyclerView and adapter
        setupRecyclerView()

        // Setup UI components
        setupHeader()
        setupFilterButtons()
        setupSearchIcon()
        setupLoadMoreButton()

        // Load transactions for current user
        loadUserTransactions()
    }

    private fun setupHeader() {
        // Add back button to header
        binding.backButton.setOnClickListener {
            finish()
        }

        // Center the title text
        binding.headerTitle.gravity = android.view.Gravity.CENTER
    }

    private fun setupSearchIcon() {
        binding.searchIcon.setOnClickListener {
            // Create a search dialog
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search, null)
            val searchView = dialogView.findViewById<SearchView>(R.id.searchView)

            val dialog = AlertDialog.Builder(this)
                .setTitle("Search Transactions")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create()

            // Set up search functionality
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    filterTransactionsBySearch(query)
                    dialog.dismiss()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    return false // Only filter on submit
                }
            })

            // Show the dialog
            dialog.show()

            // Make sure SearchView has focus and show keyboard
            searchView.requestFocus()

            // This will show the keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)

            // Make sure EditText in SearchView has focus
            val searchEditText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            searchEditText?.let {
                it.requestFocus()
                it.isFocusableInTouchMode = true
            }

            // Set dialog window properties to ensure it's correctly sized
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
    }

    private fun filterTransactionsBySearch(query: String?) {
        if (query.isNullOrBlank()) {
            // Reset to the current filter state
            applyAllFilters()
            return
        }

        val searchQuery = query.lowercase()
        val filteredList = allTransactions.filter { transaction ->
            transaction.merchant.lowercase().contains(searchQuery) ||
                    transaction.category.lowercase().contains(searchQuery) ||
                    (transaction.description?.lowercase()?.contains(searchQuery) ?: false)
        }

        transactionAdapter.updateList(filteredList)
        updateTransactionSummary(filteredList)
        binding.transactionsCount.text = "${filteredList.size} transactions"

        // Show search indicator
        Toast.makeText(this, "Showing results for: $query", Toast.LENGTH_SHORT).show()
    }

    private fun setupFilterButtons() {
        // Period filter
        binding.periodFilter.setOnClickListener {
            val popup = PopupMenu(this, binding.periodFilter)
            popup.menu.add("All Time")
            popup.menu.add("This Month")
            popup.menu.add("Previous Month")
            popup.menu.add("Last 3 Months")
            popup.menu.add("Last 6 Months")
            popup.menu.add("This Year")
            popup.menu.add("Custom Range...")

            popup.setOnMenuItemClickListener { item ->
                currentPeriod = item.title.toString()
                binding.periodFilter.text = currentPeriod

                when (currentPeriod) {
                    "This Month" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_MONTH, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time

                        calendar.add(Calendar.MONTH, 1)
                        calendar.add(Calendar.SECOND, -1)
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

                        calendar.add(Calendar.MONTH, 1)
                        calendar.add(Calendar.SECOND, -1)
                        endDate = calendar.time
                    }
                    "Last 3 Months" -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, -3)
                        startDate = calendar.time
                        endDate = Calendar.getInstance().time
                    }
                    "Last 6 Months" -> {
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.MONTH, -6)
                        startDate = calendar.time
                        endDate = Calendar.getInstance().time
                    }
                    "This Year" -> {
                        val calendar = Calendar.getInstance()
                        calendar.set(Calendar.DAY_OF_YEAR, 1)
                        calendar.set(Calendar.HOUR_OF_DAY, 0)
                        calendar.set(Calendar.MINUTE, 0)
                        calendar.set(Calendar.SECOND, 0)
                        startDate = calendar.time
                        endDate = Calendar.getInstance().time
                    }
                    "Custom Range..." -> {
                        showDateRangePicker()
                        return@setOnMenuItemClickListener true
                    }
                    else -> { // All Time
                        startDate = null
                        endDate = null
                    }
                }

                applyAllFilters()
                true
            }

            popup.show()
        }

        // Category filter
        binding.categoryFilter.setOnClickListener {
            val popup = PopupMenu(this, binding.categoryFilter)
            popup.menu.add("All Categories")

            // Add all category constants
            listOf(
                CategoryConstants.FOOD,
                CategoryConstants.HOUSING,
                CategoryConstants.TRANSPORT,
                CategoryConstants.ENTERTAINMENT,
                "Income" // Special category for income
            ).forEach {
                popup.menu.add(it)
            }

            popup.setOnMenuItemClickListener { item ->
                currentCategory = item.title.toString()
                binding.categoryFilter.text = currentCategory
                applyAllFilters()
                true
            }

            popup.show()
        }

        // Sort filter
        binding.sortFilter.setOnClickListener {
            val popup = PopupMenu(this, binding.sortFilter)
            popup.menu.add("Date (Newest)")
            popup.menu.add("Date (Oldest)")
            popup.menu.add("Amount (Highest)")
            popup.menu.add("Amount (Lowest)")
            popup.menu.add("Merchant (A-Z)")
            popup.menu.add("Merchant (Z-A)")

            popup.setOnMenuItemClickListener { item ->
                currentSortOption = item.title.toString()
                binding.sortFilter.text = currentSortOption
                applyAllFilters()
                true
            }

            popup.show()
        }
    }

    private fun showDateRangePicker() {

        val startCalendar = Calendar.getInstance()
        val endCalendar = Calendar.getInstance()

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
                        val rangeText = "${dateFormat.format(startDate!!)} - ${dateFormat.format(endDate!!)}"
                        binding.periodFilter.text = rangeText

                        applyAllFilters()
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

    private fun applyAllFilters() {
        var filtered = allTransactions

        // Apply date filter
        if (startDate != null && endDate != null) {
            filtered = filtered.filter { transaction ->
                transaction.date.time >= startDate!!.time &&
                        transaction.date.time <= endDate!!.time
            }
        }

        // Apply category filter
        if (currentCategory != "All Categories") {
            if (currentCategory == "Income") {
                filtered = filtered.filter { !it.isExpense }
            } else {
                filtered = filtered.filter {
                    it.category.equals(currentCategory, ignoreCase = true)
                }
            }
        }

        // Apply sorting
        filtered = when (currentSortOption) {
            "Date (Newest)" -> filtered.sortedByDescending { it.date }
            "Date (Oldest)" -> filtered.sortedBy { it.date }
            "Amount (Highest)" -> filtered.sortedByDescending { it.amount }
            "Amount (Lowest)" -> filtered.sortedBy { it.amount }
            "Merchant (A-Z)" -> filtered.sortedBy { it.merchant }
            "Merchant (Z-A)" -> filtered.sortedByDescending { it.merchant }
            else -> filtered.sortedByDescending { it.date }
        }

        // Update adapter and summary
        transactionAdapter.updateList(filtered)
        updateTransactionSummary(filtered)
        binding.transactionsCount.text = "${filtered.size} transactions"
    }

    private fun updateTransactionSummary(transactions: List<Transaction>) {
        // Calculate expenses and income
        val totalExpenses = transactions
            .filter { it.isExpense }
            .sumOf { it.amount }

        val totalIncome = transactions
            .filter { !it.isExpense }
            .sumOf { it.amount }

        val netAmount = totalIncome - totalExpenses

        // Update summary text with both income and expenses
        binding.monthSummary.text = "Income: +R${"%,.2f".format(totalIncome)}\n" +
                "Expenses: -R${"%,.2f".format(totalExpenses)}\n" +
                "Net: R${"%,.2f".format(netAmount)}"
    }

    private fun setupLoadMoreButton() {
        binding.loadMoreButton.isVisible = false // Initially hidden

        binding.loadMoreButton.setOnClickListener {
            // This would typically load more transactions with pagination
            Toast.makeText(this, "No more transactions to load", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prepares the RecyclerView with layout manager and adapter
     */
    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(emptyList()) { transaction ->
            // When a transaction is clicked, this will be triggered
            val bottomSheet = TransactionDetailBottomSheet.newInstance(transaction)
            bottomSheet.show(supportFragmentManager, "TransactionDetail")
        }

        binding.transactionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TransactionsActivity)
            adapter = transactionAdapter
        }
    }

    /**
     * Loads transactions for the current logged-in user
     */
    private fun loadUserTransactions() {
        val userId = getCurrentUserId()

        // Update summary info
        binding.transactionsCount.text = "Loading transactions..."

        if (userId == -1) {
            // User not logged in, show mock data
            Log.d(TAG, "No user logged in, showing mock data")
            val mockTransactions = generateMockTransactions()
            allTransactions = mockTransactions
            transactionAdapter.updateList(mockTransactions)
            updateTransactionSummary(mockTransactions)
            binding.transactionsCount.text = "${mockTransactions.size} transactions (sample)"
            return
        }

        // User is logged in, load their transactions
        val db = AppDatabase.getDatabase(this)
        val transactionDao = db.transactionDao()

        lifecycleScope.launch {
            try {
                val transactions = transactionDao.getAllTransactionsForUser(userId)

                runOnUiThread {
                    allTransactions = transactions

                    if (transactions.isEmpty()) {
                        // No transactions found
                        Log.d(TAG, "No transactions found for user $userId")
                        binding.transactionsCount.text = "No transactions found"
                    } else {
                        // Update adapter with real data
                        Log.d(TAG, "Loaded ${transactions.size} transactions for user $userId")
                        transactionAdapter.updateList(transactions)
                        updateTransactionSummary(transactions)
                        binding.transactionsCount.text = "${transactions.size} transactions"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading transactions", e)
                runOnUiThread {
                    Toast.makeText(
                        this@TransactionsActivity,
                        "Error loading transactions",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Fall back to mock data
                    val mockTransactions = generateMockTransactions()
                    allTransactions = mockTransactions
                    transactionAdapter.updateList(mockTransactions)
                    updateTransactionSummary(mockTransactions)
                    binding.transactionsCount.text = "${mockTransactions.size} transactions (sample)"
                }
            }
        }
    }

    /**
     * Get current user ID from SharedPreferences
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getInt("current_user_id", -1)
        Log.d(TAG, "Retrieved userId from SharedPreferences: $userId")
        return userId
    }

    /**
     * Generates sample transactions for testing UI
     */
    private fun generateMockTransactions(): List<Transaction> {
        return listOf(
            Transaction(
                id = 1,
                userId = -1,
                merchant = "ABC Inc",
                category = "Income",
                amount = 30000.00,
                date = Date(),
                isExpense = false,
                description = "Monthly salary"
            ),
            Transaction(
                id = 2,
                userId = -1,
                merchant = "CoCT",
                category = "Utilities",
                amount = 1100.00,
                date = Date(),
                isExpense = true,
                description = "Electricity and water"
            ),
            Transaction(
                id = 3,
                userId = -1,
                merchant = "UberEats",
                category = "Food",
                amount = 199.90,
                date = Date(),
                isExpense = true,
                description = "Dinner delivery"
            )
        )
    }

    /**
     * Handles bottom navigation bar.
     */
    private fun setupBottomNavigation() {
        val bottomNavBar = binding.bottomNavBar
        bottomNavBar.selectedItemId = R.id.nav_wallet

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

                R.id.nav_wallet -> true // Already on this screen

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