
package com.example.mjpegstreamer

import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream

class MJPEGStream : InputStream() {
    override fun read(): Int {
        return inputStream.read()
    }

    companion object {
        private val inputStream: PipedInputStream
        private val outputStream: PipedOutputStream

        init {
            outputStream = PipedOutputStream()
            inputStream = PipedInputStream(outputStream, 64 * 1024)
        }

        fun sendJpegFrame(jpegData: ByteArray) {
            try {
                outputStream.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpegData.size}\r\n\r\n").toByteArray())
                outputStream.write(jpegData)
                outputStream.write("\r\n\r\n".toByteArray())
                outputStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
