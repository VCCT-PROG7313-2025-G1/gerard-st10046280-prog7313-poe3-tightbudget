package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.example.tightbudget.models.Category
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase Category Manager handles custom categories in Firebase Realtime Database
 * Allows users to create, update, and retrieve custom categories
 */
class FirebaseCategoryManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val categoriesRef: DatabaseReference = database.getReference("categories")
    private val categoryCounterRef: DatabaseReference = database.getReference("categoryCounter")

    companion object {
        private const val TAG = "FirebaseCategoryManager"

        @Volatile
        private var INSTANCE: FirebaseCategoryManager? = null

        fun getInstance(): FirebaseCategoryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseCategoryManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates a new custom category in Firebase
     */
    suspend fun createCategory(category: Category): Category {
        return try {
            val nextId = getNextCategoryId()
            val categoryKey = categoriesRef.push().key
                ?: throw Exception("Failed to generate category key")

            val firebaseCategory = category.copy(id = nextId)
            categoriesRef.child(categoryKey).setValue(firebaseCategory).await()

            Log.d(TAG, "Category created with ID: $nextId")
            firebaseCategory

        } catch (e: Exception) {
            Log.e(TAG, "Error creating category: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing category
     */
    suspend fun updateCategory(category: Category) {
        try {
            val categoryKey = findCategoryKeyById(category.id)
            if (categoryKey != null) {
                categoriesRef.child(categoryKey).setValue(category).await()
                Log.d(TAG, "Category updated successfully")
            } else {
                throw Exception("Category not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating category: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a category from Firebase
     */
    suspend fun deleteCategory(category: Category) {
        try {
            val categoryKey = findCategoryKeyById(category.id)
            if (categoryKey != null) {
                categoriesRef.child(categoryKey).removeValue().await()
                Log.d(TAG, "Category deleted successfully")
            } else {
                throw Exception("Category not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets all categories from Firebase
     */
    suspend fun getAllCategories(): List<Category> {
        return suspendCoroutine { continuation ->
            categoriesRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val categories = mutableListOf<Category>()
                        for (categorySnapshot in snapshot.children) {
                            val category = categorySnapshot.getValue(Category::class.java)
                            category?.let { categories.add(it) }
                        }
                        // Sort by name for consistent ordering
                        categories.sortBy { it.name }
                        continuation.resume(categories)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing categories: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error: ${error.message}")
                    continuation.resumeWithException(Exception(error.message))
                }
            })
        }
    }

    /**
     * Gets categories by user (if you want user-specific categories)
     * For now, categories are global, but this method allows future user-specific categories
     */
    suspend fun getCategoriesForUser(userId: Int): List<Category> {
        // For now, return all categories since they're global
        // In the future, you could filter by userId if categories become user-specific
        return getAllCategories()
    }

    /**
     * Seeds default categories on first app launch
     */
    suspend fun seedDefaultCategories() {
        try {
            val existingCategories = getAllCategories()
            if (existingCategories.isEmpty()) {
                Log.d(TAG, "Seeding default categories to Firebase")

                val defaultCategories = getDefaultCategories()

                for (category in defaultCategories) {
                    createCategory(category)
                }

                Log.d(TAG, "Successfully seeded ${defaultCategories.size} default categories")
            } else {
                Log.d(TAG, "Categories already exist in Firebase (${existingCategories.size} found)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding default categories: ${e.message}", e)
        }
    }

    /**
     * Get default categories for seeding
     */
    private fun getDefaultCategories(): List<Category> {
        return listOf(
            Category(
                name = "Food",
                emoji = "🍔",
                color = "#FF9800",
                budget = 2500.0
            ),
            Category(
                name = "Housing",
                emoji = "🏠",
                color = "#4CAF50",
                budget = 6000.0
            ),
            Category(
                name = "Transport",
                emoji = "🚗",
                color = "#2196F3",
                budget = 1500.0
            ),
            Category(
                name = "Entertainment",
                emoji = "🎬",
                color = "#9C27B0",
                budget = 800.0
            ),
            Category(
                name = "Utilities",
                emoji = "⚡",
                color = "#FFC107",
                budget = 1200.0
            ),
            Category(
                name = "Health",
                emoji = "⚕️",
                color = "#E91E63",
                budget = 1000.0
            ),
            Category(
                name = "Shopping",
                emoji = "🛍️",
                color = "#00BCD4",
                budget = 1500.0
            ),
            Category(
                name = "Education",
                emoji = "📚",
                color = "#3F51B5",
                budget = 2000.0
            ),
            Category(
                name = "Groceries",
                emoji = "🛒",
                color = "#8BC34A",
                budget = 1800.0
            ),
            Category(
                name = "Fitness",
                emoji = "💪",
                color = "#FF5722",
                budget = 500.0
            )
        )
    }

    /**
     * Search categories by name
     */
    suspend fun searchCategories(query: String): List<Category> {
        return try {
            val allCategories = getAllCategories()
            allCategories.filter {
                it.name.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching categories: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Check if a category name already exists
     */
    suspend fun categoryExists(name: String): Boolean {
        return try {
            val categories = getAllCategories()
            categories.any { it.name.equals(name, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking category existence: ${e.message}", e)
            false
        }
    }

    // ====================== HELPER METHODS ======================

    /**
     * Gets the next incremental category ID
     */
    private suspend fun getNextCategoryId(): Int {
        return suspendCoroutine { continuation ->
            categoryCounterRef.get().addOnSuccessListener { snapshot ->
                val currentCounter = snapshot.getValue(Int::class.java) ?: 0
                val nextId = currentCounter + 1

                categoryCounterRef.setValue(nextId).addOnSuccessListener {
                    Log.d(TAG, "Category counter updated to: $nextId")
                    continuation.resume(nextId)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating category counter: ${exception.message}")
                    continuation.resume(System.currentTimeMillis().toInt())
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting category counter: ${exception.message}")
                continuation.resume(System.currentTimeMillis().toInt())
            }
        }
    }

    /**
     * Finds a category's Firebase key by its ID
     */
    private suspend fun findCategoryKeyById(categoryId: Int): String? {
        return suspendCoroutine { continuation ->
            categoriesRef.orderByChild("id").equalTo(categoryId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val categoryKey = snapshot.children.first().key
                            continuation.resume(categoryKey)
                        } else {
                            continuation.resume(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        continuation.resumeWithException(Exception(error.message))
                    }
                })
        }
    }

    /**
     * Gets a category by ID
     */
    suspend fun getCategoryById(categoryId: Int): Category? {
        return suspendCoroutine { continuation ->
            categoriesRef.orderByChild("id").equalTo(categoryId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                val categorySnapshot = snapshot.children.first()
                                val category = categorySnapshot.getValue(Category::class.java)
                                continuation.resume(category)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing category: ${e.message}", e)
                            continuation.resume(null)
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Database error: ${error.message}")
                        continuation.resumeWithException(Exception(error.message))
                    }
                })
        }
    }
}