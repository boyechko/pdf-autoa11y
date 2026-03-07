# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows [Semantic
Versioning](https://semver.org/).

## Unreleased

## Added
- Added detection for image-only PDFs and other fatal document-level problems so
  remediation can stop early when OCR or manual intervention is required.
- Added checks for unexpected widget annotations, missing figure alt text, and
  unverified PDF/UA conformance claims.
- Added stronger remediation for mistagged bulleted lists, including fixes for
  bullet-aligned content that should be converted into proper list structure.
- Added cleanup for empty structural elements at the end of processing.
- Added an accessibility report output (`-r/--report`) so processing results can
  be saved as an audit trail.
- Added CLI options to skip checks or run only selected checks.
- Added configurable per-input pipeline temp directories via
  `AUTOA11Y_PIPELINE_DIR` or `-Dautoa11y.pipeline.dir=...`.
- Added a "Resolved Breakdown" section to the accessibility report summary.

## Changed
- Improved CLI and report output to make issue locations, resolutions, and
  summaries clearer and easier to scan.
- Updated structure-tree dump output to show object-reference details more
  clearly.
- Refined paragraph-of-links detection to avoid reporting shorter sequences that
  do not meet the minimum threshold.
- Expanded mistagged-artifact detection to handle path content and individual
  marked-content references more precisely.
- Standardized the tool's terminology around "checks" in the CLI and
  documentation.
- Upgraded iText PDF from `9.3.0` to `9.5.0`.

## Fixed
- Fixed fatal-issue handling so processing stops sooner and reports those
  failures more accurately.
- Fixed widget-removal remediation to also remove annotations from `/AcroForm`.
- Fixed PDF/UA metadata handling so the tool no longer adds conformance claims
  automatically.
- Fixed detailed structure-tree output to preserve `/K` order more reliably.
- Fixed output-file handling so CLI output is copied safely from temporary
  pipeline files.
- Fixed tag-schema validation for several valid structures, including inline
  elements under `LBody`, `Figure` under `Lbl`, and `Form` under `P`.
- Fixed several paragraph/list remediation edge cases, including handling for
  marked-content references inside paragraph-of-links patterns.

## [0.7.0] - 2026-02-17

### Added
- New structural validation architecture based on visitors and a dedicated
  structure tree walker/context.
- New remediation capabilities for common tagging issues, including unmarked
  links, empty link tags, mistagged artifacts, figure-text mismatches, and
  needless nesting.
- New list remediation features: paragraph-run and paragraph-of-links
  detection/fixes, plus page-part normalization.
- New CLI options for analysis and tree inspection output, including
  `-a/--analyze`, `--dump-tree`, and `--dump-tree-detailed`.
- New role-tree utility renderers in `StructureTree` for compact, indented,
  and detailed debug output.
- New goal-driven integration testing flow for remediation outputs.

### Changed
- Refactored `ProcessingService` and related core types for clearer
  construction, defaults, and result handling.
- Consolidated and expanded `StructureTree` utilities, including normalization
  and traversal helpers.
- Updated validation/reporting flow to improve issue descriptions and output
  readability.

### Fixed
- Stopped visitor state leakage by switching visitor construction to
  `Supplier`-based initialization.
- Fixed multiple CLI edge cases around logging, option mapping, and error
  handling.
- Adjusted tag-schema handling and related validation messages.

### Removed
- Replaced several legacy rule implementations with visitor-based equivalents
  where appropriate.

## [0.1.0] - 2025-09-16

### Added
- Initial public release.
