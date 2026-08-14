package com.fatmambo33.eclipsecam.media

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ExportCardBackground = Color(0xFF111827)
private val ExportMuted = Color(0xFFCBD5E1)
private val ExportReady = Color(0xFF4ADE80)
private val ExportFailed = Color(0xFFFCA5A5)
private val ExportAccent = Color(0xFF60A5FA)

private sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Working(val action: String) : ExportUiState
    data class Complete(val message: String) : ExportUiState
    data class Failed(val reason: String) : ExportUiState
    data object Cancelled : ExportUiState
}

/** Explicit local export/share controls for one complete or interrupted Gallery session. */
@Composable
fun LocalExportShareCard(session: LocalCaptureSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets = remember(session) { session.assets.filter { localAssetMimeType(it.file) != null } }
    var selectedIndex by remember(session.sessionId) { mutableIntStateOf(0) }
    var locationPolicy by remember(session.sessionId) { mutableStateOf(LocationMetadataPolicy.REMOVE) }
    var state by remember(session.sessionId) { mutableStateOf<ExportUiState>(ExportUiState.Idle) }
    var pendingSafExport by remember(session.sessionId) { mutableStateOf<PreparedLocalExport?>(null) }
    val stager = remember { LocalExportStager(AndroidJpegLocationMetadataSanitizer()) }
    val stagingRoot = remember(context) { File(context.cacheDir, "shared-exports") }

    val publishingMessage = stringResource(R.string.gallery_export_publishing)
    val completeMessage = stringResource(R.string.gallery_export_complete)
    val preparingPrivateMessage = stringResource(R.string.gallery_export_preparing_private)
    val preparingLibraryMessage = stringResource(R.string.gallery_export_preparing_library)
    val savedLibraryMessage = stringResource(R.string.gallery_export_saved_library)
    val preparingShareMessage = stringResource(R.string.gallery_export_preparing_share)
    val shareChooserTitle = stringResource(R.string.gallery_export_share_chooser)
    val shareOpenedMessage = stringResource(R.string.gallery_export_share_opened)

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val export = pendingSafExport
        pendingSafExport = null
        val destination = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || destination == null || export == null) {
            export?.cleanup()
            state = ExportUiState.Cancelled
        } else {
            scope.launch {
                state = ExportUiState.Working(publishingMessage)
                val publish = AndroidSafLocalExporter(context.contentResolver).publish(export, destination)
                export.cleanup()
                state = when (publish) {
                    is ExternalPublishResult.Completed -> ExportUiState.Complete(completeMessage)
                    is ExternalPublishResult.Failed -> ExportUiState.Failed(publish.reason)
                }
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("export-share-card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ExportCardBackground),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.gallery_export_title), fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.gallery_export_body),
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                color = ExportMuted,
            )

            if (assets.isEmpty()) {
                Text(stringResource(R.string.gallery_export_empty), color = ExportMuted)
                return@Column
            }
            if (selectedIndex !in assets.indices) selectedIndex = 0
            val selected = assets[selectedIndex]
            val mimeType = checkNotNull(localAssetMimeType(selected.file))

            Text(
                stringResource(R.string.gallery_export_asset_position, selectedIndex + 1, assets.size),
                color = ExportAccent,
                modifier = Modifier.testTag("export-asset-position"),
            )
            Text(selected.file.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
            Text(
                stringResource(
                    R.string.gallery_export_asset_detail,
                    assetKindLabel(selected.kind),
                    selected.sizeBytes,
                ),
                color = ExportMuted,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = selectedIndex > 0 && state !is ExportUiState.Working,
                    onClick = { selectedIndex -= 1 },
                    modifier = Modifier.testTag("export-previous"),
                ) { Text(stringResource(R.string.gallery_export_previous)) }
                OutlinedButton(
                    enabled = selectedIndex < assets.lastIndex && state !is ExportUiState.Working,
                    onClick = { selectedIndex += 1 },
                    modifier = Modifier.testTag("export-next"),
                ) { Text(stringResource(R.string.gallery_export_next)) }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.gallery_export_location_title), fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = locationPolicy == LocationMetadataPolicy.REMOVE,
                    onClick = { locationPolicy = LocationMetadataPolicy.REMOVE },
                    label = { Text(stringResource(R.string.gallery_export_location_remove)) },
                    modifier = Modifier.testTag("export-location-remove"),
                )
                FilterChip(
                    selected = locationPolicy == LocationMetadataPolicy.PRESERVE,
                    onClick = { locationPolicy = LocationMetadataPolicy.PRESERVE },
                    label = { Text(stringResource(R.string.gallery_export_location_preserve)) },
                    modifier = Modifier.testTag("export-location-preserve"),
                )
            }
            Text(
                if (mimeType == "image/jpeg") {
                    if (locationPolicy == LocationMetadataPolicy.REMOVE) {
                        stringResource(R.string.gallery_export_privacy_remove)
                    } else {
                        stringResource(R.string.gallery_export_privacy_preserve)
                    }
                } else {
                    stringResource(R.string.gallery_export_privacy_non_jpeg)
                },
                color = ExportMuted,
            )

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = state !is ExportUiState.Working,
                onClick = {
                    scope.launch {
                        state = ExportUiState.Working(preparingPrivateMessage)
                        when (val staged = stage(stager, selected, locationPolicy, stagingRoot)) {
                            is LocalExportStageResult.Failed -> state = ExportUiState.Failed(staged.reason)
                            is LocalExportStageResult.Ready -> {
                                pendingSafExport = staged.export
                                safLauncher.launch(
                                    LocalExportIntents.createDocument(
                                        staged.export.displayName,
                                        staged.export.mimeType,
                                    ),
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("export-destination"),
            ) { Text(stringResource(R.string.gallery_export_choose_destination)) }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = state !is ExportUiState.Working &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    isMediaStoreExportable(mimeType),
                onClick = {
                    scope.launch {
                        state = ExportUiState.Working(preparingLibraryMessage)
                        when (val staged = stage(stager, selected, locationPolicy, stagingRoot)) {
                            is LocalExportStageResult.Failed -> state = ExportUiState.Failed(staged.reason)
                            is LocalExportStageResult.Ready -> {
                                val publish = AndroidMediaStoreLocalExporter(context.contentResolver)
                                    .publish(staged.export)
                                staged.export.cleanup()
                                state = when (publish) {
                                    is ExternalPublishResult.Completed -> ExportUiState.Complete(savedLibraryMessage)
                                    is ExternalPublishResult.Failed -> ExportUiState.Failed(publish.reason)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("export-media-store"),
            ) { Text(stringResource(R.string.gallery_export_save_library)) }

            Spacer(Modifier.height(8.dp))
            Button(
                enabled = state !is ExportUiState.Working,
                onClick = {
                    scope.launch {
                        state = ExportUiState.Working(preparingShareMessage)
                        pruneOldShareStaging(stagingRoot)
                        when (val staged = stage(stager, selected, locationPolicy, stagingRoot)) {
                            is LocalExportStageResult.Failed -> state = ExportUiState.Failed(staged.reason)
                            is LocalExportStageResult.Ready -> {
                                val uri = staged.export.fileProviderUri(context)
                                val send = LocalExportIntents.share(
                                    uri,
                                    staged.export.displayName,
                                    staged.export.mimeType,
                                )
                                context.startActivity(Intent.createChooser(send, shareChooserTitle))
                                state = ExportUiState.Complete(shareOpenedMessage)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("export-share"),
            ) { Text(stringResource(R.string.gallery_export_share_android)) }

            Spacer(Modifier.height(10.dp))
            when (val current = state) {
                ExportUiState.Idle -> Text(
                    stringResource(R.string.gallery_export_ready),
                    color = ExportMuted,
                    modifier = Modifier.testTag("export-status"),
                )
                is ExportUiState.Working -> Text(current.action, color = ExportAccent, modifier = Modifier.testTag("export-status"))
                is ExportUiState.Complete -> Text(current.message, color = ExportReady, modifier = Modifier.testTag("export-status"))
                is ExportUiState.Failed -> Text(current.reason, color = ExportFailed, modifier = Modifier.testTag("export-status"))
                ExportUiState.Cancelled -> Text(
                    stringResource(R.string.gallery_export_cancelled),
                    color = ExportMuted,
                    modifier = Modifier.testTag("export-status"),
                )
            }
        }
    }
}

private suspend fun stage(
    stager: LocalExportStager,
    asset: LocalSessionAsset,
    policy: LocationMetadataPolicy,
    stagingRoot: File,
): LocalExportStageResult = withContext(Dispatchers.IO) {
    stager.stage(asset, policy, stagingRoot)
}

private suspend fun pruneOldShareStaging(root: File) = withContext(Dispatchers.IO) {
    val cutoff = System.currentTimeMillis() - SHARE_STAGING_RETENTION_MS
    root.listFiles()?.filter(File::isDirectory)?.forEach { directory ->
        if (directory.lastModified() < cutoff) directory.deleteRecursively()
    }
}

@Composable
private fun assetKindLabel(kind: LocalSessionAssetKind): String = stringResource(
    when (kind) {
        LocalSessionAssetKind.ORIGINAL_CAPTURE -> R.string.gallery_asset_original
        LocalSessionAssetKind.TIMELAPSE -> R.string.gallery_asset_timelapse
        LocalSessionAssetKind.MONTAGE -> R.string.gallery_asset_montage
        LocalSessionAssetKind.CAPTURE_REPORT -> R.string.gallery_asset_report
        LocalSessionAssetKind.GENERATED -> R.string.gallery_asset_generated
    },
)

private const val SHARE_STAGING_RETENTION_MS = 24L * 60L * 60L * 1_000L
