package app.guitarnest.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit

/** The player's own AI connection. Off until the player sets it up. */
data class AiConnection(val baseUrl: String, val model: String, val apiKey: String) {
    val configured: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
    /** Plain HTTP is only fine inside a home network (for example a local model server). */
    val unencrypted: Boolean get() = baseUrl.trim().startsWith("http://", ignoreCase = true)
}

/** Stores the connection; the API key is encrypted with a key that never leaves the Android Keystore. */
class AiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher()

    fun load(): AiConnection {
        val key = prefs.getString(KEY, null)?.let { stored ->
            runCatching { cipher.decrypt(Base64.decode(stored, Base64.NO_WRAP)).decodeToString() }.getOrDefault("")
        } ?: ""
        return AiConnection(prefs.getString(URL, "") ?: "", prefs.getString(MODEL, "") ?: "", key)
    }

    fun save(connection: AiConnection) = prefs.edit {
        putString(URL, connection.baseUrl.trim().trimEnd('/'))
        putString(MODEL, connection.model.trim())
        if (connection.apiKey.isBlank()) remove(KEY)
        else putString(KEY, Base64.encodeToString(cipher.encrypt(connection.apiKey.trim().toByteArray()), Base64.NO_WRAP))
    }

    fun clear() {
        prefs.edit { clear() }
        cipher.deleteKey()
    }

    private companion object {
        const val URL = "base_url"
        const val MODEL = "model"
        const val KEY = "api_key"
    }
}
