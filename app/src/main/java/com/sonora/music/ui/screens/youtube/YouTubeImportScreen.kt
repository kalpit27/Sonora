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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import coil.compose.AsyncImage
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
    onBack: () -> Unit,
    onFetchYouTubeUrl: (String) -> Unit,
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
                            text = "Paste YouTube Video or Playlist Link",
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
                                    text = "e.g. https://www.youtube.com/watch?v=...",
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
                                            text = "${playlist.author} • ${playlist.tracks.size} tracks found • Converted to MP3",
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
                                                    AsyncImage(
                                                        model = track.thumbnailUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
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
                                        AsyncImage(
                                            model = info.thumbnailUrl,
                                            contentDescription = "Video Thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface.copy(alpha = 0.6f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Audio Format: MP3 (Audio/MPEG)", color = colors.primaryText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Converter: FFmpeg Engine", color = colors.secondaryText, fontSize = 10.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ready for Offline", color = Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Audio Quality Selection
                                Text(
                                    text = "Select Audio Quality",
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
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
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(quality.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(
                                                        text = "~${quality.formatEstimatedSize(info.durationSeconds)}",
                                                        fontSize = 9.sp,
                                                        color = if (selectedQuality == quality) Color.White.copy(alpha = 0.85f) else colors.secondaryText
                                                    )
                                                }
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
                                            selectedQuality,
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
                                                text = "Downloading Audio (${activeDownload.progress}%)",
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
                                                text = "Converting to MP3 (FFmpeg)...",
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
                                                text = "Import & MP3 Conversion Complete!",
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
                                                text = "Import Failed",
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
                                                text = "Import Cancelled",
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
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = activeDownload.stageDescription ?: "Processing...",
                                        color = colors.secondaryText,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
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
                                                if (!activeDownload.thumbnailUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = activeDownload.thumbnailUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = colors.accent)
                                                }
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
                                                    Text(
                                                        text = "Format: MP3 (${activeDownload.quality.label})",
                                                        color = colors.accent,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = " • Local Offline Ready",
                                                        color = Color(0xFF4CAF50),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Saved to: Music/Sonora. Successfully added to Sonora Music Library.",
                                            color = colors.secondaryText,
                                            fontSize = 10.sp
                                        )
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
                                            text = if (queueOverallProgress.isQueueActive) "Importing Playlist Queue (MP3)" else "Playlist Import Summary",
                                            color = colors.primaryText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Text(
                                        text = "${queueOverallProgress.targetPlaylistName ?: "Playlist"} • ${queueOverallProgress.completedTracks} of ${queueOverallProgress.totalTracks} converted to MP3",
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
                                                AsyncImage(
                                                    model = qItem.thumbnailUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
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
                                                            DownloadStatus.COMPLETED -> if (qItem.isDuplicate) "Already in Library" else "Downloaded as MP3"
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
                                if (!item.thumbnailUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
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
