# Skill: Verifying on Android TV

**When to use:** after a change, to manually verify PIXELTV behavior on an Android TV / emulator — D-pad navigation, native player, stream-sniff, and WebView fallback. The Activities aren't unit-tested, so this is how "it works" gets confirmed honestly.

Before claiming a feature works, run it and observe. Report what you actually saw — if you couldn't test something, say so.

## Build & install

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.streamtv.app/.SplashActivity
```

If no device is attached (`adb devices` empty), start an Android TV emulator (a leanback AVD) or ask the user to connect a device. Don't claim a manual pass without a device.

## D-pad-only rule

Drive everything with the **remote/arrow keys**, never touch. If a control can't be reached or activated with D-pad Up/Down/Left/Right + Center, it's a bug.

```powershell
adb shell input keyevent 19   # DPAD_UP
adb shell input keyevent 20   # DPAD_DOWN
adb shell input keyevent 21   # DPAD_LEFT
adb shell input keyevent 22   # DPAD_RIGHT
adb shell input keyevent 23   # DPAD_CENTER (OK)
adb shell input keyevent 4    # BACK
adb shell input keyevent 82   # MENU (overlay)
```

## Core verification checklist

**Navigation / focus**
- [ ] Splash → Home. Home shows `▶ Mulai Streaming` and `⚙️ Settings` (no 3-site grid). Both focusable and visibly highlight.
- [ ] D-pad reaches every card; OK activates the focused one.

**Settings**
- [ ] `⚙️ Settings` opens the form; endpoint pre-filled.
- [ ] Invalid URL (`notaurl`) → Save → rejected with a toast, value unchanged.
- [ ] Valid `https://...` → Save → returns; `Mulai Streaming` loads the new site.
- [ ] Toggle auto-sniff off → no pill appears while browsing (pure WebView).

**Stream sniff → native player (the headline feature)**
- [ ] Browse the endpoint, open a movie, start its player.
- [ ] The `▶ Putar tanpa iklan` pill appears at bottom-center within a few seconds.
- [ ] OK on the pill → `PlayerActivity` opens and plays the video **with no ads/popups**.
- [ ] In the player: OK = play/pause, Left/Right = seek, Back = return to the WebView on the same page.

**Fallback (must never dead-end)**
- [ ] A DRM/`blob:` title (no `.m3u8`) → no pill → the WebView player still plays (ad-blocked). No crash, no black screen.
- [ ] Force a player error (stale/expired stream) → toast + return to WebView, not a frozen black screen.

**Watch the logs while testing**
```powershell
adb logcat -s PIXELTV ExoPlayerImpl AndroidRuntime
```
- `AndroidRuntime` crashes = 🔴 stop and fix.
- ExoPlayer `403`/source errors on a stream that should play = a dropped replay header (see [implementing-stream-features](implementing-stream-features.md)).

## Reporting

State plainly: which checklist items passed, which failed (with the logcat line), and which you couldn't run (e.g., "no DRM title available to test fallback"). Don't round a partial test up to "works".
