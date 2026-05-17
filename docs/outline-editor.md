# Outline Editor

`pdf-autoa11y` can dump a PDF's outline (bookmarks) to a plain-text file and
apply an edited version back. Useful when the outline produced by a tool like
Adobe Web Capture is too noisy or mis-structured to use as-is, and rebuilding
it by hand in Acrobat would be tedious.

## Round-trip workflow

```
pdf-autoa11y --dump-outline input.pdf > outline.txt
$EDITOR outline.txt
pdf-autoa11y --apply-outline outline.txt input.pdf output.pdf
```

`--dump-outline` is read-only and writes to stdout. `--apply-outline` is
**replacive**: the entire existing `/Catalog/Outlines` is removed before the
new tree is written. Keep the original dump as a backup if you want a way
back.

## File format

Each non-blank, non-comment line is one bookmark. Nesting is indicated by
indentation; two spaces equals one level deeper. The destination page is
written as a trailing `@<number>` sigil preceded by one or more spaces
(the dumper emits two for readability):

```
General Catalog 2024-2025  @1
  1-General Catalog  @1
  2-Degree programs  @6
    School of Business  @552
    School of Educational Studies  @565
University of Washington Course Descriptions - Bothell  @1
  ACCOUNTING (BOTHELL)  @324
  BUSINESS ADMINISTRATION  @340
```

### Rules

- **Indent.** Spaces only; tabs are rejected. Each level of nesting is
  exactly two spaces. Indenting by an odd number of spaces is an error.
  Jumping more than one level deeper than the previous line is an error.
- **Page sigil.** The last token on each line must be `@<digits>` (or
  `@?` for unresolved entries), preceded by **one or more spaces**.
  End-of-line anchoring means titles can contain `@<digits>` elsewhere
  (e.g. `Twitter @42 status @5` resolves to title "Twitter @42 status",
  page 5). Known limitation: a title whose own text ends in `@<digits>`
  (e.g. `Op. 12 @3`) will have that token misparsed as the page sigil;
  the workaround is to rename the title.
- **Title.** Everything before the trailing `  @<page>` is the title, with
  trailing whitespace trimmed. Embedded double-spaces inside the title are
  preserved.
- **Comments.** Lines whose first non-whitespace character is `#` are
  ignored. Useful for leaving notes inside the file for yourself.
- **Blank lines** are ignored.

### Errors

Parse errors are reported on stderr with the offending line number. Parsing
continues past errors so a single run shows all problems. Bookmarks whose
page number is outside the document's page range are skipped (with a warning)
along with their children. The final report includes a count of parse
errors.

## What survives the round-trip

Only **title and target page**. Specifically:

| Preserved | Discarded |
|---|---|
| Bookmark title text | Color (`/C`) |
| Page number of `GoTo` destination | Bold / italic flags (`/F`) |
| Hierarchy (parent/child structure) | Open / closed state (`/Count` sign) |
| | Non-page views (zoom factor, `/FitR`, etc.) |
| | Named destinations (replaced with explicit `/GoTo` to page) |
| | Non-`GoTo` actions (URI, JavaScript, etc.) |

All emitted destinations are `[page /FitH top]`, where `top` is the page's
MediaBox upper edge. This is intentional: the broken `zoom=nan,0,0`
destinations that Adobe Web Capture frequently produces are silently
normalized to a working view.

Bookmarks whose destination cannot be resolved to a page (broken named
references, URI/JavaScript actions, malformed `/GoTo` arrays) are emitted
with `@?` as the page marker so they remain visible during editing. For
example, an outline like:

```
Course Descriptions - Bothell  @?
  washington.edu  @?
    University of Washington Course Descriptions  @?
      ACCOUNTING (BOTHELL)  @324
      BUSINESS ADMINISTRATION  @340
```

…tells you the three ancestor entries lost their destinations (commonly the
case after `InlineDestinationsCheck` ran with `/Dests` orphans and `/Dests`
was later cleared), while the leaf children resolve cleanly to specific
pages. You then have two choices per `@?` line:

- **Edit** the `@?` to a real page number, recovering the entry.
- **Delete** the line entirely if the entry is no longer useful.

`--apply-outline` rejects `@?` with a clear parse error so the decision is
explicit. Leaving `@?` in place by accident causes that entry (and only
that entry — parsing continues for the rest of the file) to be skipped with
a one-line warning on stderr.

## Interaction with the remediation pipeline

The outline editor is a standalone transform — no checks or fixes run
during `--dump-outline` or `--apply-outline`. Typical sequencing:

```
# Step 1: clean the outline
pdf-autoa11y --dump-outline messy.pdf > outline.txt
$EDITOR outline.txt
pdf-autoa11y --apply-outline outline.txt messy.pdf cleaned.pdf

# Step 2: run the normal accessibility remediation
pdf-autoa11y cleaned.pdf final.pdf
```

`InlineDestinationsFix` (when active) rewrites references to named
destinations into explicit page destinations. Since `--apply-outline`
already emits explicit destinations, the two are compatible in either
order.
