package dev.treder.info.claude.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.treder.info.claude.domain.model.MonthUsage
import dev.treder.info.claude.presentation.PricingPhase
import dev.treder.info.claude.presentation.UsageUiState
import dev.treder.info.claude.presentation.UsageViewModel
import kotlinx.datetime.Month

@Composable
fun UsageDashboard(
    viewModel: UsageViewModel,
    onContentSizeChanged: (IntSize) -> Unit = {},
    backgroundColor: Color? = null,
    onClose: () -> Unit = {},
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
            Header(preferredModel = state.preferredModel, onClose = onClose)

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            when {
                state.pricingPhase == PricingPhase.Failed -> PricingErrorContent(onRetry = viewModel::retryPricing)
                state.showDashboard -> UsageContent(state)
                else -> LoadingContent()
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Text(
                text = "Statistiken stammen ausschließlich aus Claude-Sessions auf diesem Gerät.",
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
private fun Header(preferredModel: String?, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Claude Info",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (!preferredModel.isNullOrBlank()) {
                Text(
                    text = "Bevorzugtes Modell: $preferredModel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
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
