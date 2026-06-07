package dev.treder.info.claude.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.treder.info.claude.domain.model.PeriodUsage
import dev.treder.info.claude.domain.model.TokenUsage

@Composable
fun PeriodSection(
    title: String,
    period: PeriodUsage?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (period?.total != null) formatUsd(period.total!!.cost) else "—",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (period == null) {
            Text(
                text = "Lade …",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            return@Column
        }

        if (period.total != null) {
            TokenGrid(period.total!!)
        }
    }
}

@Composable
private fun TokenGrid(usage: TokenUsage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TokenCell("Input", usage.inputTokens)
        TokenCell("Output", usage.outputTokens)
        TokenCell("Cache Write", usage.cacheWriteTokens)
        TokenCell("Cache Read", usage.cacheReadTokens)
        TokenCell(
            label = "Gesamt",
            value = usage.inputTokens + usage.outputTokens + usage.cacheWriteTokens + usage.cacheReadTokens,
        )
    }
}

@Composable
private fun TokenCell(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            text = formatTokens(value),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
