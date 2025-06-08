package com.example.tightbudget.ui

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.R
import com.example.tightbudget.databinding.FragmentTransactionDetailBinding
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.firebase.FirebaseDataManager
import com.example.tightbudget.models.Category
import com.example.tightbudget.models.Transaction
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A bottom sheet dialog that displays detailed information about a selected transaction.
 * This includes transaction details, receipt viewing, and delete functionality.
 * Updated to use Firebase instead of Room database.
 */
class TransactionDetailBottomSheet : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentTransactionDetailBinding
    private lateinit var transaction: Transaction
    private lateinit var firebaseDataManager: FirebaseDataManager
    private val TAG = "TransactionDetail"

    // Category manager and loaded categories
    private lateinit var firebaseCategoryManager: FirebaseCategoryManager
    private var loadedCategories: List<Category> = emptyList()

    /**
     * Called when the bottom sheet dialog is created.
     * Sets a custom background for the bottom sheet.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

            // Set fully transparent background for the bottom sheet
            bottomSheet?.background =
                ContextCompat.getDrawable(requireContext(), R.drawable.bottom_sheet_background)

            // Remove any system-imposed rounded corners behind it
            (bottomSheet?.parent as? View)?.background =
                ContextCompat.getDrawable(requireContext(), android.R.color.transparent)
        }

        dialog.window?.attributes?.windowAnimations = R.style.DialogFadeAnimation
        return dialog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase data manager
        firebaseDataManager = FirebaseDataManager.getInstance()

        // Initialize category manager
        firebaseCategoryManager = FirebaseCategoryManager.getInstance()

        // Retrieve the transaction object passed in via arguments
        arguments?.let {
            transaction = it.getParcelable("transaction")!!
        }

        // Load categories for emoji lookup
        loadCategoriesForEmoji()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTransactionDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply white circular background
        DrawableUtils.applyWhiteCircleBackground(binding.detailEmoji, requireContext())

        // Set the emoji based on real category data
        setupTransactionEmoji()

        // Fill in details
        binding.detailMerchant.text = transaction.merchant
        binding.detailCategory.text = transaction.category
        binding.detailAmount.text = formatAmount(transaction.amount, transaction.isExpense)
        binding.detailDate.text = formatDate(transaction.date)
        binding.detailType.text = if (transaction.isExpense) "Expense" else "Income"

        // Set appropriate transaction amount color
        binding.detailAmount.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (transaction.isExpense) R.color.red_light else R.color.green_light
            )
        )

        // Set transaction notes/description if available
        if (transaction.description.isNullOrEmpty()) {
            binding.detailNotes.text = "No notes added"
        } else {
            binding.detailNotes.text = transaction.description
        }

        // Handle receipt image display if the transaction has one
        handleReceiptImage()

        // Set up button click listeners
        setupButtons()
    }

    /**
     * Load categories for emoji lookup
     */
    private fun loadCategoriesForEmoji() {
        lifecycleScope.launch {
            try {
                loadedCategories = firebaseCategoryManager.getAllCategories()
                // Update emoji if view is already created
                if (true) {
                    requireActivity().runOnUiThread {
                        setupTransactionEmoji()
                    }
                }
            } catch (e: Exception) {
                Log.e("TransactionDetail", "Error loading categories: ${e.message}")
                // Will fallback to EmojiUtils
            }
        }
    }

    /**
     * Setup transaction emoji with real category data
     */
    private fun setupTransactionEmoji() {
        binding.detailEmoji.text = getCategoryEmojiFromData(transaction.category)
    }

    /**
     * Get category emoji from loaded data or fallback
     */
    private fun getCategoryEmojiFromData(categoryName: String): String {
        val category = loadedCategories.find { it.name.equals(categoryName, ignoreCase = true) }
        return if (category != null && category.emoji.isNotBlank()) {
            category.emoji // Use real stored emoji
        } else {
            EmojiUtils.getCategoryEmoji(categoryName) // Fallback to hardcoded
        }
    }

    /**
     * Sets up the close, edit and delete buttons.
     */
    private fun setupButtons() {
        // Close button dismisses the bottom sheet
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        // Delete button shows a confirmation dialog
        binding.deleteButton.setOnClickListener {
            ConfirmDeleteDialogFragment {
                // When confirmed, delete the transaction using Firebase
                deleteTransaction()
            }.show(parentFragmentManager, "ConfirmDeleteDialog")
        }

        // Edit button for future implementation
        binding.editButton?.setOnClickListener {
            // This will be implemented in a future update
            Toast.makeText(context, "Edit functionality not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Loads and displays the receipt image if one exists for this transaction.
     */
    private fun handleReceiptImage() {
        // Only proceed if there's a receipt path saved
        if (!transaction.receiptPath.isNullOrEmpty()) {
            try {
                val receiptFile = File(transaction.receiptPath!!)
                if (receiptFile.exists()) {
                    // Show the receipt section
                    binding.receiptSection?.visibility = View.VISIBLE

                    // Load the image from the file path
                    binding.receiptImage?.setImageURI(Uri.fromFile(receiptFile))

                    // Set up full-screen viewing when clicked
                    binding.viewFullImageBtn?.setOnClickListener {
                        showFullScreenReceipt(receiptFile)
                    }
                } else {
                    // Hide receipt section if file doesn't exist
                    binding.receiptSection?.visibility = View.GONE
                    Log.d(TAG, "Receipt file does not exist: ${transaction.receiptPath}")
                }
            } catch (e: Exception) {
                // Handle errors gracefully
                binding.receiptSection?.visibility = View.GONE
                Log.e(TAG, "Error loading receipt: ${e.message}")
            }
        } else {
            // No receipt path, so hide the section
            binding.receiptSection?.visibility = View.GONE
        }
    }

    /**
     * Opens a dialog that displays the receipt image in full screen.
     */
    private fun showFullScreenReceipt(imageFile: File) {
        // Create a full-screen dialog
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        // Create and configure the ImageView
        val imageView = ImageView(requireContext())
        imageView.setImageURI(Uri.fromFile(imageFile))
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER

        // Allow closing by tapping the image
        imageView.setOnClickListener { dialog.dismiss() }

        // Show the dialog
        dialog.setContentView(imageView)
        dialog.show()
    }

    /**
     * Deletes the current transaction from Firebase database.
     */
    private fun deleteTransaction() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Deleting transaction ${transaction.id} from Firebase")

                // Delete the transaction using Firebase
                firebaseDataManager.deleteTransaction(transaction)

                // Show success message and dismiss
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Transaction deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    dismiss()
                }

                Log.d(TAG, "Transaction deleted successfully from Firebase")

            } catch (e: Exception) {
                // Handle errors
                Log.e(TAG, "Error deleting transaction from Firebase: ${e.message}", e)

                withContext(Dispatchers.Main) {
                    val errorMessage = when {
                        e.message?.contains("network") == true ->
                            "Network error. Please check your connection and try again"
                        e.message?.contains("permission") == true ->
                            "Permission denied. Please check your Firebase configuration"
                        else -> "Error deleting transaction: ${e.message}"
                    }

                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Formats a Date object into a readable string format.
     */
    private fun formatDate(date: Date): String {
        val formatter = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        return formatter.format(date)
    }

    /**
     * Returns a formatted currency string (e.g., -R342.50 or +R1200.00).
     */
    private fun formatAmount(amount: Double, isExpense: Boolean): String {
        val sign = if (isExpense) "-" else "+"
        return "${sign}R${"%,.2f".format(amount)}"
    }

    companion object {
        /**
         * Creates a new instance of TransactionDetailBottomSheet with the specified transaction.
         */
        fun newInstance(transaction: Transaction): TransactionDetailBottomSheet {
            val args = Bundle().apply {
                putParcelable("transaction", transaction)
            }
            return TransactionDetailBottomSheet().apply {
                arguments = args
            }
        }
    }
}