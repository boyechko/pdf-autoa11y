# Architecture Decision Records

This file records architectural decisions for PDF-Auto-A11y using the
[MADR](https://adr.github.io/madr/) (minimal) format. Each entry captures the
context, considered alternatives, and chosen outcome so future contributors
understand *why* the code is shaped the way it is.

For the current architecture overview, see `CONTRIBUTING.md`.

Individual ADRs live in [`docs/decisions/`](decisions/):

| ADR | Title |
|-----|-------|
| [0001](decisions/0001-keep-issue-as-separate-package.md) | Keep `issue` as a Separate Package |
| [0002](decisions/0002-processingdefaults-as-explicit-registry.md) | `ProcessingDefaults` as an Explicit Registry |
| [0003](decisions/0003-issuetype-as-centralized-enum.md) | `IssueType` as a Centralized Enum |
| [0004](decisions/0004-needlessnestingcheck-emits-one-batched-fix.md) | `NeedlessNestingCheck` Emits One Batched Fix |
