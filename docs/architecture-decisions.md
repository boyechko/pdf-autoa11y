# Architecture Decision Records

This file records architectural decisions for PDF-Auto-A11y using the
[MADR](https://adr.github.io/madr/) (minimal) format. Each entry captures the
context, considered alternatives, and chosen outcome so future contributors
understand *why* the code is shaped the way it is.

For the current architecture overview, see `CONTRIBUTING.md`.

## ADR-001: Keep `issue` as a separate package (2026-03-14)

### Context and Problem Statement

The `issue` package (`Issue`, `IssueFix`, `IssueType`, `IssueLoc`, `IssueSev`,
`IssueList`) contains domain model classes referenced by nearly every package:
`checks/`, `fixes/`, `core/`, and `ui/`. Should it be merged into `validation/`
to reduce the number of packages?

### Considered Options

* Merge `issue/` into `validation/`
* Keep `issue/` as a separate package

### Decision Outcome

Chosen option: "Keep `issue/` as a separate package", because `issue/` is a
domain model package while `validation/` is check-execution infrastructure.
Merging them would force every package that references the issue model
(including `ui/`, which has nothing to do with validation) to depend on
`validation/`. The current separation keeps `issue/` at the bottom of the
dependency graph where it belongs: a small, cohesive set of data carriers that
flow through the whole system.

#### Consequences

* Good, because the package stays small (6--7 classes) and tightly focused.
* Bad, because adding a new issue type requires modifying `IssueType` in
  `issue/` (see ADR-003).

## ADR-002: `ProcessingDefaults` as an explicit registry (2026-03-14)

### Context and Problem Statement

Every new check must be registered in `ProcessingDefaults.allChecks()`. Should
the registry use auto-discovery to stay closed for modification, or remain
hand-maintained?

### Considered Options

* `ServiceLoader` / classpath scanning
* Annotation-based registration (`@AutoRegister`)
* Hand-maintained explicit list

### Decision Outcome

Chosen option: "Hand-maintained explicit list", because check ordering matters
--- prerequisites must appear before the checks that depend on them, and the
pipeline processes checks in list order. An explicit list makes that ordering
readable, auditable, and easy to reason about. Auto-discovery mechanisms would
obscure the ordering and add infrastructure complexity disproportionate to the
project's size.

The registry's single responsibility *is* to be the catalog of checks.
Modifying it when a check is added is expected, low-risk, and typically a
one-line change.

#### Consequences

* Good, because the full check pipeline is visible in one place.
* Good, because adding a check is a one-line edit in `ProcessingDefaults`.
* Bad, because adding a check requires touching a file outside the check's own
  package.
* Neutral: if the check count grows large (dozens), this decision could be
  revisited.

## ADR-003: `IssueType` as a centralized enum (2026-03-14)

### Context and Problem Statement

`IssueType` is an enum in `issue/`. Every new check that introduces a new issue
type must add a constant here, which means `checks/` and `fixes/` (which depend
on `issue/`) also cause `issue/` to be modified. This is a minor Open-Closed
Principle tension: the dependency arrow points the wrong way for that one edit.
Should `IssueType` be decentralized so checks define their own types?

### Considered Options

* Make `IssueType` a record or interface; each check defines its own constant
* Keep `IssueType` as a centralized enum

### Decision Outcome

Chosen option: "Keep `IssueType` as a centralized enum", because the OCP cost
is one line per new check --- adding an enum constant with a group label. In
return, the enum provides:

* A single, scannable catalog of every issue the tool can report.
* Type-safe identity for test assertions (`i.type() == IssueType.X`).
* Natural grouping keys for reports and UI.

The project does not use `switch` exhaustiveness on `IssueType`, so the enum's
compile-time completeness guarantee is not load-bearing. But the readability and
discoverability benefits outweigh the minor OCP friction at the current project
scale.

#### Consequences

* Good, because all issue types are discoverable in one file.
* Bad, because adding a new issue type touches `IssueType.java` in addition to
  the check/fix classes.
* Neutral: if the tool evolves toward a plugin model where third parties add
  checks, this decision should be revisited in favor of an interface or record.

## ADR-004: `NeedlessNestingCheck` emits one batched fix (2026-04-23)

### Context and Problem Statement

Most checks (e.g. `MistaggedArtifactCheck`) emit one `Issue` with a per-element
`IssueFix` for each detection. `NeedlessNestingCheck` and `EmptyElementCheck`
instead collect all target elements during traversal and emit a single `Issue`
whose fix operates on the whole list. This is visibly inconsistent with the
rest of the catalog. Should `NeedlessNestingCheck` be split into per-element
issues and fixes?

### Considered Options

* Split into per-element `Issue`s and per-element `NeedlessNestingFix`
  instances, with a new depth-based tiebreaker added to `IssueFix` so
  `IssueList.applyFixes()` can apply deepest-first within a priority bucket.
* Keep the current batched design: one `Issue`, one `NeedlessNestingFix` that
  owns the full worklist and applies it in reverse-traversal (bottom-up) order.

### Decision Outcome

Chosen option: "Keep the current batched design", because the payoff of
splitting is essentially per-element location reporting, and that benefit is
low for grouping elements, which have no meaningful page location (they are
pure structure). The cost of splitting is non-trivial:

* A new ordering hook (`applyOrder()` or similar) on `IssueFix`, plus a
  secondary sort in `IssueList.applyFixes()`.
* Defensive stale-reference guards at the top of every per-element
  `apply()` --- parent-null checks, kid-index checks, role-still-grouping
  checks --- to survive sibling mutations that the batched design avoids by
  iterating over a snapshot inside one transaction.
* Extra care around `PdfStructElem.getKids()` caching (see memory note), since
  each per-element fix would re-read state after earlier mutations.

The batched design encodes a simple contract --- "collect top-down, apply
bottom-up" --- inside one object, and keeps the mutation to a single loop where
ordering is obvious. `EmptyElementCheck` follows the same pattern for the same
reason (`pruneEmpty` cascades upward).

#### Consequences

* Good, because the fix is simple and robust: one snapshot, one reverse loop,
  no inter-fix ordering plumbing.
* Good, because `NeedlessNestingFix` and `EmptyElementFix` share a consistent
  "structural batch" shape, distinct from per-node semantic fixes.
* Bad, because the two batch checks are superficially inconsistent with the
  rest of the catalog; new contributors may wonder why.
* Bad, because the final report cannot point at individual flattened wrappers
  --- only an aggregate count.
* Neutral: if per-element provenance ever becomes valuable (e.g. an
  interactive UI that lets users approve individual flattenings), this
  decision should be revisited together with the `applyOrder()` hook design.
