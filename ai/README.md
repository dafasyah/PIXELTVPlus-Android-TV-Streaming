# `ai/` — Project Brain for AI Agents

This folder is a **vendor-neutral knowledge base** about PIXELTV, meant to make *any* AI model/agent productive on this repo immediately after `git clone`. Everything here is plain Markdown — no tool-specific format required.

> **Start at the repo root [`AGENTS.md`](../AGENTS.md)** for the 1-page entry point. This folder holds the depth.

## Contents

| File | What it is |
|---|---|
| [`project-overview.md`](project-overview.md) | What PIXELTV is, the goal of the v3 upgrade, current status |
| [`architecture.md`](architecture.md) | Hybrid model, components, data flow, fallback matrix |
| [`conventions.md`](conventions.md) | Hard rules + code style (the canonical version) |
| [`tasklist.md`](tasklist.md) | ⭐ The planned task list (Task 0–8) with live status |
| [`roadmap.md`](roadmap.md) | Future ideas beyond v3.0 |
| [`build-and-test.md`](build-and-test.md) | How to build/test from the CLI |
| [`planning/design-spec.md`](planning/design-spec.md) | Full approved design spec |
| [`planning/implementation-plan.md`](planning/implementation-plan.md) | Full TDD plan with exact code per step |
| [`skills/`](skills/) | Reusable how-to guides (workflows) for this project |
| [`agents/`](agents/) | Reusable AI "role" prompts (reviewer, build runner) |

## How to onboard yourself (any AI agent)

1. Read [`AGENTS.md`](../AGENTS.md) → [`project-overview.md`](project-overview.md) → [`architecture.md`](architecture.md).
2. Internalize [`conventions.md`](conventions.md) — especially the 5 hard rules. Breaking them silently breaks playback.
3. To continue the build, open [`tasklist.md`](tasklist.md), find the next unchecked task, then follow the exact steps in [`planning/implementation-plan.md`](planning/implementation-plan.md).
4. When touching stream/player code, read [`skills/implementing-stream-features.md`](skills/implementing-stream-features.md). To verify on-device, read [`skills/verifying-on-android-tv.md`](skills/verifying-on-android-tv.md).

## Relationship to tool-specific files

This `ai/` folder is the **single source of truth**. Tool-native integrations mirror or point to it:

- **Root entry:** `AGENTS.md` (read by most agent tools incl. Codex/Cursor) — canonical pointers.
- **Claude Code:** `CLAUDE.md` imports `AGENTS.md`; `.claude/skills/` and `.claude/agents/` are Claude-native copies of [`skills/`](skills/) and [`agents/`](agents/) (same content, with the loader frontmatter Claude needs). Keep them in sync with this folder.
- **Gemini / Cursor:** `GEMINI.md` and `.cursorrules` point here.

If you change a rule or workflow, update the version in **this folder** and mirror it to `.claude/` if the Claude-native copy exists.
