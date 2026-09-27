# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root: the domain glossary.
- **`docs/DECISIONS.md`**: this repo's ADR log, one dated entry per decision, append-only. Read the entries that touch the area you're about to work in. `AGENTS.md` is the other must-read: it carries the rules those decisions produced.

If `CONTEXT.md` doesn't exist yet, **proceed silently**. Don't flag its absence; don't suggest creating it upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and `/improve-codebase-architecture`) creates it lazily when terms actually get resolved.

## File structure

Single-context repo:

```
/
├── AGENTS.md             ← rules and architecture (CLAUDE.md imports it)
├── CONTEXT.md            ← glossary, created lazily
└── docs/
    ├── DECISIONS.md      ← ADRs: dated, append-only entries
    └── STATE.md          ← where the build currently stands
```

There is no `docs/adr/` directory. When a skill says "write an ADR", append an entry to `docs/DECISIONS.md` in the format of `.claude/skills/domain-modeling/ADR-FORMAT.md`.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing decision, surface it explicitly rather than silently overriding, citing the entry by date and title:

> _Contradicts the 2026-08-19 decision "KSP, never kapt", but worth reopening because…_
