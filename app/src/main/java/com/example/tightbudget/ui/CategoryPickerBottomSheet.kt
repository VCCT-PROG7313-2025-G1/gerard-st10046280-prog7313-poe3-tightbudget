package com.example.tightbudget.ui

import android.content.ContentValues.TAG
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
 * A BottomSheetDialogFragment that displays a list of categories for selection.
 * It allows users to pick an existing category or create a new one.
 *
 * @param categoryList List of categories to display.
 * @param onCategorySelected Callback invoked when a category is selected.
 * @param onCreateNewClicked Callback invoked when the "Create New" button is clicked.
 */
class CategoryPickerBottomSheet(
    private val categoryList: List<CategoryItem>, // List of categories to display
    private val onCategorySelected: (CategoryItem) -> Unit, // Callback for category selection
    private val onCreateNewClicked: () -> Unit // Callback for creating a new category
) : BottomSheetDialogFragment() {

    // View binding for accessing UI components
    private var _binding: FragmentCategoryPickerBinding? = null
    private val binding get() = _binding!!

    // Adapter for displaying the list of categories
    private lateinit var adapter: CategoryAdapter

    // ADDED: Firebase manager for loading real categories
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

        // Log the categories passed to the constructor for debugging purposes
        Log.d("CategoryPicker", "Categories: $categoryList")

        // Initialize the adapter with the category list and handle item selection
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

        loadCategoriesFromFirebase()
    }

    /**
     * Load categories from Firebase with real emojis
     */
    private fun loadCategoriesFromFirebase() {
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Loading categories from Firebase...")

                val categories = firebaseCategoryManager.getAllCategories()
                Log.d(TAG, "Loaded ${categories.size} categories from Firebase")

                // Convert Firebase categories to CategoryItem with REAL emoji data
                val categoryItems = categories.map { category ->
                    Log.d(TAG, "Category: ${category.name}, Emoji: ${category.emoji}")
                    CategoryItem(
                        name = category.name,
                        emoji = category.emoji,  // Use the REAL stored emoji (not EmojiUtils!)
                        color = category.color,
                        budget = category.budget
                    )
                }.sortedBy { it.name } // Sort alphabetically for better UX

                // FIXED: Use requireActivity().runOnUiThread for fragments
                requireActivity().runOnUiThread {
                    if (categoryItems.isNotEmpty()) {
                        adapter.updateCategories(categoryItems)

                        // Only set visibility if these views exist in your layout
                        try {
                        } catch (e: Exception) {
                            // Loading progress bar doesn't exist, skip
                        }

                        binding.categoryRecyclerView.visibility = View.VISIBLE

                        try {
                        } catch (e: Exception) {
                            // Empty state layout doesn't exist, skip
                        }

                        Log.d(TAG, "Successfully updated category list with ${categoryItems.size} items")
                    } else {
                        // Show empty state if no categories found
                        showEmptyState()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading categories from Firebase: ${e.message}", e)
                requireActivity().runOnUiThread {
                    // Show error state or fallback to predefined categories
                    showErrorState()
                }
            }
        }
    }

    /**
     * Show empty state when no categories are found
     * Added error handling for missing views
     */
    private fun showEmptyState() {
        try {
        } catch (e: Exception) {
            // Loading progress bar doesn't exist
        }

        binding.categoryRecyclerView.visibility = View.GONE

        try {
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
        } catch (e: Exception) {
            // Loading progress bar doesn't exist
        }

        binding.categoryRecyclerView.visibility = View.VISIBLE

        try {
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
     * Cleans up the binding object to avoid memory leaks when the view is destroyed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding reference
    }
}