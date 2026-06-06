# CLAUDE.md — PIXELTV

The canonical, vendor-neutral project guide lives in **[`AGENTS.md`](AGENTS.md)** and the **[`ai/`](ai/README.md)** folder. Read those first — this file imports `AGENTS.md` and adds Claude-Code-specific notes.

@AGENTS.md

## Claude Code specifics

- **Project skills** (`.claude/skills/`): `implementing-stream-features`, `verifying-on-android-tv` — Claude-native copies of [`ai/skills/`](ai/skills/).
- **Project agents** (`.claude/agents/`): `android-tv-reviewer`, `gradle-runner` — Claude-native copies of [`ai/agents/`](ai/agents/).
- **Hook** (`.claude/settings.json` + `.claude/hooks/pixeltv-guard.ps1`): PostToolUse guard that warns if `stream/StreamSniffer|MediaStream` import `android.*`, and reminds to bump the version when `app/build.gradle.kts` changes. (Activates after `/hooks` or a restart.)
- To continue the build, use `superpowers:subagent-driven-development` against [`ai/planning/implementation-plan.md`](ai/planning/implementation-plan.md); status in [`ai/tasklist.md`](ai/tasklist.md).
- If you change a rule/workflow, update the canonical version in `ai/` and keep the `.claude/` copy in sync.
