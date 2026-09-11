# Remediation Log

Every remediation run appends to a per-document log recording which fix
changed which element, and when. For `document.pdf`, the log is
`document.autoa11y.log`, written beside the output PDF.

## File Discovery

The log name is derived from the document's *lineage* base, not its
filename: trailing `_autoa11y` suffixes are stripped, the same rule the
sidecar config uses. So `document.pdf`, the `document_autoa11y.pdf` it
produces, and anything remediated from that in turn all append to one
`document.autoa11y.log`. A chain of runs stays in one file.

The log is resolved from the *output* path, so an explicit output
directory keeps the log with the PDFs it describes.

To skip logging for a run, pass `--no-log`:

```bash
./pdf-autoa11y --no-log document.pdf
```

Nothing is written when no output PDF is saved, so `--analyze` and the
`--dump-*` modes never touch the log.

## Format

One JSON object per line. Each run writes a `run` record naming the tool
version and the files involved, followed by a `fix` record per changed
element referencing that run's `id`:

```json
{"t":"run","id":"2026-09-11T19:05:41Z","tool":"0.5.0","in":"catalog.pdf","out":"catalog_autoa11y.pdf"}
{"t":"fix","run":"2026-09-11T19:05:41Z","obj":97,"pg":1,"role":"Figure","fix":"FigureWithTextFix","msg":"Changed Figure to P"}
{"t":"fix","run":"2026-09-11T19:05:41Z","obj":97,"pg":1,"role":"P","fix":"MistaggedHeadingFix","msg":"Marked for retagging as H1"}
```

| Field | Meaning |
|-------|---------|
| `obj` | PDF object number of the element; `null` for document-wide fixes |
| `pg`  | 1-based page number, or `null` |
| `role` | the element's role *at the time the fix ran* |
| `fix` | the fix class, or the enclosing check for checks that declare their fix anonymously |
| `msg` | the fix's own description of what it did |

A run record is written even when no fixes applied, so the log shows
that the tool ran and changed nothing.

## Reading It

`obj` matches the object numbers `--dump-tree` prints, so a log entry and
a tree dump can be read side by side:

```bash
# everything that ever touched object 97
grep '"obj":97' catalog.autoa11y.log

# just the most recent run
tail -r catalog.autoa11y.log | awk '/"t":"run"/{exit} {print}'
```

Two properties are worth knowing:

**Roles change; object numbers do not.** The same element can appear
under several roles within one run --- object 97 above starts as a
`Figure` and ends as an `H1`. That is the history, not an
inconsistency.

**Deleted elements leave entries behind.** Fixes remove elements
(artifacting, widget removal, flattening), so the log accumulates
entries pointing at objects that are no longer in the document. The log
is a history, not an index.

Object numbers are stable across this tool's own pipeline but not across other
editors --- Acrobat might renumber objects on save. See
`docs/decisions/0007-keep-remediation-history-in-a-sidecar-log.md`.
