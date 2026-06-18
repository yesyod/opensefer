package app.opensefer.core.data

import app.opensefer.core.model.ChapterText

/**
 * A tiny in‑memory LRU for loaded chapters — this is the "network‑first but feels instant" engine.
 * Insertion‑order [LinkedHashMap] + manual re‑insert on access keeps it multiplatform‑safe
 * (no JVM‑only access‑order constructor). See BLUEPRINT §8 for the caching strategy.
 */
class SectionCache(private val maxSize: Int = 64) {

    private val entries = LinkedHashMap<String, ChapterText>()

    fun get(key: String): ChapterText? {
        val value = entries.remove(key) ?: return null
        entries[key] = value // mark most‑recently used
        return value
    }

    fun put(key: String, value: ChapterText) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > maxSize) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }
}
