package com.example.tightbudget.utils

import android.util.Log
import com.example.tightbudget.firebase.FirebaseCategoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper class to migrate existing global categories to user-specific categories
 * Run this once during app upgrade to migrate existing data
 */
object CategoryMigrationHelper {
    private const val TAG = "CategoryMigrationHelper"

    /**
     * Migrates all existing global categories to user-specific for a given user
     * This should be called once per user during their first login after the update
     */
    suspend fun migrateGlobalCategoriesToUserSpecific(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting category migration for user: $userId")

            val firebaseCategoryManager = FirebaseCategoryManager.getInstance()

            // Check if user already has categories (skip migration if they do)
            val existingUserCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
            if (existingUserCategories.isNotEmpty()) {
                Log.d(TAG, "User $userId already has ${existingUserCategories.size} categories, skipping migration")
                return@withContext true
            }

            // Perform the migration
            firebaseCategoryManager.migrateGlobalCategoriesToUserSpecific(userId)

            // Verify migration worked
            val migratedCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
            Log.d(TAG, "Migration completed for user $userId. Migrated ${migratedCategories.size} categories")

            return@withContext migratedCategories.isNotEmpty()

        } catch (e: Exception) {
            Log.e(TAG, "Error during category migration for user $userId: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Checks if a user needs category migration
     * Returns true if user has no categories and global categories exist
     */
    suspend fun userNeedsMigration(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val firebaseCategoryManager = FirebaseCategoryManager.getInstance()

            // Check if user has any categories
            val userCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
            if (userCategories.isNotEmpty()) {
                return@withContext false // User already has categories
            }

            // Check if there are any global categories to migrate
            // This would require checking the old "categories" node in Firebase
            // For now, we'll assume migration is needed if user has no categories
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Error checking migration status for user $userId: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Seeds default categories for a user if they have none
     * This is called during login/registration to ensure users have default categories
     */
    suspend fun ensureUserHasCategories(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val firebaseCategoryManager = FirebaseCategoryManager.getInstance()

            // Check if user already has categories
            val existingCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
            if (existingCategories.isNotEmpty()) {
                Log.d(TAG, "User $userId already has ${existingCategories.size} categories")
                return@withContext true
            }

            // First try migration (in case there are global categories to migrate)
            val migrationSuccess = migrateGlobalCategoriesToUserSpecific(userId)
            if (migrationSuccess) {
                Log.d(TAG, "Successfully migrated categories for user $userId")
                return@withContext true
            }

            // If migration didn't work, seed default categories
            Log.d(TAG, "No categories to migrate, seeding defaults for user $userId")
            firebaseCategoryManager.seedDefaultCategoriesForUser(userId)

            // Verify seeding worked
            val seededCategories = firebaseCategoryManager.getAllCategoriesForUser(userId)
            Log.d(TAG, "Seeded ${seededCategories.size} default categories for user $userId")

            return@withContext seededCategories.isNotEmpty()

        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring user has categories: ${e.message}", e)
            return@withContext false
        }
    }
}