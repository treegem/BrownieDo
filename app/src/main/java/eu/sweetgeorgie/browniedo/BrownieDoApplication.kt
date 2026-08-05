package eu.sweetgeorgie.browniedo

import android.app.Application

class BrownieDoApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(applicationContext)
    }
}
