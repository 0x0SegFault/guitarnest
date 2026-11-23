package app.guitarnest.net

import app.guitarnest.report.Reports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Sends an anonymous report to the report service (see [Reports]). No account, no IP stored by the service. */
object ReportClient {
    suspend fun send(kind: String, title: String, details: String, version: String, device: String) = withContext(Dispatchers.IO) {
        val base = Reports.SERVICE_URL?.trimEnd('/') ?: throw AiException("The report service is not set up yet.")
        try {
            val challenge = Reports.parseChallenge(get("$base/challenge"))
            val proof = withContext(Dispatchers.Default) { Reports.solve(challenge) } ?: throw AiException("Could not solve the anti-spam puzzle.")
            val http = (URL("$base/report").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                http.outputStream.use { it.write(Reports.body(kind, title, details, version, device, proof).toByteArray()) }
                if (http.responseCode != 200) throw AiException("The report service answered ${http.responseCode}.")
            } finally {
                http.disconnect()
            }
        } catch (e: IOException) {
            throw AiException("Cannot reach the report service: ${e.message}")
        }
    }

    private fun get(url: String): String {
        val http = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 15_000 }
        try {
            if (http.responseCode != 200) throw AiException("The report service answered ${http.responseCode}.")
            return http.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            http.disconnect()
        }
    }
}
