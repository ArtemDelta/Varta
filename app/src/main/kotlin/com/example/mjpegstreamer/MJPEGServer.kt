package com.example.mjpegstreamer

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class MJPEGServer : NanoHTTPD(8080) {

    companion object {
        @Volatile
        var motionDetected = false
    }

    fun startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false)
            println("✅ MJPEG Server started on port 8080")
        } catch (ioe: IOException) {
            System.err.println("❌ Couldn't start server:\n$ioe")
        }
    }

    override fun serve(session: IHTTPSession): Response {
        return when (session.uri) {

            "/" -> {
                val html = this::class.java.getResource("/assets/index.html")?.readText()
                    ?: "<h1>📷 MJPEG Stream</h1><img src='/stream' />"
                newFixedLengthResponse(Response.Status.OK, "text/html", html)
            }

            "/stream" -> {
                newChunkedResponse(
                    Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=--frame",
                    MJPEGStream()
                )
            }

            "/motion-status" -> {
                val result = if (motionDetected) {
                    motionDetected = false
                    "true"
                } else "false"
                newFixedLengthResponse(result)
            }

            "/switch-camera" -> {
                MainActivity.switchRequested = true
                return newFixedLengthResponse("ok")
            }

            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
            }
        }
    }
}
