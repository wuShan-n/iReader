---
name: context-bridge
description: Prepare implementation context bundles for external AI agents by discovering feature-related files across the repository, collecting full file contents, and exporting them to a single Markdown file. Use when Codex needs to act as a context relay, build a handoff package for another AI, or gather all relevant code for implementing a feature.
---

# Context Bridge Skill

## Overview

Use this skill to package repository context for an external AI that will implement a feature.
The skill discovers relevant files, expands imports, and exports full file contents to one Markdown file.

## Workflow

1. Resolve intent and output target.
- Ask for feature name if missing.
- Default output path to `docs/context_bundle_<feature_slug>.md` when the user does not specify one.

2. Detect relevant files.
- Run:
```bash
py .codex/skills/context-bridge/scripts/detect_context_files.py --feature "<feature>" --repo "." > build/context-bridge/manifest.json
```
- Add user constraints when provided:
```bash
py .codex/skills/context-bridge/scripts/detect_context_files.py --feature "<feature>" --repo "." --include "feature/library/**,core/database/**" --exclude "docs/**" > build/context-bridge/manifest.json
```

3. Build the Markdown bundle.
- Run:
```bash
py .codex/skills/context-bridge/scripts/build_context_bundle.py --repo "." --manifest build/context-bridge/manifest.json --output "<output_path>"
```

4. Report result.
- Return output path and summary counts.
- Mention missing/skipped files when present.

## Default Policies

- Produce full file content, not summaries.
- Overwrite the output file path if it already exists.
- Keep docs and Markdown matches in `optional_files` unless explicitly included.
- Keep source files unchanged; write only the context bundle output file.

## References

- Read `references/discovery_rules.md` to understand discovery and dependency expansion rules.
- Read `references/output_template.md` for the expected output file layout.

## Failure Handling

- If `rg` is unavailable, fall back to a slower text scan.
- If files are unreadable or binary, skip them and record them in summary output.
- If user provides explicit include patterns and some do not match, list those paths in `missing_files`.
