# `ProcessingDefaults` as an Explicit Registry

## Context and Problem Statement

Every new check must be registered in `ProcessingDefaults.allChecks()`. Should
the registry use auto-discovery to stay closed for modification, or remain
hand-maintained?

## Considered Options

* `ServiceLoader` / classpath scanning
* Annotation-based registration (`@AutoRegister`)
* Hand-maintained explicit list

## Decision Outcome

Chosen option: "Hand-maintained explicit list", because check ordering matters
--- prerequisites must appear before the checks that depend on them, and the
pipeline processes checks in list order. An explicit list makes that ordering
readable, auditable, and easy to reason about. Auto-discovery mechanisms would
obscure the ordering and add infrastructure complexity disproportionate to the
project's size.

The registry's single responsibility *is* to be the catalog of checks.
Modifying it when a check is added is expected, low-risk, and typically a
one-line change.

### Consequences

* Good, because the full check pipeline is visible in one place.
* Good, because adding a check is a one-line edit in `ProcessingDefaults`.
* Bad, because adding a check requires touching a file outside the check's own
  package.
* Neutral: if the check count grows large (dozens), this decision could be
  revisited.
