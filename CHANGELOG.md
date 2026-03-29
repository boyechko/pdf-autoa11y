# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows [Semantic
Versioning](https://semver.org/).

## Unreleased

### Added
- Added `ScribbledInstructionCheck` and `ScribbledInstructionFix` to carry out
  structural instructions encoded in /T scribbles. Supports a workflow where a
  remediator annotates elements in Acrobat and the tool executes the
  instructions. Supported instructions: `!ADD_CHILD <tags>` (adds structure
  elements as children, e.g., `Reference[Lbl[]]`) and `!ADD_PARENT <tag>`
  (wraps element in a new parent, e.g., `Note[]`).
- Added `ArtifactFlaggedElementsCheck` to automatically artifact elements
  whose /T scribble is `__artifact`, supporting a manual remediation workflow
  where headers, footers, and other decorative content are flagged in Acrobat.
  Runs before `EmptyElementCheck` so emptied parents get cleaned up.
- Added `artifact-patterns` sidecar config key for specifying custom text
  artifact patterns (name-to-regex map in YAML) that replace the built-in
  defaults. Converted built-in patterns from `artifact_patterns.txt` to
  `artifact_patterns.yaml`.
- Added space-only leaf element detection to `MistaggedArtifactCheck`:
  elements whose only children are MCRs containing nothing but whitespace
  glyphs are now flagged as artifacts. Common in InDesign-produced PDFs.
- Added optional `MisartifactedTextCheck` to detect digit-only text inside
  artifact blocks that may have been incorrectly artifacted. Inserts a
  scribbled `Lbl` signpost (`__misartifacted`) near the neighboring structure
  element to mark where manual de-artifacting is needed. Activate via
  `--include-checks=MisartifactedTextCheck`.

### Fixed
- Fixed `--only-checks` ignoring sidecar check replacements (e.g.,
  `artifact-patterns`). The `only` filter was not consulting the `skip` set,
  causing both the default and sidecar-injected check to run.

### Added
- Added `StaleScribbleCheck` to flag structure elements with workflow
  scribbles (/T values starting with `__`) left over from manual remediation
  in Acrobat. Runs last in the pipeline; no automatic fix.
- Added structure tree order check and fix (`StructTreeOrderCheck` /
  `StructTreeOrderFix`) that detects and corrects siblings out of reading order
  by sorting them based on their first marked-content appearance (page number,
  then MCID within page).
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
- Added `--include-checks` CLI option and sidecar `include-checks:` key to
  activate optional checks that don't run by default.
- Added optional check registry (`ProcessingDefaults.optionalChecks()`) so new
  opt-in checks only need a class and one registry line — no CLI changes.
- Added `ClearRoleMapCheck` optional check to remove `/RoleMap` from the
  structure tree root, activatable via `--include-checks=ClearRoleMapCheck`
  or sidecar config.
- Added `ReplaceRoleMapCheck` to replace `/RoleMap` with custom mappings
  specified in the sidecar config under the `role-map:` key.
- Added sidecar `role-map:` key supporting both a mapping dictionary and the
  literal `clear` to remove the role map entirely.
- Added `--create-sidecar` CLI option to generate a template
  `<basename>.autoa11y.yaml` file with all known checks and options listed as
  commented-out examples.
- Added per-PDF sidecar config files (`<basename>.autoa11y.yaml`) for persistent
  check configuration, so skip/only-checks settings travel with each input file.
- Added configurable per-input pipeline temp directories via
  `AUTOA11Y_PIPELINE_DIR` or `-Dautoa11y.pipeline.dir=...`.
- Added a "Resolved Breakdown" section to the accessibility report summary.

### Changed
- `--skip-checks` and `--only-checks` now apply to all checks, including
  document-level checks (previously only structure tree checks were filterable).
- `--only-checks` now selects from all known checks (default and optional),
  so `--only-checks=ClearRoleMapCheck` works without also needing
  `--include-checks`.
- Simplified `ProcessingResult` to three unified issue lists (`detectedIssues`,
  `appliedFixes`, `remainingIssues`) instead of separate document-level and
  tag-level fields.
- Accessibility report now groups all issues by type in a single section instead
  of splitting into "Document-Level" and "Structure Tree" sections.
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

### Fixed
- Fixed `--only-checks` ignoring sidecar check replacements (e.g.,
  `artifact-patterns`). The `only` filter was not consulting the `skip` set,
  causing both the default and sidecar-injected check to run.
- Fixed default `page-number` artifact pattern matching bare numbers like "2" or
  "15". The `Page` prefix is now required, preventing false positives on list
  labels, footnote markers, and other legitimate numeric content.
- Fixed `--dump-tree` page markers appearing after grouping elements instead of
  before them, so the output now correctly shows which page a subtree belongs to.
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
