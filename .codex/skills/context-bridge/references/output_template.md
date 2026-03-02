# Output Template

`build_context_bundle.py` writes one Markdown file with this structure:

1. Header metadata
- generation time
- feature name
- requested/written/missing/skipped counts

2. File index
- `[x] <path>` for included files
- `[ ] <path> (missing)` for missing files
- `[-] <path> (skipped binary/unreadable)` for skipped files

3. Full file sections
- one section per file:
  - `## <path>`
  - fenced code block with inferred language
  - complete file text

## Example

```markdown
# Context Bundle

Generated at: 2026-03-02T18:00:00+08:00
Feature: 书架功能
Resolved files requested: 42
Written files: 41
Missing files: 1
Skipped files: 0

## File Index

- [x] app/src/main/java/com/ireader/MainActivity.kt
- [ ] feature/library/src/main/kotlin/... (missing)

## app/src/main/java/com/ireader/MainActivity.kt

```kotlin
// file content...
```
```
