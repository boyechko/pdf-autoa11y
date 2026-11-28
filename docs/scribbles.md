# Scribbles

A scribble is a note kept in a structure element's `/T` key. The tool
writes scribbles to record what its fixes did and what its checks found,
and reads scribbles written by hand in Acrobat's tags panel to take
instructions or skip reviewed subtrees.

## Scribbles and plain titles

A `/T` value counts as a scribble only when it carries the `__` prefix.
A plain title left by the authoring tool is not a scribble: no check
touches it, and `--dump-tree` shows it as `title "..."` to keep the two
apart.

## Verified elements: the `OK` scribble

An element whose scribble has a user-authored segment that is exactly
`OK` (e.g. `__OK`, or `__OK // check figure later` to carry a note in a
separate segment) is treated as reviewed to be correct. The
structure-tree walker skips the element and its entire subtree, so no
check sees it and no fix touches it — including `StaleScribbleCheck`,
so the mark itself persists across runs. Remove the scribble to bring
the subtree back under remediation.

Only a user-authored mark counts: a tool-authored scribble (leading `:`)
never verifies an element.

## Segments and tags

A scribble holds one or more segments separated by ` // `. Every
tool-authored segment opens with an upper-case tag naming what wrote it,
so a segment can be found and replaced without matching its prose. The
tag is what `clearScribbleSegments` keys on.

Segments come in two kinds:

- **State** describes the element as it now stands, and is replaced each
  time it is written.
- **Event** records something a fix or check did, and accumulates. A list
  touched by two fixes carries both, so the scribble reads as a short
  history ending in the current state, e.g.
  `__:WRAP paragraph run // MERGE lists // LIST 7 items`.

| Tag | Kind | Written by |
|---|---|---|
| `LIST` | state | every list-producing fix, as its last step |
| `WRAP` | event | fixes that wrap content into a list |
| `SPLIT` | event | fixes that split one element into several |
| `MERGE` | event | fixes that fold one element into another |
| `INST` | event | `ScribbledInstructionFix`, as an instruction receipt |
| `SCHEMA` | event | `SchemaValidationCheck` findings |
| `LINK_URI` | event | `InvalidLinkUriCheck` findings |

Appending a segment that is already present is a no-op, so a fix that
runs twice does not stamp itself twice. A tool segment appended to a
hand-written scribble leaves that scribble user-authored, so the
`SCRIBBLE_VERIFIED_TOKEN` mark and `StaleScribbleCheck` with
`scope: TOOL_AUTHORED` keep working.

## Instructions: ScribbledInstructionCheck

`ScribbledInstructionCheck` carries out structural instructions
scribbled into `/T` with a leading `!` (e.g. `!SET_ROLE H2`), leaving an
`INST` receipt in their place. See `docs/scribbled-instructions.md` for
the supported instructions and their syntax.

## Clearing: StaleScribbleCheck

`StaleScribbleCheck` flags elements that still carry a scribble and
clears the `/T` key. By default it treats every scribble as stale. Set
`scope: TOOL_AUTHORED` in the sidecar (see `docs/sidecar.md`) to limit
it to the tool's own scribbles, so hand-written notes survive the run.
