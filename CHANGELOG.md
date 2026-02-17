# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows [Semantic
Versioning](https://semver.org/).

## Unreleased

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
