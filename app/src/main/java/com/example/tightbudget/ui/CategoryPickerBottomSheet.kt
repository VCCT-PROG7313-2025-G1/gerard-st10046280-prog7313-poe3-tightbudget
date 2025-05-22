package com.example.tightbudget.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tightbudget.adapters.CategoryAdapter
import com.example.tightbudget.databinding.FragmentCategoryPickerBinding
import com.example.tightbudget.models.CategoryItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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
    }

    /**
     * Cleans up the binding object to avoid memory leaks when the view is destroyed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Clear the binding reference
    }
}