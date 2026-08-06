package com.fatmambo33.eclipsecam.capture

import androidx.camera.core.ImageCapture
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CameraXJpegCaptureTest {
    @Test
    fun `awaits successful CameraX save callback`() = runBlocking {
        val port = FakePort()
        val capture = CameraXJpegCapture(port)
        val frame = frame()

        val result = async { capture.capture(frame) }
        yield()

        assertEquals(frame.output.imageFile, port.outputFile)
        port.callback!!.onSaved()

        assertSame(CameraFrameCaptureResult.Captured, result.await())
    }

    @Test
    fun `maps CameraX callback errors through stable failure policy`() = runBlocking {
        val port = FakePort()
        val capture = CameraXJpegCapture(port)

        val result = async { capture.capture(frame()) }
        yield()
        port.callback!!.onError(ImageCapture.ERROR_CAMERA_CLOSED, "camera restarted")

        assertEquals(
            CameraFrameCaptureResult.RecoverableFailure("camera restarted"),
            result.await(),
        )
    }

    @Test
    fun `ignores late callback after coroutine cancellation`() = runBlocking {
        val port = FakePort()
        val capture = CameraXJpegCapture(port)

        val job = launch { capture.capture(frame()) }
        yield()
        job.cancelAndJoin()

        port.callback!!.onSaved()
        port.callback!!.onError(ImageCapture.ERROR_FILE_IO, "late failure")

        assertEquals(true, job.isCancelled)
    }

    private fun frame(): CameraCaptureFrame {
        val directory = File("build/test-capture")
        return CameraCaptureFrame(
            ordinal = 0,
            exposure = CaptureExposureStep.Compensation(0),
            output = CaptureOutput(directory, File(directory, "frame.jpg")),
        )
    }

    private class FakePort : CameraXJpegCapturePort {
        var outputFile: File? = null
        var callback: CameraXJpegCapturePort.Callback? = null

        override fun capture(outputFile: File, callback: CameraXJpegCapturePort.Callback) {
            this.outputFile = outputFile
            this.callback = callback
        }
    }
}
