#!/usr/bin/env python3
"""Detect repository files needed to implement a feature and emit a JSON manifest."""

from __future__ import annotations

import argparse
import fnmatch
import glob
import json
import re
import shutil
import subprocess
from collections import defaultdict, deque
from pathlib import Path
from typing import Iterable

SEARCH_GLOBS = (
    "*.kt",
    "*.kts",
    "*.java",
    "*.xml",
    "*.gradle",
    "*.pro",
    "*.properties",
    "*.toml",
    "*.md",
)

DEFAULT_EXCLUDE_PATTERNS = (
    ".git/*",
    ".gradle/*",
    ".idea/*",
    ".kotlin/*",
    "build/*",
    "*/build/*",
    "*/src/test/*",
    "*/src/androidTest/*",
    "build-logic/*",
    ".codex/skills/*",
    "docs/context_bundle_*",
)

ALLOWED_TOP_LEVELS = {"app", "core", "feature", "engines", "docs"}

IMPORT_RE = re.compile(r"^\s*import\s+([A-Za-z0-9_.*\s]+)$", re.MULTILINE)
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*$", re.MULTILINE)
TOKEN_RE = re.compile(r"[a-z0-9_]+|[\u4e00-\u9fff]+", re.IGNORECASE)

SYNONYM_RULES = {
    "书架": ["bookshelf", "library", "book", "import", "shelf"],
    "bookshelf": ["bookshelf", "library", "book", "import", "shelf"],
    "library": ["library", "book", "bookshelf", "shelf", "import"],
    "导入": ["import", "scan", "uri", "worker", "storage", "files"],
    "import": ["import", "scan", "uri", "worker", "storage", "files"],
    "阅读": ["reader", "openbook", "session", "runtime", "engine"],
    "reader": ["reader", "openbook", "session", "runtime", "engine"],
    "搜索": ["search", "query", "index", "keyword"],
    "search": ["search", "query", "index", "keyword"],
    "设置": ["settings", "preferences", "config"],
    "settings": ["settings", "preferences", "config"],
}

SEARCH_STOPWORDS = {
    "import",
    "function",
    "功能",
    "实现",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Discover feature-related files and emit a context manifest JSON."
    )
    parser.add_argument("--feature", required=True, help="Feature request or feature name")
    parser.add_argument("--repo", default=".", help="Repository root")
    parser.add_argument(
        "--include",
        action="append",
        default=[],
        help="Extra file/path glob(s), comma-separated or repeatable",
    )
    parser.add_argument(
        "--exclude",
        action="append",
        default=[],
        help="Exclude glob(s), comma-separated or repeatable",
    )
    parser.add_argument(
        "--max-import-depth",
        type=int,
        default=1,
        help="Max depth for Kotlin import expansion",
    )
    return parser.parse_args()


def normalize_path(value: str) -> str:
    return value.replace("\\", "/").lstrip("./")


def parse_pattern_args(raw_values: list[str]) -> list[str]:
    output: list[str] = []
    for raw in raw_values:
        for part in raw.split(","):
            cleaned = normalize_path(part.strip())
            if cleaned:
                output.append(cleaned)
    return output


def rel_path(root: Path, path: Path) -> str:
    return normalize_path(path.resolve().relative_to(root).as_posix())


def derive_keywords(feature: str) -> list[str]:
    lowered = feature.lower().strip()
    keywords = {
        token
        for token in TOKEN_RE.findall(lowered)
        if len(token) >= 2 or re.search(r"[\u4e00-\u9fff]", token)
    }
    if lowered:
        keywords.add(lowered)

    for trigger, expansions in SYNONYM_RULES.items():
        if trigger in lowered:
            keywords.update(expansions)

    return sorted(keywords)


def search_keywords(keywords: list[str]) -> list[str]:
    return [
        keyword
        for keyword in keywords
        if keyword not in SEARCH_STOPWORDS and len(keyword.strip()) >= 2
    ]


def run_ripgrep(root: Path, keywords: list[str], notes: list[str]) -> set[str]:
    if not keywords:
        return set()
    if shutil.which("rg") is None:
        notes.append("rg not found; fallback scanner used.")
        return set()

    pattern = "|".join(re.escape(keyword) for keyword in keywords)
    cmd = ["rg", "-l", "-i"]
    for glob_pattern in SEARCH_GLOBS:
        cmd.extend(["-g", glob_pattern])
    cmd.extend([pattern, "."])

    process = subprocess.run(
        cmd,
        cwd=root,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    if process.returncode not in (0, 1):
        notes.append(f"rg failed (exit={process.returncode}); fallback scanner used.")
        return set()

    return {
        normalize_path(line.strip())
        for line in process.stdout.splitlines()
        if line.strip()
    }


def fallback_scan(root: Path, keywords: list[str]) -> set[str]:
    lowered_keywords = [keyword.lower() for keyword in keywords if keyword]
    if not lowered_keywords:
        return set()

    matches: set[str] = set()
    for glob_pattern in SEARCH_GLOBS:
        for path in root.rglob(glob_pattern):
            if not path.is_file():
                continue
            if path.stat().st_size > 2_000_000:
                continue
            try:
                text = path.read_text(encoding="utf-8", errors="ignore").lower()
            except OSError:
                continue
            if any(keyword in text for keyword in lowered_keywords):
                matches.add(rel_path(root, path))
    return matches


def keep_repo_scope(path: str) -> bool:
    normalized = normalize_path(path)
    if normalized in {"settings.gradle.kts", "build.gradle.kts", "gradle/libs.versions.toml"}:
        return True
    first = normalized.split("/", 1)[0]
    return first in ALLOWED_TOP_LEVELS


def read_small_text(path: Path, max_bytes: int = 512_000) -> str:
    try:
        data = path.read_bytes()
    except OSError:
        return ""
    if len(data) > max_bytes:
        data = data[:max_bytes]
    return data.decode("utf-8", errors="ignore")


def build_kotlin_indexes(root: Path) -> tuple[dict[str, str], dict[str, list[str]]]:
    symbol_index: dict[str, str] = {}
    package_index: dict[str, list[str]] = defaultdict(list)

    for kt_path in root.rglob("*.kt"):
        if not kt_path.is_file():
            continue
        content = read_small_text(kt_path, max_bytes=128_000)
        if not content:
            continue
        package_match = PACKAGE_RE.search(content)
        if not package_match:
            continue
        package_name = package_match.group(1)
        rel = rel_path(root, kt_path)
        symbol_index[f"{package_name}.{kt_path.stem}"] = rel
        package_index[package_name].append(rel)

    sorted_package_index = {
        package: sorted(paths) for package, paths in package_index.items()
    }
    return symbol_index, sorted_package_index


def resolve_import_target(
    import_line: str,
    symbol_index: dict[str, str],
    package_index: dict[str, list[str]],
) -> set[str]:
    cleaned = import_line.split(" as ")[0].strip()
    if not cleaned:
        return set()

    if cleaned.endswith(".*"):
        package_name = cleaned[:-2]
        return set(package_index.get(package_name, []))

    if cleaned in symbol_index:
        return {symbol_index[cleaned]}

    parts = cleaned.split(".")
    for end in range(len(parts) - 1, 1, -1):
        candidate = ".".join(parts[:end])
        if candidate in symbol_index:
            return {symbol_index[candidate]}
        package_hits = package_index.get(candidate)
        if package_hits:
            return set(package_hits[:8])

    return set()


def expand_import_dependencies(
    root: Path,
    seed_paths: set[str],
    symbol_index: dict[str, str],
    package_index: dict[str, list[str]],
    max_depth: int,
) -> set[str]:
    result = set(seed_paths)
    queue: deque[tuple[str, int]] = deque(
        (path, 0) for path in sorted(seed_paths) if path.endswith(".kt")
    )
    visited: set[str] = set()

    while queue:
        current, depth = queue.popleft()
        if current in visited:
            continue
        visited.add(current)
        if depth >= max_depth:
            continue
        if current.startswith("app/"):
            continue

        current_file = root / current
        if not current_file.is_file():
            continue
        content = read_small_text(current_file, max_bytes=512_000)
        if not content:
            continue

        for import_line in IMPORT_RE.findall(content):
            for target in resolve_import_target(import_line, symbol_index, package_index):
                if target in result:
                    continue
                result.add(target)
                if target.endswith(".kt"):
                    queue.append((target, depth + 1))

    return result


def module_root(path: str) -> str | None:
    parts = Path(path).parts
    if not parts:
        return None
    if parts[0] in ("core", "feature", "engines") and len(parts) >= 2:
        return f"{parts[0]}/{parts[1]}"
    return parts[0]


def feature_module(path: str) -> str | None:
    parts = Path(path).parts
    if len(parts) >= 2 and parts[0] == "feature":
        return parts[1]
    return None


def pick_primary_feature_modules(paths: Iterable[str], keywords: list[str]) -> set[str]:
    modules = sorted(
        {
            module
            for module in (feature_module(path) for path in paths)
            if module is not None
        }
    )
    if not modules:
        return set()

    lowered_keywords = [keyword.lower() for keyword in keywords if keyword]
    scores: dict[str, int] = {}
    for module in modules:
        module_lower = module.lower()
        scores[module] = sum(1 for keyword in lowered_keywords if keyword in module_lower)

    best_score = max(scores.values(), default=0)
    if best_score <= 0:
        return set()
    return {module for module, score in scores.items() if score == best_score}


def attach_wiring_files(root: Path, paths: set[str]) -> set[str]:
    output = set(paths)
    modules = {m for m in (module_root(path) for path in paths) if m}

    for module in modules:
        for candidate in (
            f"{module}/build.gradle.kts",
            f"{module}/src/main/AndroidManifest.xml",
        ):
            if (root / candidate).is_file():
                output.add(candidate)

    if any(path.startswith("feature/") for path in output):
        for candidate in (
            "settings.gradle.kts",
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "core/navigation/src/main/kotlin/com/ireader/core/navigation/AppRoutes.kt",
        ):
            if (root / candidate).is_file():
                output.add(candidate)
        for main_activity in root.glob("app/src/main/java/**/MainActivity.kt"):
            if main_activity.is_file():
                output.add(rel_path(root, main_activity))

    return output


def apply_excludes(
    paths: Iterable[str], exclude_patterns: list[str], protected: set[str]
) -> tuple[set[str], set[str]]:
    kept: set[str] = set()
    removed: set[str] = set()

    normalized_patterns = [normalize_path(pattern) for pattern in exclude_patterns]
    for path in paths:
        normalized = normalize_path(path)
        if normalized in protected:
            kept.add(normalized)
            continue
        if any(fnmatch.fnmatch(normalized, pattern) for pattern in normalized_patterns):
            removed.add(normalized)
        else:
            kept.add(normalized)
    return kept, removed


def resolve_include_patterns(root: Path, include_patterns: list[str]) -> tuple[set[str], list[str]]:
    included: set[str] = set()
    missing: list[str] = []

    for pattern in include_patterns:
        normalized_pattern = normalize_path(pattern)
        full_pattern = str(root / normalized_pattern)
        hits = [Path(hit) for hit in glob.glob(full_pattern, recursive=True)]
        file_hits = [hit for hit in hits if hit.is_file()]

        if not file_hits:
            fallback = root / normalized_pattern
            if fallback.is_file():
                file_hits = [fallback]

        if not file_hits:
            missing.append(normalized_pattern)
            continue

        for hit in file_hits:
            included.add(rel_path(root, hit))

    return included, missing


def classify_paths(paths: Iterable[str], explicit_includes: set[str]) -> tuple[list[str], list[str]]:
    required: set[str] = set()
    optional: set[str] = set()

    for path in paths:
        normalized = normalize_path(path)
        if normalized in explicit_includes:
            required.add(normalized)
            continue
        if normalized.startswith("docs/") or normalized.endswith(".md"):
            optional.add(normalized)
        else:
            required.add(normalized)

    return sorted(required), sorted(optional)


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()
    if not repo.is_dir():
        raise SystemExit(f"Repository path does not exist: {repo}")

    include_patterns = parse_pattern_args(args.include)
    exclude_patterns = parse_pattern_args(args.exclude)

    notes: list[str] = []
    keywords = derive_keywords(args.feature)
    notes.append("keywords=" + ", ".join(keywords))

    effective_keywords = search_keywords(keywords)
    notes.append("search_keywords=" + ", ".join(effective_keywords))

    matched = run_ripgrep(repo, effective_keywords, notes)
    if not matched:
        matched = fallback_scan(repo, effective_keywords)
        notes.append("used_fallback_scan=true")

    matched = {path for path in matched if keep_repo_scope(path)}

    include_matches, include_missing = resolve_include_patterns(repo, include_patterns)

    candidates = set(matched) | include_matches
    primary_feature_modules = pick_primary_feature_modules(candidates, keywords)
    if primary_feature_modules:
        candidates = {
            path
            for path in candidates
            if (feature_module(path) is None) or (feature_module(path) in primary_feature_modules)
        }
        notes.append(
            "primary_feature_modules=" + ", ".join(sorted(primary_feature_modules))
        )
    candidates, removed_initial = apply_excludes(
        candidates,
        list(DEFAULT_EXCLUDE_PATTERNS) + exclude_patterns,
        protected=include_matches,
    )
    if removed_initial:
        notes.append(f"excluded_initial={len(removed_initial)}")

    symbol_index, package_index = build_kotlin_indexes(repo)
    expanded = expand_import_dependencies(
        repo,
        candidates,
        symbol_index,
        package_index,
        max_depth=max(0, args.max_import_depth),
    )
    expanded = attach_wiring_files(repo, expanded)
    expanded, removed_after_wiring = apply_excludes(
        expanded,
        list(DEFAULT_EXCLUDE_PATTERNS) + exclude_patterns,
        protected=include_matches,
    )
    if removed_after_wiring:
        notes.append(f"excluded_after_wiring={len(removed_after_wiring)}")

    existing_files = sorted(path for path in expanded if (repo / path).is_file())
    required_files, optional_files = classify_paths(existing_files, include_matches)

    output = {
        "feature": args.feature,
        "resolved_files": required_files,
        "missing_files": sorted(set(include_missing)),
        "optional_files": optional_files,
        "notes": notes,
    }
    print(json.dumps(output, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
