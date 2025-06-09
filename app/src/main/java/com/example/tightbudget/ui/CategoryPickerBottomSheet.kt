package com.example.tightbudget.ui

import android.content.ContentValues.TAG
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tightbudget.adapters.CategoryAdapter
import com.example.tightbudget.databinding.FragmentCategoryPickerBinding
import com.example.tightbudget.firebase.FirebaseCategoryManager
import com.example.tightbudget.models.CategoryItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

/**
 * A BottomSheetDialogFragment that displays a list of user-specific categories for selection.
 * It allows users to pick an existing category or create a new one.
 * Updated to support user-specific categories from Firebase.
 *
 * @param categoryList List of fallback categories to display if Firebase fails.
 * @param onCategorySelected Callback invoked when a category is selected.
 * @param onCreateNewClicked Callback invoked when the "Create New" button is clicked.
 */
class CategoryPickerBottomSheet(
    private val categoryList: List<CategoryItem>, // Fallback categories if Firebase fails
    private val onCategorySelected: (CategoryItem) -> Unit, // Callback for category selection
    private val onCreateNewClicked: () -> Unit // Callback for creating a new category
) : BottomSheetDialogFragment() {

    // View binding for accessing UI components
    private var _binding: FragmentCategoryPickerBinding? = null
    private val binding get() = _binding!!

    // Adapter for displaying the list of categories
    private lateinit var adapter: CategoryAdapter

    // Firebase manager for loading user-specific categories
    private lateinit var firebaseCategoryManager: FirebaseCategoryManager

    /**
     * Inflates the layout for the bottom sheet and initializes the binding object.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using view binding
        _binding = FragmentCategoryPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Called after the view is created. Sets up the RecyclerView, buttons, and event listeners.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase manager
        firebaseCategoryManager = FirebaseCategoryManager.getInstance()

        // Log the fallback categories passed to the constructor for debugging purposes
        Log.d("CategoryPicker", "Fallback categories: $categoryList")

        // Initialize the adapter with the fallback category list and handle item selection
        adapter = CategoryAdapter(categoryList) { selectedCategory ->
            onCategorySelected(selectedCategory) // Invoke the callback with the selected category
            dismiss() // Close the bottom sheet
        }

        // Set up the RecyclerView with a linear layout manager and the adapter
        binding.categoryRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.categoryRecyclerView.adapter = adapter

        // Set up the close button to dismiss the bottom sheet when clicked
        binding.closeButton.setOnClickListener { dismiss() }

        // Set up the "Create New" button to invoke the callback and dismiss the bottom sheet
        binding.createNewCategoryButton.setOnClickListener {
            onCreateNewClicked() // Invoke the callback for creating a new category
            dismiss() // Close the bottom sheet
        }

        // Load user-specific categories from Firebase
        loadUserCategoriesFromFirebase()
    }

    /**
     * Load user-specific categories from Firebase
     */
    private fun loadUserCategoriesFromFirebase() {
        lifecycleScope.launch {
            try {
                val userId = getCurrentUserId()
                Log.d(TAG, "Loading categories from Firebase for user: $userId")

                if (userId == -1) {
                    Log.d(TAG, "No user logged in, using fallback categories")
                    requireActivity().runOnUiThread {
                        showFallbackCategories()
                    }
                    return@launch
                }

                // Get user-specific categories from Firebase
                val userCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
                Log.d(TAG, "Loaded ${userCategories.size} user-specific categories from Firebase")

                if (userCategories.isNotEmpty()) {
                    // Convert Firebase categories to CategoryItem with REAL emoji data
                    val categoryItems = userCategories.map { category ->
                        Log.d(TAG, "User category: ${category.name}, Emoji: ${category.emoji}")
                        CategoryItem(
                            name = category.name,
                            emoji = category.emoji,  // Use the REAL stored emoji (not EmojiUtils!)
                            color = category.color,
                            budget = category.budget
                        )
                    }.sortedBy { it.name } // Sort alphabetically for better UX

                    requireActivity().runOnUiThread {
                        updateCategoryList(categoryItems)
                    }
                } else {
                    // No categories found - seed defaults and retry
                    Log.d(TAG, "No user categories found, seeding defaults")
                    firebaseCategoryManager.seedDefaultCategoriesForUser(userId)

                    // Retry loading after seeding
                    val seededCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
                    if (seededCategories.isNotEmpty()) {
                        val categoryItems = seededCategories.map { category ->
                            CategoryItem(
                                name = category.name,
                                emoji = category.emoji,
                                color = category.color,
                                budget = category.budget
                            )
                        }.sortedBy { it.name }

                        requireActivity().runOnUiThread {
                            updateCategoryList(categoryItems)
                        }
                    } else {
                        // Fallback to constructor categories
                        requireActivity().runOnUiThread {
                            showFallbackCategories()
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading user categories from Firebase: ${e.message}", e)
                requireActivity().runOnUiThread {
                    // Show error state using fallback categories
                    showErrorState()
                }
            }
        }
    }

    /**
     * Update the category list with new categories
     */
    private fun updateCategoryList(categoryItems: List<CategoryItem>) {
        if (categoryItems.isNotEmpty()) {
            adapter.updateCategories(categoryItems)

            // Only set visibility if these views exist in your layout
            try {
                // Hide loading indicator if it exists
            } catch (e: Exception) {
                // Loading progress bar doesn't exist, skip
            }

            binding.categoryRecyclerView.visibility = View.VISIBLE

            try {
                // Hide empty state if it exists
            } catch (e: Exception) {
                // Empty state layout doesn't exist, skip
            }

            Log.d(TAG, "Successfully updated category list with ${categoryItems.size} user-specific items")
        } else {
            // Show empty state if no categories found
            showEmptyState()
        }
    }

    /**
     * Show fallback categories (from constructor)
     */
    private fun showFallbackCategories() {
        Log.d(TAG, "Using fallback categories from constructor")
        adapter.updateCategories(categoryList)
        binding.categoryRecyclerView.visibility = View.VISIBLE

        if (categoryList.isNotEmpty()) {
            Toast.makeText(
                requireContext(),
                "Using default categories",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Show empty state when no categories are found
     */
    private fun showEmptyState() {
        try {
            // Hide loading indicator if it exists
        } catch (e: Exception) {
            // Loading progress bar doesn't exist
        }

        binding.categoryRecyclerView.visibility = View.GONE

        try {
            // Show empty state layout if it exists
        } catch (e: Exception) {
            // Empty state layout doesn't exist, show toast instead
            Toast.makeText(requireContext(), "No categories found", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show error state and load fallback categories
     * Uses the original categoryList as fallback
     */
    private fun showErrorState() {
        Log.d(TAG, "Loading fallback categories due to Firebase error")

        // Uses the original categoryList passed to constructor as fallback
        adapter.updateCategories(categoryList)

        try {
            // Hide loading indicator if it exists
        } catch (e: Exception) {
            // Loading progress bar doesn't exist
        }

        binding.categoryRecyclerView.visibility = View.VISIBLE

        try {
            // Hide empty state if it exists
        } catch (e: Exception) {
            // Empty state layout doesn't exist
        }

        // Show a toast to inform user
        Toast.makeText(
            requireContext(),
            "Using offline categories (connection issue)",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Gets the current user ID from SharedPreferences
     */
    private fun getCurrentUserId(): Int {
        val sharedPreferences = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getInt("current_user_id", -1)
        Log.d(TAG, "Current user ID: $userId")
        return userId
    }

    /**
     * Cleans up the binding object to avoid memory leaks when the view is destroyed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding reference
    }
}