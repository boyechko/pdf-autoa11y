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
| EmptyLinkTagCheck | Detects Link elements without link description | Moves adjacent text into Link |
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

`ScribbledInstructionCheck` detects structure elements whose `/T`
(scribble) value starts with `!` and treats it as a structural
instruction. The fix carries out the instruction and replaces the
scribble with a receipt naming what it did — the instruction without
its `!`, plus the outcome where there is one, e.g. `!SET_ROLE H2`
leaves `INST SET_ROLE P -> H2`. Receipts from an earlier run are swept
from the whole tree before each run, so they always describe the last
run only. Scribbles are written in Acrobat's tags panel and executed by
the tool on the next run.

The following instructions are supported:

### !ADD_CHILD \<template\>, !ADD_CHILDREN \<template\>

Adds child structure elements under the scribbled element. Wrappers
can be empty (creating new structure) or reference existing kids by
index to redistribute them. Ranges must be ascending, contiguous,
and cover every existing kid exactly once.

```text
Template  := Wrapper { "," Wrapper }
Wrapper   := TagName "[" Body "]"
           | TagName                      (* empty leaf wrapper *)
Body      := Range
           | Template                     (* nested wrappers *)
Range     := N                            (* single kid, 1-based *)
           | N ".." M                     (* inclusive range, M ≥ N *)
           | N ".."                       (* from N to last kid *)
```

Range references always index the scribbled element's direct kids,
regardless of nesting depth.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!ADD_CHILDREN Lbl[], LBody[]` | `LI[]` | `LI[Lbl[], LBody[]]` |
| `!ADD_CHILDREN Lbl[1], LBody[2..]` | `LI[MCR₁, MCR₂, MCR₃]` | `LI[Lbl[MCR₁], LBody[MCR₂, MCR₃]]` |
| `!ADD_CHILDREN Lbl[1], Note[], LBody[2..]` | `LI[MCR₁, MCR₂]` | `LI[Lbl[MCR₁], Note[], LBody[MCR₂]]` |
| `!ADD_CHILDREN TD[Caption[]]` | `TR[]` | `TR[TD[Caption[]]]` |
| `!ADD_CHILDREN LI[LBody[1]], LI[LBody[2]], LI[LBody[3]]` | `L[MCR₁, MCR₂, MCR₃]` | `L[LI[LBody[MCR₁]], LI[LBody[MCR₂]], LI[LBody[MCR₃]]]` |

### !ADD_PARENT \<chain\> / !ADD_PARENTS \<chain\>

Wraps the scribbled element in a chain of new parent elements.
The chain must be linear (one child per level); branching is
rejected. The element is inserted as the only child of the
innermost wrapper.

```text
Chain     := TagName "[" [ Chain ] "]"
```

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!ADD_PARENT Note[]` | `P[Span]` | `P[Note[Span]]` |
| `!ADD_PARENTS Reference[Link[P[]]]` | `Sect[Span]` | `Sect[Reference[Link[P[Span]]]]` |

### !ARTIFACT

Converts the scribbled element and its entire subtree to artifacts,
removing them from the structure tree. Empty ancestor elements left
behind are pruned automatically.

```text
Instruction := "!ARTIFACT"
```

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!ARTIFACT` | `P[Span[MCR₁]]` | *(element and MCRs removed; content marked as artifact)* |

### !FLATTEN

Hoists every leaf element in the scribbled element's subtree to become
an immediate child of it, in document order, and removes the
intermediate elements the hoisting empties. A leaf is a structure
element with no structure-element children of its own, so its own
marked content rides along; children that are already leaves, and the
scribbled element's own marked content, stay where they are. The
scribbled element survives, so a receipt naming the number of hoisted
elements is written, e.g. `INST FLATTEN hoisted 14`.

An intermediate element that holds marked content directly alongside
nested elements is refused before any mutation, since hoisting its
leaves would orphan that content. Nothing else is refused: `!FLATTEN`
knows no roles, so it will just as readily take apart a genuine list
or table as a spurious one. Review the subtree in `--dump-tree` before
scribbling it.

Because the whole subtree is rewritten, instruction scribbles on
descendants are not executed in the same run.

```text
Instruction := "!FLATTEN"
```

Typical use: undoing a false-positive list conversion. Scribble
`!FLATTEN` on the *parent* of the list — the element the paragraphs
should end up under — and the `L > LI > LBody` wrappers come apart,
leaving the paragraphs in place. Note that a later run of
`MistaggedListCheck` sees the restored paragraphs afresh and may
convert them again; keep that check in `--skip-checks` for the
document once its output has been triaged.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!FLATTEN` on `Sect` | `Sect[L[LI[LBody[P₁]], LI[LBody[P₂]]]]` | `Sect[P₁, P₂]` |
| `!FLATTEN` on `Sect` | `Sect[H1[MCR₁], Div[P₁[MCR₂], Div[P₂[MCR₃]]]]` | `Sect[H1[MCR₁], P₁[MCR₂], P₂[MCR₃]]` |
| `!FLATTEN` on `Div` | `Div[MCR₁, Span[Span[MCR₂]]]` | *(refused: `Span` mixes marked content with nested elements)* |

### !MERGE

Merges the scribbled element into its preceding sibling: its children
move to that sibling and the emptied element is removed. To merge a run,
mark every element after the first; each one then merges leftward into
the surviving first element.

The preceding sibling must be an element with the same mapped role.
Merging into a different role or loose marked content is refused without
changing the tree. The content stream is not changed, and moved content
keeps its page when the siblings span pages.

The survivor receives `INST MERGE absorbed N`, where `N` is the number of
elements absorbed. The count is state rather than history: it is replaced
each time, so a run collapsing leftward ends with one receipt naming the
total.

```text
Instruction := "!MERGE"
```

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!MERGE` on P₂ | `Sect[P₁[MCR₁], P₂[MCR₂]]` | `Sect[P₁[MCR₁, MCR₂]]` |
| `!MERGE` on P₂ and P₃ | `Sect[P₁[MCR₁], P₂[MCR₂], P₃[MCR₃]]` | `Sect[P₁[MCR₁, MCR₂, MCR₃]]` |
| `!MERGE` on H1₂ | `Sect[P₁, H1₂]` | *(refused: preceding sibling is P, not H1)* |

### !SPLIT_LINES [\<N\>]

Splits marked-content blocks that lump several one-line list items
into one item per line. Acrobat's auto-tagger often marks a whole run of
one-line items (e.g. course listings) as a single MCR under one `P`;
retagging them by hand is laborious. This instruction rewrites the
content stream so each line becomes its own `BDC...EMC` block with a
fresh MCID, and gives each line its own `LI > LBody > P`.

The scribbled element's kids must all be MCRs (one or more, e.g. a
`P` holding two MCRs that an artifact interrupts); each block is
split at its line boundaries and every resulting line becomes an
item, in reading order. If the element already
sits inside an `LBody > LI > L` chain, the new items join that list
right after the original item. A bare element (e.g. a `P` under a
container) is first wrapped in a new `L > LI > LBody` at its own
position.

The element's MCRs may span pages (e.g. a lumped listing that runs
over a page break); each block is located and split in its own page's
content stream, and moved or newly minted MCRs carry an explicit
`/Pg` where a bare MCID number would resolve to the wrong page.
Intervening content between the MCRs (artifacted text, other
marked-content blocks) is likewise fine — each block is located and
split independently.

Line boundaries are text-positioning operators (`Td`, `TD`, `T*`,
`Tm`, `'`, `"`) that follow shown text. The spec is optional: without
it, the blocks split at every detected line. A plain number is the
expected total line count and acts as a safety catch — if the actual
count differs, for example because an item wraps onto a second line,
the instruction refuses rather than mis-split, and the scribble stays
for review.

A comma-separated spec gives each item's line count in reading order
(counting lines across all the element's MCRs), so wrapped items can
be expressed: `1,1,2` makes two one-line items and one two-line item.
Consecutive lines grouped into one item keep a single unsplit block;
a multi-line item whose lines straddle an MCR boundary holds one MCR
per fragment. Whether a line is a new item or a continuation is a
judgment call the content stream cannot settle, which is why it is
expressed in the scribble. The sizes must sum to the actual line
count, or the instruction refuses.

```text
Instruction := "!SPLIT_LINES" [ Spec ]
Spec        := N                          (* expected total lines, one item each, ≥ 2 *)
             | N { "," N }                (* per-item line counts, in reading order *)
```

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!SPLIT_LINES 3` | `L[LI[LBody[P[MCR₁₋₃]]]]` | `L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂]]], LI[LBody[P[MCR₃]]]]` |
| `!SPLIT_LINES 3` | `Sect[P[MCR₁₋₃]]` | `Sect[L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂]]], LI[LBody[P[MCR₃]]]]]` |
| `!SPLIT_LINES 1,2` | `Sect[P[MCR₁₋₃]]` | `Sect[L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂₋₃]]]]]` — lines 2-3 stay one block |
| `!SPLIT_LINES 2,1` | `Sect[P[MCR₁₋₂, MCR₃]]` | `Sect[L[LI[LBody[P[MCR₁₋₂]]], LI[LBody[P[MCR₃]]]]]` |

### !UNLINK

Unwraps a `Link` element: promotes its non-`OBJR` kids to the parent
at the Link's original position, removes the Link element, and
deletes the associated Link annotation from the page's `/Annots`
array. The element is destroyed, so no receipt is written. Only
valid on `Link` elements; applying it elsewhere raises an error.

```text
Instruction := "!UNLINK"
```

Typical use: pair with `InvalidLinkUriCheck`, which flags Link
elements whose `/A /URI` is not a plausible web address. Review
the `LINK_URI` scribbles via `--dump-tree`, rewrite legitimate
offenders as `!UNLINK`, then rerun the tool.

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!UNLINK` | `P[Link[Span[MCR₁], OBJR]]` | `P[Span[MCR₁]]` *(annotation also removed from page)* |

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
