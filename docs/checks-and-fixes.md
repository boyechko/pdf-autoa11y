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
| MistaggedBulletedListCheck | Detects vector bullet glyphs near elements that should be lists | Wraps in list structure |
| ParagraphOfLinksCheck | Detects paragraphs containing only links | Converts to list structure |
| EmptyElementCheck | Detects empty structure elements | Removes empty elements |
| SchemaValidationCheck | Validates elements against the PDF/UA-1 tag schema | Restructures children to match schema |

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
           | Template                     (* nested new wrappers *)
Range     := N                            (* single kid, 1-based *)
           | N ".." M                     (* inclusive range, M ≥ N *)
           | N ".."                       (* from N to last kid *)
```

Range references are only allowed at the top level — nested wrappers
inside a range-bearing wrapper must be empty new structures.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!ADD_CHILDREN Lbl[], LBody[]` | `LI[]` | `LI[Lbl[], LBody[]]` |
| `!ADD_CHILDREN Lbl[1], LBody[2..]` | `LI[MCR₁, MCR₂, MCR₃]` | `LI[Lbl[MCR₁], LBody[MCR₂, MCR₃]]` |
| `!ADD_CHILDREN Lbl[1], Note[], LBody[2..]` | `LI[MCR₁, MCR₂]` | `LI[Lbl[MCR₁], Note[], LBody[MCR₂]]` |
| `!ADD_CHILDREN TD[Caption[]]` | `TR[]` | `TR[TD[Caption[]]]` |

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
