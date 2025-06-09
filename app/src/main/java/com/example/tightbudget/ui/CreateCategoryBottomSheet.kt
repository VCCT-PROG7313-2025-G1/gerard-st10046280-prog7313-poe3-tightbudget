package com.example.tightbudget.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.tightbudget.databinding.FragmentCreateCategoryBinding
import com.example.tightbudget.utils.CategoryConstants
import com.example.tightbudget.utils.DrawableUtils
import com.example.tightbudget.utils.EmojiUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * A bottom sheet dialog for creating a new custom category.
 * Updated to support user-specific categories in Firebase.
 */
class CreateCategoryBottomSheet : BottomSheetDialogFragment() {

    private var selectedEmoji: String = "📁"
    private var selectedColor: String = "#FF9800"
    private var selectedColorView: View? = null

    private var _binding: FragmentCreateCategoryBinding? = null
    private val binding get() = _binding!!

    /**
     * Inflate layout using ViewBinding.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Handle UI setup after the view is created.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupIconGrid()
        setupColorGrid()

        Log.d("CreateCategorySheet", "onViewCreated triggered - User-specific Firebase mode")

        // Close button dismisses the bottom sheet
        binding.closeCreateButton.setOnClickListener {
            Log.d("CreateCategorySheet", "Close button clicked")
            dismiss()
        }

        // Save category button - Updated for user-specific Firebase
        binding.saveCategoryButton.setOnClickListener {
            saveCategory()
        }

        Log.d("CreateCategorySheet", "Fragment loaded successfully - User-specific Firebase mode")
    }

    /**
     * Saves a new category to Firebase (user-specific)
     */
    private fun saveCategory() {
        val name = binding.categoryNameInput.text.toString().trim()
        val budgetText = binding.budgetInput.text.toString().trim()

        // Input validation
        if (name.isEmpty() || budgetText.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val budgetAmount = try {
            budgetText.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Invalid budget amount", Toast.LENGTH_SHORT).show()
            return
        }

        if (budgetAmount < CategoryConstants.MINIMUM_BUDGET_AMOUNT) {
            Toast.makeText(
                requireContext(),
                "Budget must be at least R${CategoryConstants.MINIMUM_BUDGET_AMOUNT.toInt()}",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (selectedEmoji.isEmpty()) {
            Toast.makeText(requireContext(), "Please pick an emoji", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedColor.isEmpty()) {
            Toast.makeText(requireContext(), "Please pick a color", Toast.LENGTH_SHORT).show()
            return
        }

        // Get current user ID
        val userId = getCurrentUserId()
        if (userId == -1) {
            Toast.makeText(requireContext(), "Please log in to create categories", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        // Save the category to Firebase (user-specific)
        lifecycleScope.launch {
            try {
                // Show saving state
                binding.saveCategoryButton.isEnabled = false
                binding.saveCategoryButton.text = "Saving..."

                val firebaseCategoryManager = com.example.tightbudget.firebase.FirebaseCategoryManager.getInstance()

                // Check if category already exists for this user
                if (firebaseCategoryManager.categoryExistsForUser(name, userId)) {
                    Toast.makeText(
                        requireContext(),
                        "Category '$name' already exists for your account!",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Create new category for this user
                val newCategory = com.example.tightbudget.models.Category(
                    id = 0, // Will be set by Firebase
                    name = name,
                    emoji = selectedEmoji,
                    color = selectedColor,
                    budget = budgetAmount
                )

                // Save to Firebase with user ID
                val savedCategory = firebaseCategoryManager.createCategory(newCategory, userId)

                Log.d("CreateCategorySheet", "Category '$name' created successfully for user $userId with ID: ${savedCategory.id}")

                Toast.makeText(
                    requireContext(),
                    "Category '$name' created successfully!",
                    Toast.LENGTH_SHORT
                ).show()

                dismiss()

            } catch (e: Exception) {
                Log.e("CreateCategorySheet", "Error creating category for user $userId: ${e.message}", e)

                val errorMessage = when {
                    e.message?.contains("network") == true ->
                        "Network error. Please check your connection and try again"
                    e.message?.contains("already exists") == true ->
                        "Category '$name' already exists for your account"
                    e.message?.contains("permission") == true ->
                        "Permission denied. Please log in and try again"
                    else -> "Error creating category: ${e.message}"
                }

                Toast.makeText(
                    requireContext(),
                    errorMessage,
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                // Reset button state
                binding.saveCategoryButton.isEnabled = true
                binding.saveCategoryButton.text = "SAVE CATEGORY"
            }
        }
    }

    /**
     * Gets the current user ID from SharedPreferences
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getInt("current_user_id", -1)
        Log.d("CreateCategorySheet", "Current user ID: $userId")
        return userId
    }

    /// Set up the emoji grid with emojis for different categories
    private fun setupIconGrid() {
        val categoryNames = listOf(
            "Food", "Transport", "Entertainment", "Housing", "Utilities",
            "Health", "Shopping", "Education", "Travel", "Groceries",
            "Salary", "Gifts", "Pets", "Subscriptions", "Insurance",
            "Fitness", "Personal Care", "Savings", "Childcare", "Donations"
        )
        // Remove all previous views from the grid
        binding.iconGrid.removeAllViews()

        // For each category name, create a TextView with the emoji and set up a click listener
        for (categoryName in categoryNames) {
            val emoji = EmojiUtils.getCategoryEmoji(categoryName)

            val emojiView = TextView(requireContext()).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(90, 90).apply {
                    setMargins(12, 12, 12, 12)
                }
                setOnClickListener {
                    selectedEmoji = emoji
                    Toast.makeText(context, "Selected: $emoji", Toast.LENGTH_SHORT).show()
                }
            }

            binding.iconGrid.addView(emojiView)
        }
    }

    /// Set up the color grid with color options
    private fun setupColorGrid() {
        val colorOptions = listOf(
            "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
            "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
            "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E", "#607D8B"
        )

        binding.colorGrid.removeAllViews()

        // For each color option, create a View with the color and set up a click listener
        for (colorHex in colorOptions) {
            val colorCircle = View(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(90, 90).apply {
                    setMargins(12, 12, 12, 12)
                }
                background = DrawableUtils.createCircleDrawable(Color.parseColor(colorHex))
                setOnClickListener {
                    selectedColor = colorHex
                    Toast.makeText(context, "Selected colour: $colorHex", Toast.LENGTH_SHORT).show()

                    // Remove previous highlight
                    selectedColorView?.background = DrawableUtils.createCircleDrawable(
                        Color.parseColor(selectedColorView?.tag as? String ?: colorHex)
                    )

                    // Highlight current selection
                    this.background =
                        DrawableUtils.createHighlightedCircleDrawable(Color.parseColor(colorHex))

                    selectedColorView = this
                    this.tag = colorHex
                }
                tag = colorHex // Save the original color with the view
            }

            binding.colorGrid.addView(colorCircle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}