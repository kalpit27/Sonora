package com.sonora.music.ui.screens.youtube

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import android.provider.DocumentsContract
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.sonora.music.data.importer.DriveScanSessionState
import com.sonora.music.data.importer.GoogleDriveImportState
import com.sonora.music.data.importer.SmartDriveAudioHelper
import com.sonora.music.data.importer.StagedDriveAudioItem
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.theme.SpotifySurfaceHighlight
import com.sonora.music.data.local.database.entity.DownloadHistoryEntity
import com.sonora.music.data.model.ActiveDownloadState
import com.sonora.music.data.model.AudioQuality
import com.sonora.music.data.model.DownloadStatus
import com.sonora.music.data.model.QueueDownloadItem
import com.sonora.music.data.model.QueueOverallProgress
import com.sonora.music.data.model.YouTubePlaylistInfo
import com.sonora.music.data.model.YouTubePlaylistItem
import com.sonora.music.data.model.YouTubeVideoInfo
import com.sonora.music.ui.theme.LocalSonoraColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun YouTubeImportScreen(
    videoInfo: YouTubeVideoInfo?,
    playlistInfo: YouTubePlaylistInfo?,
    isFetchingInfo: Boolean,
    isResolvingStream: Boolean = false,
    urlError: String?,
    activeDownload: ActiveDownloadState,
    downloadQueue: List<QueueDownloadItem>,
    queueOverallProgress: QueueOverallProgress,
    downloadHistory: List<DownloadHistoryEntity>,
    isExternalDownloaderAvailable: Boolean,
    defaultDownloadQuality: AudioQuality = AudioQuality.HIGH_320,
    driveImportState: GoogleDriveImportState = GoogleDriveImportState(),
    driveScanState: DriveScanSessionState = DriveScanSessionState(),
    drivePreviewUri: String? = null,
    isDrivePreviewPlaying: Boolean = false,
    drivePreviewError: String? = null,
    onBack: () -> Unit,
    onFetchYouTubeUrl: (String) -> Unit,
    onImportGoogleDriveUrl: (String) -> Unit = {},
    onImportDriveFiles: (List<Uri>) -> Unit = {},
    onScanDriveFolder: (Uri) -> Unit = {},
    onToggleDriveItemSelection: (String) -> Unit = {},
    onToggleSelectAllDriveItems: () -> Unit = {},
    onToggleDrivePreview: (StagedDriveAudioItem) -> Unit = {},
    onStartStagedDriveImport: () -> Unit = {},
    onDismissDriveSelection: () -> Unit = {},
    onCancelDriveScan: () -> Unit = {},
    onClearDriveEmptyMessage: () -> Unit = {},
    onCancelDriveImport: () -> Unit = {},
    onResetDriveImport: () -> Unit = {},
    onClearPreview: () -> Unit,
    onTogglePlaylistItemSelection: (videoId: String, isSelected: Boolean) -> Unit,
    onSelectAllPlaylistItems: (Boolean) -> Unit,
    onStartDownload: (
        videoInfo: YouTubeVideoInfo,
        sourceUrl: String,
        quality: AudioQuality,
        customTitle: String?,
        customArtist: String?,
        allowDuplicate: Boolean,
        destinationTreeUri: Uri?
    ) -> Unit,
    onStartPlaylistImport: (
        playlistInfo: YouTubePlaylistInfo,
        selectedTracks: List<YouTubePlaylistItem>,
        quality: AudioQuality,
        allowDuplicates: Boolean
    ) -> Unit,
    onCancelDownload: () -> Unit,
    onCancelQueue: () -> Unit,
    onCancelQueueItem: (String) -> Unit,
    onRetryQueueItem: (String) -> Unit,
    onRetryFailedQueueItems: () -> Unit,
    onClearQueue: () -> Unit,
    onResetActiveDownload: () -> Unit,
    onRetryDownload: (DownloadHistoryEntity) -> Unit,
    onDeleteHistoryItem: (Long) -> Unit,
    onClearHistory: () -> Unit,
    onPlaySong: (title: String, uriString: String?) -> Unit,
    onOpenPlaylistDetail: (Long) -> Unit,
    onOpenExternalDownloader: (title: String, artist: String, url: String?) -> Unit,
    onOpenWebSearch: (title: String, artist: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var urlInput by remember { mutableStateOf("") }
    var selectedQuality by remember { mutableStateOf(AudioQuality.HIGH_320) }
    var customAudioUrl by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var customArtist by remember { mutableStateOf("") }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    // Optional user-selected destination folder via Storage Access Framework
    var customDestinationUri by remember { mutableStateOf<Uri?>(null) }
    var customDestinationName by remember { mutableStateOf<String?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            customDestinationUri = uri
            val folderName = try {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                val cursor = context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val idx = it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        if (idx >= 0) it.getString(idx) else null
                    } else null
                } ?: docId.substringAfterLast(":", "Selected Folder")
            } catch (e: Exception) {
                "Selected Folder"
            }
            customDestinationName = folderName
        }
    }

    val drivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty()) {
            onImportDriveFiles(uris)
        }
    }

    val driveFolderScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            onScanDriveFolder(uri)
        }
    }

    val wmaSupported = remember { SmartDriveAudioHelper.isDeviceWmaDecoderAvailable() }

    if (driveScanState.showSelectionModal && driveScanState.items.isNotEmpty()) {
        SmartDriveAudioSelectionDialog(
            scanState = driveScanState,
            previewUri = drivePreviewUri,
            isPreviewPlaying = isDrivePreviewPlaying,
            previewError = drivePreviewError,
            onToggleItem = onToggleDriveItemSelection,
            onToggleSelectAll = onToggleSelectAllDriveItems,
            onTogglePreview = onToggleDrivePreview,
            onConfirmImport = onStartStagedDriveImport,
            onDismiss = onDismissDriveSelection
        )
    }

    // Synchronize custom titles whenever new videoInfo is loaded
    if (videoInfo != null && customTitle.isBlank()) {
        customTitle = videoInfo.title
        customArtist = videoInfo.channelTitle
    }

    if (activeDownload.isDuplicate) {
        showDuplicateDialog = true
    }

    // Duplicate Confirmation Dialog
    if (showDuplicateDialog && videoInfo != null) {
        AlertDialog(
            onDismissRequest = {
                showDuplicateDialog = false
                onResetActiveDownload()
            },
            containerColor = colors.elevatedBackground,
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Track Already Exists",
                        color = colors.primaryText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "\"${videoInfo.title}\" is already present in your Sonora music library.",
                        color = colors.primaryText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Would you like to play it now or download a fresh MP3 copy to your device?",
                        color = colors.secondaryText,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDuplicateDialog = false
                        onResetActiveDownload()
                        onPlaySong(videoInfo.title, null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Now")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDuplicateDialog = false
                        onResetActiveDownload()
                    }) {
                        Text("Cancel", color = colors.secondaryText)
                    }
                    TextButton(onClick = {
                        showDuplicateDialog = false
                        val streamUrl = customAudioUrl.trim().ifBlank { videoInfo.originalUrl }
                        onStartDownload(
                            videoInfo,
                            streamUrl,
                            selectedQuality,
                            customTitle,
                            customArtist,
                            true,
                            customDestinationUri
                        )
                    }) {
                        Text("Download Anyway", color = colors.accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = colors.elevatedBackground,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Clear Download History?",
                    color = colors.primaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove the download log entries. Your saved MP3 audio files will remain safe in your Music folder.",
                    color = colors.secondaryText,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Clear Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel", color = colors.secondaryText)
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            // Header Section: Import Audio
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.primaryText
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Import Audio",
                                color = colors.primaryText,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "YouTube to MP3 • Offline Sonora Library",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (downloadHistory.isNotEmpty()) {
                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear History",
                                tint = colors.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // URL Input Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = colors.elevatedBackground
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Paste Music or Cloud Link",
                            color = colors.primaryText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = {
                                urlInput = it
                            },
                            placeholder = {
                                Text(
                                    text = "YouTube, YouTube Music, or Google Drive link",
                                    color = colors.secondaryText.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (urlInput.isNotBlank()) {
                                        IconButton(onClick = {
                                            urlInput = ""
                                            onClearPreview()
                                            customTitle = ""
                                            customArtist = ""
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = colors.secondaryText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        clipboardManager.getText()?.text?.let { clip ->
                                            urlInput = clip.trim()
                                            onFetchYouTubeUrl(clip.trim())
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste",
                                            tint = colors.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            isError = urlError != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = colors.surface,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                                focusedTextColor = colors.primaryText,
                                unfocusedTextColor = colors.primaryText
                            )
                        )

                        // Error Banner
                        AnimatedVisibility(visible = urlError != null) {
                            urlError?.let { err ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = err,
                                        color = Color(0xFFFF5252),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Import Button
                        Button(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onFetchYouTubeUrl(urlInput.trim())
                                }
                            },
                            enabled = urlInput.isNotBlank() && !isFetchingInfo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            )
                        ) {
                            if (isFetchingInfo) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Processing...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Smart Drive Import (Audio-Filtered) Card
            item {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    color = colors.elevatedBackground
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Smart Drive Audio Import",
                                    color = colors.primaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val formatsDesc = if (wmaSupported) {
                                    "Audio filter: MP3, WAV, FLAC, M4A, AAC, OGG, OPUS, WMA"
                                } else {
                                    "Audio filter: MP3, WAV, FLAC, M4A, AAC, OGG, OPUS"
                                }
                                Text(
                                    text = formatsDesc,
                                    color = colors.secondaryText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                drivePickerLauncher.launch(SmartDriveAudioHelper.PICKER_AUDIO_MIME_TYPES)
                            },
                            enabled = !driveScanState.isScanning && !driveImportState.isImporting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Import from Drive",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { driveFolderScannerLauncher.launch(null) },
                            enabled = !driveScanState.isScanning && !driveImportState.isImporting,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Scan Folder (Auto-Filter Subfolders)",
                                color = colors.primaryText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (driveScanState.isScanning) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surface, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = driveScanState.scanProgressText.ifBlank { "Filtering audio files..." },
                                    color = colors.primaryText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onCancelDriveScan) {
                                    Text("Cancel", color = Color(0xFFFF5252), fontSize = 12.sp)
                                }
                            }
                        }

                        driveScanState.emptyMessage?.let { emptyMsg ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFFB74D).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "No Supported Audio Files Found",
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = emptyMsg,
                                        color = colors.secondaryText,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = onClearDriveEmptyMessage,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = colors.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Google Drive Active Import Progress / Completed / Cancelled Card
            if (driveImportState.isImporting || driveImportState.statusText.isNotBlank() || driveImportState.lastImportedUri != null || driveImportState.errorMessage != null) {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp)),
                        color = colors.elevatedBackground
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            driveImportState.isImporting -> Icons.Default.CloudDownload
                                            driveImportState.errorMessage != null || driveImportState.wasCancelled -> Icons.Default.Warning
                                            else -> Icons.Default.CheckCircle
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            driveImportState.errorMessage != null -> Color(0xFFFF5252)
                                            driveImportState.wasCancelled -> Color(0xFFFFB74D)
                                            else -> colors.accent
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = when {
                                            driveImportState.isImporting -> "Importing Drive Audio"
                                            driveImportState.wasCancelled -> "Import Cancelled"
                                            driveImportState.errorMessage != null -> "Import Failed"
                                            else -> "Drive Import Complete"
                                        },
                                        color = colors.primaryText,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (driveImportState.isImporting) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${driveImportState.progress}%",
                                            color = colors.accent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        TextButton(onClick = onCancelDriveImport) {
                                            Text("Cancel", color = Color(0xFFFF5252), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = onResetDriveImport,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = driveImportState.currentTitle.ifBlank { driveImportState.lastImportedTitle ?: "Google Drive Audio" },
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (driveImportState.currentFilename.isNotBlank() && driveImportState.isImporting) {
                                Text(
                                    text = driveImportState.currentFilename,
                                    color = colors.secondaryText.copy(alpha = 0.75f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = driveImportState.errorMessage
                                    ?: driveImportState.statusText.ifBlank {
                                        if (driveImportState.isImporting) "Importing..." else "Saved to Offline Library"
                                    },
                                color = if (driveImportState.errorMessage != null) Color(0xFFFF5252) else colors.secondaryText,
                                fontSize = 12.sp
                            )

                            if (driveImportState.isImporting) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (driveImportState.progress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = colors.accent,
                                    trackColor = colors.surfaceVariant
                                )
                            }

                            if (!driveImportState.isImporting && driveImportState.lastImportedUri != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        onPlaySong(driveImportState.lastImportedTitle ?: "Google Drive Track", driveImportState.lastImportedUri)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Offline in Sonora", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Playlist Preview & Batch Selection Card
            item {
                AnimatedVisibility(
                    visible = playlistInfo != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    playlistInfo?.let { playlist ->
                        val selectedCount = playlist.tracks.count { it.isSelected }
                        val isAllSelected = selectedCount == playlist.tracks.size && playlist.tracks.isNotEmpty()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            color = colors.elevatedBackground
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "PLAYLIST DETECTED",
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = playlist.title,
                                            color = colors.primaryText,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.author} • ${playlist.tracks.size} tracks found",
                                            color = colors.secondaryText,
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(onClick = onClearPreview) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.secondaryText)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Select All / Deselect All Controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$selectedCount of ${playlist.tracks.size} tracks selected",
                                        color = colors.primaryText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    TextButton(
                                        onClick = { onSelectAllPlaylistItems(!isAllSelected) }
                                    ) {
                                        Text(
                                            text = if (isAllSelected) "Deselect All" else "Select All",
                                            color = colors.accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Quality Selector
                                Text(
                                    text = "MP3 Audio Quality",
                                    color = colors.secondaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AudioQuality.entries.forEach { quality ->
                                        FilterChip(
                                            selected = selectedQuality == quality,
                                            onClick = { selectedQuality = quality },
                                            label = {
                                                Text(quality.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = colors.accent,
                                                selectedLabelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons: Import Selected or Import All
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val selected = playlist.tracks.filter { it.isSelected }
                                            onStartPlaylistImport(playlist, selected, selectedQuality, false)
                                        },
                                        enabled = selectedCount > 0,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import Selected ($selectedCount)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onStartPlaylistImport(playlist, playlist.tracks, selectedQuality, false)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                                    ) {
                                        Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import All (${playlist.tracks.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Tracklist preview container
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface)
                                        .padding(8.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(playlist.tracks, key = { it.videoId }) { track ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        onTogglePlaylistItemSelection(track.videoId, !track.isSelected)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = track.isSelected,
                                                    onCheckedChange = { isChecked ->
                                                        onTogglePlaylistItemSelection(track.videoId, isChecked)
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = colors.accent,
                                                        checkmarkColor = Color.White
                                                    ),
                                                    modifier = Modifier.size(24.dp)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(colors.elevatedBackground),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AlbumArtImage(
                                                        artworkUri = track.thumbnailUrl,
                                                        modifier = Modifier.fillMaxSize(),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = track.title,
                                                        color = colors.primaryText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = track.channelTitle,
                                                        color = colors.secondaryText,
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Single Video Preview & Audio Information Area
            item {
                AnimatedVisibility(
                    visible = videoInfo != null && playlistInfo == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    videoInfo?.let { info ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            color = colors.elevatedBackground
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "AUDIO PREVIEW & METADATA",
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    IconButton(onClick = onClearPreview) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.secondaryText)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))

                                // Video Card Preview
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.elevatedBackground)
                                            .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AlbumArtImage(
                                            artworkUri = info.thumbnailUrl,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = info.title,
                                            color = colors.primaryText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = info.channelTitle,
                                            color = colors.secondaryText,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Duration: ${info.formattedDuration}",
                                                color = colors.secondaryText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = " • MP3",
                                                color = colors.accent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Audio Information & Conversion Details
                                Spacer(modifier = Modifier.height(14.dp))

                                // Download Quality applied from Settings
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Download Quality",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = defaultDownloadQuality.label,
                                        color = colors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Track Title & Artist Customization
                                Text(
                                    text = "Track Title & Artist",
                                    color = colors.primaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    label = { Text("Title", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.surface,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                        focusedTextColor = colors.primaryText,
                                        unfocusedTextColor = colors.primaryText
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = customArtist,
                                    onValueChange = { customArtist = it },
                                    label = { Text("Artist / Channel", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.accent,
                                        unfocusedBorderColor = colors.surface,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                        focusedTextColor = colors.primaryText,
                                        unfocusedTextColor = colors.primaryText
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Destination Folder Selector
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface)
                                        .clickable { folderPickerLauncher.launch(null) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            tint = colors.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Save Destination",
                                                color = colors.secondaryText,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = customDestinationName ?: "Music/Sonora (Default)",
                                                color = colors.primaryText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (customDestinationUri != null) {
                                        IconButton(
                                            onClick = {
                                                customDestinationUri = null
                                                customDestinationName = null
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Reset to Default",
                                                tint = colors.secondaryText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Change",
                                            color = colors.accent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Primary Download MP3 Button
                                Button(
                                    onClick = {
                                        val streamUrl = customAudioUrl.trim().ifBlank { info.originalUrl }
                                        onStartDownload(
                                            info,
                                            streamUrl,
                                            defaultDownloadQuality,
                                            customTitle,
                                            customArtist,
                                            false,
                                            customDestinationUri
                                        )
                                    },
                                    enabled = !isResolvingStream && activeDownload.status != DownloadStatus.DOWNLOADING && activeDownload.status != DownloadStatus.CONVERTING,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accent,
                                        contentColor = Color.White,
                                        disabledContainerColor = colors.accent.copy(alpha = 0.6f),
                                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                                    )
                                ) {
                                    if (isResolvingStream) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Resolving Audio Stream…", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    } else {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download MP3", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }

                                if (isExternalDownloaderAvailable) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            onOpenExternalDownloader(customTitle.ifBlank { info.title }, customArtist.ifBlank { info.channelTitle }, info.originalUrl)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Extract with Seal / Downloader App", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Live Download & FFmpeg Conversion Progress Section
            item {
                AnimatedVisibility(
                    visible = (activeDownload.status == DownloadStatus.DOWNLOADING ||
                            activeDownload.status == DownloadStatus.CONVERTING ||
                            activeDownload.status == DownloadStatus.COMPLETED ||
                            activeDownload.status == DownloadStatus.FAILED ||
                            activeDownload.status == DownloadStatus.CANCELLED) && downloadQueue.isEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        color = colors.elevatedBackground
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (activeDownload.status) {
                                        DownloadStatus.DOWNLOADING -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = colors.accent,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Downloading...",
                                                color = colors.primaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        DownloadStatus.CONVERTING -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = colors.accent,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Converting Audio...",
                                                color = colors.accent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        DownloadStatus.COMPLETED -> {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF4CAF50),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Download Completed",
                                                color = Color(0xFF4CAF50),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        DownloadStatus.FAILED -> {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Download Failed",
                                                color = Color(0xFFFF5252),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        DownloadStatus.CANCELLED -> {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                tint = colors.secondaryText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Download Cancelled",
                                                color = colors.secondaryText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        else -> {}
                                    }
                                }

                                if (activeDownload.status == DownloadStatus.DOWNLOADING || activeDownload.status == DownloadStatus.CONVERTING) {
                                    TextButton(onClick = onCancelDownload) {
                                        Text("Cancel", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    IconButton(onClick = onResetActiveDownload) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = activeDownload.title,
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (activeDownload.status == DownloadStatus.DOWNLOADING || activeDownload.status == DownloadStatus.CONVERTING) {
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { activeDownload.progress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = colors.accent,
                                    trackColor = colors.surface
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = "${activeDownload.progress}%",
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Offline Music Library Integration display when completed
                            if (activeDownload.status == DownloadStatus.COMPLETED) {
                                Spacer(modifier = Modifier.height(10.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)),
                                    color = colors.surface
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.elevatedBackground),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AlbumArtImage(
                                                    artworkUri = activeDownload.thumbnailUrl,
                                                    audioUri = activeDownload.localUri,
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = activeDownload.title,
                                                    color = colors.primaryText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = activeDownload.artist.ifBlank { "YouTube Audio" },
                                                    color = colors.secondaryText,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Ready in Sonora Library",
                                                        color = Color(0xFF4CAF50),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        onPlaySong(activeDownload.title, activeDownload.localUri)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play Offline in Sonora", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (activeDownload.status == DownloadStatus.FAILED) {
                                activeDownload.errorMessage?.let { err ->
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = err, color = Color(0xFFFF5252), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Playlist Queue Progress Section
            item {
                AnimatedVisibility(
                    visible = queueOverallProgress.isQueueActive || downloadQueue.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        color = colors.elevatedBackground
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (queueOverallProgress.isQueueActive) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = colors.accent,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = if (queueOverallProgress.isQueueActive) "Downloading Playlist..." else "Playlist Download Summary",
                                            color = colors.primaryText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Text(
                                        text = "${queueOverallProgress.targetPlaylistName ?: "Playlist"} • ${queueOverallProgress.completedTracks} of ${queueOverallProgress.totalTracks} downloaded",
                                        color = colors.secondaryText,
                                        fontSize = 12.sp
                                    )
                                }

                                if (queueOverallProgress.isQueueActive) {
                                    TextButton(onClick = onCancelQueue) {
                                        Text("Cancel All", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                } else {
                                    IconButton(onClick = onClearQueue) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { queueOverallProgress.overallPercentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = colors.accent,
                                trackColor = colors.surface
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (queueOverallProgress.isQueueActive) "Now: ${queueOverallProgress.currentTrackTitle}" else "Import finished",
                                    color = colors.secondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${queueOverallProgress.overallPercentage}%",
                                    color = colors.accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            if (!queueOverallProgress.isQueueActive && queueOverallProgress.targetPlaylistId != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        onOpenPlaylistDetail(queueOverallProgress.targetPlaylistId!!)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open \"${queueOverallProgress.targetPlaylistName}\" in Sonora", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (!queueOverallProgress.isQueueActive && queueOverallProgress.failedTracks > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onRetryFailedQueueItems,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Failed Tracks (${queueOverallProgress.failedTracks})", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Individual items list in queue
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface)
                                    .padding(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(downloadQueue, key = { it.id }) { qItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(colors.elevatedBackground),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                AlbumArtImage(
                                                    artworkUri = qItem.thumbnailUrl,
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = qItem.title,
                                                    color = colors.primaryText,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (qItem.status == DownloadStatus.DOWNLOADING || qItem.status == DownloadStatus.CONVERTING) {
                                                    LinearProgressIndicator(
                                                        progress = { qItem.progress / 100f },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp)
                                                            .padding(top = 2.dp)
                                                            .clip(RoundedCornerShape(2.dp)),
                                                        color = colors.accent,
                                                        trackColor = colors.elevatedBackground
                                                    )
                                                } else {
                                                    Text(
                                                        text = when (qItem.status) {
                                                            DownloadStatus.COMPLETED -> if (qItem.isDuplicate) "Already in Library" else "Downloaded"
                                                            DownloadStatus.PENDING -> "Pending in queue"
                                                            DownloadStatus.FAILED -> "Failed: ${qItem.errorMessage ?: ""}"
                                                            DownloadStatus.CANCELLED -> "Cancelled"
                                                            else -> ""
                                                        },
                                                        color = when (qItem.status) {
                                                            DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                                                            DownloadStatus.FAILED -> Color(0xFFFF5252)
                                                            else -> colors.secondaryText
                                                        },
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            when (qItem.status) {
                                                DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING -> {
                                                    IconButton(
                                                        onClick = { onCancelQueueItem(qItem.id) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.secondaryText, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                DownloadStatus.COMPLETED -> {
                                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                }
                                                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                                                    IconButton(
                                                        onClick = { onRetryQueueItem(qItem.id) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = colors.accent, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Download History Section Header
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Download History (${downloadHistory.size})",
                            color = colors.primaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Empty History State
            if (downloadHistory.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = colors.elevatedBackground.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colors.secondaryText.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No audio imports yet",
                                color = colors.secondaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Paste a YouTube link above to download authorized audio directly as MP3 for offline listening",
                                color = colors.secondaryText.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else {
                items(downloadHistory, key = { it.id }) { item ->
                    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
                    val formattedDate = remember(item.timestamp) { dateFormat.format(Date(item.timestamp)) }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (item.status == "COMPLETED") {
                                    onPlaySong(item.title, item.mediaStoreUri)
                                }
                            },
                        color = colors.elevatedBackground
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumArtImage(
                                    artworkUri = item.thumbnailUrl,
                                    audioUri = item.mediaStoreUri,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.artist.ifBlank { "YouTube Audio" },
                                        color = colors.secondaryText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text(
                                        text = " • MP3 • $formattedDate",
                                        color = colors.secondaryText.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            when (item.status) {
                                "COMPLETED" -> {
                                    IconButton(onClick = { onPlaySong(item.title, item.mediaStoreUri) }) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = colors.accent,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                "FAILED" -> {
                                    IconButton(onClick = { onRetryDownload(item) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                "CANCELLED" -> {
                                    IconButton(onClick = { onRetryDownload(item) }) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = colors.secondaryText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                else -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = colors.accent,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }

                            IconButton(onClick = { onDeleteHistoryItem(item.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = colors.secondaryText.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SmartDriveAudioSelectionDialog(
    scanState: DriveScanSessionState,
    previewUri: String?,
    isPreviewPlaying: Boolean,
    previewError: String?,
    onToggleItem: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onTogglePreview: (StagedDriveAudioItem) -> Unit,
    onConfirmImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalSonoraColors.current
    val items = scanState.items
    val selectableItems = items.filter { !it.isDuplicate }
    val selectedCount = selectableItems.count { it.isSelected }
    val allSelectableChecked = selectableItems.isNotEmpty() && selectedCount == selectableItems.size
    val duplicateCount = items.count { it.isDuplicate }
    val filteredOutCount = scanState.skippedNonAudioCount + scanState.skippedUnsupportedCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = colors.elevatedBackground),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drive Audio Files",
                            color = colors.primaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${items.size} supported audio track${if (items.size == 1) "" else "s"} ready",
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.secondaryText)
                    }
                }

                if (filteredOutCount > 0 || duplicateCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val infoMsg = buildList {
                        if (filteredOutCount > 0) add("$filteredOutCount non-audio/unsupported hidden")
                        if (duplicateCount > 0) add("$duplicateCount already in library")
                    }.joinToString(" • ")
                    Text(
                        text = infoMsg,
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFB74D).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                if (previewError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = previewError,
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFF5252).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Select All + Total Selected Count Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(12.dp))
                        .clickable(enabled = selectableItems.isNotEmpty()) { onToggleSelectAll() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = allSelectableChecked,
                            onCheckedChange = { onToggleSelectAll() },
                            enabled = selectableItems.isNotEmpty(),
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.accent,
                                uncheckedColor = colors.secondaryText
                            )
                        )
                        Text(
                            text = if (allSelectableChecked) "Deselect All" else "Select All",
                            color = colors.primaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$selectedCount selected",
                        color = if (selectedCount > 0) colors.accent else colors.secondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Filtered Audio List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.uriString }) { item ->
                        val isPreviewingThis = previewUri == item.uriString && isPreviewPlaying
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (item.isDuplicate) 0.58f else 1f)
                                .background(colors.surface, RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isPreviewingThis) 1.dp else 0.dp,
                                    color = if (isPreviewingThis) colors.accent else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = !item.isDuplicate) { onToggleItem(item.uriString) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { onToggleItem(item.uriString) },
                                enabled = !item.isDuplicate,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.secondaryText
                                )
                            )

                            // Artwork Thumbnail or Fallback
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.elevatedBackground),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = colors.accent.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (!item.artworkUriString.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.artworkUriString,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.artist} • ${item.album}",
                                    color = colors.secondaryText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val metaParts = buildList {
                                    add(item.formatLabel)
                                    val dur = SmartDriveAudioHelper.formatDuration(item.durationMs)
                                    if (dur != "--:--") add(dur)
                                    val sz = SmartDriveAudioHelper.formatFileSize(item.fileSizeBytes)
                                    if (sz.isNotBlank()) add(sz)
                                    if (item.isDuplicate) add(item.duplicateReason ?: "Duplicate")
                                }
                                Text(
                                    text = metaParts.joinToString(" · "),
                                    color = if (item.isDuplicate) Color(0xFFFFB74D) else colors.accent.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Preview Play/Stop Toggle
                            IconButton(onClick = { onTogglePreview(item) }) {
                                Icon(
                                    imageVector = if (isPreviewingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPreviewingThis) "Stop Preview" else "Preview Song",
                                    tint = if (isPreviewingThis) colors.accent else colors.primaryText,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Cancel", color = colors.primaryText, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConfirmImport,
                        enabled = selectedCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import ($selectedCount)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
