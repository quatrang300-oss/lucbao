@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package vn.lucbao.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * HTTP data source whose requests are shaped by the engine (headers, POST body,
 * range/rn query parameters), so YouTube-specific quirks can be fixed by an
 * engine update without reinstalling the app.
 */
class EngineHttpDataSource private constructor(
    private val kind: Int,
    private val upstream: HttpDataSource,
) : DataSource {

    class Factory(private val kind: Int) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            EngineHttpDataSource(kind, OkHttpDataSource.Factory(client).createDataSource())
    }

    companion object {
        /**
         * One client for every video request: connections to YouTube stay open and are reused
         * (HTTP/2 when offered), so each new piece of video starts downloading right away
         * instead of connecting again.
         */
        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val plan = EngineManager.get().shapeRequest(
            dataSpec.uri.toString(), dataSpec.position, dataSpec.length, kind
        )
        val headers = HashMap(dataSpec.httpRequestHeaders)
        plan.headers?.let { headers.putAll(it) }
        val builder = dataSpec.buildUpon()
            .setUri(Uri.parse(plan.url))
            .setHttpRequestHeaders(headers)
            .setHttpMethod(
                if (plan.method == "POST") DataSpec.HTTP_METHOD_POST else DataSpec.HTTP_METHOD_GET
            )
            .setHttpBody(plan.body)
        if (plan.rangeInUrl) {
            // The range is in the URL: ask for the whole response, without a Range header.
            builder.setPosition(0).setLength(C.LENGTH_UNSET.toLong())
        }
        return upstream.open(builder.build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()
}
