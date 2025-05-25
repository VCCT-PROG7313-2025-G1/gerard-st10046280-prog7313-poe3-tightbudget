package com.example.tightbudget

import android.app.Application
import android.util.Log

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApp", "TightBudget app started - Firebase mode")

        // Room database initialization removed - now using Firebase
        // Category seeding is handled by individual Firebase managers
        // All data is now stored in Firebase Realtime Database

        Log.d("MyApp", "Firebase managers initialized successfully")
    }
}