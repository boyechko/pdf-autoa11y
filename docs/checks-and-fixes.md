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

## Document-Level Checks

These checks examine the PDF as a whole and run before structure tree
checks.

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

## Structure Tree Checks

These checks walk the structure tree and run as individual pipeline
steps, each reading the output of the previous step. Listed in
execution order.

| Check | Description | Fix | Prerequisites |
|---|---|---|---|
| NeedlessNestingCheck | Detects unnecessary Part/Sect/Art/Div grouping wrappers | Flattens wrappers, promoting children | None |
| MissingPagePartsCheck | Detects content not grouped into page-level Part elements | Creates Part-per-page grouping | NeedlessNestingCheck |
| MistaggedArtifactCheck | Detects decorative or noisy content that should be artifacts | Converts to artifacts | None |
| FigureWithTextCheck | Detects Figure elements containing text content | Changes Figure role | None |
| MissingAltTextCheck | Detects content images missing alt text | None (manual) | None |
| EmptyLinkTagCheck | Detects Link elements without link description | Moves adjacent text into Link | None |
| MistaggedBulletedListCheck | Detects vector bullet glyphs near elements that should be lists | Wraps in list structure | None |
| ParagraphOfLinksCheck | Detects paragraphs containing only links | Converts to list structure | None |
| EmptyElementCheck | Detects empty structure elements | Removes empty elements | None |
| SchemaValidationCheck | Validates elements against the PDF/UA-1 tag schema | Restructures children to match schema | None |

## Limitations

### StructTreeOrderCheck

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
