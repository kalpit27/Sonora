package com.sonora.music.ui.screens.smart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.sonora.music.data.model.DuplicateGroup
import com.sonora.music.data.model.Song
import com.sonora.music.ui.components.AlbumArtImage
import com.sonora.music.ui.components.ConfirmDeleteDialog
import com.sonora.music.ui.components.EmptyStateView
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun DuplicatesScreen(
    duplicateGroups: List<DuplicateGroup>,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDeleteSong: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    songToDelete?.let { target ->
        ConfirmDeleteDialog(
            title = "Delete Duplicate Track",
            message = "Remove \"${target.title}\" (${target.folderName}) from your library and storage?",
            onDismiss = { songToDelete = null },
            onConfirm = {
                onDeleteSong(target)
                songToDelete = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.primaryText)
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Duplicate Audio Files",
                    color = colors.primaryText,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${duplicateGroups.size} potential duplicate groups found",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        if (duplicateGroups.isEmpty()) {
            EmptyStateView(
                title = "No Duplicates Detected",
                message = "Your local music library is clean with no duplicate audio files detected.",
                icon = Icons.Default.CheckCircle
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(duplicateGroups, key = { it.signature }) { group ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.elevatedBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = group.displayTitle,
                                color = colors.primaryText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${group.songs.size} copies • ${group.songs.first().formattedDuration}",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )

                            group.songs.forEachIndexed { idx, copy ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface)
                                        .clickable { onPlaySong(copy, group.songs) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AlbumArtImage(
                                        artworkUri = copy.albumArtUri,
                                        audioUri = copy.contentUri,
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (idx == 0) "Copy #1 • ${copy.artist}" else "Copy #${idx + 1} • ${copy.artist}",
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Folder: ${copy.folderName} • ${copy.mimeType} • ${copy.playCount} plays",
                                            color = colors.secondaryText,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { onPlaySong(copy, group.songs) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = colors.accent)
                                    }
                                    IconButton(onClick = { songToDelete = copy }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete duplicate", tint = Color(0xFFFF6B6B))
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

