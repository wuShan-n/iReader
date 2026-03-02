# Discovery Rules

Use this file when tuning or reviewing feature-to-file discovery.

## Goals

- Find enough files for an external AI to implement a feature end-to-end.
- Keep output practical by separating must-have files from optional context.
- Keep discovery deterministic and explainable.

## Detection Pipeline

1. Derive keywords from user feature text.
2. Expand with synonym rules (Chinese and English).
3. Search repository text with `rg`.
4. Expand matches through Kotlin import dependencies.
5. Attach module wiring files (`build.gradle.kts`, manifest, routes, app entry).
6. Apply default and user excludes.
7. Classify code/config as `resolved_files` and docs/markdown as `optional_files`.

## Keyword Expansion

Use feature-specific synonyms to improve recall. Example groups:

- Bookshelf: `书架`, `bookshelf`, `library`, `book`, `import`
- Import: `导入`, `import`, `scan`, `worker`, `storage`
- Reader: `阅读`, `reader`, `openbook`, `session`
- Search: `搜索`, `search`, `query`, `index`
- Settings: `设置`, `settings`, `preferences`, `config`

## Import Expansion

- Build package and symbol indexes from Kotlin files.
- Resolve direct imports first.
- Resolve package-level imports when only package is available.
- Stop at configured depth (`--max-import-depth`, default `2`) to avoid graph explosion.

## Wiring Files

When feature files are detected, include repository/module glue files when present:

- `settings.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/**/MainActivity.kt`
- `core/navigation/.../AppRoutes.kt`
- Module-level `build.gradle.kts`
- Module-level `src/main/AndroidManifest.xml`

## Default Excludes

Exclude noisy/generated paths unless explicitly included:

- `.git/*`, `.gradle/*`, `.idea/*`, `.kotlin/*`
- `build/*`
- `.codex/skills/*`
- `docs/context_bundle_*`

## Output Contract

`detect_context_files.py` returns:

- `feature`
- `resolved_files`
- `missing_files`
- `optional_files`
- `notes`

Use `build_context_bundle.py` with this manifest to generate the final Markdown bundle.
