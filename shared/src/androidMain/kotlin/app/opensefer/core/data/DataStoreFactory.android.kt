package app.opensefer.core.data

import android.content.Context
import androidx.startup.Initializer

/** Application context, captured by [AppContextInitializer] before any DataStore is built. */
internal lateinit var appContext: Context

internal actual fun dataStoreDir(): String = appContext.filesDir.absolutePath

/**
 * Captures the application [Context] via Jetpack App Startup, so the persistence layer needs no
 * `Application` subclass and the app keeps Koin Context‑free. Registered in this module's manifest
 * (`shared/src/androidMain/AndroidManifest.xml`). See docs/adr/0002.
 */
class AppContextInitializer : Initializer<Context> {
    override fun create(context: Context): Context =
        context.applicationContext.also { appContext = it }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
