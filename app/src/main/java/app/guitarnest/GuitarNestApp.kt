package app.guitarnest

import android.app.Application
import app.guitarnest.feedback.CrashLog

class GuitarNestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
