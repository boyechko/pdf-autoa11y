# Sidecar Config

PDF-Auto-A11y reads a per-PDF sidecar config file automatically when
it exists alongside the input. For `document.pdf`, the tool looks for
`document.autoa11y.yaml` in the same directory.

The sidecar lets you persist per-file settings — which checks to run,
custom role mappings, or custom artifact patterns — without repeating
CLI flags on every run.

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

## Keys

### checks

Ordered list of checks to run. The pipeline runs exactly these checks,
in the order given. When the `checks:` key is absent, the default
pipeline runs (modified by any CLI flags).

```yaml
checks:
  - StructureTreeExistsCheck
  - NeedlessNestingCheck
  - SchemaValidationCheck
```

An explicit empty list (`checks: []`) means "run no checks". Unknown
check names cause the run to fail with an error naming the offender.

### role-map

Manage the PDF's `/RoleMap` entry in the structure tree root. Two
forms are supported:

**Clear**: remove the `/RoleMap` entirely.

```yaml
role-map: clear
```

**Replace**: replace the `/RoleMap` with the specified mappings.
Custom role names map to standard PDF/UA-1 tag names.

```yaml
role-map:
  CustomHeading: H1
  CustomFigure: Figure
```

The `role-map` directive is honored regardless of whether `checks:`
is set, so it always takes effect when present.

### artifact-patterns

Override the built-in text artifact detection patterns. Each entry is
a name and a Java regex. When this key is present, the supplied
patterns **replace** the built-in defaults entirely; they are not
merged.

```yaml
artifact-patterns:
  page-number: '^\s*Page\s+\d+\s*(of\s+\d+)?\s*$'
  footer-url: 'https?://.*\[\d{1,2}/\d{1,2}/\d{4}.*\]'
```

Patterns are matched against the full text content of each structure
element.

## CLI Override Behavior

When the sidecar provides a `checks:` list, it fully specifies the
pipeline and the CLI flags `--skip-checks`, `--only-checks`, and
`--include-checks` are ignored for that run. To use those flags,
remove or comment out the `checks:` key in the sidecar.

| Sidecar key | CLI flag | Behavior when both present |
|---|---|---|
| `checks` | `--skip-checks` / `--only-checks` / `--include-checks` | Sidecar wins |
| `role-map` | _(none)_ | Sidecar only |
| `artifact-patterns` | _(none)_ | Sidecar only |
