package com.fatmambo33.eclipsecam.capture

import android.Manifest
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.fatmambo33.eclipsecam.camera.capabilities.CameraCapabilityInventory
import com.fatmambo33.eclipsecam.camera.capabilities.LensFacing
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@ExperimentalCamera2Interop
class CameraXRearLensInstrumentationTest {
    @get:Rule
    val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val owner = InstrumentedLifecycleOwner()

    @Before
    fun startLifecycle() {
        instrumentation.runOnMainSync { owner.start() }
    }

    @After
    fun stopLifecycle() {
        instrumentation.runOnMainSync { owner.stop() }
    }

    @Test
    fun everyRearJpegCameraBindsToItsExactCameraId() = runBlocking {
        val rearCameras = CameraCapabilityInventory(context).readAll()
            .filter { it.facing == LensFacing.BACK && it.jpegSizes.isNotEmpty() }
        assertTrue("At least one rear JPEG camera is required.", rearCameras.isNotEmpty())

        rearCameras.forEach { capability ->
            val size = capability.jpegSizes.first()
            val binding = ProcessCameraProviderCaptureBindingPort(context, owner)
            val result = binding.bind(
                CameraXCaptureBindingRequest(capability.cameraId, size.width, size.height),
            )
            assertTrue(
                "Rear camera ${capability.cameraId} failed exact CameraX binding: $result",
                result is CameraXCaptureBindingResult.Ready,
            )
            binding.unbind()
        }
    }

    private class InstrumentedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun stop() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}
