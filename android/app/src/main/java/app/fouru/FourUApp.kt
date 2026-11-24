package app.fouru

import android.app.Application
import app.fouru.push.Notifier

class FourUApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
    }
}
