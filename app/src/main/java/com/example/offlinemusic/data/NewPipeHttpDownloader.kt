package com.example.offlinemusic.data

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/** Adapts the platform HTTP client for NewPipe's URL resolver. */
class NewPipeHttpDownloader : Downloader() {
    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            request.headers().forEach { (name, values) ->
                values.forEach { value -> addRequestProperty(name, value) }
            }
            request.dataToSend()?.let { body ->
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        val responseBody = (if (connection.responseCode in 200..399) {
            connection.inputStream
        } else {
            connection.errorStream
        })?.bufferedReader()?.use { it.readText() }.orEmpty()
        val headers = connection.headerFields
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        return Response(
            connection.responseCode,
            connection.responseMessage.orEmpty(),
            headers,
            responseBody,
            connection.url.toString()
        )
    }
}
