#!/usr/bin/env python3
"""Check every key in the Spring AI application.yml files against the real property metadata.

Spring ignores a property it does not recognise. A typo, a renamed key or a property that moved
between versions costs you nothing at startup and silently does nothing at runtime - which is how
`spring.ai.model.chat=ollama` ended up looking like it switched providers when it only switched
the chat model.

Every Spring dependency ships META-INF/spring-configuration-metadata.json. This reads all of them
out of the local Maven repository and compares.

    python tools/check_config_keys.py

Run a build first so the jars are in ~/.m2. Exits non-zero on an unknown key.
"""

import glob
import json
import os
import sys
import zipfile

# where the metadata lives; narrow on purpose, scanning all of ~/.m2 takes minutes
METADATA_ROOTS = ["org/springframework", "io/micrometer"]

METADATA_FILES = (
    "META-INF/spring-configuration-metadata.json",
    "META-INF/additional-spring-configuration-metadata.json",
)

# map-valued properties: the key carries a user-chosen name, so an exact match is impossible
MAP_PREFIXES = (
    "logging.level.",
    "spring.ai.mcp.client.sse.connections.",
    "spring.ai.mcp.client.stdio.connections.",
    "spring.ai.mcp.client.streamable-http.connections.",
)


def known_keys() -> set:
    m2 = os.path.join(os.path.expanduser("~"), ".m2", "repository")
    keys = set()

    for root in METADATA_ROOTS:
        for jar in glob.glob(os.path.join(m2, root, "**", "*.jar"), recursive=True):
            if "sources" in jar or "javadoc" in jar:
                continue
            try:
                with zipfile.ZipFile(jar) as archive:
                    names = set(archive.namelist())
                    for entry in METADATA_FILES:
                        if entry in names:
                            data = json.loads(archive.read(entry).decode("utf-8"))
                            for item in data.get("properties", []) + data.get("groups", []):
                                keys.add(item["name"])
            except (zipfile.BadZipFile, OSError, json.JSONDecodeError):
                continue

    return keys


def leaf_keys(path: str):
    """Dotted paths of the keys that actually carry a value. Deliberately tiny, no yaml dependency."""
    stack = []
    for raw in open(path, encoding="utf-8"):
        line = raw.rstrip("\n")
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("- ") or ":" not in stripped:
            continue

        indent = len(line) - len(line.lstrip(" "))
        name, _, value = stripped.partition(":")

        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, name.strip()))

        if value.strip():
            yield ".".join(part for _, part in stack)


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    keys = known_keys()

    if len(keys) < 100:
        print(f"only {len(keys)} properties found in ~/.m2 - build the recipes first", file=sys.stderr)
        return 2

    print(f"{len(keys)} known property names from the local repository")

    unknown = []
    files = sorted(glob.glob(os.path.join(root, "spring-ai", "*", "src", "main", "resources", "application.yml")))

    for path in files:
        for key in leaf_keys(path):
            if key in keys or key.startswith(MAP_PREFIXES):
                continue
            unknown.append((os.path.relpath(path, root), key))

    print(f"checked {len(files)} application.yml files")
    if unknown:
        print("not found in any dependency's metadata:")
        for path, key in unknown:
            print(f"  {path} -> {key}")
        return 1

    print("every key exists")
    return 0


if __name__ == "__main__":
    sys.exit(main())
