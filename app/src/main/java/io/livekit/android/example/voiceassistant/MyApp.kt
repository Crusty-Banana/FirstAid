package io.livekit.android.example.voiceassistant
import android.app.Application
import com.github.ajalt.timberkt.Timber
import io.livekit.android.example.voiceassistant.auth.AuthManager

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Timber for logging (if you use it throughout the app)
        val DEBUG = true
        if (DEBUG) { // BuildConfig is auto-generated
            Timber.plant(Timber.DebugTree())
        }
        // Initialize AuthManager with the application context
        AuthManager.initialize(this)
    }
}