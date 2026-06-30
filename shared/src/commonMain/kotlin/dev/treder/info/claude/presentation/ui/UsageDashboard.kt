package dev.treder.info.claude.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.domain.model.UpdateStatus
import dev.treder.info.claude.domain.model.YearMonth
import dev.treder.info.claude.domain.model.UsageLimitsStatus
import dev.treder.info.claude.presentation.PricingPhase
import dev.treder.info.claude.presentation.UsageUiState
import dev.treder.info.claude.presentation.UsageViewModel
import kotlin.math.roundToInt
import kotlinx.datetime.Month

@Composable
fun UsageDashboard(
    viewModel: UsageViewModel,
    onContentSizeChanged: (IntSize) -> Unit = {},
    backgroundColor: Color? = null,
    onClose: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolvedBackground = backgroundColor
        ?: MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(maxHeight = Constraints.Infinity))
                layout(placeable.width, placeable.height) {
                    placeable.place(0, 0)
                }
            }
            .onSizeChanged(onContentSizeChanged),
        color = resolvedBackground,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Header(
                preferredModel = state.preferredModel,
                updateStatus = state.updateStatus,
                limitsStatus = state.usageLimitsStatus,
                onOpenUrl = onOpenUrl,
                onClose = onClose,
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            when {
                state.pricingPhase == PricingPhase.Failed -> PricingErrorContent(onRetry = viewModel::retryPricing)
                state.showDashboard -> UsageContent(state)
                else -> LoadingContent()
            }
        }
    }
}

/** Shown while the first pricing fetch (and the initial usage scan) is still running. */
@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Text(
            text = "Lade Preisdaten …",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Shown when the very first pricing fetch failed and there is nothing to fall back to. */
@Composable
private fun PricingErrorContent(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Preisdaten konnten nicht geladen werden",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Ohne aktuelle Preise lassen sich die Kosten nicht berechnen. " +
                "Prüfe deine Internetverbindung und versuche es erneut.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onRetry) {
            Text("Erneut versuchen")
        }
    }
}

/** The full dashboard once prices and usage data are available. */
@Composable
private fun UsageContent(state: UsageUiState) {
    if (state.usageLimits.isNotEmpty()) {
        UsageLimitsSection(limits = state.usageLimits)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }

    PeriodSection(
        title = "Heute",
        period = state.today,
    )

    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

    val history = state.history
    when {
        history == null -> {
            Text(
                text = "Lade …",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        history.isEmpty() -> {
            Text(
                text = "Keine Claude-Nutzungsdaten gefunden. " +
                    "Erwartet werden JSONL-Dateien in ~/.claude/projects/.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        else -> MonthPager(history = history)
    }

    state.error?.let { errorText ->
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Text(
            text = "Fehler: $errorText",
            color = Color(0xFFFFB4A2),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Largest number of dots the indicator renders before it starts windowing. */
private const val MAX_DOTS = 9

/**
 * Horizontal month pager: one month per page, the full history. The newest
 * (current) month is the first page; the chevrons move to the newer (‹) or
 * older (›) month and are hidden when no month exists in that direction; a
 * centered dot indicator marks the position.
 */
@Composable
private fun MonthPager(history: List<MonthUsage>) {
    // Newest (current month) first — leftmost dot — so paging right (›) walks
    // back into history. history already arrives newest-first.
    val months = history
    if (months.isEmpty()) return
    val lastIndex = months.lastIndex
    // Track the selected *month*, not its index: a background refresh can insert
    // or drop a month and reshuffle indices, so an index would silently point at
    // a different month. null means "follow the newest month".
    var selectedMonth by remember { mutableStateOf<YearMonth?>(null) }
    val current = selectedMonth
        ?.let { sel -> months.indexOfFirst { it.month == sel } }
        ?.takeIf { it >= 0 }
        ?: 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AnimatedContent(
            targetState = current,
            modifier = Modifier.fillMaxWidth().clipToBounds(),
            transitionSpec = {
                val forward = targetState > initialState
                val enter = slideInHorizontally { width -> if (forward) width else -width } + fadeIn()
                val exit = slideOutHorizontally { width -> if (forward) -width else width } + fadeOut()
                enter togetherWith exit
            },
            label = "monthPager",
        ) { index ->
            val month = months.getOrNull(index) ?: return@AnimatedContent
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PeriodSection(title = formatMonth(month), period = month)
                DailyUsageBarChart(month = month)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Equal-weight side boxes keep the indicator centered regardless of
            // the (asymmetric) month-label widths.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (current > 0) {
                    val target = months[current - 1]
                    PagerNavButton(
                        pointsLeft = true,
                        label = formatMonthShort(target),
                        onClick = { selectedMonth = target.month },
                    )
                }
            }
            MonthPagerIndicator(
                total = months.size,
                current = current,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (current < lastIndex) {
                    val target = months[current + 1]
                    PagerNavButton(
                        pointsLeft = false,
                        label = formatMonthShort(target),
                        onClick = { selectedMonth = target.month },
                    )
                }
            }
        }
    }
}

/**
 * A flanking pager control: a vector chevron plus the name of the month it
 * pages to. The chevron and the label form a single click target; the chevron
 * sits on the outer edge and the month label between it and the indicator.
 */
@Composable
private fun PagerNavButton(pointsLeft: Boolean, label: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text: @Composable () -> Unit = {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        if (pointsLeft) {
            ChevronIcon(pointsLeft = true, color = color)
            text()
        } else {
            text()
            ChevronIcon(pointsLeft = false, color = color)
        }
    }
}

/** The bare chevron glyph, drawn as a vector so it sits exactly on the centerline. */
@Composable
private fun ChevronIcon(pointsLeft: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(11.dp)) {
        val near = size.width * 0.3f
        val far = size.width * 0.7f
        val top = size.height * 0.2f
        val mid = size.height * 0.5f
        val bottom = size.height * 0.8f
        val apexX = if (pointsLeft) near else far
        val baseX = if (pointsLeft) far else near
        val path = Path().apply {
            moveTo(baseX, top)
            lineTo(apexX, mid)
            lineTo(baseX, bottom)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/**
 * Centered dot indicator for the month pager. Shows every month as a dot when
 * they fit; for longer histories it windows the dots around the current one and
 * shrinks the edge dots to hint that more months exist beyond the view.
 */
@Composable
private fun MonthPagerIndicator(total: Int, current: Int, modifier: Modifier = Modifier) {
    if (total <= 0) return
    val start = if (total <= MAX_DOTS) 0 else (current - MAX_DOTS / 2).coerceIn(0, total - MAX_DOTS)
    val end = minOf(start + MAX_DOTS, total)
    val moreLeft = start > 0
    val moreRight = end < total
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in start until end) {
            val edge = (moreLeft && i == start) || (moreRight && i == end - 1)
            val nearEdge = (moreLeft && i == start + 1) || (moreRight && i == end - 2)
            val diameter = when {
                edge -> 4.dp
                nearEdge -> 5.dp
                i == current -> 7.dp
                else -> 6.dp
            }
            Box(
                modifier = Modifier
                    .size(diameter)
                    .clip(CircleShape)
                    .background(if (i == current) activeColor else inactiveColor),
            )
        }
    }
}

@Composable
private fun Header(
    preferredModel: String?,
    updateStatus: UpdateStatus,
    limitsStatus: UsageLimitsStatus,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
) {
    val warningText = limitsWarningText(limitsStatus)
    var headerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var warningIconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val warningInteraction = remember { MutableInteractionSource() }
    val warningHovered by warningInteraction.collectIsHoveredAsState()

    var infoIconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val infoInteraction = remember { MutableInteractionSource() }
    val infoHovered by infoInteraction.collectIsHoveredAsState()

    val showWarningTip = warningText != null && warningHovered

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (showWarningTip || infoHovered) 1f else 0f)
            .onGloballyPositioned { headerCoords = it },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Claude Info",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (updateStatus is UpdateStatus.UpdateAvailable) {
                        Text(
                            text = "Update verfügbar",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .alignByBaseline()
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onOpenUrl(updateStatus.url) },
                        )
                    }
                }
                if (!preferredModel.isNullOrBlank()) {
                    Text(
                        text = "Bevorzugtes Modell: $preferredModel",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            // The trailing icons share one centered baseline; the close button's
            // IconButton centers its glyph in 24dp, so the others are centered in
            // matching 24dp boxes to line up exactly with it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warningText != null) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .pointerHoverIcon(PointerIcon.Default)
                            .hoverable(warningInteraction)
                            .onGloballyPositioned { warningIconCoords = it },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⚠",
                            color = Color(0xFFFFB4A2),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .pointerHoverIcon(PointerIcon.Default)
                        .hoverable(infoInteraction)
                        .onGloballyPositioned { infoIconCoords = it },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "ⓘ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp),
                ) {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }

        if (showWarningTip) {
            HeaderHoverTooltip(anchorRect = anchorRectOf(headerCoords, warningIconCoords), text = warningText)
        }
        if (infoHovered) {
            HeaderHoverTooltip(anchorRect = anchorRectOf(headerCoords, infoIconCoords), text = USAGE_INFO_TEXT)
        }
    }
}

/** The header overlay-tooltip anchor: the icon's box expressed in the header's coordinate space. */
private fun anchorRectOf(header: LayoutCoordinates?, icon: LayoutCoordinates?): Rect? =
    if (header != null && icon != null && header.isAttached && icon.isAttached) {
        header.localBoundingBoxOf(icon, clipBounds = false)
    } else {
        null
    }

/** Hover hint for a header icon (warning / info), dropped just below it. */
@Composable
private fun HeaderHoverTooltip(anchorRect: Rect?, text: String) {
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }
    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Zero-size node: the tooltip overlays without affecting the header height.
                layout(0, 0) {
                    val rect = anchorRect ?: return@layout
                    val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                    val x = (rect.right - placeable.width).toInt().coerceIn(0, maxX)
                    val y = (rect.bottom + gapPx).roundToInt()
                    placeable.place(x, y)
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.97f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.widthIn(max = 260.dp),
        )
    }
}

/** General info shown via the header info icon, replacing the old footer note. */
private const val USAGE_INFO_TEXT =
    "Nutzungslimits werden von der Claude API abgerufen. " +
        "Die Statistiken stammen ausschließlich aus Claude-Sessions auf diesem Gerät."

/** The warning message for the header icon, or null when no warning should show. */
private fun limitsWarningText(status: UsageLimitsStatus): String? = when (status) {
    UsageLimitsStatus.Unavailable ->
        "Die Limit-Anzeige ist momentan nicht verfügbar (Authentifizierung). " +
            "Führe \"claude /login\" im Terminal aus um die Session zu erneuern."
    UsageLimitsStatus.RateLimited ->
        "Die Nutzungslimits werden von der Claude API gerade gedrosselt (Rate-Limit). " +
            "Die angezeigten Werte können veraltet sein und aktualisieren sich automatisch, " +
            "sobald der Server wieder bereit ist."
    UsageLimitsStatus.Loading, UsageLimitsStatus.Available -> null
}

private fun formatMonth(month: MonthUsage): String {
    val name = when (month.month.month) {
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
        else -> month.month.month.toString()
    }
    return "$name ${month.month.year}"
}

/** Compact month label for the pager navigation hints, e.g. "Mai 2026". */
private fun formatMonthShort(month: MonthUsage): String {
    val name = when (month.month.month) {
        Month.JANUARY -> "Jan"
        Month.FEBRUARY -> "Feb"
        Month.MARCH -> "Mär"
        Month.APRIL -> "Apr"
        Month.MAY -> "Mai"
        Month.JUNE -> "Jun"
        Month.JULY -> "Jul"
        Month.AUGUST -> "Aug"
        Month.SEPTEMBER -> "Sep"
        Month.OCTOBER -> "Okt"
        Month.NOVEMBER -> "Nov"
        Month.DECEMBER -> "Dez"
        else -> month.month.month.toString()
    }
    return "$name ${month.month.year}"
}
