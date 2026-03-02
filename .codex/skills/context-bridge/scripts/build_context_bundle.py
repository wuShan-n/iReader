#!/usr/bin/env python3
"""Build a Markdown context bundle from a manifest JSON."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a markdown context bundle from manifest JSON."
    )
    parser.add_argument("--repo", default=".", help="Repository root")
    parser.add_argument("--manifest", required=True, help="Manifest JSON file path or '-' for stdin")
    parser.add_argument("--output", required=True, help="Output markdown file path")
    parser.add_argument(
        "--include-optional",
        action="store_true",
        help="Include optional_files from manifest in the output",
    )
    return parser.parse_args()


def normalize_path(value: str) -> str:
    return value.replace("\\", "/").lstrip("./")


def unique_in_order(paths: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for path in paths:
        normalized = normalize_path(path)
        if normalized in seen:
            continue
        seen.add(normalized)
        ordered.append(normalized)
    return ordered


def load_manifest(manifest_arg: str) -> dict[str, Any]:
    if manifest_arg == "-":
        import sys

        content = sys.stdin.read()
    else:
        raw = Path(manifest_arg).read_bytes()
        content = None
        for encoding in ("utf-8", "utf-8-sig", "utf-16", "utf-16-le", "gb18030"):
            try:
                content = raw.decode(encoding)
                break
            except UnicodeDecodeError:
                continue
        if content is None:
            content = raw.decode("utf-8", errors="replace")
    return json.loads(content)


def is_probably_binary(data: bytes) -> bool:
    if not data:
        return False
    if b"\x00" in data:
        return True
    sample = data[:4096]
    control_chars = sum(1 for byte in sample if (byte < 9) or (13 < byte < 32))
    return (control_chars / max(1, len(sample))) > 0.30


def decode_text(data: bytes) -> str:
    for encoding in ("utf-8", "utf-8-sig", "gb18030"):
        try:
            return data.decode(encoding)
        except UnicodeDecodeError:
            continue
    return data.decode("utf-8", errors="replace")


def code_fence_language(path: str) -> str:
    lower = path.lower()
    if lower.endswith(".kt") or lower.endswith(".kts"):
        return "kotlin"
    if lower.endswith(".java"):
        return "java"
    if lower.endswith(".xml"):
        return "xml"
    if lower.endswith(".md"):
        return "markdown"
    if lower.endswith(".json"):
        return "json"
    if lower.endswith(".toml"):
        return "toml"
    if lower.endswith(".yaml") or lower.endswith(".yml"):
        return "yaml"
    if lower.endswith(".properties"):
        return "properties"
    if lower.endswith(".sh"):
        return "bash"
    if lower.endswith(".py"):
        return "python"
    if lower.endswith(".pro"):
        return "pro"
    return ""


def main() -> int:
    args = parse_args()
    repo = Path(args.repo).resolve()
    if not repo.is_dir():
        raise SystemExit(f"Repository path does not exist: {repo}")

    manifest = load_manifest(args.manifest)
    feature = str(manifest.get("feature", "")).strip()
    resolved_files = list(manifest.get("resolved_files", []))
    optional_files = list(manifest.get("optional_files", []))
    missing_files = {normalize_path(path) for path in manifest.get("missing_files", [])}

    candidates = resolved_files + (optional_files if args.include_optional else [])
    ordered_files = unique_in_order(candidates)

    written_files: list[str] = []
    skipped_files: list[str] = []
    file_contents: dict[str, str] = {}

    for rel in ordered_files:
        file_path = repo / rel
        if not file_path.is_file():
            missing_files.add(rel)
            continue
        try:
            data = file_path.read_bytes()
        except OSError:
            missing_files.add(rel)
            continue

        if is_probably_binary(data):
            skipped_files.append(rel)
            continue

        text = decode_text(data).rstrip("\r\n")
        file_contents[rel] = text
        written_files.append(rel)

    output_path = Path(args.output)
    if not output_path.is_absolute():
        output_path = repo / output_path
    output_path.parent.mkdir(parents=True, exist_ok=True)

    now = datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")

    with output_path.open("w", encoding="utf-8", newline="\n") as stream:
        stream.write("# Context Bundle\n\n")
        stream.write(f"Generated at: {now}\n")
        stream.write(f"Feature: {feature or 'N/A'}\n")
        stream.write(f"Resolved files requested: {len(ordered_files)}\n")
        stream.write(f"Written files: {len(written_files)}\n")
        stream.write(f"Missing files: {len(missing_files)}\n")
        stream.write(f"Skipped files: {len(skipped_files)}\n\n")

        stream.write("## File Index\n\n")
        for rel in written_files:
            stream.write(f"- [x] {rel}\n")
        for rel in sorted(missing_files):
            stream.write(f"- [ ] {rel} (missing)\n")
        for rel in skipped_files:
            stream.write(f"- [-] {rel} (skipped binary/unreadable)\n")
        stream.write("\n")

        for rel in written_files:
            lang = code_fence_language(rel)
            stream.write(f"## {rel}\n\n")
            stream.write(f"```{lang}\n" if lang else "```\n")
            stream.write(file_contents[rel])
            stream.write("\n```\n\n")

    summary = {
        "output_path": str(output_path),
        "written_files_count": len(written_files),
        "missing_files_count": len(missing_files),
        "skipped_files_count": len(skipped_files),
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
