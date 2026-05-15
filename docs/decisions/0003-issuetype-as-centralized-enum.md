# `IssueType` as a Centralized Enum

## Context and Problem Statement

`IssueType` is an enum in `issue/`. Every new check that introduces a new issue
type must add a constant here, which means `checks/` and `fixes/` (which depend
on `issue/`) also cause `issue/` to be modified. This is a minor Open-Closed
Principle tension: the dependency arrow points the wrong way for that one edit.
Should `IssueType` be decentralized so checks define their own types?

## Considered Options

* Make `IssueType` a record or interface; each check defines its own constant
* Keep `IssueType` as a centralized enum

## Decision Outcome

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

### Consequences

* Good, because all issue types are discoverable in one file.
* Bad, because adding a new issue type touches `IssueType.java` in addition to
  the check/fix classes.
* Neutral: if the tool evolves toward a plugin model where third parties add
  checks, this decision should be revisited in favor of an interface or record.
