package app.opensefer.core.data

import app.opensefer.core.domain.DataError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

/**
 * Runs a data call and wraps its outcome in a [Result] whose failure is always a [DataError].
 * Unlike a bare `runCatching`, cancellation is re‑thrown, so a cancelled load never masquerades as
 * an error (structured concurrency stays intact).
 */
internal suspend inline fun <T> dataResult(block: () -> T): Result<T> {
    val result = runCatching { block() }
    val error = result.exceptionOrNull() ?: return result
    if (error is CancellationException) throw error
    return Result.failure(error.toDataError())
}

/** Maps transport failures onto the domain's small [DataError] vocabulary. */
internal fun Throwable.toDataError(): DataError = when (this) {
    is DataError -> this
    is ClientRequestException ->
        if (response.status == HttpStatusCode.NotFound) DataError.NotFound(this) else DataError.Server(this)
    is ResponseException -> DataError.Server(this)
    is HttpRequestTimeoutException -> DataError.Offline(this)
    is IOException -> DataError.Offline(this)
    else -> DataError.Server(this)
}
