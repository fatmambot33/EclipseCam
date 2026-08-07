package com.fatmambo33.eclipsecam.media

import com.fatmambo33.eclipsecam.capture.CapturePhase
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class MontageSlot {
    PARTIAL_EARLY,
    CONTACT_EARLY,
    TOTALITY,
    CONTACT_LATE,
    PARTIAL_LATE,
}

enum class MontagePanelState { SELECTED, MISSING, EXCLUDED }

data class MontagePanel(
    val slot: MontageSlot,
    val asset: LocalSessionAsset?,
    val state: MontagePanelState,
)

data class MontageSelection(
    val panels: List<MontagePanel>,
) {
    init {
        require(panels.map(MontagePanel::slot) == MontageSlot.entries) {
            "Montage panels must contain every slot exactly once in chronological display order."
        }
        val selectedPaths = panels.mapNotNull(MontagePanel::asset).map { it.file.canonicalPath }
        require(selectedPaths.size == selectedPaths.distinct().size) {
            "A source capture may not be duplicated across montage slots."
        }
    }

    val selectedAssets: List<LocalSessionAsset>
        get() = panels.mapNotNull(MontagePanel::asset)

    val missingSlots: List<MontageSlot>
        get() = panels.filter { it.state == MontagePanelState.MISSING }.map(MontagePanel::slot)
}

fun interface MontageFrameProbe {
    fun isReadable(file: File): Boolean
}

fun interface MontageImageRenderer {
    suspend fun render(selection: MontageSelection, output: File)
}

sealed interface MontageRenderResult {
    data class Completed(
        val output: File,
        val selectedFrameCount: Int,
        val missingSlots: List<MontageSlot>,
    ) : MontageRenderResult

    data class NoFrames(val reason: String) : MontageRenderResult
    data class Failed(val reason: String) : MontageRenderResult
}

/**
 * Chooses an honest, deterministic representative sequence from persisted capture-plan phases.
 *
 * Phase classification must already exist on [LocalSessionAsset.phase]. Filenames are used by the
 * Gallery only to connect a capture to its persisted instruction metadata; this selector never
 * infers a phase from a filename. A single partial/contact capture is shown once in the early slot
 * and the corresponding late slot remains explicitly missing. Totality uses the middle classified
 * frame as a representative; EclipseCam does not claim that this frame is astronomical maximum.
 */
object MontageFrameSelector {
    fun select(
        captures: List<LocalSessionAsset>,
        includedSlots: Set<MontageSlot> = MontageSlot.entries.toSet(),
    ): MontageSelection {
        val classified = captures
            .asSequence()
            .filter { asset ->
                asset.kind == LocalSessionAssetKind.ORIGINAL_CAPTURE &&
                    asset.phase != null &&
                    asset.file.isFile &&
                    asset.sizeBytes > 0L
            }
            .sortedWith(ASSET_ORDER)
            .toList()
        val partial = classified.filter { it.phase == CapturePhase.PARTIAL }
        val contacts = classified.filter { it.phase == CapturePhase.CONTACT_BURST }
        val totality = classified.filter { it.phase == CapturePhase.TOTALITY }

        val defaults = mapOf(
            MontageSlot.PARTIAL_EARLY to partial.firstOrNull(),
            MontageSlot.CONTACT_EARLY to contacts.firstOrNull(),
            MontageSlot.TOTALITY to totality.takeIf(List<*>::isNotEmpty)?.let { it[it.size / 2] },
            MontageSlot.CONTACT_LATE to contacts.lastOrNull()?.takeUnless { it === contacts.firstOrNull() },
            MontageSlot.PARTIAL_LATE to partial.lastOrNull()?.takeUnless { it === partial.firstOrNull() },
        )

        val panels = MontageSlot.entries.map { slot ->
            val default = defaults[slot]
            when {
                slot !in includedSlots -> MontagePanel(slot, null, MontagePanelState.EXCLUDED)
                default == null -> MontagePanel(slot, null, MontagePanelState.MISSING)
                else -> MontagePanel(slot, default, MontagePanelState.SELECTED)
            }
        }
        return MontageSelection(panels)
    }

    private val ASSET_ORDER = compareBy<LocalSessionAsset> { it.instructionIndex ?: Int.MAX_VALUE }
        .thenBy(LocalSessionAsset::modifiedAtUtc)
        .thenBy { it.file.name }
}

/**
 * Renders a phase-aware montage locally and publishes only a complete JPEG.
 *
 * The renderer writes to `generated/montage.rendering.jpg`. A prior complete montage remains
 * untouched until the new temporary output is non-empty and can replace it. Failure or cancellation
 * removes only the temporary artifact. Original captures are never mutated.
 */
class LocalMontageGenerator(
    private val renderer: MontageImageRenderer,
    private val frameProbe: MontageFrameProbe,
) {
    suspend fun render(
        session: LocalCaptureSession,
        includedSlots: Set<MontageSlot> = MontageSlot.entries.toSet(),
    ): MontageRenderResult {
        val readableCaptures = withContext(Dispatchers.IO) {
            session.captures.filter { asset -> frameProbe.isReadable(asset.file) }
        }
        val selection = MontageFrameSelector.select(readableCaptures, includedSlots)
        if (selection.selectedAssets.isEmpty()) {
            return MontageRenderResult.NoFrames(
                "No readable capture with persisted eclipse-phase metadata is available.",
            )
        }

        val generatedDirectory = File(session.directory, GENERATED_DIRECTORY)
        if (!generatedDirectory.isDirectory && !generatedDirectory.mkdirs()) {
            return MontageRenderResult.Failed("Unable to create the local generated-media directory.")
        }
        val temporary = File(generatedDirectory, TEMP_OUTPUT)
        val finalOutput = File(generatedDirectory, FINAL_OUTPUT)
        temporary.delete()

        return try {
            renderer.render(selection, temporary)
            if (!temporary.isFile || temporary.length() <= 0L) {
                temporary.delete()
                MontageRenderResult.Failed("The montage renderer completed without an image output.")
            } else {
                publish(temporary, finalOutput)
                MontageRenderResult.Completed(
                    output = finalOutput,
                    selectedFrameCount = selection.selectedAssets.size,
                    missingSlots = selection.missingSlots,
                )
            }
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: Throwable) {
            temporary.delete()
            MontageRenderResult.Failed(error.message ?: "Local montage rendering failed.")
        }
    }

    private fun publish(temporary: File, finalOutput: File) {
        try {
            Files.move(
                temporary.toPath(),
                finalOutput.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporary.toPath(),
                finalOutput.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val GENERATED_DIRECTORY = "generated"
        const val TEMP_OUTPUT = "montage.rendering.jpg"
        const val FINAL_OUTPUT = "montage.jpg"
    }
}
