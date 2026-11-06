#!/usr/bin/env python3
"""Search and install skills from the local AAS library."""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
from pathlib import Path

LIBRARY = Path.home() / "skills-library/aas"
DEFAULT_INDEX = LIBRARY / "_library/index.json"


def load_index() -> list[dict]:
    p = Path(os.environ.get("SKILL_LIBRARY_INDEX", DEFAULT_INDEX))
    if not p.is_file():
        print(f"index not found: {p}", file=sys.stderr)
        raise SystemExit(2)
    return json.loads(p.read_text())


def text(e: dict) -> str:
    return f"{e['id']} {e['name']} {e['description']}".lower()


def hit(hay: str, token: str) -> bool:
    """Match at a word start; allow substring only for longer tokens."""
    if re.search(r"(?<![a-z0-9])" + re.escape(token), hay):
        return True
    return len(token) >= 5 and token in hay


def score(e: dict, q: str, tokens: list[str]) -> int:
    sid, name, desc = e["id"].lower(), e["name"].lower(), e["description"].lower()
    s = 0
    if sid == q:
        s += 200
    if name == q:
        s += 120
    if q in sid:
        s += 40
    if q in name:
        s += 20
    for t in tokens:
        if hit(sid, t):
            s += 12
        if hit(name, t):
            s += 8
        if hit(desc, t):
            s += 3
        elif hit(text(e), t):
            s += 1
    if tokens and all(hit(text(e), t) for t in tokens):
        s += 15
    return s


def clean(s: str, n: int) -> str:
    s = re.sub(r"\s+", " ", s).strip()
    return s if len(s) <= n else s[: n - 1] + "…"


def cmd_search(args) -> int:
    entries = load_index()
    q = " ".join(args.query).lower().strip()
    tokens = [t for t in re.split(r"[^a-z0-9а-яё._+-]+", q) if len(t) > 1]
    rows = []
    for e in entries:
        if args.risk == "high" and not (set(e.get("risk", [])) & {"pipe-to-shell", "reverse-shell", "webhook-exfil", "destructive-rm"}):
            continue
        if args.risk == "none" and e.get("risk"):
            continue
        sc = score(e, q, tokens) if q else 1
        if sc > 0:
            rows.append((sc, e))
    rows.sort(key=lambda r: (-r[0], r[1]["id"]))
    if not rows:
        print("no matches")
        return 1
    for sc, e in rows[: args.limit]:
        risk = f"  !{','.join(e['risk'])}" if e.get("risk") else ""
        print(f"{e['id']}\t{clean(e['name'], 40)}\t{clean(e['description'], 150)}{risk}")
    if len(rows) > args.limit:
        print(f"... {len(rows) - args.limit} more; narrow the query or raise --limit")
    return 0


def cmd_show(args) -> int:
    for e in load_index():
        if e["id"] == args.id:
            print(f"id:          {e['id']}")
            print(f"name:        {e['name']}")
            print(f"size:        {e['size']} bytes")
            print(f"risk:        {', '.join(e.get('risk') or []) or 'none'}")
            print(f"path:        {LIBRARY / e['id'] / 'SKILL.md'}")
            print()
            print(e["description"])
            return 0
    print(f"not found: {args.id}", file=sys.stderr)
    return 1


def cmd_stats(_args) -> int:
    entries = load_index()
    risk = sum(1 for e in entries if e.get("risk"))
    high = sum(1 for e in entries if set(e.get("risk", [])) & {"pipe-to-shell", "reverse-shell", "webhook-exfil", "destructive-rm"})
    print(f"skills: {len(entries)}")
    print(f"risk-flagged: {risk}")
    print(f"high-risk: {high}")
    print(f"total: {sum(e['size'] for e in entries) / 1048576:.1f} MiB")
    return 0


def cmd_enable(args) -> int:
    src = LIBRARY / args.id
    if not (src / "SKILL.md").is_file():
        print(f"not found in library: {args.id}", file=sys.stderr)
        return 1
    dest = Path.home() / ".config/opencode/skills" / args.id
    if dest.exists():
        print(f"already active: {dest}", file=sys.stderr)
        return 1
    shutil.copytree(src, dest)
    p = dest / "SKILL.md"
    text = p.read_text()
    text = re.sub(r"(?m)^disable-model-invocation\s*:\s*true\s*\n?", "", text)
    text = re.sub(r"(?m)^\s*opencode/autoinvoke\s*:\s*false\s*\n?", "", text)
    p.write_text(text)
    print(f"enabled: {dest}")
    print("Run `opencode reload` to advertise it in the current server.")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("search")
    s.add_argument("query", nargs="+")
    s.add_argument("--limit", type=int, default=10)
    s.add_argument("--risk", choices=["any", "high", "none"], default="any")
    s.set_defaults(func=cmd_search)
    sh = sub.add_parser("show")
    sh.add_argument("id")
    sh.set_defaults(func=cmd_show)
    st = sub.add_parser("stats")
    st.set_defaults(func=cmd_stats)
    en = sub.add_parser("enable")
    en.add_argument("id")
    en.set_defaults(func=cmd_enable)
    args = ap.parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
