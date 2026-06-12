package com.streamtv.app.data

/**
 * Legacy default endpoint. The active endpoint is now managed by
 * [SettingsManager]; this only provides the first-run default value.
 */
object SiteManager {
    const val DEFAULT_URL = SettingsManager.DEFAULT_ENDPOINT
}
