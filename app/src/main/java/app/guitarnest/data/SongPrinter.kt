package app.guitarnest.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import app.guitarnest.tab.ChordShape
import app.guitarnest.tab.ChordShapes
import app.guitarnest.tab.PlayAlong
import app.guitarnest.tab.Song
import app.guitarnest.tab.TextTab
import java.io.FileOutputStream

/**
 * Prints a tab, or saves it as PDF, with the Android print service. No permission needed.
 * Text only, in a monospace font, so the tab columns stay aligned.
 */
object SongPrinter {
    fun print(context: Context, song: Song) {
        val manager = context.getSystemService(PrintManager::class.java)
        val chords = PlayAlong.chordsInSong(song).map { it to ChordShapes.shapeFor(song, it) }
        manager.print(song.title, Adapter(context, TextTab.printLines(song), chords, context.getString(app.guitarnest.R.string.chords_in_song)), null)
    }

    private class Adapter(
        private val context: Context,
        private val lines: List<String>,
        private val chords: List<Pair<String, ChordShape?>>,
        private val chordsTitle: String,
    ) : PrintDocumentAdapter() {
        private var attributes: PrintAttributes? = null
        private var pages: List<List<String>> = emptyList()

        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
            attributes = new
            val heightMils = new.mediaSize?.heightMils ?: 11_690
            val linesPerPage = ((heightMils / 1000.0 * 72 - 2 * MARGIN) / LINE_HEIGHT).toInt().coerceAtLeast(10)
            // The first page starts with the chord diagrams; they take the place of some text lines.
            pages = paginate(linesPerPage, ((diagramRows() * DIAGRAM_ROW) / LINE_HEIGHT).toInt())
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder("tab.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages.size).build(),
                true,
            )
        }

        /** Keeps blocks (chord line + six strings) together on a page. */
        private fun diagramRows(): Int = if (chords.isEmpty()) 0 else (chords.size + PER_ROW - 1) / PER_ROW

        private fun paginate(perPage: Int, firstPageReserved: Int): List<List<String>> {
            val blocks = mutableListOf<MutableList<String>>(mutableListOf())
            for (line in lines) {
                blocks.last() += line
                if (line.isEmpty()) blocks += mutableListOf<String>()
            }
            val result = mutableListOf(mutableListOf<String>())
            for (block in blocks) {
                val room = if (result.size == 1) perPage - firstPageReserved else perPage
                if (result.last().size + block.size > room && result.last().isNotEmpty()) result += mutableListOf<String>()
                result.last() += block
            }
            return result.filter { it.isNotEmpty() }
        }

        override fun onWrite(range: Array<out PageRange>, destination: ParcelFileDescriptor, cancel: CancellationSignal, callback: WriteResultCallback) {
            val document = PrintedPdfDocument(context, attributes ?: return callback.onWriteFailed(null))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.MONOSPACE
                textSize = FONT_SIZE
            }
            val bold = Paint(paint).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
            try {
                pages.forEachIndexed { index, pageLines ->
                    if (cancel.isCanceled) return callback.onWriteCancelled()
                    val page = document.startPage(index)
                    var y = MARGIN + FONT_SIZE
                    if (index == 0 && chords.isNotEmpty()) {
                        // Title first, then the diagrams, then the rest of the text.
                        page.canvas.drawText(pageLines.firstOrNull() ?: "", MARGIN, y, bold)
                        y += LINE_HEIGHT * 1.5f
                        page.canvas.drawText(chordsTitle, MARGIN, y, bold)
                        chords.forEachIndexed { i, (name, shape) ->
                            drawDiagram(page.canvas, name, shape, MARGIN + (i % PER_ROW) * DIAGRAM_WIDTH, y + 6 + (i / PER_ROW) * DIAGRAM_ROW)
                        }
                        y += diagramRows() * DIAGRAM_ROW + 6
                        pageLines.drop(1).forEach { line -> page.canvas.drawText(line, MARGIN, y, paint); y += LINE_HEIGHT }
                        document.finishPage(page)
                        return@forEachIndexed
                    }
                    pageLines.forEachIndexed { i, line ->
                        page.canvas.drawText(line, MARGIN, y, if (index == 0 && i == 0) bold else paint)
                        y += LINE_HEIGHT
                    }
                    document.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } finally {
                document.close()
            }
        }
    }

    /** A chord diagram in points: name, 6 strings, 5 frets, dots, barre, x / o. */
    private fun drawDiagram(canvas: android.graphics.Canvas, name: String, shape: ChordShape?, x: Float, y: Float) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.7f; textSize = 9f; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText(name, x + 4, y + 9, ink)
        if (shape == null) return
        val left = x + 10
        val top = y + 20
        val gap = 7f
        val first = shape.firstFret
        for (s in 0 until 6) canvas.drawLine(left + s * gap, top, left + s * gap, top + 5 * gap, ink)
        for (f in 0..5) {
            ink.strokeWidth = if (f == 0 && first == 1) 2f else 0.7f
            canvas.drawLine(left, top + f * gap, left + 5 * gap, top + f * gap, ink)
        }
        ink.strokeWidth = 0.7f
        val small = Paint(ink).apply { textSize = 6f; typeface = Typeface.DEFAULT }
        if (first > 1) canvas.drawText("$first", x, top + gap * 0.8f, small)
        shape.barre?.let { (fret, from, to) ->
            val cy = top + (fret - first + 0.5f) * gap
            canvas.drawRoundRect(left + from * gap - 2.5f, cy - 2.5f, left + to * gap + 2.5f, cy + 2.5f, 2.5f, 2.5f, ink)
        }
        shape.frets.forEachIndexed { s, fret ->
            val cx = left + s * gap
            when {
                fret < 0 -> canvas.drawText("x", cx - 1.8f, top - 2, small)
                fret == 0 -> canvas.drawText("o", cx - 1.8f, top - 2, small)
                else -> canvas.drawCircle(cx, top + (fret - first + 0.5f) * gap, 2.6f, ink)
            }
        }
    }

    private const val PER_ROW = 8
    private const val DIAGRAM_WIDTH = 62f
    private const val DIAGRAM_ROW = 64f

    // In points (1/72 inch). 80 columns of 7.5 pt monospace fit on A4 and Letter.
    private const val MARGIN = 36f
    private const val FONT_SIZE = 7.5f
    private const val LINE_HEIGHT = 9.5f
}
