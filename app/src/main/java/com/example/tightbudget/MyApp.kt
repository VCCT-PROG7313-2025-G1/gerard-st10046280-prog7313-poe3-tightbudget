package com.example.tightbudget

import android.app.Application
import android.util.Log
import com.example.tightbudget.firebase.FirebaseCategoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApp", "TightBudget app started - Firebase mode")

        // Room database initialization removed - now using Firebase
        // Seed default categories to Firebase on first launch
        seedDefaultCategoriesToFirebase()

        Log.d("MyApp", "Firebase managers initialized successfully")
    }

    /**
     * Seeds default categories to Firebase if none exist
     */
    private fun seedDefaultCategoriesToFirebase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val firebaseCategoryManager = FirebaseCategoryManager.getInstance()
                firebaseCategoryManager.seedDefaultCategories()
                Log.d("MyApp", "Default categories seeded to Firebase")
            } catch (e: Exception) {
                Log.e("MyApp", "Error seeding categories to Firebase: ${e.message}", e)
            }
        }
    }
}