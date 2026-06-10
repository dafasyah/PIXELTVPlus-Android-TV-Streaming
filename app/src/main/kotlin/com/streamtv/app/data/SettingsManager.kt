package com.streamtv.app.data

import android.content.Context
import java.net.URI

/**
 * Sumber kebenaran tunggal untuk endpoint streaming dan opsi native player.
 * Logikanya hanya bergantung pada [KeyValueStore] agar tetap unit-testable.
 */
class SettingsManager(private val store: KeyValueStore) {

    var endpointUrl: String
        get() = store.getString(KEY_ENDPOINT, DEFAULT_ENDPOINT)
        set(value) = store.putString(KEY_ENDPOINT, value)

    var autoSniff: Boolean
        get() = store.getBoolean(KEY_AUTOSNIFF, true)
        set(value) = store.putBoolean(KEY_AUTOSNIFF, value)

    var userAgent: String
        get() = store.getString(KEY_UA, DEFAULT_UA)
        set(value) = store.putString(KEY_UA, value)

    /** Simpan endpoint hanya jika URL valid dan memakai http(s). */
    fun setEndpoint(url: String): Boolean {
        val trimmed = url.trim()
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            return false
        }
        if (uri.scheme != "http" && uri.scheme != "https") return false
        if (uri.host.isNullOrBlank()) return false

        endpointUrl = trimmed
        return true
    }

    fun resetToDefaults() {
        endpointUrl = DEFAULT_ENDPOINT
        autoSniff = true
        userAgent = DEFAULT_UA
    }

    companion object {
        private const val KEY_ENDPOINT = "endpoint_url"
        private const val KEY_AUTOSNIFF = "auto_sniff"
        private const val KEY_UA = "user_agent"

        const val DEFAULT_ENDPOINT = "https://z1.idlixku.com"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun from(context: Context): SettingsManager = SettingsManager(PrefsKeyValueStore(context))
    }
}
