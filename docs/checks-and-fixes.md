# Checks and Fixes

PDF-Auto-A11y runs a series of checks against each input PDF, reporting
issues and automatically applying fixes where possible. Checks can be
selectively enabled or disabled via CLI options:

```bash
# Skip specific checks
./pdf-autoa11y --skip-checks=NeedlessNestingCheck,MissingPagePartsCheck input.pdf

# Run only specific checks, in the order given
./pdf-autoa11y --only-checks=StructTreeOrderCheck,SchemaValidationCheck input.pdf
```

Without either `--skip-checks` or `--only-checks`, PDF-Auto-A11y runs
the default checks.

## Available Checks

Checks are grouped below by what they deal with. The *Name* column gives
the exact, case-sensitive name to pass to the CLI options and the sidecar.
Checks marked *optional* do not run by default; add them with
`--include-checks` or the sidecar's `checks:` key (see `docs/sidecar.md`).
Each check runs as its own pipeline step, in the order defined by
`ProcessingDefaults`.

### Preconditions

| Check | Description | Fix | Name |
|---|---|---|---|
| Image Only Document | Detects scanned/image-only PDFs that need OCR | None (fatal) | `ImageOnlyDocumentCheck` |
| Structure Tree Exists | Verifies the PDF has a structure tree | None (fatal) | `StructureTreeExistsCheck` |
| Missing Document | Verifies a Document element exists under the structure tree root | Creates Document element | `MissingDocumentCheck` |

### Document settings

| Check | Description | Fix | Name |
|---|---|---|---|
| Language Set | Verifies the document language is set | Sets language | `LanguageSetCheck` |
| Tagged Pdf | Verifies the PDF is marked as tagged | Marks as tagged | `TaggedPdfCheck` |
| PDF/UA Conformance | Detects false PDF/UA conformance claims in XMP metadata | Strips false claims | `PdfUaConformanceCheck` |
| Tab Order | Verifies tab order follows structure tree order | Sets tab order | `TabOrderCheck` |

### Content and fonts

| Check | Description | Fix | Name |
|---|---|---|---|
| Badly Mapped Ligature | Detects fonts with broken ligature-to-Unicode mappings | Remaps ligatures | `BadlyMappedLigatureCheck` |
| Badly Marked Content | Detects one marked-content block lumping lines of different font sizes | Splits into one same-role element per size run | `BadlyMarkedContentCheck` |
| Orphaned Content | Detects content references whose page no longer exists | Removes the references | `OrphanedContentCheck` |
| Mistagged Artifact | Detects decorative or noisy content that should be artifacts | Converts to artifacts | `MistaggedArtifactCheck` |
| Misartifacted Text (*optional*) | Detects digit-only text artifacted by an earlier run | Restores as `Reference`/`Lbl` | `MisartifactedTextCheck` |
| Inconsistent Parent Tree (*optional*) | Detects a ParentTree that disagrees with the structure tree | Rebuilds the ParentTree | `InconsistentParentTreeCheck` |

### Links and annotations

| Check | Description | Fix | Name |
|---|---|---|---|
| Unmarked Link | Detects Link annotations not tagged as structure elements | Creates Link tags | `UnmarkedLinkCheck` |
| Unexpected Widget | Detects non-functional Widget annotation remnants | Removes Widget annotations | `UnexpectedWidgetCheck` |
| Empty Link Tag | Detects Link elements without link description | Moves adjacent text into Link, or moves the annotation into a text-only Link under it | `EmptyLinkTagCheck` |
| Invalid Link URI | Detects Link elements whose `/A /URI` is not a plausible http(s) web address | Writes `LINK_URI` scribble for manual review | `InvalidLinkUriCheck` |
| Badly Split Link | Detects one link split across adjacent Link tags | Merges them into one Link | `BadlySplitLinkCheck` |
| Inline Destinations (*optional*) | Detects named destinations, as left by Web Capture | Replaces them with explicit page destinations | `InlineDestinationsCheck` |

### Reading order

| Check | Description | Fix | Name |
|---|---|---|---|
| Structure Tree Order | Detects structure tree siblings out of reading order (see [below](#structtreeordercheck)) | Reorders siblings by where their content sits on the page | `StructTreeOrderCheck` |

### Text structure

| Check | Description | Fix | Name |
|---|---|---|---|
| Fragmented Paragraph | Detects one paragraph split across adjacent P tags | Merges them into one P | `FragmentedParagraphCheck` |
| Fused Paragraph | Detects several paragraphs fused into one P | Splits at paragraph ends | `FusedParagraphCheck` |
| Mistagged Heading | Detects paragraphs and mis-leveled headings by font size | Retags as the matching heading level | `MistaggedHeadingCheck` |
| Mistagged List | Detects bulleted content that should be lists | Wraps in (sub)list structure, merging split lists | `MistaggedListCheck` |
| Adjacent Lists | Detects adjacent sibling lists | Merges each run into its first list | `AdjacentListsCheck` |
| Unmarked List Item (*optional*) | Detects list items without a bullet or number | Marks them `!BREAK_OUT` for the next run | `UnmarkedListItemCheck` |
| Irregular TOC | Detects TOC contents not nested into a TOCI hierarchy | Rebuilds entries as `TOCI`, nesting by indentation | `IrregularTocCheck` |

### Figures and tables

| Check | Description | Fix | Name |
|---|---|---|---|
| Figure With Text | Detects Figure elements containing text content | Changes Figure role | `FigureWithTextCheck` |
| Missing Alt Text | Detects content images missing alt text | None (manual) | `MissingAltTextCheck` |
| Unscoped Table Header | Detects `TH` cells without a `/Scope` attribute | Scopes a header row as Column and a leading header column as Row; other tables manual | `UnscopedTableHeaderCheck` |

### Tree hygiene

| Check | Description | Fix | Name |
|---|---|---|---|
| Empty Element | Detects empty structure elements | Removes empty elements | `EmptyElementCheck` |
| Schema Validation | Validates elements against the PDF/UA-1 tag schema | Restructures children to match schema | `SchemaValidationCheck` |
| Needless Nesting (*optional*) | Detects unnecessary Part/Sect/Art/Div grouping wrappers | Flattens wrappers, promoting children | `NeedlessNestingCheck` |
| Missing Page Parts (*optional*) | Detects content not grouped into page-level Part elements | Creates Part-per-page grouping | `MissingPagePartsCheck` |
| Clear Role Map (*optional*) | Detects presence of `/RoleMap` (see [below](#rolemap-checks)) | Removes `/RoleMap` | `ClearRoleMapCheck` |
| Replace Role Map (*optional*) | Compares `/RoleMap` to sidecar-supplied mappings | Replaces `/RoleMap` with supplied mappings | `ReplaceRoleMapCheck` |

### Web captures

| Check | Description | Fix | Name |
|---|---|---|---|
| Reorder Web Captures (*optional*) | Reorders pages to a URL order given in the sidecar | Moves pages | `ReorderWebCapturesCheck` |
| Wrap Web Captures (*optional*) | Detects Adobe Web Capture PDFs | Wraps each captured URL's pages in an `Art` | `WrapWebCapturesCheck` |

### Scribbles

| Check | Description | Fix | Name |
|---|---|---|---|
| Scribbled Instruction | Detects `!` instructions scribbled into `/T` | Carries them out (see `docs/scribbled-instructions.md`) | `ScribbledInstructionCheck` |
| Stale Scribble | Detects leftover scribbles | Clears `/T` | `StaleScribbleCheck` |

See `docs/scribbles.md` for how scribbles are written and read.

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
