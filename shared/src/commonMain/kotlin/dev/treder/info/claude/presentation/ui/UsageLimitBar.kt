package dev.treder.info.claude.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.treder.info.claude.domain.model.UsageLimit
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/** Tint for a window that is nearly exhausted — reuses the app's existing problem color. */
private val NearLimitColor = Color(0xFFFFB4A2)

/** The stack of usage-limit bars rendered above the statistics. */
@Composable
fun UsageLimitsSection(limits: List<UsageLimit>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        limits.forEach { UsageLimitBar(it) }
    }
}

@Composable
private fun UsageLimitBar(limit: UsageLimit) {
    val fraction = limit.used.coerceIn(0f, 1f)
    val accent = if (fraction >= 0.9f) NearLimitColor else MaterialTheme.colorScheme.primary
    val zone = remember { TimeZone.currentSystemDefault() }
    val now = remember { Clock.System.now() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = limit.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Wird ${formatReset(limit.resetAt, now, zone)} zurückgesetzt",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = formatPercent(fraction.toDouble()),
                color = accent,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.outline),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(accent),
                )
            }
        }
    }
}
