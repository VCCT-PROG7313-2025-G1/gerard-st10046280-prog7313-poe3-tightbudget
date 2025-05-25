package com.example.tightbudget

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.ActivityAddTransactionBinding
import com.example.tightbudget.firebase.FirebaseTransactionManager
import com.example.tightbudget.models.CategoryItem
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.ui.CategoryPickerBottomSheet
import com.example.tightbudget.ui.CreateCategoryBottomSheet
import com.example.tightbudget.utils.CategoryConstants
import com.example.tightbudget.utils.EmojiUtils
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Constants for camera and storage permissions
private const val CAMERA_PERMISSION_CODE = 100
private const val STORAGE_PERMISSION_CODE = 101

class AddTransactionActivity : AppCompatActivity() {
    // Binds layout elements from activity_add_transaction.xml to this file
    private lateinit var binding: ActivityAddTransactionBinding
    private val TAG = "AddTransactionActivity"

    // Variables to track the current state
    private var selectedCategory: CategoryItem? = null
    private var isExpense = true
    private var isRecurring = false
    private var selectedDate = Calendar.getInstance()
    private var receiptImageUri: Uri? = null

    private fun checkAndRequestPermissions() {
        // Check if we have camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request camera permission if we don't have it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // For Android 9 (Pie) and below, also check storage permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        } else {
            // We have all the permissions, so proceed with taking the photo
            takePhoto()
        }
    }

    // Method to handle permission results
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Camera permission granted, now check storage if needed
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            STORAGE_PERMISSION_CODE
                        )
                    } else {
                        // All permissions granted, proceed with camera
                        takePhoto()
                    }
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Camera permission is required to take receipt photos",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            STORAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storage permission granted, proceed with camera
                    takePhoto()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Storage permission is required to save receipt photos",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // Handles capturing a receipt photo using the device camera
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && receiptImageUri != null) {
            binding.receiptImageView.setImageURI(receiptImageUri)
            binding.receiptImageView.visibility = View.VISIBLE
            binding.addPhotoButton.visibility = View.GONE
            Log.d(TAG, "Receipt image captured successfully")
        } else {
            Log.d(TAG, "Failed to capture receipt image")
        }
    }

    // Handles selecting an image from the device gallery
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            receiptImageUri = uri
            binding.receiptImageView.setImageURI(receiptImageUri)
            binding.receiptImageView.visibility = View.VISIBLE
            binding.addPhotoButton.visibility = View.GONE
            Log.d(TAG, "Receipt image selected from gallery")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewAllCategoriesButton.setOnClickListener {
            showCategoryPicker()
        }

        binding.addCategoryChip.setOnClickListener {
            showCategoryPicker()
        }

        binding.createNewCategoryButton.setOnClickListener {
            showCreateCategoryModal()
        }

        setupTransactionTypeToggle() // Switch between Expense and Income
        setupCategoryChips()        // Category options with emojis
        setupTransactionDatePicker()// Main date of transaction
        setupRecurringSwitch()      // Toggle for recurring transaction
        setupPhotoButton()          // Option to attach photo
        setupSaveButton()           // Save and validate inputs
        setupBackButton()           // Handle back navigation
        setupBottomNavigation()     // Bottom navigation bar

        Log.d(TAG, "AddTransactionActivity created")
    }

    // This function sets up toggle buttons to switch between 'Expense' and 'Income'
    private fun setupTransactionTypeToggle() {
        binding.expenseButton.isChecked = true

        binding.expenseButton.setOnClickListener {
            isExpense = true
            updateTransactionTypeUI()
        }

        binding.incomeButton.setOnClickListener {
            isExpense = false
            updateTransactionTypeUI()
        }
    }

    // Updates labels and hints when switching between Expense and Income
    private fun updateTransactionTypeUI() {
        if (isExpense) {
            binding.expenseButton.setTextColor(getColor(R.color.white))
            binding.expenseButton.setBackgroundColor(getColor(R.color.teal_light))
            binding.incomeButton.setTextColor(getColor(R.color.text_medium))
            binding.incomeButton.setBackgroundColor(getColor(R.color.white))

            binding.merchantInput.hint = "Who did you pay?"
            binding.merchantInputLabel.text = "Merchant"
        } else {
            binding.incomeButton.setTextColor(getColor(R.color.white))
            binding.incomeButton.setBackgroundColor(getColor(R.color.teal_light))
            binding.expenseButton.setTextColor(getColor(R.color.text_medium))
            binding.expenseButton.setBackgroundColor(getColor(R.color.white))

            binding.merchantInput.hint = "Where is the money from?"
            binding.merchantInputLabel.text = "Income Source"
        }
    }

    // Initialises the category chips and assigns emojis using EmojiUtils
    private fun setupCategoryChips() {
        binding.foodChip.isChecked = true

        binding.foodChip.text = EmojiUtils.getCategoryEmoji(CategoryConstants.FOOD)
        binding.transportChip.text = EmojiUtils.getCategoryEmoji(CategoryConstants.TRANSPORT)
        binding.entertainmentChip.text =
            EmojiUtils.getCategoryEmoji(CategoryConstants.ENTERTAINMENT)
        binding.housingChip.text = EmojiUtils.getCategoryEmoji(CategoryConstants.HOUSING)
        binding.addCategoryChip.text = EmojiUtils.getActionEmoji("add")

        // Chip click listeners
        binding.foodChip.setOnClickListener {
            selectedCategory = CategoryItem(
                name = CategoryConstants.FOOD,
                emoji = EmojiUtils.getCategoryEmoji(CategoryConstants.FOOD),
                color = "#FF9800",
                budget = 0.0 // Set 0.0 because this is a quick-pick
            )
            updateSelectedCategoryDisplay()
        }
        binding.transportChip.setOnClickListener {
            selectedCategory = CategoryItem(
                name = CategoryConstants.TRANSPORT,
                emoji = EmojiUtils.getCategoryEmoji(CategoryConstants.TRANSPORT),
                color = "#2196F3",
                budget = 0.0
            )
            updateSelectedCategoryDisplay()
        }
        binding.entertainmentChip.setOnClickListener {
            selectedCategory = CategoryItem(
                name = CategoryConstants.ENTERTAINMENT,
                emoji = EmojiUtils.getCategoryEmoji(CategoryConstants.ENTERTAINMENT),
                color = "#9C27B0",
                budget = 0.0
            )
            updateSelectedCategoryDisplay()
        }
        binding.housingChip.setOnClickListener {
            selectedCategory = CategoryItem(
                name = CategoryConstants.HOUSING,
                emoji = EmojiUtils.getCategoryEmoji(CategoryConstants.HOUSING),
                color = "#4CAF50",
                budget = 0.0
            )
            updateSelectedCategoryDisplay()
        }

        updateSelectedCategoryDisplay()
    }

    // Updates the text showing which category is currently selected
    private fun updateSelectedCategoryDisplay() {
        selectedCategory?.let { category ->
            binding.selectedCategoryDisplay.text = "${category.emoji} ${category.name}"
        }
    }

    // Allows the user to pick a transaction date (limited to the next 30 days)
    private fun setupTransactionDatePicker() {
        updateTransactionDateDisplay()

        binding.transactionDateButton.setOnClickListener {
            showTransactionDatePickerDialog()
        }
    }

    private fun showTransactionDatePickerDialog() {
        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(this, { _, y, m, d ->
            val pickedDate = Calendar.getInstance()
            pickedDate.set(Calendar.YEAR, y)
            pickedDate.set(Calendar.MONTH, m)
            pickedDate.set(Calendar.DAY_OF_MONTH, d)

            selectedDate = pickedDate
            updateTransactionDateDisplay()
        }, year, month, day)

        // Prevent selection outside of allowed range
        val today = Calendar.getInstance()
        val maxDate = Calendar.getInstance()
        maxDate.add(Calendar.DAY_OF_YEAR, 30)

        datePickerDialog.datePicker.minDate = today.timeInMillis
        datePickerDialog.datePicker.maxDate = maxDate.timeInMillis

        datePickerDialog.show()
    }

    // Formats and updates the date text on screen
    private fun updateTransactionDateDisplay() {
        val formattedDate =
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(selectedDate.time)
        binding.transactionDateButton.text = "$formattedDate 📅"
    }

    // This handles the recurring switch and displays a projected recurring date
    private fun setupRecurringSwitch() {
        binding.recurringSwitch.isChecked = false // Switch defaults to OFF
        binding.recurringDatePicker.visibility = View.GONE

        binding.recurringSwitch.setOnCheckedChangeListener { _, isChecked ->
            isRecurring = isChecked
            binding.recurringDatePicker.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                val recurringDate = Calendar.getInstance()
                recurringDate.timeInMillis = selectedDate.timeInMillis
                recurringDate.add(Calendar.DAY_OF_YEAR, 30)

                val formatted = SimpleDateFormat(
                    "EEEE, d MMMM yyyy",
                    Locale.getDefault()
                ).format(recurringDate.time)
                binding.recurringDatePicker.text = "Repeats on: $formatted"
            }
        }
    }

    // Sets up the add photo button to use the camera or gallery
    private fun setupPhotoButton() {
        val cameraIconTextView =
            binding.addPhotoButton.findViewById<TextView>(R.id.cameraIconTextView)
        cameraIconTextView?.text = "📷"

        binding.addPhotoButton.setOnClickListener {
            showImageSourceDialog()
        }
    }

    // Opens a dialog allowing the user to select a photo source
    private fun showImageSourceDialog() {
        // Add sample image option (for emulator testing)
        val options = if (isRunningOnEmulator()) {
            arrayOf("Take Photo", "Choose from Gallery", "Use Sample Image")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Add Receipt Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkAndRequestPermissions() // Check permissions before taking photo
                    1 -> chooseFromGallery()
                    2 -> {
                        if (isRunningOnEmulator()) {
                            useSampleImage()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Takes a new photo with the camera
    private fun takePhoto() {
        val photoFile = createImageFile()
        photoFile?.let {
            receiptImageUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            takePictureLauncher.launch(receiptImageUri)
        }
    }

    // Selects an existing image from the gallery
    private fun chooseFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    // Creates a temporary image file in the receipts directory
    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir = getExternalFilesDir("receipts")
        return try {
            File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating image file: ${e.message}")
            null
        }
    }

    // Checks if the app is running on an emulator or a real device
    private fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion"))
    }

    // Uses a sample image from resources (for emulator testing)
    private fun useSampleImage() {
        try {
            // Use a sample image from resources
            val drawable = ContextCompat.getDrawable(this, R.drawable.sample_receipt)
            binding.receiptImageView.setImageDrawable(drawable)
            binding.receiptImageView.visibility = View.VISIBLE
            binding.addPhotoButton.visibility = View.GONE

            // Create a file to store the sample image
            val storageDir = getExternalFilesDir("receipt_images")
            if (!storageDir?.exists()!!) {
                storageDir.mkdirs()
            }

            val imageFile = File(storageDir, "sample_receipt_${System.currentTimeMillis()}.jpg")

            // Save the drawable to a file
            val bitmap = (drawable as BitmapDrawable).bitmap
            FileOutputStream(imageFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            }

            receiptImageUri = Uri.fromFile(imageFile)
            Log.d(TAG, "Sample receipt loaded: ${imageFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error using sample image: ${e.message}", e)
        }
    }

    // Save button listener
    private fun setupSaveButton() {
        binding.saveTransactionButton.setOnClickListener {
            if (validateInputs()) saveTransaction()
        }
    }

    // Validates amount and merchant input before saving
    private fun validateInputs(): Boolean {
        val amount = binding.amountInput.text.toString()
        val merchant = binding.merchantInput.text.toString()

        if (amount.isEmpty() || amount == "0" || amount == "0.00") {
            Toast.makeText(this, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
            return false
        }

        if (merchant.isEmpty()) {
            Toast.makeText(this, "Please enter a merchant/income source", Toast.LENGTH_SHORT).show()
            return false
        }

        // Check if user is logged in
        if (getCurrentUserId() == -1) {
            Toast.makeText(this, "Please log in to save transactions", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    // Get current user ID from SharedPreferences
    private fun getCurrentUserId(): Int {
        val sharedPreferences = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("current_user_id", -1)
    }

    // Saves the receipt image to permanent storage and returns the path
    private fun saveReceiptImage(): String? {
        if (receiptImageUri == null) return null

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir("receipt_images")

            if (!storageDir?.exists()!!) {
                storageDir.mkdirs()
            }

            val destinationFile = File(storageDir, "RECEIPT_${timeStamp}.jpg")

            // Copy the temporary file to permanent storage
            contentResolver.openInputStream(receiptImageUri!!)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.d(TAG, "Receipt image saved permanently at: ${destinationFile.absolutePath}")
            return destinationFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save receipt image permanently", e)
            return null
        }
    }

    /**
     * Saves the transaction details using Firebase and returns to the Dashboard
     */
    private fun saveTransaction() {
        try {
            val amountText = binding.amountInput.text.toString()
            val amount = amountText.toDoubleOrNull() ?: 0.0

            val merchant = binding.merchantInput.text.toString()
            val description = binding.descriptionInput.text.toString()
            val category = selectedCategory?.name ?: "Uncategorized"

            // Save the receipt image permanently
            val receiptPath = saveReceiptImage()

            // Get the current user ID
            val userId = getCurrentUserId()

            // Show saving state
            binding.saveTransactionButton.isEnabled = false
            binding.saveTransactionButton.text = "Saving..."

            lifecycleScope.launch {
                try {
                    val transaction = Transaction(
                        id = 0, // Auto-generated by Firebase
                        userId = userId,
                        merchant = merchant,
                        category = category,
                        amount = amount,
                        date = selectedDate.time, // Uses helper constructor
                        isExpense = isExpense,
                        description = description.takeIf { it.isNotEmpty() },
                        receiptPath = receiptPath,
                        isRecurring = isRecurring
                    )

                    // Save to Firebase instead of Room
                    val firebaseTransactionManager = FirebaseTransactionManager.getInstance()
                    val savedTransaction = firebaseTransactionManager.createTransaction(transaction)

                    Log.d(TAG, "Transaction saved to Firebase with ID: ${savedTransaction.id}")
                    Log.d(TAG, "Merchant/Source: $merchant")
                    Log.d(TAG, "Description: $description")
                    Log.d(TAG, "Category: $category")
                    Log.d(TAG, "Amount: $amount, ${if (isExpense) "Expense" else "Income"}")
                    Log.d(
                        TAG,
                        "Date: ${
                            SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.getDefault()
                            ).format(selectedDate.time)
                        }"
                    )
                    Log.d(TAG, "Recurring: $isRecurring")

                    runOnUiThread {
                        // Reset button state
                        binding.saveTransactionButton.isEnabled = true
                        binding.saveTransactionButton.text = "SAVE TRANSACTION"

                        Toast.makeText(
                            this@AddTransactionActivity,
                            "Transaction saved to Firebase successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Return to Dashboard
                        startActivity(
                            Intent(
                                this@AddTransactionActivity,
                                DashboardActivity::class.java
                            )
                        )
                        finish()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving transaction to Firebase", e)

                    runOnUiThread {
                        // Reset button state
                        binding.saveTransactionButton.isEnabled = true
                        binding.saveTransactionButton.text = "SAVE TRANSACTION"

                        val errorMessage = when {
                            e.message?.contains("network") == true ->
                                "Network error. Please check your connection and try again"
                            else -> "Error saving transaction: ${e.message}"
                        }

                        Toast.makeText(
                            this@AddTransactionActivity,
                            errorMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in saveTransaction", e)
            Toast.makeText(this, "An error occurred: ${e.message}", Toast.LENGTH_SHORT).show()

            // Reset button state
            binding.saveTransactionButton.isEnabled = true
            binding.saveTransactionButton.text = "SAVE TRANSACTION"
        }
    }

    /**
     * Opens a modal to create a new category
     */
    private fun showCategoryPicker() {
        // For POE purposes, we'll use predefined categories from CategoryConstants
        // This simplifies the demo while maintaining full UI functionality

        val predefinedCategories = listOf(
            CategoryItem(
                name = "Food",
                emoji = EmojiUtils.getCategoryEmoji("Food"),
                color = "#FF9800",
                budget = 2500.0
            ),
            CategoryItem(
                name = "Transport",
                emoji = EmojiUtils.getCategoryEmoji("Transport"),
                color = "#2196F3",
                budget = 1500.0
            ),
            CategoryItem(
                name = "Entertainment",
                emoji = EmojiUtils.getCategoryEmoji("Entertainment"),
                color = "#9C27B0",
                budget = 800.0
            ),
            CategoryItem(
                name = "Housing",
                emoji = EmojiUtils.getCategoryEmoji("Housing"),
                color = "#4CAF50",
                budget = 6000.0
            ),
            CategoryItem(
                name = "Utilities",
                emoji = EmojiUtils.getCategoryEmoji("Utilities"),
                color = "#FFC107",
                budget = 1200.0
            ),
            CategoryItem(
                name = "Health",
                emoji = EmojiUtils.getCategoryEmoji("Health"),
                color = "#E91E63",
                budget = 1000.0
            ),
            CategoryItem(
                name = "Shopping",
                emoji = EmojiUtils.getCategoryEmoji("Shopping"),
                color = "#00BCD4",
                budget = 1500.0
            ),
            CategoryItem(
                name = "Education",
                emoji = EmojiUtils.getCategoryEmoji("Education"),
                color = "#3F51B5",
                budget = 2000.0
            )
        )

        try {
            Log.d("AddTransactionActivity", "Showing category picker with ${predefinedCategories.size} predefined categories")

            val picker = CategoryPickerBottomSheet(
                categoryList = predefinedCategories,
                onCategorySelected = { selectedCategoryItem ->
                    selectedCategory = selectedCategoryItem
                    updateSelectedCategoryDisplay()
                    Log.d("AddTransactionActivity", "Selected category: ${selectedCategoryItem.name}")
                },
                onCreateNewClicked = {
                    showCreateCategoryModal()
                }
            )

            if (!isFinishing) {
                picker.show(supportFragmentManager, "CategoryPicker")
            }
        } catch (e: Exception) {
            Log.e("AddTransactionActivity", "Error showing category picker: ${e.message}", e)
            Toast.makeText(this, "Error showing categories", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateCategoryModal() {
        Log.d("AddTransactionActivity", "Showing CreateCategoryBottomSheet")

        val createSheet = CreateCategoryBottomSheet()
        createSheet.show(supportFragmentManager, "CreateCategory")
    }

    // Handles the back button click
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressed()
        }
    }

    /**
     * Handles bottom navigation bar.
     */
    private fun setupBottomNavigation() {
        val bottomNavBar = binding.bottomNavBar
        bottomNavBar.selectedItemId = R.id.nav_add_transaction

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

                R.id.nav_add_transaction -> true // Already on this screen

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

    override fun onBackPressed() {
        super.onBackPressed()
    }
}