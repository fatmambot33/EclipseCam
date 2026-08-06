package com.fatmambo33.eclipsecam.capture

import com.fatmambo33.eclipsecam.camera.capabilities.CameraOutputSize
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraXCaptureSequenceBackendTest {
    @Test
    fun preparesControlsThenCapturesCompensatedFrame() = runBlocking {
        val controls = FakeControls()
        val jpegPort = FakeJpegPort()
        val backend = CameraXCaptureSequenceBackend(controls, CameraXJpegCapture(jpegPort))
        val request = request(focusMode = CaptureFocusMode.MANUAL_INFINITY)
        val frame = frame(CaptureExposureStep.Compensation(-2))

        assertEquals(CameraSequencePreparationResult.Ready, backend.prepare(request))
        assertEquals(CameraFrameCaptureResult.Captured, backend.capture(frame))
        backend.close()

        assertEquals(
            listOf(
                "bind:rear:4000x3000",
                "focus:infinity",
                "meter:white-balance-lock",
                "exposure:compensation:-2",
                "restore",
            ),
            controls.calls,
        )
        assertEquals(frame.output.imageFile, jpegPort.output)
    }

    @Test
    fun returnsRecoverablePreparationFailureWithoutCapturing() = runBlocking {
        val controls = FakeControls(
            bindResult = CameraXControlResult.RecoverableFailure("Camera is busy."),
        )
        val jpegPort = FakeJpegPort()
        val backend = CameraXCaptureSequenceBackend(controls, CameraXJpegCapture(jpegPort))

        assertEquals(
            CameraSequencePreparationResult.RecoverableFailure("Camera is busy."),
            backend.prepare(request()),
        )
        assertEquals(null, jpegPort.output)
    }

    @Test
    fun appliesRelativeManualExposureBeforeJpeg() = runBlocking {
        val controls = FakeControls()
        val backend = CameraXCaptureSequenceBackend(
            controls,
            CameraXJpegCapture(FakeJpegPort()),
        )
        val frame = frame(CaptureExposureStep.RelativeManualEv(2))

        assertEquals(CameraSequencePreparationResult.Ready, backend.prepare(request()))
        assertEquals(CameraFrameCaptureResult.Captured, backend.capture(frame))

        assertTrue("exposure:manual:2" in controls.calls)
    }

    @Test
    fun rejectsCaptureBeforePreparation() = runBlocking {
        val backend = CameraXCaptureSequenceBackend(
            FakeControls(),
            CameraXJpegCapture(FakeJpegPort()),
        )

        assertEquals(
            CameraFrameCaptureResult.FatalFailure("CameraX capture sequence was not prepared."),
            backend.capture(frame(CaptureExposureStep.Compensation(0))),
        )
    }

    private fun request(
        focusMode: CaptureFocusMode = CaptureFocusMode.CONTINUOUS_AUTO,
    ) = CameraCaptureRequest(
        cameraId = "rear",
        outputSize = CameraOutputSize(width = 4000, height = 3000),
        focusMode = focusMode,
        whiteBalanceMode = CaptureWhiteBalanceMode.AUTO_LOCK_AFTER_METERING,
        exposureProgram = CaptureExposureProgram.ExposureCompensation(listOf(0)),
    )

    private fun frame(exposure: CaptureExposureStep): CameraCaptureFrame {
        val file = File.createTempFile("eclipsecam-backend-", ".jpg").apply { deleteOnExit() }
        return CameraCaptureFrame(
            ordinal = 0,
            exposure = exposure,
            output = CaptureOutput(imageFile = file),
        )
    }

    private class FakeJpegPort : CameraXJpegCapturePort {
        var output: File? = null

        override fun capture(outputFile: File, callback: CameraXJpegCapturePort.Callback) {
            output = outputFile
            callback.onSaved()
        }
    }

    private class FakeControls(
        private val bindResult: CameraXControlResult = CameraXControlResult.Applied,
    ) : CameraXCaptureControlPort {
        val calls = mutableListOf<String>()

        override suspend fun bind(cameraId: String, width: Int, height: Int): CameraXControlResult {
            calls += "bind:$cameraId:${width}x$height"
            return bindResult
        }

        override suspend fun setContinuousAutoFocus(): CameraXControlResult {
            calls += "focus:continuous"
            return CameraXControlResult.Applied
        }

        override suspend fun setManualInfinityFocus(): CameraXControlResult {
            calls += "focus:infinity"
            return CameraXControlResult.Applied
        }

        override suspend fun meterAndLockWhiteBalance(): CameraXControlResult {
            calls += "meter:white-balance-lock"
            return CameraXControlResult.Applied
        }

        override suspend fun setExposureCompensation(steps: Int): CameraXControlResult {
            calls += "exposure:compensation:$steps"
            return CameraXControlResult.Applied
        }

        override suspend fun setRelativeManualExposure(offsetEv: Int): CameraXControlResult {
            calls += "exposure:manual:$offsetEv"
            return CameraXControlResult.Applied
        }

        override suspend fun restore(): CameraXControlResult {
            calls += "restore"
            return CameraXControlResult.Applied
        }
    }
}
