#!/usr/bin/env python3
"""Patch goal.txt files from GoalDrivenIntegrationTest failure output.

The test compares *normalized* trees: normalizeTree() strips " #<obj>",
" &<mcid>", and ' "<scribble>"' before diffing. The emitted unified diff
therefore does not match the raw goal files literally -- but normalizeTree
only substitutes within lines, never adds/removes/reorders them, so
normalized line N is raw line N. That lets us apply each hunk by line
number, re-normalizing the raw line to confirm it matches the diff's '-'
line before rewriting it.

Only same-length hunks (pure substitutions) are applied; any hunk that
adds or removes lines is a structural change and is refused, since that
would mean the remediation itself changed, not just its rendering.

Usage:
    mvn test -Dgroups=GoalDriven -Dmaven-surefire-plugin.excludedGroups= ; \
      tools/patch-goal-files.py [--apply]

Default is a dry run; pass --apply to write.
"""

import re
import sys
from pathlib import Path

# Paths are resolved against the repo root, so the script works from any cwd.
ROOT = Path(__file__).resolve().parent.parent
REPORT = ROOT / "target/surefire-reports/net.boyechko.pdf.autoa11y.GoalDrivenIntegrationTest.txt"

# Mirrors GoalDrivenIntegrationTest.normalizeTree()
OBJ_MCID = re.compile(r" [#&]\d+")
SCRIBBLE = re.compile(r' "[^"]*"')

GOAL_PATH = re.compile(r"--dump-tree=plain <fixed\.pdf> > (\S+\.goal\.txt)")
HUNK = re.compile(r"^@@ -(\d+),?(\d*) \+(\d+),?(\d*) @@")


def normalize(line):
    return SCRIBBLE.sub("", OBJ_MCID.sub("", line))


def parse_failures(text):
    """Yields (goal_path, [(line_no, old, new), ...]) per failing case."""
    blocks = text.split("--- goal")
    for block in blocks[1:]:
        # The goal path is printed in the failure message just before the diff.
        preamble = block.split("+++ actual")[0]
        m = GOAL_PATH.search(blocks[blocks.index(block) - 1] + preamble)
        if not m:
            continue
        yield m.group(1), list(parse_hunks(block))


def parse_hunks(block):
    """Yields (goal_line_no, old_line, new_line) for pure-substitution hunks."""
    lineno = None
    for raw in block.splitlines():
        m = HUNK.match(raw)
        if m:
            lineno = int(m.group(1))
            pending_old = []
            pending_new = []
            continue
        if lineno is None:
            continue
        if raw.startswith("-"):
            pending_old.append((lineno, raw[1:]))
            lineno += 1
        elif raw.startswith("+"):
            pending_new.append(raw[1:])
        elif raw.startswith(" ") or raw == "":
            if pending_old or pending_new:
                yield from pair_up(pending_old, pending_new)
                pending_old, pending_new = [], []
            lineno += 1
        else:
            break
    if lineno is not None and (pending_old or pending_new):
        yield from pair_up(pending_old, pending_new)


def pair_up(olds, news):
    if len(olds) != len(news):
        raise SystemExit(
            f"Refusing: hunk changes line count ({len(olds)} removed, {len(news)} added).\n"
            f"  removed: {[o[1] for o in olds]}\n  added:   {news}\n"
            "That is a structural change, not a rendering change."
        )
    for (no, old), new in zip(olds, news):
        yield no, old, new


# Trailing `...' segment of a tree-diagram leaf line (the MCR's text).
QUOTED = re.compile(r"`[^`]*'$")


def split_quoted(line):
    """Splits a line into (prefix, quoted-text-or-None)."""
    m = QUOTED.search(line)
    if m:
        return line[: m.start()].rstrip(), m.group(0)
    return line.rstrip(), None


def rewrite(goal_path, lineno, raw, old, new):
    """Re-applies a normalized old->new change to the raw line, keeping its
    stripped obj/mcid/scribble tokens.

    Two shapes are supported: a pure suffix append, and a change confined to
    the trailing quoted text (replaced, added, or dropped) with the prefix
    unchanged. Anything else would have to guess where the stripped tokens
    belong, so it is refused.
    """
    if new.startswith(old):
        return raw + new[len(old) :]

    old_prefix, _ = split_quoted(old)
    new_prefix, new_quoted = split_quoted(new)
    if old_prefix == new_prefix:
        raw_prefix, _ = split_quoted(raw)
        return raw_prefix + (" " + new_quoted if new_quoted else "")

    raise SystemExit(
        f"{goal_path}:{lineno} changes more than the trailing quoted text.\n"
        f"  old: {old!r}\n  new: {new!r}"
    )


def apply_to_file(goal_path, edits, write):
    path = ROOT / goal_path
    lines = path.read_text().splitlines()
    changed = 0
    for lineno, old, new in edits:
        idx = lineno - 1
        if idx >= len(lines):
            raise SystemExit(f"{goal_path}:{lineno} beyond end of file ({len(lines)} lines)")
        raw = lines[idx]
        if normalize(raw) != old:
            raise SystemExit(
                f"{goal_path}:{lineno} does not match the diff.\n"
                f"  raw:        {raw!r}\n"
                f"  normalized: {normalize(raw)!r}\n"
                f"  expected:   {old!r}"
            )
        lines[idx] = rewrite(goal_path, lineno, raw, old, new)
        changed += 1
    print(f"{goal_path}: {changed} line(s)")
    for lineno, _, new in edits[:3]:
        print(f"    {lineno}: {lines[lineno - 1].strip()}")
    if len(edits) > 3:
        print(f"    ... and {len(edits) - 3} more")
    if write:
        path.write_text("\n".join(lines) + "\n")


def main():
    write = "--apply" in sys.argv
    if not REPORT.exists():
        raise SystemExit(f"No surefire report at {REPORT}; run the GoalDriven tests first.")
    failures = list(parse_failures(REPORT.read_text()))
    if not failures:
        print("No goal mismatches found in the report.")
        return
    for goal_path, edits in failures:
        apply_to_file(goal_path, edits, write)
    print("\n" + ("Written." if write else "Dry run -- pass --apply to write."))


if __name__ == "__main__":
    main()
