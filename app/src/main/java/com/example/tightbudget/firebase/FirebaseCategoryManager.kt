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
 * Firebase Category Manager handles user-specific custom categories in Firebase Realtime Database
 * Updated to support user-specific categories instead of global categories
 */
class FirebaseCategoryManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val userCategoriesRef: DatabaseReference = database.getReference("userCategories")
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
     * Creates a new custom category for a specific user in Firebase
     */
    suspend fun createCategory(category: Category, userId: Int): Category {
        return try {
            val nextId = getNextCategoryId()
            val categoryKey = userCategoriesRef.child(userId.toString()).push().key
                ?: throw Exception("Failed to generate category key")

            val firebaseCategory = category.copy(id = nextId)
            userCategoriesRef.child(userId.toString()).child(categoryKey).setValue(firebaseCategory).await()

            Log.d(TAG, "Category created with ID: $nextId for user: $userId")
            firebaseCategory

        } catch (e: Exception) {
            Log.e(TAG, "Error creating category for user $userId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing category for a specific user
     */
    suspend fun updateCategory(category: Category, userId: Int) {
        try {
            val categoryKey = findCategoryKeyById(category.id, userId)
            if (categoryKey != null) {
                userCategoriesRef.child(userId.toString()).child(categoryKey).setValue(category).await()
                Log.d(TAG, "Category updated successfully for user: $userId")
            } else {
                throw Exception("Category not found for user: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating category for user $userId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a category for a specific user from Firebase
     */
    suspend fun deleteCategory(category: Category, userId: Int) {
        try {
            val categoryKey = findCategoryKeyById(category.id, userId)
            if (categoryKey != null) {
                userCategoriesRef.child(userId.toString()).child(categoryKey).removeValue().await()
                Log.d(TAG, "Category deleted successfully for user: $userId")
            } else {
                throw Exception("Category not found for user: $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category for user $userId: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets all categories for a specific user from Firebase
     */
    suspend fun getAllCategoriesForUser(userId: Int): List<Category> {
        return suspendCoroutine { continuation ->
            userCategoriesRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val categories = mutableListOf<Category>()
                        for (categorySnapshot in snapshot.children) {
                            val category = categorySnapshot.getValue(Category::class.java)
                            category?.let { categories.add(it) }
                        }
                        // Sort by name for consistent ordering
                        categories.sortBy { it.name }
                        Log.d(TAG, "Loaded ${categories.size} categories for user: $userId")
                        continuation.resume(categories)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing categories for user $userId: ${e.message}", e)
                        continuation.resume(emptyList())
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Database error loading categories for user $userId: ${error.message}")
                    continuation.resumeWithException(Exception(error.message))
                }
            })
        }
    }

    /**
     * Gets categories by user - same as getAllCategoriesForUser but kept for backward compatibility
     */
    suspend fun getCategoriesForUser(userId: Int): List<Category> {
        return getAllCategoriesForUser(userId)
    }

    /**
     * Seeds default categories for a new user on first app launch
     */
    suspend fun seedDefaultCategoriesForUser(userId: Int) {
        try {
            val existingCategories = getAllCategoriesForUser(userId)
            if (existingCategories.isEmpty()) {
                Log.d(TAG, "Seeding default categories for new user: $userId")

                val defaultCategories = getDefaultCategories()

                for (category in defaultCategories) {
                    createCategory(category, userId)
                }

                Log.d(TAG, "Successfully seeded ${defaultCategories.size} default categories for user: $userId")
            } else {
                Log.d(TAG, "User $userId already has ${existingCategories.size} categories")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error seeding default categories for user $userId: ${e.message}", e)
        }
    }

    /**
     * Seeds default categories globally (DEPRECATED - kept for migration purposes)
     * Use seedDefaultCategoriesForUser instead
     */
    @Deprecated("Use seedDefaultCategoriesForUser instead")
    suspend fun seedDefaultCategories() {
        Log.w(TAG, "seedDefaultCategories() is deprecated. Use seedDefaultCategoriesForUser(userId) instead.")
        // This method is now deprecated since we're using user-specific categories
    }

    /**
     * Gets all categories from Firebase (DEPRECATED - kept for backward compatibility)
     * Use getAllCategoriesForUser instead
     */
    @Deprecated("Use getAllCategoriesForUser instead")
    suspend fun getAllCategories(): List<Category> {
        Log.w(TAG, "getAllCategories() is deprecated. Use getAllCategoriesForUser(userId) instead.")
        return emptyList()
    }

    /**
     * Get default categories for seeding new users
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
     * Search categories by name for a specific user
     */
    suspend fun searchCategoriesForUser(query: String, userId: Int): List<Category> {
        return try {
            val allCategories = getAllCategoriesForUser(userId)
            allCategories.filter {
                it.name.contains(query, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching categories for user $userId: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Check if a category name already exists for a specific user
     */
    suspend fun categoryExistsForUser(name: String, userId: Int): Boolean {
        return try {
            val categories = getAllCategoriesForUser(userId)
            categories.any { it.name.equals(name, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking category existence for user $userId: ${e.message}", e)
            false
        }
    }

    // ====================== HELPER METHODS ======================

    /**
     * Gets the next incremental category ID (global counter)
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
     * Finds a category's Firebase key by its ID for a specific user
     */
    private suspend fun findCategoryKeyById(categoryId: Int, userId: Int): String? {
        return suspendCoroutine { continuation ->
            userCategoriesRef.child(userId.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (categorySnapshot in snapshot.children) {
                        val category = categorySnapshot.getValue(Category::class.java)
                        if (category?.id == categoryId) {
                            continuation.resume(categorySnapshot.key)
                            return
                        }
                    }
                    continuation.resume(null)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error finding category key for user $userId: ${error.message}")
                    continuation.resume(null)
                }
            })
        }
    }

    /**
     * Migration method to convert existing global categories to user-specific
     * Call this once to migrate existing data
     */
    suspend fun migrateGlobalCategoriesToUserSpecific(userId: Int) {
        try {
            Log.d(TAG, "Starting migration of global categories to user-specific for user: $userId")

            // Get existing global categories (from old structure)
            val globalCategoriesRef = database.getReference("categories")
            val snapshot = globalCategoriesRef.get().await()

            val globalCategories = mutableListOf<Category>()
            for (categorySnapshot in snapshot.children) {
                val category = categorySnapshot.getValue(Category::class.java)
                category?.let { globalCategories.add(it) }
            }

            if (globalCategories.isNotEmpty()) {
                Log.d(TAG, "Found ${globalCategories.size} global categories to migrate")

                // Create them as user-specific categories
                for (category in globalCategories) {
                    try {
                        createCategory(category.copy(id = 0), userId) // Reset ID to get new one
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not migrate category ${category.name}: ${e.message}")
                    }
                }

                Log.d(TAG, "Migration completed for user: $userId")
            } else {
                Log.d(TAG, "No global categories found to migrate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration for user $userId: ${e.message}", e)
        }
    }
}