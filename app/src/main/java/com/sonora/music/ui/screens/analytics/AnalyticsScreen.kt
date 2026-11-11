package com.sonora.music.ui.screens.analytics

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.AnalyticsDashboardData
import com.sonora.music.data.model.AnalyticsRangeFilter
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.theme.LocalSonoraColors
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    data: AnalyticsDashboardData,
    onBack: () -> Unit,
    onSelectFilter: (AnalyticsRangeFilter) -> Unit,
    onStepRange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
    ) {
        // Header
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
                    text = "My Analytics",
                    color = colors.primaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Offline listening habits & playback analytics",
                    color = colors.secondaryText,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section A: Daily Listening Hours – Line Graph (7 Days / 30 Days / 12 Months + Prev/Next navigation)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily Listening Hours",
                            color = colors.primaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter selector pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnalyticsRangeFilter.entries.forEach { filter ->
                                val selected = data.filter == filter
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectFilter(filter) },
                                    color = if (selected) colors.accent else colors.surface,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = filter.label,
                                        color = if (selected) Color.White else colors.primaryText,
                                        fontSize = 12.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Date range navigation bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onStepRange(-1) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Range", tint = colors.primaryText)
                            }
                            Text(
                                text = data.rangeLabel,
                                color = colors.primaryText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { if (data.canGoNext) onStepRange(1) },
                                enabled = data.canGoNext,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Next Range",
                                    tint = if (data.canGoNext) colors.primaryText else colors.secondaryText.copy(alpha = 0.35f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        SmoothInteractiveLineChart(
                            points = data.mainLinePoints,
                            accentColor = colors.accent
                        )
                    }
                }
            }

            // Section B: Analytics Overview Summary Cards
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "ANALYTICS OVERVIEW",
                        color = colors.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewStatCard(
                            label = "Total Listening",
                            value = formatListeningDuration(data.totalListeningMs),
                            subtext = "${String.format("%.1f", data.totalListeningMs / 3_600_000f)} hours",
                            modifier = Modifier.weight(1f)
                        )
                        OverviewStatCard(
                            label = "Daily Average",
                            value = formatListeningDuration(data.dailyAverageMs),
                            subtext = "Per calendar day",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverviewStatCard(
                            label = "Songs Played",
                            value = "${data.songsPlayedCount}",
                            subtext = "Recorded sessions",
                            modifier = Modifier.weight(1f)
                        )
                        OverviewStatCard(
                            label = "Most Active Day",
                            value = data.mostActiveDay?.let { formatListeningDuration(it.totalListeningTimeMs) } ?: "0m",
                            subtext = data.mostActiveDay?.displayLabel ?: "None yet",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section C: Additional Analytics Graphs (All 7)
            item {
                Text(
                    text = "LISTENING GRAPHS",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // C1: Listening Hours by Day
            item {
                ChartCard(title = "Listening Hours by Day") {
                    SmoothInteractiveLineChart(
                        points = data.listeningHoursByDay,
                        accentColor = colors.accent
                    )
                }
            }

            // C2: Weekly Listening Trends
            item {
                ChartCard(title = "Weekly Listening Trends") {
                    SmoothInteractiveLineChart(
                        points = data.weeklyTrends,
                        accentColor = Color(0xFF4ECDC4)
                    )
                }
            }

            // C3: Monthly Listening Trends
            item {
                ChartCard(title = "Monthly Listening Trends") {
                    SmoothInteractiveLineChart(
                        points = data.monthlyTrends,
                        accentColor = Color(0xFFFFB74D)
                    )
                }
            }

            // C4: Most-Played Artists (Bar Graph)
            item {
                ChartCard(title = "Most-Played Artists") {
                    VerticalInteractiveBarChart(
                        entries = data.mostPlayedArtists.map {
                            BarChartEntry(
                                shortLabel = it.artistName,
                                fullLabel = it.artistName,
                                value = it.totalPlayCount.toFloat(),
                                formattedValue = "${it.totalPlayCount} plays"
                            )
                        },
                        accentColor = colors.accent,
                        maxVisibleLabels = 6
                    )
                }
            }

            // C5: Top 10 Most-Played Songs (Horizontal Bar Graph)
            item {
                ChartCard(title = "Top 10 Most-Played Songs") {
                    if (data.top10Songs.isEmpty()) {
                        Text("Play tracks to populate top songs.", color = colors.secondaryText, fontSize = 13.sp)
                    } else {
                        val maxPlays = data.top10Songs.maxOf { it.playCount }.coerceAtLeast(1)
                        Column {
                            data.top10Songs.forEachIndexed { idx, song ->
                                HorizontalBarRow(
                                    rank = idx + 1,
                                    title = song.title,
                                    subtitle = song.artist,
                                    valueLabel = "${song.playCount} plays (${formatListeningDuration(song.totalListeningTimeMs)})",
                                    progress = song.playCount.toFloat() / maxPlays.toFloat(),
                                    accentColor = colors.accent
                                )
                            }
                        }
                    }
                }
            }

            // C6: Listening Time by Artist (Bar Graph)
            item {
                ChartCard(title = "Listening Time by Artist") {
                    VerticalInteractiveBarChart(
                        entries = data.listeningTimeByArtist.map {
                            BarChartEntry(
                                shortLabel = it.artistName,
                                fullLabel = it.artistName,
                                value = it.totalListeningTimeMs / 60_000f,
                                formattedValue = formatListeningDuration(it.totalListeningTimeMs)
                            )
                        },
                        accentColor = Color(0xFF81C784),
                        maxVisibleLabels = 6
                    )
                }
            }

            // C7: Listening Activity by Hour (Bar Graph)
            item {
                ChartCard(title = "Listening Activity by Hour (24h)") {
                    VerticalInteractiveBarChart(
                        entries = data.hourlyActivity.map {
                            BarChartEntry(
                                shortLabel = it.label,
                                fullLabel = "${it.label} hour window",
                                value = (it.durationMs / 60_000f).let { mins ->
                                    if (mins == 0f && it.playCount > 0) it.playCount.toFloat() else mins
                                },
                                formattedValue = "${formatListeningDuration(it.durationMs)} • ${it.playCount} plays"
                            )
                        },
                        accentColor = Color(0xFFBA68C8),
                        maxVisibleLabels = 8
                    )
                }
            }

            // Section D: Additional Listening Insights (All 8)
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.elevatedBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "ADDITIONAL LISTENING INSIGHTS",
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        InsightRow(
                            label = "Most-Listened Artist",
                            value = data.mostListenedArtist?.let {
                                "${it.artistName} (${formatListeningDuration(it.totalListeningTimeMs)})"
                            } ?: "No data yet"
                        )
                        InsightRow(
                            label = "Most-Played Song",
                            value = data.mostPlayedSong?.let {
                                "${it.title} (${it.playCount} plays)"
                            } ?: "No data yet"
                        )
                        InsightRow(
                            label = "Longest Listening Day",
                            value = data.longestListeningDay?.let {
                                "${it.displayLabel} (${formatListeningDuration(it.totalListeningTimeMs)})"
                            } ?: "No data yet"
                        )
                        InsightRow(
                            label = "Average Daily Listening Time",
                            value = formatListeningDuration(data.averageDailyListeningMs)
                        )
                        InsightRow(
                            label = "Highest Listening Month",
                            value = data.highestListeningMonth?.let {
                                "${it.fullLabel} (${formatListeningDuration(it.durationMs)})"
                            } ?: "No data yet"
                        )
                        InsightRow(
                            label = "Most Active Listening Hour",
                            value = data.mostActiveHour?.let {
                                "${it.label} (${formatListeningDuration(it.durationMs)} • ${it.playCount} plays)"
                            } ?: "No data yet"
                        )

                        // Week-over-week comparison with trend icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "vs Previous Week",
                                color = colors.secondaryText,
                                fontSize = 13.sp
                            )
                            val delta = data.weekComparisonDeltaPercent
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when {
                                    delta == null -> {
                                        Icon(Icons.Default.TrendingFlat, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${formatListeningDuration(data.currentWeekListeningMs)} this week",
                                            color = colors.primaryText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    delta >= 0f -> {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "+${delta.roundToInt()}% (${formatListeningDuration(data.currentWeekListeningMs)} vs ${formatListeningDuration(data.previousWeekListeningMs)})",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    else -> {
                                        Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFFFF7043), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${delta.roundToInt()}% (${formatListeningDuration(data.currentWeekListeningMs)} vs ${formatListeningDuration(data.previousWeekListeningMs)})",
                                            color = Color(0xFFFF7043),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        InsightRow(
                            label = "Current Year Total",
                            value = formatListeningDuration(data.currentYearListeningMs)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    val colors = LocalSonoraColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = colors.primaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            content()
        }
    }
}

@Composable
private fun OverviewStatCard(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = colors.elevatedBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, color = colors.secondaryText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = colors.accent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtext,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InsightRow(
    label: String,
    value: String
) {
    val colors = LocalSonoraColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = colors.secondaryText,
            fontSize = 13.sp,
            modifier = Modifier.weight(0.45f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            color = colors.primaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}

