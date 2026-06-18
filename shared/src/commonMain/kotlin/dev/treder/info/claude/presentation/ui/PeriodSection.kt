package dev.treder.info.claude.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.treder.info.claude.domain.model.PeriodUsage
import dev.treder.info.claude.domain.model.TokenUsage
import kotlin.math.roundToInt

@Composable
fun PeriodSection(
    title: String,
    period: PeriodUsage?,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val models = period?.byModel.orEmpty()
    val showTooltip = hovered && models.isNotEmpty()

    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var costCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Lift the whole section above its siblings while the tooltip is open
            // so the overlay isn't drawn behind the following sections.
            .zIndex(if (showTooltip) 1f else 0f)
            .onGloballyPositioned { boxCoords = it },
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
                    modifier = Modifier
                        .hoverable(interactionSource)
                        .onGloballyPositioned { costCoords = it },
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

        if (showTooltip) {
            val box = boxCoords
            val cost = costCoords
            var anchorRect: Rect? = null
            var boxTopInRoot = 0f
            var availableHeight = 0
            if (box != null && cost != null && box.isAttached && cost.isAttached) {
                anchorRect = box.localBoundingBoxOf(cost, clipBounds = false)
                boxTopInRoot = box.localToRoot(Offset.Zero).y
                availableHeight = box.findRootCoordinates().size.height
            }
            ModelCostTooltip(
                models = models,
                totalCost = period?.total?.cost ?: models.sumOf { it.cost },
                anchorRect = anchorRect,
                boxTopInRoot = boxTopInRoot,
                availableHeight = availableHeight,
            )
        }
    }
}

@Composable
private fun ModelCostTooltip(
    models: List<TokenUsage>,
    totalCost: Double,
    anchorRect: Rect?,
    boxTopInRoot: Float,
    availableHeight: Int,
) {
    val sorted = remember(models) {
        models.sortedWith(
            compareByDescending<TokenUsage> { it.cost }
                .thenByDescending { it.inputTokens + it.outputTokens + it.cacheWriteTokens + it.cacheReadTokens },
        )
    }
    val gapPx = with(LocalDensity.current) { 4.dp.roundToPx() }

    Box(
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                // Zero-size node: the tooltip overlays its siblings without
                // affecting the section's measured height (and thus the popup size).
                layout(0, 0) {
                    val rect = anchorRect ?: return@layout
                    val maxX = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
                    val x = (rect.right - placeable.width).toInt().coerceIn(0, maxX)

                    // Drop below the cost by default; flip above when it would
                    // overflow the bottom of the window (e.g. the last month),
                    // then clamp so it always stays fully visible.
                    val yLocal = if (availableHeight <= 0) {
                        rect.bottom + gapPx
                    } else {
                        val below = boxTopInRoot + rect.bottom + gapPx
                        val above = boxTopInRoot + rect.top - gapPx - placeable.height
                        val yRoot = when {
                            below + placeable.height <= availableHeight -> below
                            above >= 0f -> above
                            else -> below
                        }
                        val maxYRoot = (availableHeight - placeable.height).coerceAtLeast(0).toFloat()
                        yRoot.coerceIn(0f, maxYRoot) - boxTopInRoot
                    }
                    placeable.place(x, yLocal.roundToInt())
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.97f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Kosten je Modell",
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            sorted.forEach { usage ->
                ModelCostRow(usage = usage, totalCost = totalCost)
            }
        }
    }
}

@Composable
private fun ModelCostRow(usage: TokenUsage, totalCost: Double) {
    val share = if (totalCost > 0.0) usage.cost / totalCost else Double.NaN
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatModelName(usage.model),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (!share.isNaN()) {
            Text(
                text = formatPercent(share),
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            text = formatUsd(usage.cost),
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
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
