package com.sonora.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.ImportedTrack
import com.sonora.music.data.model.MatchedPlaylistSummary
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun ImportPlaylistDialog(
    isImporting: Boolean,
    isExternalDownloaderAvailable: Boolean = false,
    onDismiss: () -> Unit,
    onImport: (urlOrText: String, onMatched: (MatchedPlaylistSummary) -> Unit, onError: (String) -> Unit) -> Unit,
    onSavePlaylist: (MatchedPlaylistSummary) -> Unit,
    onStartDirectDownload: (title: String, artist: String, url: String) -> Unit = { _, _, _ -> },
    onOpenWebSearch: (title: String, artist: String) -> Unit = { _, _ -> },
    onOpenYouTubeMusic: (title: String, artist: String) -> Unit = { _, _ -> },
    onOpenExternalDownloader: (title: String, artist: String) -> Unit = { _, _ -> }
) {
    val colors = LocalSonoraColors.current
    val clipboardManager = LocalClipboardManager.current

    var inputMode by remember { mutableStateOf(0) } // 0: Link, 1: Text
    var inputText by remember { mutableStateOf("") }
    var matchSummary by remember { mutableStateOf<MatchedPlaylistSummary?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMissingList by remember { mutableStateOf(true) }
    var trackToDownload by remember { mutableStateOf<ImportedTrack?>(null) }

    trackToDownload?.let { targetTrack ->
        DownloadTrackDialog(
            track = targetTrack,
            isExternalDownloaderAvailable = isExternalDownloaderAvailable,
            onDismiss = { trackToDownload = null },
            onStartDirectDownload = onStartDirectDownload,
            onOpenWebSearch = onOpenWebSearch,
            onOpenYouTubeMusic = onOpenYouTubeMusic,
            onOpenExternalDownloader = onOpenExternalDownloader
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.elevatedBackground,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Smart Playlist Importer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.primaryText
                    )
                    Text(
                        text = "Import Spotify & YT Music to local space",
                        fontSize = 11.sp,
                        color = colors.secondaryText
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (matchSummary == null) {
                    // Mode selector chips: Link vs Text
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = inputMode == 0,
                            onClick = { inputMode = 0 },
                            label = { Text("Spotify / YouTube Link") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = inputMode == 1,
                            onClick = { inputMode = 1 },
                            label = { Text("Tracklist Text") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            errorMessage = null
                        },
                        label = {
                            Text(if (inputMode == 0) "Paste Spotify or YouTube Music URL" else "Paste song lines (e.g. Title - Artist)")
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                clipboardManager.getText()?.text?.let {
                                    inputText = it
                                    errorMessage = null
                                }
                            }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = colors.accent)
                            }
                        },
                        maxLines = if (inputMode == 0) 2 else 6,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface,
                            focusedTextColor = colors.primaryText,
                            unfocusedTextColor = colors.primaryText
                        )
                    )

                    if (inputMode == 0) {
                        Text(
                            text = "Supports public Spotify playlists, albums, and YouTube Music links. Fetches metadata and matches songs stored locally on your device.",
                            color = colors.secondaryText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = errorMessage!!, color = colors.accent, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Match summary view
                    val summary = matchSummary!!
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp)),
                        color = colors.surface
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = summary.playlistName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.primaryText,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.accent.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = summary.source,
                                        fontSize = 10.sp,
                                        color = colors.accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "${summary.matchedSongs.size} of ${summary.totalTracks} tracks matched on your device",
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { summary.matchPercentage },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = colors.accent,
                                trackColor = colors.surfaceVariant
                            )

                            if (summary.missingTracks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${summary.missingTracks.size} missing from device",
                                        color = colors.secondaryText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (showMissingList) "Hide ▲" else "Show ▼",
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showMissingList = !showMissingList }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }

                                AnimatedVisibility(visible = showMissingList) {
                                    LazyColumn(modifier = Modifier.height(140.dp).padding(top = 6.dp)) {
                                        items(summary.missingTracks) { missing ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(colors.surface.copy(alpha = 0.5f))
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                                    contentDescription = null,
                                                    tint = colors.secondaryText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = missing.title,
                                                        color = colors.primaryText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (missing.artist.isNotBlank()) {
                                                        Text(
                                                            text = missing.artist,
                                                            color = colors.secondaryText,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                // Download Action Chip
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(colors.accent)
                                                        .clickable { trackToDownload = missing }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Download,
                                                            contentDescription = "Download",
                                                            tint = Color.White,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Download",
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
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
            }
        },
        confirmButton = {
            if (matchSummary == null) {
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onImport(
                                inputText,
                                { matched -> matchSummary = matched },
                                { err -> errorMessage = err }
                            )
                        }
                    },
                    enabled = !isImporting && inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.primaryText
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Matching...")
                    } else {
                        Text("Analyze & Match")
                    }
                }
            } else {
                Button(
                    onClick = {
                        matchSummary?.let { onSavePlaylist(it) }
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.primaryText
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Sonora Playlist (${matchSummary!!.matchedSongs.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.secondaryText)
            }
        }
    )
}
