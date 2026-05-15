# `NeedlessNestingCheck` Emits One Batched Fix

## Context and Problem Statement

Most checks (e.g. `MistaggedArtifactCheck`) emit one `Issue` with a per-element
`IssueFix` for each detection. `NeedlessNestingCheck` and `EmptyElementCheck`
instead collect all target elements during traversal and emit a single `Issue`
whose fix operates on the whole list. This is visibly inconsistent with the
rest of the catalog. Should `NeedlessNestingCheck` be split into per-element
issues and fixes?

## Considered Options

* Split into per-element `Issue`s and per-element `NeedlessNestingFix`
  instances, with a new depth-based tiebreaker added to `IssueFix` so
  `IssueList.applyFixes()` can apply deepest-first within a priority bucket.
* Keep the current batched design: one `Issue`, one `NeedlessNestingFix` that
  owns the full worklist and applies it in reverse-traversal (bottom-up) order.

## Decision Outcome

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

### Consequences

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
