package app.splitaudio.adb

import android.app.Application
import app.splitaudio.Crash

class App : Application() {
    override fun onCreate() { super.onCreate(); Crash.install(this); Adb.init(this) }
}
