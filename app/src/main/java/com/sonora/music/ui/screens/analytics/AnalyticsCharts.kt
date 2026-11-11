package com.sonora.music.ui.screens.analytics

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonora.music.data.model.TimePeriodPoint
import com.sonora.music.ui.components.formatListeningDuration
import com.sonora.music.ui.theme.LocalSonoraColors
import kotlin.math.abs

data class BarChartEntry(
    val shortLabel: String,
    val fullLabel: String,
    val value: Float,
    val formattedValue: String
)

@Composable
fun SmoothInteractiveLineChart(
    points: List<TimePeriodPoint>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    val density = LocalDensity.current
    var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }

    val labelPaint = remember(colors.secondaryText, density) {
        Paint().apply {
            color = colors.secondaryText.toArgb()
            textSize = with(density) { 10.sp.toPx() }
            isAntiAlias = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tooltip bar when user taps any point
        val activeIdx = selectedIdx?.takeIf { it in points.indices }
        val activePoint = activeIdx?.let { points[it] }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(10.dp),
            color = colors.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activePoint != null) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activePoint.fullLabel,
                            color = colors.primaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${activePoint.playCount} plays",
                            color = colors.secondaryText,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = "${String.format("%.2f", activePoint.hours)} hrs (${formatListeningDuration(activePoint.durationMs)})",
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Tap any point on the curve to inspect exact listening hours",
                        color = colors.secondaryText,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No points available", color = colors.secondaryText, fontSize = 13.sp)
            }
            return
        }

        val maxHoursRaw = points.maxOfOrNull { it.hours } ?: 0f
        val maxY = maxHoursRaw.coerceAtLeast(0.5f) * 1.15f

        val leftPadPx = with(density) { 36.dp.toPx() }
        val rightPadPx = with(density) { 12.dp.toPx() }
        val topPadPx = with(density) { 12.dp.toPx() }
        val bottomPadPx = with(density) { 26.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .pointerInput(points) {
                    detectTapGestures { tapOffset ->
                        val chartW = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
                        val count = points.size
                        val nearest = if (count <= 1) 0 else {
                            (0 until count).minByOrNull { i ->
                                val x = leftPadPx + (i.toFloat() / (count - 1)) * chartW
                                abs(x - tapOffset.x)
                            } ?: 0
                        }
                        selectedIdx = if (selectedIdx == nearest) null else nearest
                    }
                }
        ) {
            val chartW = (size.width - leftPadPx - rightPadPx).coerceAtLeast(1f)
            val chartH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)

            // Y-axis horizontal dashed grid lines & hour labels
            val steps = 3
            for (s in 0..steps) {
                val frac = s.toFloat() / steps
                val y = topPadPx + chartH * (1f - frac)
                drawLine(
                    color = colors.divider.copy(alpha = 0.55f),
                    start = Offset(leftPadPx, y),
                    end = Offset(leftPadPx + chartW, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                )
                val yVal = maxY * frac
                val label = if (yVal >= 10f) "${yVal.toInt()}h" else String.format("%.1fh", yVal)
                labelPaint.textAlign = Paint.Align.RIGHT
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    leftPadPx - 6.dp.toPx(),
                    y + 4.dp.toPx(),
                    labelPaint
                )
            }

            // Compute data coordinates
            val coords = points.mapIndexed { idx, p ->
                val x = if (points.size <= 1) {
                    leftPadPx + chartW / 2f
                } else {
                    leftPadPx + (idx.toFloat() / (points.size - 1)) * chartW
                }
                val ratio = (p.hours / maxY).coerceIn(0f, 1f)
                val y = topPadPx + chartH * (1f - ratio)
                Offset(x, y)
            }

            // Build smooth cubic spline path
            val strokePath = Path()
            val fillPath = Path()
            if (coords.isNotEmpty()) {
                strokePath.moveTo(coords.first().x, coords.first().y)
                fillPath.moveTo(coords.first().x, topPadPx + chartH)
                fillPath.lineTo(coords.first().x, coords.first().y)

                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    strokePath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }

                fillPath.lineTo(coords.last().x, topPadPx + chartH)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.35f),
                            accentColor.copy(alpha = 0.02f)
                        ),
                        startY = topPadPx,
                        endY = topPadPx + chartH
                    )
                )

                drawPath(
                    path = strokePath,
                    color = accentColor,
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Selected guide line & X-axis labels
            val labelStride = when {
                points.size <= 8 -> 1
                points.size <= 15 -> 2
                else -> 5
            }

            labelPaint.textAlign = Paint.Align.CENTER
            coords.forEachIndexed { idx, pt ->
                val isSelected = idx == activeIdx
                if (isSelected) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.6f),
                        start = Offset(pt.x, topPadPx),
                        end = Offset(pt.x, topPadPx + chartH),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Points: show dots cleanly (every point for <= 14, or active + non-zero points)
                if (points.size <= 14 || points[idx].durationMs > 0 || isSelected) {
                    drawCircle(
                        color = if (isSelected) Color.White else accentColor,
                        radius = if (isSelected) 5.5.dp.toPx() else 3.dp.toPx(),
                        center = pt
                    )
                    if (isSelected) {
                        drawCircle(
                            color = accentColor,
                            radius = 3.2.dp.toPx(),
                            center = pt
                        )
                    }
                }

                if (idx % labelStride == 0 || idx == coords.lastIndex) {
                    drawContext.canvas.nativeCanvas.drawText(
                        points[idx].label,
                        pt.x,
                        size.height - 4.dp.toPx(),
                        labelPaint
                    )
                }
            }
        }
    }
}

@Composable
fun VerticalInteractiveBarChart(
    entries: List<BarChartEntry>,
    accentColor: Color,
    modifier: Modifier = Modifier,
    maxVisibleLabels: Int = 8
) {
    val colors = LocalSonoraColors.current
    val density = LocalDensity.current
    var selectedIdx by remember(entries) { mutableStateOf<Int?>(null) }

    val labelPaint = remember(colors.secondaryText, density) {
        Paint().apply {
            color = colors.secondaryText.toArgb()
            textSize = with(density) { 9.5.sp.toPx() }
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        val active = selectedIdx?.takeIf { it in entries.indices }?.let { entries[it] }
        if (active != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = colors.surface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = active.fullLabel,
                        color = colors.primaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = active.formattedValue,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No listening data recorded yet", color = colors.secondaryText, fontSize = 13.sp)
            }
            return
        }

        val maxVal = entries.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
        val topPadPx = with(density) { 8.dp.toPx() }
        val bottomPadPx = with(density) { 24.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .pointerInput(entries) {
                    detectTapGestures { tap ->
                        if (entries.isEmpty()) return@detectTapGestures
                        val slotW = size.width.toFloat() / entries.size
                        val idx = (tap.x / slotW).toInt().coerceIn(0, entries.lastIndex)
                        selectedIdx = if (selectedIdx == idx) null else idx
                    }
                }
        ) {
            val slotW = size.width / entries.size
            val barW = (slotW * 0.58f).coerceIn(4f, 44f)
            val chartH = (size.height - topPadPx - bottomPadPx).coerceAtLeast(1f)
            val stride = (entries.size / maxVisibleLabels).coerceAtLeast(1)

            entries.forEachIndexed { idx, entry ->
                val xCenter = idx * slotW + slotW / 2f
                val frac = (entry.value / maxVal).coerceIn(0f, 1f)
                val barH = (chartH * frac).coerceAtLeast(if (entry.value > 0f) 4f else 2f)
                val barLeft = xCenter - barW / 2f
                val barTop = topPadPx + chartH - barH

                val isSelected = selectedIdx == idx
                drawRoundRect(
                    color = when {
                        isSelected -> Color.White
                        entry.value > 0f -> accentColor.copy(alpha = 0.85f)
                        else -> colors.divider.copy(alpha = 0.35f)
                    },
                    topLeft = Offset(barLeft, barTop),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                if (idx % stride == 0 || isSelected) {
                    drawContext.canvas.nativeCanvas.drawText(
                        entry.shortLabel.take(8),
                        xCenter,
                        size.height - 4.dp.toPx(),
                        labelPaint
                    )
                }
            }
        }
    }
}

@Composable
fun HorizontalBarRow(
    rank: Int,
    title: String,
    subtitle: String,
    valueLabel: String,
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalSonoraColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "#$rank  $title",
                color = colors.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = valueLabel,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = colors.secondaryText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }
    }
}

