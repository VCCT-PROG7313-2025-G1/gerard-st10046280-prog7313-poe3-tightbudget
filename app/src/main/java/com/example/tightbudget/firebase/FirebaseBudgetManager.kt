package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.example.tightbudget.models.BudgetGoal
import com.example.tightbudget.models.CategoryBudget
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase Budget Manager handles budget goals and category budgets in Firebase
 * Stores all budget data online for Part 3 of the POE
 */
class FirebaseBudgetManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val budgetGoalsRef: DatabaseReference = database.getReference("budgetGoals")
    private val categoryBudgetsRef: DatabaseReference = database.getReference("categoryBudgets")
    private val budgetCounterRef: DatabaseReference = database.getReference("budgetCounter")
    private val categoryBudgetCounterRef: DatabaseReference = database.getReference("categoryBudgetCounter")

    companion object {
        private const val TAG = "FirebaseBudgetManager"

        @Volatile
        private var INSTANCE: FirebaseBudgetManager? = null

        fun getInstance(): FirebaseBudgetManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseBudgetManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates a new budget goal in Firebase
     */
    suspend fun createBudgetGoal(budgetGoal: BudgetGoal): BudgetGoal {
        return try {
            val nextId = getNextBudgetGoalId()
            val budgetKey = budgetGoalsRef.push().key
                ?: throw Exception("Failed to generate budget goal key")

            val firebaseBudgetGoal = budgetGoal.copy(id = nextId)
            budgetGoalsRef.child(budgetKey).setValue(firebaseBudgetGoal).await()

            Log.d(TAG, "Budget goal created with ID: $nextId")
            firebaseBudgetGoal

        } catch (e: Exception) {
            Log.e(TAG, "Error creating budget goal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing budget goal
     */
    suspend fun updateBudgetGoal(budgetGoal: BudgetGoal) {
        try {
            val budgetKey = findBudgetGoalKeyById(budgetGoal.id)
            if (budgetKey != null) {
                budgetGoalsRef.child(budgetKey).setValue(budgetGoal).await()
                Log.d(TAG, "Budget goal updated successfully")
            } else {
                throw Exception("Budget goal not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating budget goal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a budget goal from Firebase
     */
    suspend fun deleteBudgetGoal(budgetGoal: BudgetGoal) {
        try {
            val budgetKey = findBudgetGoalKeyById(budgetGoal.id)
            if (budgetKey != null) {
                budgetGoalsRef.child(budgetKey).removeValue().await()
                Log.d(TAG, "Budget goal deleted successfully")
            } else {
                throw Exception("Budget goal not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting budget goal: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets budget goal for a specific month and year
     */
    suspend fun getBudgetGoalForMonth(userId: Int, month: Int, year: Int): BudgetGoal? {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.orderByChild("userId").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            var foundGoal: BudgetGoal? = null
                            for (goalSnapshot in snapshot.children) {
                                val goal = goalSnapshot.getValue(BudgetGoal::class.java)
                                if (goal != null && goal.month == month && goal.year == year) {
                                    foundGoal = goal
                                    break
                                }
                            }
                            continuation.resume(foundGoal)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budget goal: ${e.message}", e)
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
     * Gets the active budget goal for a user
     */
    suspend fun getActiveBudgetGoal(userId: Int): BudgetGoal? {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.orderByChild("userId").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            var activeGoal: BudgetGoal? = null
                            for (goalSnapshot in snapshot.children) {
                                val goal = goalSnapshot.getValue(BudgetGoal::class.java)
                                if (goal != null && goal.isActive) {
                                    activeGoal = goal
                                    break
                                }
                            }
                            continuation.resume(activeGoal)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing active budget goal: ${e.message}", e)
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
     * Deactivates all budget goals for a user
     */
    suspend fun deactivateAllBudgetGoals(userId: Int) {
        try {
            val userGoals = getAllBudgetGoalsForUser(userId)
            for (goal in userGoals) {
                if (goal.isActive) {
                    val deactivatedGoal = goal.copy(isActive = false)
                    updateBudgetGoal(deactivatedGoal)
                }
            }
            Log.d(TAG, "All budget goals deactivated for user $userId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deactivating budget goals: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets all budget goals for a user
     */
    suspend fun getAllBudgetGoalsForUser(userId: Int): List<BudgetGoal> {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.orderByChild("userId").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val goals = mutableListOf<BudgetGoal>()
                            for (goalSnapshot in snapshot.children) {
                                val goal = goalSnapshot.getValue(BudgetGoal::class.java)
                                goal?.let { goals.add(it) }
                            }
                            // Sort by year and month (newest first)
                            goals.sortByDescending { it.year * 100 + it.month }
                            continuation.resume(goals)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budget goals: ${e.message}", e)
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
     * Creates a category budget
     */
    suspend fun createCategoryBudget(categoryBudget: CategoryBudget): CategoryBudget {
        return try {
            val nextId = getNextCategoryBudgetId()
            val categoryBudgetKey = categoryBudgetsRef.push().key
                ?: throw Exception("Failed to generate category budget key")

            val firebaseCategoryBudget = categoryBudget.copy(id = nextId)
            categoryBudgetsRef.child(categoryBudgetKey).setValue(firebaseCategoryBudget).await()

            Log.d(TAG, "Category budget created with ID: $nextId")
            firebaseCategoryBudget

        } catch (e: Exception) {
            Log.e(TAG, "Error creating category budget: ${e.message}", e)
            throw e
        }
    }

    /**
     * Updates an existing category budget
     */
    suspend fun updateCategoryBudget(categoryBudget: CategoryBudget) {
        try {
            val budgetKey = findCategoryBudgetKeyById(categoryBudget.id)
            if (budgetKey != null) {
                categoryBudgetsRef.child(budgetKey).setValue(categoryBudget).await()
                Log.d(TAG, "Category budget updated successfully")
            } else {
                throw Exception("Category budget not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating category budget: ${e.message}", e)
            throw e
        }
    }

    /**
     * Deletes a category budget from Firebase
     */
    suspend fun deleteCategoryBudget(categoryBudget: CategoryBudget) {
        try {
            val budgetKey = findCategoryBudgetKeyById(categoryBudget.id)
            if (budgetKey != null) {
                categoryBudgetsRef.child(budgetKey).removeValue().await()
                Log.d(TAG, "Category budget deleted successfully")
            } else {
                throw Exception("Category budget not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category budget: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets category budgets for a specific budget goal
     */
    suspend fun getCategoryBudgetsForGoal(budgetGoalId: Int): List<CategoryBudget> {
        return suspendCoroutine { continuation ->
            categoryBudgetsRef.orderByChild("budgetGoalId").equalTo(budgetGoalId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            val categoryBudgets = mutableListOf<CategoryBudget>()
                            for (budgetSnapshot in snapshot.children) {
                                val categoryBudget = budgetSnapshot.getValue(CategoryBudget::class.java)
                                categoryBudget?.let { categoryBudgets.add(it) }
                            }
                            continuation.resume(categoryBudgets)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing category budgets: ${e.message}", e)
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
     * Deletes all category budgets for a budget goal
     */
    suspend fun deleteCategoryBudgetsForGoal(budgetGoalId: Int) {
        try {
            val categoryBudgets = getCategoryBudgetsForGoal(budgetGoalId)
            for (categoryBudget in categoryBudgets) {
                val budgetKey = findCategoryBudgetKeyById(categoryBudget.id)
                if (budgetKey != null) {
                    categoryBudgetsRef.child(budgetKey).removeValue().await()
                }
            }
            Log.d(TAG, "Category budgets deleted for goal $budgetGoalId")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting category budgets: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets a specific category budget by ID
     */
    suspend fun getCategoryBudgetById(categoryBudgetId: Int): CategoryBudget? {
        return suspendCoroutine { continuation ->
            categoryBudgetsRef.orderByChild("id").equalTo(categoryBudgetId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                val budgetSnapshot = snapshot.children.first()
                                val categoryBudget = budgetSnapshot.getValue(CategoryBudget::class.java)
                                continuation.resume(categoryBudget)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing category budget: ${e.message}", e)
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
     * Gets a specific budget goal by ID
     */
    suspend fun getBudgetGoalById(budgetGoalId: Int): BudgetGoal? {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.orderByChild("id").equalTo(budgetGoalId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                val goalSnapshot = snapshot.children.first()
                                val budgetGoal = goalSnapshot.getValue(BudgetGoal::class.java)
                                continuation.resume(budgetGoal)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing budget goal: ${e.message}", e)
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

    // ====================== HELPER METHODS ======================

    /**
     * Gets the next incremental budget goal ID
     */
    private suspend fun getNextBudgetGoalId(): Int {
        return suspendCoroutine { continuation ->
            budgetCounterRef.get().addOnSuccessListener { snapshot ->
                val currentCounter = snapshot.getValue(Int::class.java) ?: 0
                val nextId = currentCounter + 1

                budgetCounterRef.setValue(nextId).addOnSuccessListener {
                    Log.d(TAG, "Budget goal counter updated to: $nextId")
                    continuation.resume(nextId)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating budget goal counter: ${exception.message}")
                    // Fallback to timestamp if counter update fails
                    continuation.resume(System.currentTimeMillis().toInt())
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting budget goal counter: ${exception.message}")
                // Fallback to timestamp if counter fails
                continuation.resume(System.currentTimeMillis().toInt())
            }
        }
    }

    /**
     * Gets the next incremental category budget ID
     */
    private suspend fun getNextCategoryBudgetId(): Int {
        return suspendCoroutine { continuation ->
            categoryBudgetCounterRef.get().addOnSuccessListener { snapshot ->
                val currentCounter = snapshot.getValue(Int::class.java) ?: 0
                val nextId = currentCounter + 1

                categoryBudgetCounterRef.setValue(nextId).addOnSuccessListener {
                    Log.d(TAG, "Category budget counter updated to: $nextId")
                    continuation.resume(nextId)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating category budget counter: ${exception.message}")
                    // Fallback to timestamp if counter update fails
                    continuation.resume(System.currentTimeMillis().toInt())
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting category budget counter: ${exception.message}")
                // Fallback to timestamp if counter fails
                continuation.resume(System.currentTimeMillis().toInt())
            }
        }
    }

    /**
     * Finds a budget goal's Firebase key by its ID
     */
    private suspend fun findBudgetGoalKeyById(budgetGoalId: Int): String? {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.orderByChild("id").equalTo(budgetGoalId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val goalKey = snapshot.children.first().key
                            continuation.resume(goalKey)
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
     * Finds a category budget's Firebase key by its ID
     */
    private suspend fun findCategoryBudgetKeyById(categoryBudgetId: Int): String? {
        return suspendCoroutine { continuation ->
            categoryBudgetsRef.orderByChild("id").equalTo(categoryBudgetId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val budgetKey = snapshot.children.first().key
                            continuation.resume(budgetKey)
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
     * Gets all category budgets (for admin purposes or testing)
     */
    suspend fun getAllCategoryBudgets(): List<CategoryBudget> {
        return suspendCoroutine { continuation ->
            categoryBudgetsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val categoryBudgets = mutableListOf<CategoryBudget>()
                        for (budgetSnapshot in snapshot.children) {
                            val categoryBudget = budgetSnapshot.getValue(CategoryBudget::class.java)
                            categoryBudget?.let { categoryBudgets.add(it) }
                        }
                        continuation.resume(categoryBudgets)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing all category budgets: ${e.message}", e)
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
     * Gets all budget goals (for admin purposes or testing)
     */
    suspend fun getAllBudgetGoals(): List<BudgetGoal> {
        return suspendCoroutine { continuation ->
            budgetGoalsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val budgetGoals = mutableListOf<BudgetGoal>()
                        for (goalSnapshot in snapshot.children) {
                            val budgetGoal = goalSnapshot.getValue(BudgetGoal::class.java)
                            budgetGoal?.let { budgetGoals.add(it) }
                        }
                        // Sort by year and month (newest first)
                        budgetGoals.sortByDescending { it.year * 100 + it.month }
                        continuation.resume(budgetGoals)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing all budget goals: ${e.message}", e)
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
}