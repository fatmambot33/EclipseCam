package com.fatmambo33.eclipsecam.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatmambo33.eclipsecam.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val MontageCardBackground = Color(0xFF111827)
private val MontageMuted = Color(0xFFCBD5E1)
private val MontageReady = Color(0xFF4ADE80)
private val MontageFailed = Color(0xFFFCA5A5)
private val MontageAccent = Color(0xFF60A5FA)

private sealed interface MontageUiState {
    data object Idle : MontageUiState
    data object Rendering : MontageUiState
    data class Complete(val frameCount: Int, val missingCount: Int) : MontageUiState
    data class Failed(val reason: String) : MontageUiState
}

/** Phase-aware montage plus explicit export/share actions embedded in one Gallery detail. */
@Composable
fun LocalMontageCard(
    session: LocalCaptureSession,
    onGenerated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val generator = remember(context) {
        LocalMontageGenerator(
            renderer = AndroidMontageImageRenderer(),
            frameProbe = AndroidJpegMontageFrameProbe(),
        )
    }
    val defaultSelection = remember(session) { MontageFrameSelector.select(session.captures) }
    val availableSlots = remember(defaultSelection) {
        defaultSelection.panels
            .filter { it.state == MontagePanelState.SELECTED }
            .associate { it.slot to checkNotNull(it.asset) }
    }
    var includedSlots by remember(session.sessionId) {
        mutableStateOf(MontageSlot.entries.toSet())
    }
    var state by remember(session.sessionId) { mutableStateOf<MontageUiState>(MontageUiState.Idle) }
    val currentSelection = remember(session, includedSlots) {
        MontageFrameSelector.select(session.captures, includedSlots)
    }
    val hasMontage = session.generatedAssets.any { it.kind == LocalSessionAssetKind.MONTAGE }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("montage-card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MontageCardBackground),
        ) {
            Column(Modifier.padding(18.dp)) {
                Text(stringResource(R.string.gallery_montage_title), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.gallery_montage_body),
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    color = MontageMuted,
                )

                MontageSlot.entries.forEach { slot ->
                    val asset = availableSlots[slot]
                    val slotLabel = montageSlotLabelResource(slot)
                    if (asset == null) {
                        Text(
                            stringResource(R.string.gallery_montage_missing_format, slotLabel),
                            modifier = Modifier.padding(vertical = 4.dp).testTag("montage-slot-${slot.name.lowercase()}"),
                            color = MontageMuted,
                        )
                    } else {
                        FilterChip(
                            selected = slot in includedSlots,
                            onClick = {
                                includedSlots = if (slot in includedSlots) {
                                    includedSlots - slot
                                } else {
                                    includedSlots + slot
                                }
                            },
                            label = { Text(slotLabel) },
                            modifier = Modifier.testTag("montage-slot-${slot.name.lowercase()}"),
                        )
                        Text(
                            asset.file.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MontageMuted,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                when (val current = state) {
                    MontageUiState.Idle -> Text(
                        if (hasMontage) {
                            stringResource(R.string.gallery_montage_available)
                        } else {
                            stringResource(R.string.gallery_montage_ready)
                        },
                        color = MontageMuted,
                        modifier = Modifier.testTag("montage-status"),
                    )
                    MontageUiState.Rendering -> Text(
                        stringResource(R.string.gallery_montage_generating),
                        color = MontageAccent,
                        modifier = Modifier.testTag("montage-status"),
                    )
                    is MontageUiState.Complete -> Text(
                        stringResource(
                            R.string.gallery_montage_complete_format,
                            current.frameCount,
                            current.missingCount,
                        ),
                        color = MontageReady,
                        modifier = Modifier.testTag("montage-status"),
                    )
                    is MontageUiState.Failed -> Text(
                        current.reason,
                        color = MontageFailed,
                        modifier = Modifier.testTag("montage-status"),
                    )
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = state !is MontageUiState.Rendering && currentSelection.selectedAssets.isNotEmpty(),
                    onClick = {
                        state = MontageUiState.Rendering
                        scope.launch {
                            try {
                                when (val result = generator.render(session, includedSlots)) {
                                    is MontageRenderResult.Completed -> {
                                        state = MontageUiState.Complete(
                                            frameCount = result.selectedFrameCount,
                                            missingCount = result.missingSlots.size,
                                        )
                                        onGenerated()
                                    }
                                    is MontageRenderResult.NoFrames -> state = MontageUiState.Failed(result.reason)
                                    is MontageRenderResult.Failed -> state = MontageUiState.Failed(result.reason)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            }
                        }
                    },
                    modifier = Modifier.testTag("montage-generate"),
                ) {
                    Text(
                        if (hasMontage) {
                            stringResource(R.string.gallery_montage_regenerate)
                        } else {
                            stringResource(R.string.gallery_montage_generate)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LocalExportShareCard(session)
    }
}

@Composable
private fun montageSlotLabelResource(slot: MontageSlot): String = stringResource(
    when (slot) {
        MontageSlot.PARTIAL_EARLY -> R.string.gallery_montage_slot_partial_early
        MontageSlot.CONTACT_EARLY -> R.string.gallery_montage_slot_contact_early
        MontageSlot.TOTALITY -> R.string.gallery_montage_slot_totality
        MontageSlot.CONTACT_LATE -> R.string.gallery_montage_slot_contact_late
        MontageSlot.PARTIAL_LATE -> R.string.gallery_montage_slot_partial_late
    },
)

fun montageSlotLabel(slot: MontageSlot): String = when (slot) {
    MontageSlot.PARTIAL_EARLY -> "Partial • early"
    MontageSlot.CONTACT_EARLY -> "Contact burst • early"
    MontageSlot.TOTALITY -> "Totality representative"
    MontageSlot.CONTACT_LATE -> "Contact burst • late"
    MontageSlot.PARTIAL_LATE -> "Partial • late"
}
