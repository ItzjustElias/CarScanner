package com.example.carscanner

import android.app.Application
import android.content.Context
import com.example.carscanner.auth.AuthManager
import com.example.carscanner.data.AppDatabase
import com.example.carscanner.data.CarRepository
import com.example.carscanner.data.GeminiService

class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)
    val auth = AuthManager(context)
    val repository = CarRepository(
        context,
        database.carDao(),
        GeminiService(BuildConfig.GEMINI_API_KEY, BuildConfig.GEMINI_MODEL, BuildConfig.GEMINI_SEARCH),
    )
}

class CarScannerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
