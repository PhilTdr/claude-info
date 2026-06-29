package dev.treder.info.claude.presentation.ui

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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                text = "Nutzungslimits werden von der Claude API abgerufen. Die Statistiken stammen ausschließlich aus Claude-Sessions auf diesem Gerät.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
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
        else -> {
            history.forEachIndexed { index, month ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
                PeriodSection(
                    title = formatMonth(month),
                    period = month,
                )
                DailyUsageBarChart(month = month)
            }
        }
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
    var iconCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val showTooltip = warningText != null && hovered

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (showTooltip) 1f else 0f)
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
            if (warningText != null) {
                Text(
                    text = "⚠",
                    color = Color(0xFFFFB4A2),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .pointerHoverIcon(PointerIcon.Default)
                        .hoverable(interactionSource)
                        .onGloballyPositioned { iconCoords = it },
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

        if (hovered) {
            warningText?.let { text ->
                val header = headerCoords
                val icon = iconCoords
                val anchorRect: Rect? =
                    if (header != null && icon != null && header.isAttached && icon.isAttached) {
                        header.localBoundingBoxOf(icon, clipBounds = false)
                    } else {
                        null
                    }
                LimitsWarningTooltip(anchorRect = anchorRect, text = text)
            }
        }
    }
}

/** Hover hint for the header warning icon, dropped just below it. */
@Composable
private fun LimitsWarningTooltip(anchorRect: Rect?, text: String) {
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
