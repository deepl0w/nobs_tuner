#!/usr/bin/env python3
"""Fails if any Markdown file links to a path that is not there.

The ADRs point at the source they are about, and moving a file silently turns
those into 404s — which is exactly what extracting the `core` module did to five
of them. A reader finds out long after whoever could have fixed it has gone.

    tools/check-doc-links.py
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SKIP = {"build", "node_modules", ".git", ".gradle", "vendor", ".kotlin"}
LINK = re.compile(r"\[(?:[^\]]*)\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")


def main() -> int:
    broken: list[str] = []
    checked = 0

    for doc in sorted(ROOT.rglob("*.md")):
        if SKIP & set(doc.relative_to(ROOT).parts):
            continue
        for target in LINK.findall(doc.read_text()):
            # External links and same-page anchors are not ours to verify.
            if target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            checked += 1
            path = (doc.parent / target.split("#")[0]).resolve()
            if not path.exists():
                broken.append(f"{doc.relative_to(ROOT)} -> {target}")

    for entry in broken:
        print(f"broken link: {entry}", file=sys.stderr)
    print(f"checked {checked} relative links in Markdown; {len(broken)} broken")
    return 1 if broken else 0


if __name__ == "__main__":
    raise SystemExit(main())
