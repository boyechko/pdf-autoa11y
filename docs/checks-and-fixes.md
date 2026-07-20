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
| StructTreeOrderCheck | Detects structure tree siblings out of reading order | Reorders siblings by page and MCID |
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
| EmptyElementCheck | Detects empty structure elements | Removes empty elements |
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
never verifies an element, and the `INST OK` receipt left by executed
instructions does not either.

## ScribbledInstructionCheck

`ScribbledInstructionCheck` detects structure elements whose `/T`
(scribble) value starts with `!` and treats it as a structural
instruction. The fix carries out the instruction and replaces the
scribble with `INST OK`. Scribbles are written in Acrobat's tags
panel and executed by the tool on the next run.

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

All the element's MCRs must lie on the same page; an element whose
marked content spans a page break is refused, and the scribble stays
in place. Intervening content between the MCRs (artifacted text,
other marked-content blocks) is fine — each block is located and
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
array. The element is destroyed, so no breadcrumb is written. Only
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

The structure tree order check sorts siblings by their first
marked-content reference: `(page number, MCID)`. This effectively
fixes **cross-page** ordering problems (e.g., pages appearing as
10, 9, 1, 5 instead of 1, 5, 9, 10).

However, **intra-page** ordering may remain incorrect. MCIDs within a
page reflect the order content was written to the content stream, which
depends on the authoring tool. In InDesign exports, this corresponds to
the order text frames were created or threaded, not the visual reading
order. A heading at the top of a page may have a higher MCID than body
text below it if the heading's text frame was created later.

Fixing intra-page order would require spatial analysis (comparing
y-coordinates and handling multi-column layouts), which is not currently
implemented. Documents with significant intra-page ordering issues may
require manual remediation.
