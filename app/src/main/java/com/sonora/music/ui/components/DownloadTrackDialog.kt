package com.sonora.music.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.ImportedTrack
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun DownloadTrackDialog(
    track: ImportedTrack,
    isExternalDownloaderAvailable: Boolean,
    onDismiss: () -> Unit,
    onStartDirectDownload: (title: String, artist: String, url: String) -> Unit,
    onOpenWebSearch: (title: String, artist: String) -> Unit,
    onOpenYouTubeMusic: (title: String, artist: String) -> Unit,
    onOpenExternalDownloader: (title: String, artist: String) -> Unit
) {
    val colors = LocalSonoraColors.current
    var directUrl by remember { mutableStateOf(track.directUrl ?: "") }
    var urlError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        containerColor = colors.surface,
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Header with Track Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download Missing Song",
                            color = colors.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = track.title,
                            color = colors.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (track.artist.isNotBlank()) {
                            Text(
                                text = track.artist,
                                color = colors.secondaryText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 1: Direct Audio URL Download
                Text(
                    text = "Direct Audio URL Download",
                    color = colors.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = directUrl,
                    onValueChange = {
                        directUrl = it
                        urlError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Paste direct MP3 / audio stream link", color = colors.secondaryText.copy(alpha = 0.6f), fontSize = 12.sp)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    },
                    isError = urlError != null,
                    supportingText = urlError?.let { { Text(it, color = Color(0xFFFF5252), fontSize = 11.sp) } },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.elevatedBackground,
                        focusedTextColor = colors.primaryText,
                        unfocusedTextColor = colors.primaryText,
                        focusedContainerColor = colors.elevatedBackground,
                        unfocusedContainerColor = colors.elevatedBackground
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (directUrl.isBlank() || (!directUrl.startsWith("http://") && !directUrl.startsWith("https://"))) {
                            urlError = "Please enter a valid HTTP/HTTPS link"
                        } else {
                            onStartDirectDownload(track.title, track.artist, directUrl)
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download with Sonora Manager", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.elevatedBackground))
                    Text(
                        text = " OR FIND ONLINE ",
                        color = colors.secondaryText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Box(modifier = Modifier.weight(1f).height(1.dp).background(colors.elevatedBackground))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Option 2: Search Web / Browser Download
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.elevatedBackground)
                        .clickable {
                            onOpenWebSearch(track.title, track.artist)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Search Web for MP3", color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Opens browser to download directly to Music folder", color = colors.secondaryText, fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Option 3: YouTube Music Search
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.elevatedBackground)
                        .clickable {
                            onOpenYouTubeMusic(track.title, track.artist)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF4B4B), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Search on YouTube Music", color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Listen or copy song stream link", color = colors.secondaryText, fontSize = 10.sp)
                    }
                }

                if (isExternalDownloaderAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.elevatedBackground)
                            .clickable {
                                onOpenExternalDownloader(track.title, track.artist)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Send to Seal / Downloader App", color = colors.primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("Automatic 1-click audio extractor", color = colors.secondaryText, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Tip: When a song finishes downloading to your phone, Sonora's automatic scanner detects it and immediately adds it to this playlist.",
                    color = colors.secondaryText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    )
}
