# Keep Remediation History in a Sidecar Log

## Context and Problem Statement

Establishing "git blame" for remediation --- which fix changed this element,
and when --- needs the history to outlive the run that made it. The document
itself is the obvious place to put it, and the tool already writes per-element
notes there as `/T` scribbles. But a run rewrites the PDF once per check, every
fix must be idempotent, and the output is only saved when something actually
changed. Where should the history live?

## Considered Options

* An append-only sidecar log beside the PDF.
* Per-element history in `/T` scribbles, extending the existing segment grammar.
* Per-element history under a private dictionary key (e.g. `/AutoA11y`).

## Decision Outcome

Chosen option: the sidecar log, because it is the only one that can hold a
durable, timestamped, run-scoped record without putting the tool at odds with
its own contracts.

Scribbles already accumulate event segments and read as a short history
(`__:WRAP paragraph run // MERGE lists // LIST 7 items`), which makes them the
tempting option. But that history is deliberately disposable: they are working
notes in Acrobat's tag panel, and `StaleScribbleCheck` clears `/T` as a matter
of course. Making them durable would fight three things at once. Appending an
already-present segment is a no-op, which is what keeps fixes idempotent --- so
a timestamped entry would break that no-op *and* mark every run dirty,
defeating the `result.dirty()` save gate. And a scribble dies with its element,
so anything a fix deletes takes its own history with it.

A private dictionary key spares the tag panel but keeps the idempotence and
dirty-gate conflict, adds invisibility in Acrobat (which is where the question
gets asked), and conflicts with the second-class-name convention of ISO 32000-1
Annex E, which PDF/UA validators may flag.

### Anchoring entries back to the document

With the history outside the document, entries need an anchor into it. The log
records the PDF object number, captured at fix time --- the same number
`--dump-tree` prints, so a log entry and a tree dump can be read against each
other without translation.

That anchor survives the pipeline. `ProcessingService.remediate()` runs each
check as its own read/write cycle, so a 30-check run rewrites the document 30
times; iText's stamping mode (`new PdfDocument(reader, writer)`, which
`PdfCustodian.openTempForModification` uses for every step) preserves the
existing xref numbering and appends new objects past the maximum rather than
recycling freed slots. Structure paths (role chain plus sibling indices) were
rejected as the anchor for the opposite reason: fixes reorder and reparent
elements (`StructTreeOrderFix`, `NeedlessNestingFix`), so a path captured at fix
time is stale by the end of the run.

### Consequences

* Good, because the history costs one integer per entry and needs no change to
  any check or fix.
* Good, because it outlives the elements a run deletes --- artifacting, widget
  removal and flattening all leave their entries behind.
* Bad, because the record travels separately from the PDF: send the document
  without its log and the history is gone.
* Bad, because the anchor is only valid within this tool's own chain. Acrobat
  and other editors could renumber objects on save, so a log written before an
  external edit cannot be aligned with the file afterward.
* Bad, because roles are mutable while numbers are not --- the same element may
  appear in the log under several roles across a run. Entries therefore record
  the role as it stood at fix time, which is informative rather than
  contradictory, but does require the reader to know that.
* Neutral: deleted elements leave entries pointing at objects no longer in the
  document. The log is a history, not an index, and readers must tolerate
  dangling anchors --- as `git blame` does for deleted lines.
* Neutral: if cross-tool identity ever becomes necessary, a minted id under a
  private key can be layered on as a *pointer* into this log, without moving the
  history into the document.
