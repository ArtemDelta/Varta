package com.example.mjpegstreamer

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MotionDetectionTest {

    private fun compareFrames(frame1: ByteArray, frame2: ByteArray, threshold: Int = 20): Boolean {
        if (frame1.size != frame2.size) return false
        var diff = 0
        for (i in frame1.indices step 10) {
            diff += kotlin.math.abs(frame1[i].toInt() - frame2[i].toInt())
        }
        val avgDiff = diff / (frame1.size / 10)
        return avgDiff > threshold
    }

    @Test
    fun testIdenticalFrames_noMotionDetected() {
        val frame1 = ByteArray(1000) { 120 }
        val frame2 = ByteArray(1000) { 120 }
        assertFalse("Identical frames should not trigger motion", compareFrames(frame1, frame2))
    }

    @Test
    fun testSlightlyDifferentFrames_motionDetected() {
        val frame1 = ByteArray(1000) { 120 }
        val frame2 = frame1.copyOf()

        // Змінюємо 20 байтів кожні 10 позицій — цього достатньо для помітного diff
        for (i in 0 until 200 step 10) {
            frame2[i] = (frame2[i] - 30).toByte()
        }

        assertTrue("Slightly different frames should trigger motion", compareFrames(frame1, frame2, threshold = 5))
    }

    @Test
    fun testRandomlyDifferentFrames_motionDetected() {
        val frame1 = ByteArray(1000) { 120 }
        val frame2 = frame1.copyOf()

        // Змінюємо випадкові 10% пікселів
        val indices = frame1.indices.shuffled().take((frame1.size * 0.1).toInt())
        for (i in indices) {
            frame2[i] = (frame2[i] + Random.nextInt(-50, 50)).toByte()
        }

        assertTrue("Random difference in 10% of pixels should trigger motion", compareFrames(frame1, frame2, threshold = 5))
    }
}
