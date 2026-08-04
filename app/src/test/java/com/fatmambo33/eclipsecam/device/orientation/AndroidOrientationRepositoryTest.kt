package com.fatmambo33.eclipsecam.device.orientation

import android.view.Surface
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidOrientationRepositoryTest {
    @Test
    fun `gyroscope magnitude is converted from radians to degrees per second`() {
        val speed = angularSpeedDegreesPerSecond(floatArrayOf(0f, 0f, PI.toFloat()))

        assertEquals(180.0, speed, 0.0001)
    }

    @Test
    fun `gyroscope magnitude combines all axes`() {
        val speed = angularSpeedDegreesPerSecond(floatArrayOf(1f, 2f, 2f))

        assertEquals(Math.toDegrees(3.0), speed, 0.0001)
    }

    @Test
    fun `invalid gyroscope sample is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            angularSpeedDegreesPerSecond(floatArrayOf(1f, 2f))
        }
    }

    @Test
    fun `surface rotations map to processor rotations`() {
        assertEquals(DisplayRotation.ROTATION_0, Surface.ROTATION_0.toDisplayRotation())
        assertEquals(DisplayRotation.ROTATION_90, Surface.ROTATION_90.toDisplayRotation())
        assertEquals(DisplayRotation.ROTATION_180, Surface.ROTATION_180.toDisplayRotation())
        assertEquals(DisplayRotation.ROTATION_270, Surface.ROTATION_270.toDisplayRotation())
    }

    @Test
    fun `unknown surface rotation falls back safely`() {
        assertEquals(DisplayRotation.ROTATION_0, 99.toDisplayRotation())
    }
}
