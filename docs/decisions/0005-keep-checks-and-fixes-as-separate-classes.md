# Keep Checks and Fixes as Separate Classes

## Context and Problem Statement

Most checks in `checks/` have a same-named counterpart in `fixes/` (e.g.
`OrphanedContentCheck` ↔ `OrphanedContentFix`), with matching test classes
under both. At a glance this looks like 1:1 duplication --- copyright header,
package boilerplate, and parallel naming --- and raises the question of whether
detection and remediation should live in a single class to avoid
quadruplication once tests are counted.

A closer look at the catalog complicates the "1:1" framing:

* Nine checks have no fix (`ImageOnlyDocumentCheck`, `LanguageSetCheck`,
  `MissingAltTextCheck`, `PdfUaConformanceCheck`, `SchemaValidationCheck`,
  `TabOrderCheck`, `TaggedPdfCheck`, `StructureTreeExistsCheck`,
  `InvalidLinkUriCheck`). They are diagnostic-only --- gates, or issues that
  require human judgement.
* Some checks emit multiple fix types: `MistaggedBulletedListCheck` produces
  both `WrapParagraphRunInList` and `WrapBulletAlignedKidsInLBody`.
* Some fixes are shared across checks: `WrapParagraphRunInList` is constructed
  by both `ListlikeParagraphsCheck` and `MistaggedBulletedListCheck`.
* `SchemaValidationCheck` chooses among several fix classes dynamically via a
  `createIfApplicable` factory.

Should `Check` and `IssueFix` be merged into a single "Rule" abstraction to
eliminate the apparent duplication?

## Considered Options

* Merge each check with its fix into a single class (a "Rule" that both
  detects and remediates).
* Keep `Check` and `IssueFix` as separate abstractions, joined through
  `Issue`, and address real duplication tactically (shared helpers, shared
  test fixtures).

## Decision Outcome

Chosen option: "Keep `Check` and `IssueFix` as separate abstractions", because
the real cardinality between detection and remediation is many-to-many with a
0-or-1 majority case --- not 1-to-1 --- and the two abstractions have
genuinely different lifecycles and responsibilities:

* A `Check` runs inside a `StructTreeContext` during traversal, accumulates
  `Issue`s, and is read-only.
* An `IssueFix` runs later through `CheckEngine`, has a `priority()`, can
  `invalidates(otherFix)`, and participates in an ordered, mutually-aware
  queue. Fixes have no notion of which check produced them once they are in
  the queue.

`Issue` is the seam: it carries detection metadata (type, severity, location,
message) and optionally a fix. Collapsing the two would force every
diagnostic-only check to either pretend to have a fix or carry a no-op fix
branch, and would force shared/multi-fix checks into awkward shapes (one class
with several inner fix strategies, or duplicated detection logic across "rule"
classes that emit different fixes for the same finding).

The apparent duplication is mostly:

* Copyright headers and package boilerplate.
* Naming symmetry (`FooCheck` / `FooFix`), which aids navigation rather than
  costing anything.
* Occasional small helpers duplicated across the pair (e.g. `typeOf(PdfMcr)`
  in `OrphanedContentCheck` and `OrphanedContentFix`), which can be pulled up
  to `StructTree` or `Format` without merging classes.

The split mirrors the well-known Detection vs. Remediation separation
(linter vs. formatter; IDE inspection vs. quick-fix), and the Command pattern
already in play on the fix side (priority, invalidation) does real work that a
merged class would obscure.

### Consequences

* Good, because diagnostic-only checks remain first-class --- no "no-op fix"
  shim, no special casing in `CheckEngine`.
* Good, because shared fixes (`WrapParagraphRunInList`) and multi-fix checks
  (`MistaggedBulletedListCheck`, `SchemaValidationCheck`) are expressible
  without forcing inheritance or duplication.
* Good, because the CLI can expose detection and remediation as separate axes
  (e.g. `--skip-checks`, and a future `--skip-fixes`) without an inverse
  mapping between Rule classes.
* Good, because tests can target detection and remediation independently:
  check tests do not need to assert document mutation; fix tests do not need
  to traverse trees.
* Bad, because two files (plus two test files) exist for the common case of a
  check with a single dedicated fix; new contributors may perceive this as
  duplication.
* Bad, because small helpers occasionally appear on both sides of a pair and
  must be deliberately pulled up rather than allowed to drift.
* Neutral: if CLI wiring or registration grows repetitive, a thin `Rule`
  aggregator pairing a `Check` with a default fix factory could be introduced
  *on top* of the existing abstractions without merging them.

## Postscript (2026-07-10)

`ListlikeParagraphsCheck`, `MistaggedBulletedListCheck`, and
`ParagraphOfLinksCheck` --- three detection strategies for the same
user-facing problem --- have since been consolidated into
`MistaggedListCheck`. This resolved the naming asymmetry at the *check*
level (one check owns all list-detection evidence) while keeping its four
fixes (`WrapParagraphRunInList`, `WrapBulletAlignedKidsInLBody`,
`MergeAdjacentListsFix`, `ParagraphOfLinksFix`) as separate classes, as
this decision prescribes. The check's Javadoc lists its fixes via `@see`;
transformation-named fixes remain the convention for multi-fix checks.
