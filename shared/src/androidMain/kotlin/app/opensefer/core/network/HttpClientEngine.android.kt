package app.opensefer.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun httpClientEngine(): HttpClientEngine = OkHttp.create()
