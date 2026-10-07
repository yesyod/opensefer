package app.opensefer.core.domain

/**
 * The few ways a data call can fail, as the UI needs to tell them apart. The data layer maps
 * transport exceptions (Ktor, IO, serialization) onto these, so no framework type crosses into the
 * domain or UI (BLUEPRINT §8 "Error model").
 */
sealed class DataError(message: String, cause: Throwable?) : Exception(message, cause) {
    /** No connection (or it timed out) and nothing usable on the device. */
    class Offline(cause: Throwable? = null) : DataError("No connection", cause)

    /** Sefaria doesn't know this title / ref. */
    class NotFound(cause: Throwable? = null) : DataError("Not found", cause)

    /** Sefaria answered with an error or with data we couldn't read. */
    class Server(cause: Throwable? = null) : DataError("Server error", cause)
}
