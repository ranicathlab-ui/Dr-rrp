package com.postpci.drrrp.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpci.drrrp.ui.theme.AlertRed
import com.postpci.drrrp.ui.theme.BorderHairline
import com.postpci.drrrp.ui.theme.TextSecondary

data class ChartPoint(val dayN: Int, val value: Float, val outOfRange: Boolean = false)
data class ChartSeries(val label: String, val color: Color, val points: List<ChartPoint>)

/**
 * Simple Canvas line chart — x-axis is days post-PCI, y-axis is the reading value, with an
 * optional shaded band for the normal range. Horizontally scrollable so longer recovery windows
 * (more days of data) don't get squeezed; a fixed pixel width per day makes "swipe to see other
 * timeframes" work for free.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    normalRange: ClosedFloatingPointRange<Float>?,
    modifier: Modifier = Modifier,
    pxPerDay: Dp = 36.dp,
    chartHeight: Dp = 220.dp,
) {
    val allPoints = series.flatMap { it.points }
    if (allPoints.isEmpty()) {
        Box(modifier = modifier.height(chartHeight), contentAlignment = Alignment.Center) {
            Text("No readings logged yet for this chart.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val minDay = allPoints.minOf { it.dayN }
    val maxDay = allPoints.maxOf { it.dayN }
    val dataMin = allPoints.minOf { it.value }
    val dataMax = allPoints.maxOf { it.value }
    val rangeMin = normalRange?.start ?: dataMin
    val rangeMax = normalRange?.endInclusive ?: dataMax
    val yMin = minOf(dataMin, rangeMin) - 2f
    val yMax = maxOf(dataMax, rangeMax) + 2f
    val dayCount = (maxDay - minDay + 1).coerceAtLeast(1)

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(40.dp).height(chartHeight),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("%.0f".format(yMax), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text("%.0f".format(yMin), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Canvas(
                    modifier = Modifier
                        .width(pxPerDay * dayCount)
                        .height(chartHeight)
                        .clip(RoundedCornerShape(12.dp)),
                ) {
                    drawChart(series, normalRange, minDay, maxDay, yMin, yMax)
                }
            }
        }
        Text(
            text = "Day $minDay – Day $maxDay post-PCI",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 6.dp, start = 44.dp),
        )
    }
}

private fun DrawScope.drawChart(
    series: List<ChartSeries>,
    normalRange: ClosedFloatingPointRange<Float>?,
    minDay: Int,
    maxDay: Int,
    yMin: Float,
    yMax: Float,
) {
    val dayCount = (maxDay - minDay + 1).coerceAtLeast(1)
    val stepX = size.width / dayCount
    fun xFor(day: Int) = (day - minDay + 0.5f) * stepX
    fun yFor(value: Float) = size.height - ((value - yMin) / (yMax - yMin)) * size.height

    // Shaded normal-range band.
    if (normalRange != null) {
        val topY = yFor(normalRange.endInclusive)
        val bottomY = yFor(normalRange.start)
        drawRect(
            color = Color(0xFF3FCF8E).copy(alpha = 0.10f),
            topLeft = Offset(0f, topY),
            size = Size(size.width, bottomY - topY),
        )
    }

    // Baseline gridline.
    drawLine(BorderHairline, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), strokeWidth = 2f)

    series.forEach { s ->
        val sorted = s.points.sortedBy { it.dayN }
        for (i in 0 until sorted.size - 1) {
            drawLine(
                color = s.color,
                start = Offset(xFor(sorted[i].dayN), yFor(sorted[i].value)),
                end = Offset(xFor(sorted[i + 1].dayN), yFor(sorted[i + 1].value)),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }
        sorted.forEach { p ->
            val center = Offset(xFor(p.dayN), yFor(p.value))
            val dotColor = if (p.outOfRange) AlertRed else s.color
            drawCircle(color = dotColor, radius = 8f, center = center)
            drawCircle(color = Color(0xFF0A0E16), radius = 8f, center = center, style = Stroke(width = 3f))
        }
    }
}
