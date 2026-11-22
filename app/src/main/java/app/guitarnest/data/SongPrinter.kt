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
        manager.print(song.title, Adapter(context, TextTab.printLines(song)), null)
    }

    private class Adapter(private val context: Context, private val lines: List<String>) : PrintDocumentAdapter() {
        private var attributes: PrintAttributes? = null
        private var pages: List<List<String>> = emptyList()

        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancel: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
            attributes = new
            val heightMils = new.mediaSize?.heightMils ?: 11_690
            val linesPerPage = ((heightMils / 1000.0 * 72 - 2 * MARGIN) / LINE_HEIGHT).toInt().coerceAtLeast(10)
            pages = paginate(linesPerPage)
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder("tab.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages.size).build(),
                true,
            )
        }

        /** Keeps blocks (chord line + six strings) together on a page. */
        private fun paginate(perPage: Int): List<List<String>> {
            val blocks = mutableListOf<MutableList<String>>(mutableListOf())
            for (line in lines) {
                blocks.last() += line
                if (line.isEmpty()) blocks += mutableListOf<String>()
            }
            val result = mutableListOf(mutableListOf<String>())
            for (block in blocks) {
                if (result.last().size + block.size > perPage && result.last().isNotEmpty()) result += mutableListOf<String>()
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

    // In points (1/72 inch). 80 columns of 7.5 pt monospace fit on A4 and Letter.
    private const val MARGIN = 36f
    private const val FONT_SIZE = 7.5f
    private const val LINE_HEIGHT = 9.5f
}
