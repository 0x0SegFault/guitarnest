package app.guitarnest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitarnest.tab.Bar
import app.guitarnest.tab.StrumPattern
import app.guitarnest.tab.TextTab

/** Strumming arrows; the [active] step is highlighted while playing. */
@Composable
fun StrumRow(pattern: StrumPattern, modifier: Modifier = Modifier, active: Int? = null, big: Boolean = false) {
    val description = pattern.name + ": " + pattern.steps.map {
        when (it) { 'D' -> "down"; 'U' -> "up"; 'x' -> "mute"; else -> "rest" }
    }.joinToString(" ")
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(if (big) 6.dp else 2.dp)) {
        pattern.steps.forEachIndexed { i, c ->
            val on = i == active
            val onBeat = i % pattern.perBeat == 0
            Box(
                Modifier.size(if (big) 34.dp else 18.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val tint = when {
                    on -> MaterialTheme.colorScheme.onPrimary
                    c == '-' -> MaterialTheme.colorScheme.outline
                    onBeat -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                when (c) {
                    'D', 'U' -> Icon(
                        if (c == 'D') Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null,
                        tint = tint, modifier = Modifier.size(if (big) 26.dp else 15.dp),
                    )
                    else -> Text(if (c == 'x') "×" else "·", fontSize = if (big) 22.sp else 13.sp, fontWeight = FontWeight.Bold, color = tint)
                }
            }
        }
    }
}

/** One bar: chords, the six strings (if the bar has notes) and its strumming pattern. */
@Composable
fun BarCard(bar: Bar, beatsPerBar: Int, pattern: StrumPattern?, active: Boolean, activeStep: Int?, number: Int) {
    val border = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(if (active) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            val chords = TextTab.chordCells(bar, beatsPerBar)
            if (bar.chords.isNotEmpty()) {
                chords.forEach { c ->
                    Text(if (c == ".") "·" else c, style = MaterialTheme.typography.titleMedium,
                        color = if (c == ".") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (bar.beats.isNotEmpty()) {
            Text(
                TextTab.barLines(bar, beatsPerBar).joinToString("\n"),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (pattern != null) StrumRow(pattern, active = if (active) activeStep else null)
    }
}
