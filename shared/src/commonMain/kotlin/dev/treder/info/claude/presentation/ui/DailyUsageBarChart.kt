package dev.treder.info.claude.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.domain.model.YearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private val LABEL_DAYS = intArrayOf(1, 5, 10, 15, 20, 25, 30)
private const val MAX_DAYS = 31
private const val BAR_HEIGHT_DP = 40
private const val LABEL_HEIGHT_DP = 12
private const val LABEL_GAP_DP = 2

@Composable
fun DailyUsageBarChart(
    month: MonthUsage,
    modifier: Modifier = Modifier,
) {
    val daysInMonth = remember(month.month) { daysInMonth(month.month) }
    val totals = remember(month.days, daysInMonth) {
        val byDay = month.days.associateBy { it.date.day }
        LongArray(daysInMonth) { idx ->
            val usage = byDay[idx + 1]?.total ?: return@LongArray 0L
            usage.inputTokens + usage.outputTokens + usage.cacheWriteTokens + usage.cacheReadTokens
        }
    }
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val highlightDay = if (today.year == month.month.year && today.month == month.month.month) today.day else -1
    val max = totals.maxOrNull() ?: 0L

    val barColor = MaterialTheme.colorScheme.secondary
    val highlightColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) {
        TextStyle(color = labelColor, fontSize = 9.sp)
    }

    var hoveredDay by remember { mutableStateOf<Int?>(null) }
    var hoverSlotCenterPx by remember { mutableStateOf(0f) }
    var canvasWidthPx by remember { mutableStateOf(0) }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((BAR_HEIGHT_DP + LABEL_GAP_DP + LABEL_HEIGHT_DP).dp)
                .pointerInput(daysInMonth) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter -> {
                                    canvasWidthPx = size.width
                                    val pos = event.changes.firstOrNull()?.position
                                    if (pos == null) {
                                        hoveredDay = null
                                    } else {
                                        val slot = size.width.toFloat() / MAX_DAYS
                                        val idx = (pos.x / slot).toInt()
                                        if (idx in 0 until daysInMonth) {
                                            hoveredDay = idx + 1
                                            hoverSlotCenterPx = (idx + 0.5f) * slot
                                        } else {
                                            hoveredDay = null
                                        }
                                    }
                                }
                                PointerEventType.Exit -> hoveredDay = null
                            }
                        }
                    }
                },
        ) {
            val n = totals.size
            if (n == 0) return@Canvas
            val barAreaHeight = BAR_HEIGHT_DP.dp.toPx()
            val slot = size.width / MAX_DAYS
            val barWidth = slot * 0.75f
            val leftPad = (slot - barWidth) / 2f
            val radius = CornerRadius(barWidth * 0.25f, barWidth * 0.25f)
            for (i in 0 until n) {
                val value = totals[i]
                val frac = if (max == 0L) 0f else value.toFloat() / max.toFloat()
                val h = barAreaHeight * frac
                if (h <= 0f) continue
                val x = i * slot + leftPad
                val y = barAreaHeight - h
                drawRoundRect(
                    color = if ((i + 1) == highlightDay) highlightColor else barColor,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = radius,
                )
            }

            drawDayLabels(
                textMeasurer = textMeasurer,
                style = labelStyle,
                daysInMonth = n,
                slot = slot,
                labelBaselineY = barAreaHeight + LABEL_GAP_DP.dp.toPx(),
            )
        }

        hoveredDay?.let { day ->
            val tokens = totals.getOrNull(day - 1) ?: 0L
            BarTooltip(
                day = day,
                month = month.month,
                tokens = tokens,
                anchorXPx = hoverSlotCenterPx,
                canvasWidthPx = canvasWidthPx,
            )
        }
    }
}

@Composable
private fun BarTooltip(
    day: Int,
    month: YearMonth,
    tokens: Long,
    anchorXPx: Float,
    canvasWidthPx: Int,
) {
    val text = "$day. ${germanMonthName(month.month)} ${month.year} · ${formatTokens(tokens)} Tokens"

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val maxX = (canvasWidthPx - placeable.width).coerceAtLeast(0)
                    val x = (anchorXPx - placeable.width / 2f).toInt().coerceIn(0, maxX)
                    placeable.place(x, 0)
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun DrawScope.drawDayLabels(
    textMeasurer: TextMeasurer,
    style: TextStyle,
    daysInMonth: Int,
    slot: Float,
    labelBaselineY: Float,
) {
    for (day in LABEL_DAYS) {
        if (day > daysInMonth) continue
        val text = day.toString()
        val layout = textMeasurer.measure(text, style)
        val centerX = (day - 0.5f) * slot
        var x = centerX - layout.size.width / 2f
        x = x.coerceIn(0f, size.width - layout.size.width)
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(x, labelBaselineY),
        )
    }
}

private fun daysInMonth(ym: YearMonth): Int {
    val lastDay: LocalDate = ym.next().atStartOfMonth().minus(1, DateTimeUnit.DAY)
    return lastDay.day
}

private fun germanMonthName(month: Month): String = when (month) {
    Month.JANUARY -> "Januar"
    Month.FEBRUARY -> "Februar"
    Month.MARCH -> "März"
    Month.APRIL -> "April"
    Month.MAY -> "Mai"
    Month.JUNE -> "Juni"
    Month.JULY -> "Juli"
    Month.AUGUST -> "August"
    Month.SEPTEMBER -> "September"
    Month.OCTOBER -> "Oktober"
    Month.NOVEMBER -> "November"
    Month.DECEMBER -> "Dezember"
    else -> month.toString()
}
