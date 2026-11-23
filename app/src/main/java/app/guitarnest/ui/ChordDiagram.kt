package app.guitarnest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.guitarnest.tab.ChordShape

/**
 * A chord diagram: six strings (low E on the left), five frets, dots for fingers, a bar for a barre,
 * x / o above the strings. [highlight] (string index) lights up the string to pick now.
 */
@Composable
fun ChordDiagram(name: String, shape: ChordShape?, width: Dp, highlight: Int? = null, active: Boolean = false, note: String? = null) {
    val ink = MaterialTheme.colorScheme.onSurface
    val faint = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    val pick = MaterialTheme.colorScheme.tertiary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { contentDescription = "$name ${shape ?: ""}" }) {
        Text(name, fontWeight = FontWeight.ExtraBold, fontSize = (width.value / 4).coerceIn(13f, 26f).sp, color = if (active) accent else ink)
        if (shape == null) {
            Text("?", color = faint)
            return@Column
        }
        Canvas(Modifier.size(width, width * 1.15f)) {
            val first = shape.firstFret
            val left = size.width * 0.16f
            val right = size.width * 0.92f
            val top = size.height * 0.16f
            val bottom = size.height * 0.98f
            val gapX = (right - left) / 5
            val gapY = (bottom - top) / 5
            val line = (size.width / 70f).coerceAtLeast(1.2f)
            // Strings and frets; a thick nut at fret 1, else the first fret number on the left.
            for (s in 0 until 6) {
                val x = left + s * gapX
                val lit = highlight == s
                drawLine(if (lit) pick else ink, Offset(x, top), Offset(x, bottom), strokeWidth = if (lit) line * 3 else line)
            }
            for (f in 0..5) {
                val y = top + f * gapY
                drawLine(ink, Offset(left, y), Offset(right, y), strokeWidth = if (f == 0 && first == 1) line * 4 else line)
            }
            if (first > 1) {
                drawContext.canvas.nativeCanvas.drawText("$first", 0f, top + gapY * 0.7f,
                    android.graphics.Paint().apply { color = android.graphics.Color.GRAY; textSize = gapY * 0.6f; isAntiAlias = true })
            }
            val r = gapX * 0.34f
            shape.barre?.let { (fret, from, to) ->
                val y = top + (fret - first + 0.5f) * gapY
                drawRoundRect(accent, Offset(left + from * gapX - r, y - r), Size((to - from) * gapX + 2 * r, 2 * r), CornerRadius(r, r))
            }
            shape.frets.forEachIndexed { s, fret ->
                val x = left + s * gapX
                val markY = top - gapY * 0.45f
                when {
                    fret < 0 -> {
                        val d = r * 0.7f
                        drawLine(faint, Offset(x - d, markY - d), Offset(x + d, markY + d), strokeWidth = line * 1.5f)
                        drawLine(faint, Offset(x - d, markY + d), Offset(x + d, markY - d), strokeWidth = line * 1.5f)
                    }
                    fret == 0 -> drawCircle(if (highlight == s) pick else ink, r * 0.7f, Offset(x, markY), style = Stroke(line * 1.5f))
                    else -> drawCircle(if (highlight == s) pick else accent, r, Offset(x, top + (fret - first + 0.5f) * gapY))
                }
            }
        }
        if (note != null) Text(note, style = MaterialTheme.typography.labelSmall, color = faint)
    }
}

