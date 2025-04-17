package com.example.mjpegstreamer

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var startBtn: Button
    private lateinit var ipText: TextView
    private lateinit var statusText: TextView

    private var server: MJPEGServer? = null
    private var isStreaming = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastFrame: ByteArray? = null

    companion object {
        @Volatile
        var useFrontCamera = false

        @Volatile
        var switchRequested = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        startBtn = findViewById(R.id.startButton)
        ipText = findViewById(R.id.ipTextView)
        statusText = findViewById(R.id.statusTextView)

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET),
            1
        )

        startBtn.setOnClickListener {
            if (!isStreaming) {
                startCamera()
                server = MJPEGServer()
                server?.startServer()
                ipText.text = "http://${getLocalIpAddress()}:8080"
                statusText.text = "📡 Streaming..."
                startBtn.text = "Stop Streaming"
                isStreaming = true
            } else {
                server?.stop()
                server = null
                statusText.text = "📴 Not streaming"
                startBtn.text = "Start Streaming"
                ipText.text = "IP will appear here"
                isStreaming = false
            }
        }

        startSwitchWatcher()
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
            "127.0.0.1"
        } catch (ex: Exception) {
            ex.printStackTrace()
            "127.0.0.1"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            if (switchRequested) {
                useFrontCamera = !useFrontCamera
                switchRequested = false
            }

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                try {
                    val yPlane = imageProxy.planes[0].buffer
                    val ySize = yPlane.remaining()
                    val currentFrame = ByteArray(ySize)
                    yPlane.get(currentFrame)

                    val motion = lastFrame?.let { compareFrames(it, currentFrame) } ?: false
                    lastFrame = currentFrame

                    if (motion) MJPEGServer.motionDetected = true

                    val uPlane = imageProxy.planes[1].buffer
                    val vPlane = imageProxy.planes[2].buffer
                    val nv21 = ByteArray(ySize + uPlane.remaining() + vPlane.remaining())

                    System.arraycopy(currentFrame, 0, nv21, 0, ySize)

                    var offset = ySize
                    val chromaRowStride = imageProxy.planes[1].rowStride
                    val chromaPixelStride = imageProxy.planes[1].pixelStride

                    for (row in 0 until imageProxy.height / 2) {
                        for (col in 0 until imageProxy.width / 2) {
                            val vuIndex = row * chromaRowStride + col * chromaPixelStride
                            nv21[offset++] = vPlane.get(vuIndex)
                            nv21[offset++] = uPlane.get(vuIndex)
                        }
                    }

                    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
                    val out = ByteArrayOutputStream()
                    yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 50, out)
                    MJPEGStream.sendJpegFrame(out.toByteArray())
                } catch (e: Exception) {
                    Log.e("CameraAnalyzer", "Error: ${e.message}")
                } finally {
                    imageProxy.close()
                }
            }

            try {
                cameraProvider.unbindAll()
                val cameraSelector = if (useFrontCamera)
                    CameraSelector.DEFAULT_FRONT_CAMERA
                else
                    CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
                Log.d("CameraX", "✅ Camera bound")
            } catch (e: Exception) {
                Log.e("CameraX", "🚨 Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun compareFrames(frame1: ByteArray, frame2: ByteArray, threshold: Int = 20): Boolean {
        if (frame1.size != frame2.size) return false
        var diff = 0
        for (i in frame1.indices step 10) {
            diff += abs(frame1[i].toInt() - frame2[i].toInt())
        }
        val avgDiff = diff / (frame1.size / 10)
        return avgDiff > threshold
    }

    private fun startSwitchWatcher() {
        val handler = Handler(mainLooper)
        handler.post(object : Runnable {
            override fun run() {
                if (switchRequested && isStreaming) {
                    Log.d("CameraX", "🔁 Switching camera...")
                    startCamera()
                }
                handler.postDelayed(this, 1000)
            }
        })
    }
}
