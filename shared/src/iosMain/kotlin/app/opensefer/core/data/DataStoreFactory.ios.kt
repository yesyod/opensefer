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
 * Saved texts live in their own folder in Application Support (never purged by the OS), flagged
 * "do not back up": they are re‑downloadable, so they must not fill the user's iCloud backup
 * (Apple's data storage guidelines). Only that folder is flagged — anything else the app keeps in
 * Application Support is still backed up.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun diskCacheDir(): String {
    val manager = NSFileManager.defaultManager
    val support: NSURL = requireNotNull(
        manager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ),
    ) { "No NSApplicationSupportDirectory" }
    val texts: NSURL = requireNotNull(support.URLByAppendingPathComponent(OFFLINE_TEXTS_DIR)) { "No texts folder URL" }
    manager.createDirectoryAtURL(texts, withIntermediateDirectories = true, attributes = null, error = null)
    texts.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(texts.path) { "No texts folder path" }
}

private const val OFFLINE_TEXTS_DIR = "OfflineTexts"
