package app.opensefer.core.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal actual fun dataStoreDir(): String {
    val documents = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(requireNotNull(documents).path) { "No NSDocumentDirectory path" }
}

/**
 * Saved texts live in Application Support (never purged by the OS) and are flagged
 * "do not back up": they are re‑downloadable, so they must not fill the user's iCloud backup
 * (Apple's data storage guidelines).
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun diskCacheDir(): String {
    val support: NSURL = requireNotNull(
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "No NSApplicationSupportDirectory" }
    support.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(support.path) { "No NSApplicationSupportDirectory path" }
}
