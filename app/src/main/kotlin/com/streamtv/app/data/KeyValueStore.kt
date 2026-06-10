package com.streamtv.app.data

import android.content.Context

/** Seam penyimpanan kecil agar SettingsManager bisa diuji tanpa runtime Android. */
interface KeyValueStore {
    fun getString(key: String, def: String): String
    fun getBoolean(key: String, def: Boolean): Boolean
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
}

/** Implementasi produksi berbasis SharedPreferences. */
class PrefsKeyValueStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("pixeltv_prefs", Context.MODE_PRIVATE)

    override fun getString(key: String, def: String): String = prefs.getString(key, def) ?: def

    override fun getBoolean(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}
