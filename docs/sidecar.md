# Sidecar Config

PDF-Auto-A11y reads a per-PDF sidecar config file automatically when
it exists alongside the input. For `document.pdf`, the tool looks for
`document.autoa11y.yaml` in the same directory.

The sidecar lets you persist per-file settings — which checks to skip,
custom role mappings, or custom artifact patterns — without repeating
CLI flags on every run.

## Creating a Template

```bash
./pdf-autoa11y --create-sidecar document.pdf
# Creates: document.autoa11y.yaml
```

The generated file is fully commented-out. Uncomment and fill in the
keys you need.

## Keys

### skip-checks

Skip specific checks by class name. Useful for silencing checks that
are not relevant to a particular document.

```yaml
skip-checks:
  - NeedlessNestingCheck
  - MissingPagePartsCheck
```

### only-checks

Run only the listed checks. All other checks are skipped.

```yaml
only-checks:
  - SchemaValidationCheck
  - StructTreeOrderCheck
```

### include-checks

Enable optional checks that are not part of the default pipeline.

```yaml
include-checks:
  - ClearRoleMapCheck
  - MisartifactedTextCheck
```

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

When both a sidecar config and CLI flags are present, CLI flags take
precedence for the same key:

| Key | CLI flag | Precedence |
|---|---|---|
| `skip-checks` | `--skip-checks` | CLI wins |
| `only-checks` | `--only-checks` | CLI wins |
| `include-checks` | `--include-checks` | CLI wins |
| `role-map` | _(none)_ | Sidecar only |
| `artifact-patterns` | _(none)_ | Sidecar only |

When `--only-checks` is given on the CLI, the sidecar's `skip-checks`
are also ignored — the explicit check list is treated as the complete
specification.
