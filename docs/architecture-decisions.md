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
