# Roadmap — Beyond v3.0

Ideas captured during design but **out of scope for v3.0**. Listed roughly by value. None of these are started; pick them up after the core (Tasks 1–8) lands.

1. **🔄 Remote config for the endpoint.** Streaming-site domains die/change often. Store the active URL in a tiny server or Firebase Remote Config so you can push a new domain **without shipping a new APK**. Highest practical value for an app like this.
2. **🎚️ Quality & subtitle selection.** Resolution picker for HLS multi-variant streams + WebVTT subtitle track selection in the native player.
3. **⏯️ Resume playback.** Persist last position per title (in `HistoryManager`) and resume on reopen.
4. **📡 Chromecast / Google Cast.** Cast the sniffed `.m3u8` to a TV/other device.
5. **🖼️ Picture-in-Picture** (phone/tablet) + **playback speed** + **multi-audio track** selection.
6. **⬇️ Offline download.** HLS download via Media3 `DownloadManager`.
7. **🎨 UI migration to Jetpack Compose for TV** (`androidx.tv:tv-material`) — the current-era direction for Android TV UIs.
8. **🧠 Metadata enrichment.** TMDB posters/synopsis, unified search, and recommendations.

## Notes for whoever implements these

- Keep the **fallback-always** and **header-replay** rules (see [`conventions.md`](conventions.md)) — they apply to every playback feature.
- Cast, PiP, and downloads all build on the same `MediaStream` (url + type + headers) the sniffer already produces, so they slot in after Tasks 2/4 are done.
- Remote config pairs naturally with the existing `SettingsManager` — make the remote value the default, still overridable in Settings.
