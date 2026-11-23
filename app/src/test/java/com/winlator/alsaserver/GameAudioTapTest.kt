package com.winlator.alsaserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GameAudioTapTest {
    private fun header(p: ByteArray): Triple<Int, Int, Int> {
        val b = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
        return Triple(b.int, b.int, b.int)
    }

    private fun samples(p: ByteArray): List<Int> {
        val b = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
        b.position(12)
        return List((p.size - 12) / 2) { b.short.toInt() }
    }

    @Test
    fun stereoS16IsMixedToMonoWithoutMovingTheBuffer() {
        val data = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        // 4 frames: (L, R)
        listOf(1000, 3000, -2000, -2000, 32767, 32767, 0, 0).forEach { data.putShort(it.toShort()) }
        data.position(0).limit(16)
        val p = GameAudioTap.encode(data, ALSAClient.DataType.S16LE, 2, 48000)!!
        assertEquals(Triple(GameAudioTap.MAGIC, 48000, 4), header(p))
        val s = samples(p)
        assertEquals(2000, s[0], 1)
        assertEquals(-2000, s[1], 1)
        assertEquals(32766, s[2], 2)
        assertEquals(0, s[3])
        assertEquals(0, data.position())
    }

    @Test
    fun floatAndPartialBufferUseOnlyTheLimit() {
        val data = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        data.putFloat(0.5f).putFloat(-0.25f).putFloat(2f) // the last is clipped
        data.position(0).limit(12)
        val p = GameAudioTap.encode(data, ALSAClient.DataType.FLOATLE, 1, 44100)!!
        assertEquals(Triple(GameAudioTap.MAGIC, 44100, 3), header(p))
        org.junit.Assert.assertEquals(listOf(16384, -8192, 32767), samples(p))
    }

    @Test
    fun emptyBufferMakesNoPacket() {
        val data = ByteBuffer.allocate(4)
        data.limit(0)
        assertNull(GameAudioTap.encode(data, ALSAClient.DataType.S16LE, 2, 48000))
    }

    private fun assertEquals(expected: Int, actual: Int, tolerance: Int) {
        org.junit.Assert.assertTrue("expected $expected got $actual", kotlin.math.abs(expected - actual) <= tolerance)
    }
}
