# Sidecar Config

PDF-Auto-A11y reads a per-PDF sidecar config file automatically when
it exists alongside the input. For `document.pdf`, the tool looks for
`document.autoa11y.yaml` in the same directory.

The sidecar lets you persist per-file settings — which checks to run
and how individual checks are configured — without repeating CLI flags
on every run.

## File Discovery

For `document.pdf`, the tool automatically loads `document.autoa11y.yaml`
from the same directory if it exists. The `_autoa11y` output suffix is
stripped when resolving the sidecar name, so the remediated output
`document_autoa11y.pdf` maps to the same sidecar as the original.

To use a different sidecar file, pass `--sidecar`:

```bash
./pdf-autoa11y --sidecar shared.autoa11y.yaml document.pdf
```

This overrides auto-discovery entirely.

## Creating a Template

```bash
./pdf-autoa11y --create-sidecar document.pdf
# Creates: document.autoa11y.yaml
```

The generated file is fully commented-out. Uncomment and fill in the
keys you need.

## Schema

The sidecar has one structural key, `checks:`, plus optional per-check
configuration keys whose names match check class names.

### checks

Ordered list of checks to run. The pipeline runs exactly these checks,
in the order given. When the `checks:` key is absent, the default
pipeline runs (modified by any CLI flags).

```yaml
checks:
  - StructureTreeExistsCheck
  - MistaggedArtifactCheck
  - ReplaceRoleMapCheck
  - SchemaValidationCheck
```

An explicit empty list (`checks: []`) means "run no checks". Unknown
check names cause the run to fail with an error naming the offender.

### Per-check configuration

To configure a check that accepts parameters, add a top-level key
named after the check's class. The value is a flat string-to-string
mapping passed to the check at construction time.

```yaml
MistaggedArtifactCheck:
  page-number: '^\s*Page\s+\d+\s*(of\s+\d+)?\s*$'
  footer-url: 'https?://.*\[\d{1,2}/\d{1,2}/\d{4}.*\]'

ReplaceRoleMapCheck:
  CustomHeading: H1
  CustomFigure: Figure

StaleScribbleCheck:
  scope: TOOL_AUTHORED
```

The check still has to appear in `checks:` (or in the default pipeline
when `checks:` is absent) for the configuration to take effect. A
side-key for a check that does not accept configuration is logged and
ignored.

#### Configurable checks

| Check | Config shape | Effect |
|---|---|---|
| `MistaggedArtifactCheck` | `name: regex` map | Replaces built-in artifact patterns |
| `ReplaceRoleMapCheck` | `customRole: standardRole` map | Replaces the PDF's `/RoleMap` |
| `StaleScribbleCheck` | `scope: ALL` or `scope: TOOL_AUTHORED` | Narrows which scribbles count as stale |

`StaleScribbleCheck` clears every scribble by default (`scope: ALL`),
which suits a final-output pass. Set `scope: TOOL_AUTHORED` for
mid-workflow runs to clear only the tool's own scribbles (those
carrying the leading `:` mark) and leave hand-written notes and
`!` instructions in place. The value is case-insensitive; an
unrecognized value aborts the run rather than silently falling back to
`ALL`.

To clear the role map without specifying replacements, list
`ClearRoleMapCheck` in `checks:` (it accepts no configuration).

## CLI Override Behavior

When the sidecar provides a `checks:` list, it fully specifies the
pipeline and the CLI flags `--skip-checks`, `--only-checks`, and
`--include-checks` are ignored for that run. To use those flags,
remove or comment out the `checks:` key in the sidecar.

| Sidecar key | CLI flag | Behavior when both present |
|---|---|---|
| `checks` | `--skip-checks` / `--only-checks` / `--include-checks` | Sidecar wins |
| Per-check side-keys | _(none)_ | Sidecar only |
