# iReader Architecture Rules

## Dependency direction

- `feature/*` can depend on `core/*` only.
- `feature/*` must not directly depend on `engines/*`.
- `engines/*` can depend on `core:reader:api` and low-level core modules when required.
- `core/*` must not depend on `feature/*`.
- `app` is the composition root and is the only module that depends on both features and engines.

## Engine registration

- Engines are registered in `app` and assembled into `EngineRegistry`.
- Feature modules always use `ReaderRuntime` from `core:reader:runtime`.

## Package boundaries

- Cross-module data models live in `com.ireader.reader.model` (`:core:model`).
- File source abstraction lives in `com.ireader.reader.source` (`:core:files`).
- Engine contracts live in `com.ireader.reader.api` (`:core:reader:api`).
