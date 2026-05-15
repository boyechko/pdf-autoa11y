# Keep `issue` as a Separate Package

## Context and Problem Statement

The `issue` package (`Issue`, `IssueFix`, `IssueType`, `IssueLoc`, `IssueSev`,
`IssueList`) contains domain model classes referenced by nearly every package:
`checks/`, `fixes/`, `core/`, and `ui/`. Should it be merged into `validation/`
to reduce the number of packages?

## Considered Options

* Merge `issue/` into `validation/`
* Keep `issue/` as a separate package

## Decision Outcome

Chosen option: "Keep `issue/` as a separate package", because `issue/` is a
domain model package while `validation/` is check-execution infrastructure.
Merging them would force every package that references the issue model
(including `ui/`, which has nothing to do with validation) to depend on
`validation/`. The current separation keeps `issue/` at the bottom of the
dependency graph where it belongs: a small, cohesive set of data carriers that
flow through the whole system.

### Consequences

* Good, because the package stays small (6--7 classes) and tightly focused.
* Bad, because adding a new issue type requires modifying `IssueType` in
  `issue/` (see [ADR-0003](0003-issuetype-as-centralized-enum.md)).
