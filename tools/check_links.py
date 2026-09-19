#!/usr/bin/env python3
"""Check that every relative link in the markdown files points at something that exists.

The recipe READMEs link into their own source files and at each other, and those links rot the
moment a class is renamed. Nothing else notices, because markdown does not compile.

    python tools/check_links.py

Exits non-zero and lists the offenders if anything is broken.
"""

import os
import re
import sys

LINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
SKIP_PREFIXES = ("http://", "https://", "mailto:", "#")

# ../../issues and friends are GitHub shortcuts, not paths on disk
GITHUB_SHORTCUTS = ("/issues", "/pulls", "/actions")


def markdown_files(root: str):
    for current, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in {"target", ".git", ".mvn", "node_modules"}]
        for name in files:
            if name.endswith(".md"):
                yield os.path.join(current, name)


def main() -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    broken = []
    checked = 0

    for path in sorted(markdown_files(root)):
        base = os.path.dirname(path)
        with open(path, encoding="utf-8") as handle:
            text = handle.read()

        for match in LINK.finditer(text):
            target = match.group(1).split("#")[0].strip()
            if not target or target.startswith(SKIP_PREFIXES):
                continue
            if target.endswith(GITHUB_SHORTCUTS):
                continue

            checked += 1
            if not os.path.exists(os.path.normpath(os.path.join(base, target))):
                broken.append((os.path.relpath(path, root), target))

    print(f"checked {checked} relative links")
    if broken:
        print("broken:")
        for path, target in broken:
            print(f"  {path} -> {target}")
        return 1

    print("all resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
