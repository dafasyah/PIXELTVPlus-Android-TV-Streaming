package com.streamtv.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsManagerTest {

    private class FakeStore : KeyValueStore {
        val strings = HashMap<String, String>()
        val booleans = HashMap<String, Boolean>()

        override fun getString(key: String, def: String): String = strings[key] ?: def

        override fun getBoolean(key: String, def: Boolean): Boolean = booleans[key] ?: def

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
    }

    private lateinit var settings: SettingsManager

    @Before
    fun setUp() {
        settings = SettingsManager(FakeStore())
    }

    @Test
    fun defaultsAreApplied() {
        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl)
        assertTrue(settings.autoSniff)
        assertEquals(SettingsManager.DEFAULT_UA, settings.userAgent)
    }

    @Test
    fun setValidHttpsEndpointPersists() {
        assertTrue(settings.setEndpoint("https://newsite.example"))

        assertEquals("https://newsite.example", settings.endpointUrl)
    }

    @Test
    fun setValidHttpEndpointPersists() {
        assertTrue(settings.setEndpoint("http://newsite.example"))

        assertEquals("http://newsite.example", settings.endpointUrl)
    }

    @Test
    fun trimsEndpointWhitespace() {
        assertTrue(settings.setEndpoint("  https://trim.example  "))

        assertEquals("https://trim.example", settings.endpointUrl)
    }

    @Test
    fun rejectsInvalidEndpoint() {
        assertFalse(settings.setEndpoint("ftp://bad.example"))
        assertFalse(settings.setEndpoint("notaurl"))
        assertFalse(settings.setEndpoint("https:///missing-host"))
        assertFalse(settings.setEndpoint(""))

        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl)
    }

    @Test
    fun autoSniffTogglePersists() {
        settings.autoSniff = false

        assertFalse(settings.autoSniff)
    }

    @Test
    fun resetRestoresDefaults() {
        settings.setEndpoint("https://other.example")
        settings.autoSniff = false

        settings.resetToDefaults()

        assertEquals(SettingsManager.DEFAULT_ENDPOINT, settings.endpointUrl)
        assertTrue(settings.autoSniff)
    }
}
