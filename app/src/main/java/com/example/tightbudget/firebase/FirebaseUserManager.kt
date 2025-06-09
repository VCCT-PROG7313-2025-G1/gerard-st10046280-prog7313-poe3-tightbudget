package com.example.tightbudget.firebase

import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.example.tightbudget.models.User
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Firebase User Manager handles user authentication and data storage in Firebase Realtime Database
 * This replaces the local Room database for user management in Part 3 of the POE
 * Uses clean incremental IDs (1, 2, 3, 4...)
 */
class FirebaseUserManager {

    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()
    private val usersRef: DatabaseReference = database.getReference("users")
    private val counterRef: DatabaseReference = database.getReference("userCounter")

    companion object {
        private const val TAG = "FirebaseUserManager"

        @Volatile
        private var INSTANCE: FirebaseUserManager? = null

        fun getInstance(): FirebaseUserManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FirebaseUserManager()
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Creates a new user in Firebase Realtime Database with incremental ID
     * @param user The user object to create
     * @return The created user with incremental ID
     */
    suspend fun createUser(user: User): User {
        return try {
            // Check if user already exists
            val existingUser = getUserByEmail(user.email)
            if (existingUser != null) {
                throw Exception("User with email ${user.email} already exists")
            }

            // Get next incremental ID
            val nextId = getNextUserId()

            // Generate a new key for the user
            val userKey = usersRef.push().key
                ?: throw Exception("Failed to generate user key")

            // Create user with incremental ID
            val firebaseUser = user.copy(id = nextId)

            // Store user in Firebase using the Firebase key
            usersRef.child(userKey).setValue(firebaseUser).await()

            Log.d(TAG, "User created successfully with ID: $nextId and Firebase key: $userKey")
            firebaseUser

        } catch (e: Exception) {
            Log.e(TAG, "Error creating user: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get the next incremental user ID (1, 2, 3, 4...)
     */
    private suspend fun getNextUserId(): Int {
        return suspendCoroutine { continuation ->
            counterRef.get().addOnSuccessListener { snapshot ->
                val currentCounter = snapshot.getValue(Int::class.java) ?: 0
                val nextId = currentCounter + 1

                // Update the counter in Firebase
                counterRef.setValue(nextId).addOnSuccessListener {
                    Log.d(TAG, "User counter updated to: $nextId")
                    continuation.resume(nextId)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Error updating user counter: ${exception.message}")
                    // Fallback to timestamp if counter update fails
                    continuation.resume(System.currentTimeMillis().toInt())
                }
            }.addOnFailureListener { exception ->
                Log.e(TAG, "Error getting user counter: ${exception.message}")
                // Fallback to timestamp if counter fails
                continuation.resume(System.currentTimeMillis().toInt())
            }
        }
    }

    /**
     * Retrieves a user by email address
     * @param email The email to search for
     * @return User object if found, null otherwise
     */
    suspend fun getUserByEmail(email: String): User? {
        return suspendCoroutine { continuation ->
            usersRef.orderByChild("email").equalTo(email)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                // Get the first (and should be only) user with this email
                                val userSnapshot = snapshot.children.first()
                                val user = userSnapshot.getValue(User::class.java)
                                continuation.resume(user)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user data: ${e.message}", e)
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
     * Retrieves a user by their ID
     * @param userId The user ID to search for
     * @return User object if found, null otherwise
     */
    suspend fun getUserById(userId: Int): User? {
        return suspendCoroutine { continuation ->
            usersRef.orderByChild("id").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        try {
                            if (snapshot.exists()) {
                                val userSnapshot = snapshot.children.first()
                                val user = userSnapshot.getValue(User::class.java)
                                continuation.resume(user)
                            } else {
                                continuation.resume(null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user data: ${e.message}", e)
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
     * Updates a user's information in Firebase
     * @param user The user object with updated information
     */
    suspend fun updateUser(user: User) {
        try {
            // Find the user's Firebase key by their ID
            val userKey = findUserKeyById(user.id)
            if (userKey != null) {
                usersRef.child(userKey).setValue(user).await()
                Log.d(TAG, "User updated successfully")
            } else {
                throw Exception("User not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user: ${e.message}", e)
            throw e
        }
    }

    /**
     * Authenticates a user with email and password
     * @param email User's email
     * @param password User's password
     * @return User object if authentication successful, null otherwise
     */
    suspend fun authenticateUser(email: String, password: String): User? {
        return try {
            // Try lowercase first (new standard)
            var user = getUserByEmail(email.lowercase())

            // If not found, try original case (for legacy users)
            if (user == null) {
                user = getUserByEmail(email)
            }

            if (user != null && user.password == password) {
                Log.d(TAG, "User authenticated successfully")
                user
            } else {
                Log.d(TAG, "Authentication failed for email: $email")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during authentication: ${e.message}", e)
            null
        }
    }

    /**
     * Helper function to find a user's Firebase key by their ID
     * @param userId The user ID to search for
     * @return Firebase key if found, null otherwise
     */
    private suspend fun findUserKeyById(userId: Int): String? {
        return suspendCoroutine { continuation ->
            usersRef.orderByChild("id").equalTo(userId.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val userKey = snapshot.children.first().key
                            continuation.resume(userKey)
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
     * Deletes a user from Firebase (for testing purposes)
     * @param userId The ID of the user to delete
     */
    suspend fun deleteUser(userId: Int) {
        try {
            val userKey = findUserKeyById(userId)
            if (userKey != null) {
                usersRef.child(userKey).removeValue().await()
                Log.d(TAG, "User deleted successfully")
            } else {
                throw Exception("User not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting user: ${e.message}", e)
            throw e
        }
    }

    /**
     * Gets all users (for admin purposes or testing)
     * @return List of all users
     */
    suspend fun getAllUsers(): List<User> {
        return suspendCoroutine { continuation ->
            usersRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val users = mutableListOf<User>()
                        for (userSnapshot in snapshot.children) {
                            val user = userSnapshot.getValue(User::class.java)
                            user?.let { users.add(it) }
                        }
                        continuation.resume(users)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing users: ${e.message}", e)
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