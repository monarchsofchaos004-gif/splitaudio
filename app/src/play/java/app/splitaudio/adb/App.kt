package app.splitaudio.adb

import android.app.Application

class App : Application() {
    override fun onCreate() { super.onCreate(); Adb.init(this) }
}
