# Split Bullet Recognition from Interpretation

## Context and Problem Statement

Bullet-glyph handling spans three classes: `Content.extractBulletPositionsForPage`
(document/) scans a content stream for bullet glyphs and returns bare
`BulletPosition(x, y)` facts; `DocContext` caches them per page; `BulletMatcher`
(checks/) relates them to structure elements for `BulletCensusDetector` and
`BulletRunDetector`. Is spreading one concept across three files justified, or
should the logic be consolidated in one layer?

## Considered Options

* Keep the split: recognition in `document/`, interpretation in `checks/`.
* Move `BulletMatcher` down into `document/`.
* Move extraction up into `checks/`.

## Decision Outcome

Chosen option: keep the split. The pieces change for different reasons:

* `Content` changes when PDFs draw bullets differently. Its tolerances
  (glyph dimensions, dedup) define what *counts as* a bullet — part of the
  fact.
* `BulletMatcher` changes when detection heuristics misjudge. Its tolerances
  (y-overlap, same-level, sublist indent) define what a bullet's position
  *means* for list structure — check policy.

Moving `BulletMatcher` down would put detection policy in the fact layer,
create a dependency inversion (`document/` → `validation/`), and widen a
two-client package-private class to public. Moving extraction up would drag
content-stream parsing into `checks/` and break standalone testing
(`ContentTest` exercises recognition with no structure tree).

### Consequences

* Good, because either side can be retuned without risking the other.
* Bad, because a reader follows one concept through three files. Mitigate
  with cross-referencing Javadoc, not consolidation.
