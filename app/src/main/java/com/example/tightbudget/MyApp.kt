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
        Log.d("MyApp", "TightBudget app started - Firebase mode with user-specific categories")

        // Note: We no longer seed global categories here since categories are now user-specific
        // Categories will be seeded when a user first logs in or creates an account
        // This is handled in:
        // 1. LoginActivity - when user logs in successfully
        // 2. RegisterActivity - when user creates a new account
        // 3. Individual activities when they detect no categories exist for the user

        Log.d("MyApp", "Firebase managers initialized successfully")
    }
}