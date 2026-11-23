package app.guitarnest.feedback

import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import app.guitarnest.report.Reports
import java.io.File
import java.security.SecureRandom

/** Reports sent from this phone (title, date, follow-up code) and the automatic crash report setting. */
class ReportStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "sent_reports.json"))
    private val prefs = context.getSharedPreferences("reports", Context.MODE_PRIVATE)

    fun sent(): List<Reports.Sent> = runCatching { Reports.decodeSent(file.readFully().decodeToString()) }.getOrDefault(emptyList())

    fun add(entry: Reports.Sent) {
        val out = file.startWrite()
        try {
            out.write(Reports.encodeSent(listOf(entry) + sent()).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    /** Off by default: crashes are only sent when the player turns this on. */
    var autoCrash: Boolean
        get() = prefs.getBoolean(AUTO, false)
        set(value) = prefs.edit { putBoolean(AUTO, value) }

    companion object {
        private const val AUTO = "auto_crash"

        /** 32 random hex characters: the follow-up code that only this phone knows. */
        fun newCode(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    }
}
