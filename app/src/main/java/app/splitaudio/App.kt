package app.splitaudio

import android.app.Application

/** 0.5.4: process-wide setup. Only the crash capture lives here (see [Crash]); everything else starts from the activity / services. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Crash.install(this)
    }
}
