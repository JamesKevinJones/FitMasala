# Vendored skills: mattpocock/skills

These skill folders are copied from https://github.com/mattpocock/skills
(MIT, see `MATTPOCOCK-LICENSE`) at commit
`c55ee46073ed923f86ce59a5eb3b6d895095d1b7` (plugin version 1.2.3).

## What was copied

Every skill the `mattpocock-skills` Claude Code plugin ships
(`.claude-plugin/plugin.json`), flattened to `.claude/skills/<name>/`, except:

- **`code-review`** - not copied. Its name collides with Claude Code's built-in
  `/code-review`. `/implement` ends by calling `/code-review`, which now runs
  the built-in review.
- **`agents/openai.yaml`** in each skill - dropped; Codex-only metadata.

## Local changes

The only edits: this repo's ADR log is `docs/DECISIONS.md` (dated,
append-only), not numbered files in `docs/adr/`.

- `domain-modeling/ADR-FORMAT.md` - template and rules rewritten for
  DECISIONS.md entries; the "when to offer" criteria are unchanged.
- `domain-modeling/SKILL.md` - "File structure" section describes this repo.
- `improve-codebase-architecture/SKILL.md` - one `docs/adr/` reference
  repointed.

`setup-matt-pocock-skills/` is left as upstream, so re-running it will
propose `docs/adr/` again; the answer for this repo is already in
`docs/agents/domain.md`.

## Updating

Re-copy the folders from a newer upstream commit, re-apply the changes above,
and update the commit SHA here.
