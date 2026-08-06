package com.fatmambo33.eclipsecam.capture

import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProductionIndexedCameraCaptureFactoryTest {
    @Test
    fun blankSessionIdIsRejectedBeforeCaptureDependenciesAreUsed() {
        val factory = factory(selectedCamera = { error("must not run") })

        assertThrows(IllegalArgumentException::class.java) {
            factory.create(" ")
        }
    }

    @Test
    fun unavailableCameraCapabilityFailsClosedBeforeOutputReservation() = runBlocking {
        var reservations = 0
        val factory = ProductionIndexedCameraCaptureFactory(
            selectedCamera = { error("No validated rear camera is available.") },
            outputAllocator = object : CaptureOutputAllocator {
                override fun reserve(
                    sessionId: String,
                    instructionIndex: Int,
                    capturedAtUtc: Instant,
                ): CaptureOutput {
                    reservations += 1
                    error("must not reserve")
                }

                override fun release(output: CaptureOutput): Boolean = true
            },
            backendFactory = CameraCaptureSequenceBackendFactory {
                error("must not construct backend")
            },
        )
        val instant = Instant.parse("2026-08-12T17:00:00Z")

        val result = factory.create("session").capture(
            instructionIndex = 0,
            instruction = CaptureInstruction(
                instantUtc = instant,
                phase = CapturePhase.CONTACT_BURST,
                exposureStrategy = ExposureStrategy.CONTACT_BRACKET,
            ),
        )

        assertEquals(
            CameraCaptureResult.FatalError("No validated rear camera is available."),
            result,
        )
        assertEquals(0, reservations)
    }

    private fun factory(
        selectedCamera: () -> com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilities,
    ): ProductionIndexedCameraCaptureFactory = ProductionIndexedCameraCaptureFactory(
        selectedCamera = selectedCamera,
        outputAllocator = object : CaptureOutputAllocator {
            override fun reserve(
                sessionId: String,
                instructionIndex: Int,
                capturedAtUtc: Instant,
            ): CaptureOutput = error("must not reserve")

            override fun release(output: CaptureOutput): Boolean = true
        },
        backendFactory = CameraCaptureSequenceBackendFactory {
            error("must not construct backend")
        },
    )
}
