package com.sonora.music.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sonora.music.audio.SonoraEqualizerManager
import com.sonora.music.ui.theme.LocalSonoraColors

@Composable
fun EqualizerDialog(
    equalizerManager: SonoraEqualizerManager,
    onDismiss: () -> Unit
) {
    val colors = LocalSonoraColors.current
    val isEnabled by equalizerManager.isEnabled.collectAsState()
    val bandLevels by equalizerManager.bandLevels.collectAsState()
    val bandFrequencies by equalizerManager.bandFrequencies.collectAsState()
    val minLevel by equalizerManager.minBandLevel.collectAsState()
    val maxLevel by equalizerManager.maxBandLevel.collectAsState()
    val bassBoost by equalizerManager.bassBoostStrength.collectAsState()
    val equalizerSupported by equalizerManager.equalizerSupported.collectAsState()
    val bassBoostSupported by equalizerManager.bassBoostSupported.collectAsState()
    val currentPreset by equalizerManager.currentPreset.collectAsState()
    val availablePresets by equalizerManager.availablePresets.collectAsState()

    val eqActive = isEnabled && equalizerSupported
    val bassActive = isEnabled && bassBoostSupported

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
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with icon, title, switch, and close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Audio Equalizer & Bass",
                                color = colors.primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (isEnabled) "Active" else "Disabled",
                                color = if (isEnabled) colors.accent else colors.secondaryText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { equalizerManager.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.secondaryText,
                                uncheckedTrackColor = colors.surface
                            )
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.secondaryText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Presets Horizontal Row
                Text(
                    text = "SOUND PRESETS",
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availablePresets.forEach { preset ->
                        val isSelected = currentPreset.equals(preset, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (preset != "Custom") {
                                    equalizerManager.applyPreset(preset)
                                }
                            },
                            enabled = equalizerSupported,
                            label = {
                                Text(
                                    text = preset,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent,
                                selectedLabelColor = Color.White,
                                containerColor = colors.surface,
                                labelColor = colors.primaryText
                            ),
                            border = null,
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Frequency Bands Slider Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FREQUENCY BANDS",
                        color = colors.secondaryText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    TextButton(
                        onClick = { equalizerManager.applyPreset("Flat") },
                        enabled = eqActive
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (eqActive) colors.accent else colors.secondaryText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Reset Flat",
                            color = if (eqActive) colors.accent else colors.secondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (bandFrequencies.isEmpty() || !equalizerSupported) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(colors.surface, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (!equalizerSupported) {
                                "Hardware equalizer not supported on this device"
                            } else {
                                "Play audio to activate hardware equalizer bands"
                            },
                            color = colors.secondaryText,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        bandFrequencies.forEachIndexed { band, freqHz ->
                            val currentLevel = bandLevels[band] ?: 0
                            val rangeTotal = (maxLevel - minLevel).toFloat().coerceAtLeast(1f)
                            val sliderVal = ((currentLevel - minLevel) / rangeTotal).coerceIn(0f, 1f)

                            val freqLabel = if (freqHz >= 1000) {
                                "${"%.1f".format(freqHz / 1000f).replace(".0", "")} kHz"
                            } else {
                                "$freqHz Hz"
                            }

                            val dbVal = currentLevel / 100
                            val dbLabel = if (dbVal > 0) "+$dbVal dB" else "$dbVal dB"

                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = freqLabel,
                                        color = colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = dbLabel,
                                        color = if (dbVal != 0 && eqActive) colors.accent else colors.secondaryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Slider(
                                    value = sliderVal,
                                    onValueChange = { frac ->
                                        val newLevel = (minLevel + (frac * rangeTotal)).toInt()
                                        equalizerManager.setBandLevel(band, newLevel)
                                    },
                                    enabled = eqActive,
                                    colors = SliderDefaults.colors(
                                        thumbColor = if (eqActive) colors.accent else colors.secondaryText,
                                        activeTrackColor = if (eqActive) colors.accent else colors.secondaryText,
                                        inactiveTrackColor = colors.elevatedBackground
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bass Boost Section
                Text(
                    text = "BASS ENHANCEMENT",
                    color = colors.secondaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (bassBoostSupported) 1f else 0.6f)
                        .background(colors.surface, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (bassActive) colors.accent else colors.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Bass Boost",
                                    color = colors.primaryText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (!bassBoostSupported) {
                                    Text(
                                        text = "Not supported on current hardware output",
                                        color = Color(0xFFFFB74D),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${(bassBoost / 10)}%",
                            color = if (bassBoost > 0 && bassActive) colors.accent else colors.secondaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Slider(
                        value = bassBoost / 1000f,
                        onValueChange = { equalizerManager.setBassBoost((it * 1000).toInt()) },
                        enabled = bassActive,
                        colors = SliderDefaults.colors(
                            thumbColor = if (bassActive) colors.accent else colors.secondaryText,
                            activeTrackColor = if (bassActive) colors.accent else colors.secondaryText,
                            inactiveTrackColor = colors.elevatedBackground
                        )
                    )
                }
            }
        }
    }
}
