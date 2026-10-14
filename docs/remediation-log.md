# Remediation Log

Every saved remediation run appends to a per-document log recording detected
issues and their outcomes, organized by check. For `document.pdf`, the log is
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

One complete JSON object per run, on one line. Each run contains `checks`
in pipeline order, including checks that found no issues. Each check contains
`issues` in detection order; this need not match fix execution order.

This example is expanded for readability:

```json
{
  "type": "run",
  "id": "2026-10-11T19:05:41Z",
  "version": "0.6.0",
  "input": "catalog.pdf",
  "output": "catalog_autoa11y.pdf",
  "checks": [
    {
      "check": "FigureWithTextCheck",
      "issues": [
        {
          "type": "FIGURE_WITH_TEXT",
          "message": "Figure contains text",
          "severity": "WARNING",
          "object": 97,
          "page": 1,
          "role": "Figure",
          "status": "resolved",
          "fix": "FigureWithTextFix",
          "resolution": "Changed Figure to P"
        }
      ]
    },
    {"check": "LanguageSetCheck", "issues": []}
  ]
}
```

| Issue field | Meaning |
|-------------|---------|
| `type` | Issue type enum name |
| `message` | Original detection message |
| `severity` | Severity enum name: `INFO`, `WARNING`, `ERROR`, or `FATAL` |
| `object` | PDF object number from the issue's location, or `null` |
| `page` | 1-based page number, or `null` |
| `role` | Structure role when the issue was detected, or `null` |
| `status` | `resolved`, `skipped`, `failed`, or `unresolved` |
| `fix` | Associated fix class, or `null` when no automatic fix exists |
| `resolution` | Outcome message from `Issue.resolution()`, or `null` |

`resolved` means the fix ran successfully. `skipped` means another fix covered
this issue, so its associated fix never ran; the resolution explains the skip.
`failed` means its fix threw, leaving the issue unresolved. `unresolved` means
no fix resolved the issue. The presence of a `fix` field alone does not imply
that the fix ran. Anonymous fixes use their enclosing class name, falling back
to their full class name when necessary.

Locations are captured when the check detects its issues, before any fix runs,
so they describe the element as detected even if a fix later changes or deletes
it; `resolution` says what changed. Snapshots describe issues and their
reported outcomes, not every object a fix changed.

A saved run is recorded even when no fixes applied. Logging still requires an
output PDF to be saved, as described above.

## Reading It

```bash
# Entire most recent run
jq -s '.[-1]' catalog.autoa11y.log

# First issue of the first check in that run
jq -s '.[-1].checks[0].issues[0]' catalog.autoa11y.log

# Every issue for object 97 in the most recent run
jq -s '.[-1].checks[].issues[] | select(.object == 97)' catalog.autoa11y.log

# Every issue on page 12 in the most recent run
jq -s '.[-1].checks[].issues[] | select(.page == 12)' catalog.autoa11y.log

# Successfully applied fixes in the most recent run
jq -s '.[-1].checks[].issues[] | select(.status == "resolved")' \
  catalog.autoa11y.log
```

**Roles change; object numbers do not.** The same object can appear under
several roles within a run. These are snapshots, not an inconsistency.
`object` matches the object numbers printed by `--dump-tree`.

**Deleted elements leave entries behind.** Fixes remove elements, so the log
can refer to objects no longer present in the document. It is a history rather
than an index.

Object numbers are stable across this tool's own pipeline but not across other
editors. See
`docs/decisions/0007-keep-remediation-history-in-a-sidecar-log.md`.
