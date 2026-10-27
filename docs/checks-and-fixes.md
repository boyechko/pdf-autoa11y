# Checks and Fixes

PDF-Auto-A11y runs a series of checks against each input PDF, reporting
issues and automatically applying fixes where possible. Checks can be
selectively enabled or disabled via CLI options:

```bash
# Skip specific checks
./pdf-autoa11y --skip-checks=NeedlessNestingCheck,MissingPagePartsCheck input.pdf

# Run only specific checks
./pdf-autoa11y --only-checks=StructTreeOrderCheck,SchemaValidationCheck input.pdf
```

Without either `--skip-checks` or `--only-checks`, PDF-Auto-A11y will
run all checks.

## Available Checks

All checks run as individual pipeline steps, each reading the output of the
previous step in the execution order listed below.

| Check | Description | Fix |
|---|---|---|
| ImageOnlyDocumentCheck | Detects scanned/image-only PDFs that need OCR | None (fatal) |
| StructureTreeExistsCheck | Verifies the PDF has a structure tree | None (fatal) |
| MissingDocumentCheck | Verifies a Document element exists under the structure tree root | Creates Document element |
| StructTreeOrderCheck | Detects structure tree siblings out of reading order | Reorders siblings by where their content sits on the page |
| UnmarkedLinkCheck | Detects Link annotations not tagged as structure elements | Creates Link tags |
| UnexpectedWidgetCheck | Detects non-functional Widget annotation remnants | Removes Widget annotations |
| BadlyMappedLigatureCheck | Detects fonts with broken ligature-to-Unicode mappings | Remaps ligatures |
| LanguageSetCheck | Verifies the document language is set | Sets language |
| TabOrderCheck | Verifies tab order follows structure tree order | Sets tab order |
| TaggedPdfCheck | Verifies the PDF is marked as tagged | Marks as tagged |
| PdfUaConformanceCheck | Detects false PDF/UA conformance claims in XMP metadata | Strips false claims |
| NeedlessNestingCheck | Detects unnecessary Part/Sect/Art/Div grouping wrappers | Flattens wrappers, promoting children
| MissingPagePartsCheck | Detects content not grouped into page-level Part elements | Creates Part-per-page grouping |
| MistaggedArtifactCheck | Detects decorative or noisy content that should be artifacts | Converts to artifacts |
| FigureWithTextCheck | Detects Figure elements containing text content | Changes Figure role |
| MissingAltTextCheck | Detects content images missing alt text | None (manual) |
| EmptyLinkTagCheck | Detects Link elements without link description | Moves adjacent text into Link, or moves the annotation into a text-only Link under it |
| InvalidLinkUriCheck | Detects Link elements whose `/A /URI` is not a plausible http(s) web address | Writes `LINK_URI` scribble for manual review |
| MistaggedListCheck | Detects bulleted, indented, or link-only content that should be lists | Wraps in (sub)list structure, merging split lists |
| IrregularTocCheck | Detects TOC contents not nested into a TOCI hierarchy | Rebuilds entries as `TOCI`, nesting by indentation |
| EmptyElementCheck | Detects empty structure elements | Removes empty elements |
| AdjacentListsCheck | Detects adjacent sibling lists | Merges each run into its first list |
| SchemaValidationCheck | Validates elements against the PDF/UA-1 tag schema | Restructures children to match schema |

## Verified elements: the `OK` scribble

An element whose scribble has a user-authored segment that is exactly
`OK` (e.g. `__OK`, or `__OK // check figure later` to carry a note in a
separate segment) is treated as reviewed to be correct. The
structure-tree walker skips the element and
its entire subtree, so no check sees it and no fix touches it — including
`StaleScribbleCheck`, so the mark itself persists across runs. Remove the
scribble to bring the subtree back under remediation.

Only a user-authored mark counts: a tool-authored scribble (leading `:`)
never verifies an element.

## Scribbles and plain titles

A `/T` value counts as a scribble only when it carries the `__` prefix.
A plain title left by the authoring tool is not a scribble: no check
touches it, and `--dump-tree` shows it as `title "..."` to keep the two
apart.

## Scribble segments and tags

A scribble holds one or more segments separated by ` // `. Every
tool-authored segment opens with an upper-case tag naming what wrote it,
so a segment can be found and replaced without matching its prose. The
tag is what `clearScribbleSegments` keys on.

Segments come in two kinds:

- **State** describes the element as it now stands, and is replaced each
  time it is written.
- **Event** records something a fix or check did, and accumulates. A list
  touched by two fixes carries both, so the scribble reads as a short
  history ending in the current state, e.g.
  `__:WRAP paragraph run // MERGE lists // LIST 7 items`.

| Tag | Kind | Written by |
|---|---|---|
| `LIST` | state | every list-producing fix, as its last step |
| `WRAP` | event | fixes that wrap content into a list |
| `SPLIT` | event | fixes that split one element into several |
| `MERGE` | event | fixes that fold one element into another |
| `INST` | event | `ScribbledInstructionFix`, as an instruction receipt |
| `SCHEMA` | event | `SchemaValidationCheck` findings |
| `LINK_URI` | event | `InvalidLinkUriCheck` findings |

Appending a segment that is already present is a no-op, so a fix that runs twice
does not stamp itself twice. A tool segment appended to a hand-written scribble
leaves that scribble user-authored, so the `SCRIBBLE_VERIFIED_TOKEN` mark and
`StaleScribbleCheck` with `scope: TOOL_AUTHORED` keep working.

## StaleScribbleCheck

`StaleScribbleCheck` flags elements that still carry a scribble and
clears the `/T` key. By default it treats every scribble as stale. Set
`scope: TOOL_AUTHORED` in the sidecar (see `docs/sidecar.md`) to limit
it to the tool's own scribbles, so hand-written notes survive the run.

## ScribbledInstructionCheck

`ScribbledInstructionCheck` carries out structural instructions
scribbled into `/T` with a leading `!` (e.g. `!SET_ROLE H2`), leaving an
`INST` receipt in their place. See `docs/scribbled-instructions.md` for
the supported instructions and their syntax.

## RoleMap Checks

`ClearRoleMapCheck` and `ReplaceRoleMapCheck` are not part of the default
pipeline. They run when the PDF's sidecar config (e.g.,
`document.autoa11y.yaml`) includes a `role-map:` key.

To remove the `/RoleMap` entirely:

```yaml
role-map: clear
```

To replace the `/RoleMap` with a specific set of mappings:

```yaml
role-map:
  CustomHeading: H1
  CustomFigure: Figure
```

| Check | Description | Fix |
|---|---|---|
| ClearRoleMapCheck | Detects presence of `/RoleMap` in the structure tree root | Removes `/RoleMap` |
| ReplaceRoleMapCheck | Compares `/RoleMap` to sidecar-supplied mappings | Replaces `/RoleMap` with supplied mappings |

## StructTreeOrderCheck

The structure tree order check sorts siblings by where their earliest
content is painted on the page: page number first, then a band counted
down the page, then distance from the left edge. This covers both
**cross-page** ordering (pages appearing as 10, 9, 1, 5 instead of
1, 5, 9, 10) and **intra-page** ordering within a single-column flow.

Position deliberately does not come from MCID order. MCIDs reflect the
order content was written to the content stream, which depends on the
authoring tool, and any fix that splits marked content mints new MCIDs
at the end of a page's numbering. In a document this tool has already
remediated, an element's MCID says nothing about where it sits.

### Table rows, and what is still unhandled

A `TR`'s children are ordered by distance from the left edge alone,
ignoring the band. Cells in one row are usually staggered vertically — a
short cell sits centred in the row while a tall multi-line cell beside
it starts higher — so ordering them down the page would report a
correctly ordered row as out of order and scramble it when fixed.

Widening the band is not an alternative: the stagger within a row is
comparable to the line pitch of body text, so any band wide enough to
merge a row's cells also merges adjacent prose lines.

The row's role is what selects the left-to-right rule, so the exemption
reaches exactly as far as the tagging does. Siblings that share a
vertical band **without** sitting in a `TR` are still ordered down the
page, and may be reported out of order when they are not: cells tagged
directly under a `Table` with no `TR` between, a page laid out in
columns, or content flowed around a figure. Ordering those correctly
needs the children of one parent grouped into rows by vertical overlap
before being ordered, which is not implemented.

Prefer `--scope` to confine a run to a subtree you have looked at.

### Elements the check declines to judge

When any child of an element paints nothing findable, the check reports
nothing for that element rather than guessing at a position. This covers
a paragraph holding only a link annotation (the annotation has no text
marked-content reference), an empty `LBody`, and a placeholder cell
whose text lives only in `/ActualText`. Run with `-vv` and
`-Dorg.slf4j.simpleLogger.log.net.boyechko.pdf.autoa11y.checks.StructTreeOrderCheck=debug`
to see which element was skipped and why.
