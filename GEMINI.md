# GEMINI.md — PIXELTV

This project's AI guide is vendor-neutral. **Read [`AGENTS.md`](AGENTS.md) first**, then the [`ai/`](ai/README.md) folder (overview, architecture, conventions, task list, planning).

In short: PIXELTV is a hybrid Android TV app — browse in a WebView, capture (sniff) the `.m3u8`/`.mpd`/`.mp4` stream from the page, and play it in a **native Media3 player** (ad-free), with the WebView kept as an automatic fallback. Single configurable endpoint via a Settings screen.

- **Hard rules & build commands:** [`AGENTS.md`](AGENTS.md)
- **Current work + task status:** [`ai/tasklist.md`](ai/tasklist.md)
- **Exact step-by-step plan (with code):** [`ai/planning/implementation-plan.md`](ai/planning/implementation-plan.md)
