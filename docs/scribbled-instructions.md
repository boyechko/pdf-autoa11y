# Scribbled Instructions

`ScribbledInstructionCheck` detects structure elements whose `/T`
(scribble) value starts with `!` and treats it as a structural
instruction. The fix carries out the instruction and replaces the
scribble with a receipt naming what it did — the instruction without
its `!`, plus the outcome where there is one, e.g. `!SET_ROLE H2`
leaves `INST SET_ROLE P -> H2`. Receipts from an earlier run are swept
from the whole tree before each run, so they always describe the last
run only. Scribbles are written in Acrobat's tags panel and executed by
the tool on the next run.

| Instruction | Effect |
|---|---|
| `!ADD_CHILD <template>` | Adds child elements, optionally redistributing existing kids |
| `!ADD_PARENT <chain>` | Wraps the element in a chain of new parents |
| `!ARTIFACT` | Converts the element's subtree to artifacts |
| `!BREAK_OUT` | Takes a stray item out of its list |
| `!FLATTEN` | Hoists the subtree's leaf elements up to the element |
| `!MERGE` | Merges the element into its preceding sibling |
| `!REORDER_KIDS` | Reorders kids by their `REORDER N` scribbles |
| `!SET_ROLE <role>` | Changes the element's role |
| `!SPLIT_LINES [<N>]` | Splits lumped one-line items into a list item per line |
| `!UNLINK` | Unwraps a `Link`, removing its annotation |

Each instruction is its own class in the `fixes.instruction` package,
named after it (e.g. `MergeInstruction`); `ScribbledInstructionFix`
parses the scribble and dispatches to it.

## !ADD_CHILD \<template\>, !ADD_CHILDREN \<template\>

Adds child structure elements under the scribbled element. Wrappers
can be empty (creating new structure) or reference existing kids by
index to redistribute them. Ranges must be ascending, contiguous,
and cover every existing kid exactly once.

```text
Template  := Wrapper { "," Wrapper }
Wrapper   := TagName "[" Body "]"
           | TagName                      (* empty leaf wrapper *)
Body      := Range
           | Template                     (* nested wrappers *)
Range     := N                            (* single kid, 1-based *)
           | N ".." M                     (* inclusive range, M ≥ N *)
           | N ".."                       (* from N to last kid *)
```

Range references always index the scribbled element's direct kids,
regardless of nesting depth.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!ADD_CHILDREN Lbl[], LBody[]` | `LI[]` | `LI[Lbl[], LBody[]]` |
| `!ADD_CHILDREN Lbl[1], LBody[2..]` | `LI[MCR₁, MCR₂, MCR₃]` | `LI[Lbl[MCR₁], LBody[MCR₂, MCR₃]]` |
| `!ADD_CHILDREN Lbl[1], Note[], LBody[2..]` | `LI[MCR₁, MCR₂]` | `LI[Lbl[MCR₁], Note[], LBody[MCR₂]]` |
| `!ADD_CHILDREN TD[Caption[]]` | `TR[]` | `TR[TD[Caption[]]]` |
| `!ADD_CHILDREN LI[LBody[1]], LI[LBody[2]], LI[LBody[3]]` | `L[MCR₁, MCR₂, MCR₃]` | `L[LI[LBody[MCR₁]], LI[LBody[MCR₂]], LI[LBody[MCR₃]]]` |

## !ADD_PARENT \<chain\> / !ADD_PARENTS \<chain\>

Wraps the scribbled element in a chain of new parent elements.
The chain must be linear (one child per level); branching is
rejected. The element is inserted as the only child of the
innermost wrapper.

```text
Chain     := TagName "[" [ Chain ] "]"
```

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!ADD_PARENT Note[]` | `P[Span]` | `P[Note[Span]]` |
| `!ADD_PARENTS Reference[Link[P[]]]` | `Sect[Span]` | `Sect[Reference[Link[P[Span]]]]` |

## !ARTIFACT

Converts the scribbled element and its entire subtree to artifacts,
removing them from the structure tree. Empty ancestor elements left
behind are pruned automatically.

```text
Instruction := "!ARTIFACT"
```

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!ARTIFACT` | `P[Span[MCR₁]]` | *(element and MCRs removed; content marked as artifact)* |

## !BREAK_OUT

Takes a stray item back out of its list. The content of the item's
`LBody` moves up beside the list, and the list is split around it so
reading order holds: items before it stay in the original list, items
after it move to a new list placed after the content, and a list left
empty is removed. To take out a run, mark every stray item; each one
splits whichever list holds it by then, so no empty lists are left
between them.

The item is destroyed, so the receipt goes on each hoisted element
instead, numbered when there are several (`INST BREAK_OUT (1 of 3)`,
`INST BREAK_OUT (2 of 3)`, ...) so that the boundary between items
broken out side by side stays visible. The surviving lists get only
their refreshed `ITEMS` count. Only valid
on `LI` elements inside a list. An item holding anything other than
`LBody` (such as a `Lbl`), or an `LBody` holding marked content
directly, is refused without changing the tree.

```text
Instruction := "!BREAK_OUT"
```

Typical use: undoing a list that ran past its end and swallowed the
headings and prose after it, often along with the items of the next
list.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!BREAK_OUT` on LI₂ | `Div[L[LI₁, LI₂[LBody[H3, P]], LI₃]]` | `Div[L[LI₁], H3, P, L[LI₃]]` |
| `!BREAK_OUT` on LI₁ | `Div[L[LI₁[LBody[P]], LI₂]]` | `Div[P, L[LI₂]]` |
| `!BREAK_OUT` on LI₁ | `L[LI₁[Lbl, LBody[P]]]` | *(refused: item holds a `Lbl`)* |

## !FLATTEN

Hoists every leaf element in the scribbled element's subtree to become
an immediate child of it, in document order, and removes the
intermediate elements the hoisting empties. A leaf is a structure
element with no structure-element children of its own, so its own
marked content rides along; children that are already leaves, and the
scribbled element's own marked content, stay where they are. The
scribbled element survives, so a receipt naming the number of hoisted
elements is written, e.g. `INST FLATTEN hoisted 14`.

An intermediate element that holds marked content directly alongside
nested elements is refused before any mutation, since hoisting its
leaves would orphan that content. Nothing else is refused: `!FLATTEN`
knows no roles, so it will just as readily take apart a genuine list
or table as a spurious one. Review the subtree in `--dump-tree` before
scribbling it.

Because the whole subtree is rewritten, instruction scribbles on
descendants are not executed in the same run.

```text
Instruction := "!FLATTEN"
```

Typical use: undoing a false-positive list conversion. Scribble
`!FLATTEN` on the *parent* of the list — the element the paragraphs
should end up under — and the `L > LI > LBody` wrappers come apart,
leaving the paragraphs in place. Note that a later run of
`MistaggedListCheck` sees the restored paragraphs afresh and may
convert them again; keep that check in `--skip-checks` for the
document once its output has been triaged.

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!FLATTEN` on `Sect` | `Sect[L[LI[LBody[P₁]], LI[LBody[P₂]]]]` | `Sect[P₁, P₂]` |
| `!FLATTEN` on `Sect` | `Sect[H1[MCR₁], Div[P₁[MCR₂], Div[P₂[MCR₃]]]]` | `Sect[H1[MCR₁], P₁[MCR₂], P₂[MCR₃]]` |
| `!FLATTEN` on `Div` | `Div[MCR₁, Span[Span[MCR₂]]]` | *(refused: `Span` mixes marked content with nested elements)* |

## !MERGE

Merges the scribbled element into its preceding sibling: its children
move to that sibling and the emptied element is removed. To merge a run,
mark every element after the first; each one then merges leftward into
the surviving first element.

The preceding sibling must be an element with the same mapped role.
Merging into a different role or loose marked content is refused without
changing the tree. The content stream is not changed, and moved content
keeps its page when the siblings span pages.

The survivor receives `INST MERGE absorbed N`, where `N` is the number of
elements absorbed. The count is state rather than history: it is replaced
each time, so a run collapsing leftward ends with one receipt naming the
total.

```text
Instruction := "!MERGE"
```

**Examples:**

| Scribble | Before | After |
|---|---|---|
| `!MERGE` on P₂ | `Sect[P₁[MCR₁], P₂[MCR₂]]` | `Sect[P₁[MCR₁, MCR₂]]` |
| `!MERGE` on P₂ and P₃ | `Sect[P₁[MCR₁], P₂[MCR₂], P₃[MCR₃]]` | `Sect[P₁[MCR₁, MCR₂, MCR₃]]` |
| `!MERGE` on H1₂ | `Sect[P₁, H1₂]` | *(refused: preceding sibling is P, not H1)* |

## !REORDER_KIDS

Reorders the scribbled element's kids by the `REORDER N` segment on
each kid, where `N` is its new 1-based position. The kid's segment has
no `!`, because it is a position read by the parent's instruction, not
an instruction of its own. Kids without one follow the reordered kids,
in their original order. The `REORDER` segments are consumed, and each
kid that moved receives `INST REORDER old -> new`; the parent gets no
receipt.

```text
Instruction := "!REORDER_KIDS"            (* on the parent *)
Position    := "REORDER" N                (* on each kid to place *)
```

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!REORDER_KIDS` on Sect; `REORDER 1` on P₃ | `Sect[P₁, P₂, P₃]` | `Sect[P₃, P₁, P₂]` |

## !SET_ROLE \<role\>

Changes the scribbled element's role. The role is taken as written,
so non-standard roles are accepted; the receipt names the previous
one, e.g. `INST SET_ROLE P -> H2`.

```text
Instruction := "!SET_ROLE" Role
```

## !SPLIT_LINES [\<N\>]

Splits marked-content blocks that lump several one-line list items
into one item per line. Acrobat's auto-tagger often marks a whole run of
one-line items (e.g. course listings) as a single MCR under one `P`;
retagging them by hand is laborious. This instruction rewrites the
content stream so each line becomes its own `BDC...EMC` block with a
fresh MCID, and gives each line its own `LI > LBody > P`.

The scribbled element's kids must all be MCRs (one or more, e.g. a
`P` holding two MCRs that an artifact interrupts); each block is
split at its line boundaries and every resulting line becomes an
item, in reading order. If the element already
sits inside an `LBody > LI > L` chain, the new items join that list
right after the original item. A bare element (e.g. a `P` under a
container) is first wrapped in a new `L > LI > LBody` at its own
position.

The element's MCRs may span pages (e.g. a lumped listing that runs
over a page break); each block is located and split in its own page's
content stream, and moved or newly minted MCRs carry an explicit
`/Pg` where a bare MCID number would resolve to the wrong page.
Intervening content between the MCRs (artifacted text, other
marked-content blocks) is likewise fine — each block is located and
split independently.

Line boundaries are text-positioning operators (`Td`, `TD`, `T*`,
`Tm`, `'`, `"`) that follow shown text. The spec is optional: without
it, the blocks split at every detected line. A plain number is the
expected total line count and acts as a safety catch — if the actual
count differs, for example because an item wraps onto a second line,
the instruction refuses rather than mis-split, and the scribble stays
for review.

A comma-separated spec gives each item's line count in reading order
(counting lines across all the element's MCRs), so wrapped items can
be expressed: `1,1,2` makes two one-line items and one two-line item.
Consecutive lines grouped into one item keep a single unsplit block;
a multi-line item whose lines straddle an MCR boundary holds one MCR
per fragment. Whether a line is a new item or a continuation is a
judgment call the content stream cannot settle, which is why it is
expressed in the scribble. The sizes must sum to the actual line
count, or the instruction refuses.

```text
Instruction := "!SPLIT_LINES" [ Spec ]
Spec        := N                          (* expected total lines, one item each, ≥ 2 *)
             | N { "," N }                (* per-item line counts, in reading order *)
```

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!SPLIT_LINES 3` | `L[LI[LBody[P[MCR₁₋₃]]]]` | `L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂]]], LI[LBody[P[MCR₃]]]]` |
| `!SPLIT_LINES 3` | `Sect[P[MCR₁₋₃]]` | `Sect[L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂]]], LI[LBody[P[MCR₃]]]]]` |
| `!SPLIT_LINES 1,2` | `Sect[P[MCR₁₋₃]]` | `Sect[L[LI[LBody[P[MCR₁]]], LI[LBody[P[MCR₂₋₃]]]]]` — lines 2-3 stay one block |
| `!SPLIT_LINES 2,1` | `Sect[P[MCR₁₋₂, MCR₃]]` | `Sect[L[LI[LBody[P[MCR₁₋₂]]], LI[LBody[P[MCR₃]]]]]` |

## !UNLINK

Unwraps a `Link` element: promotes its non-`OBJR` kids to the parent
at the Link's original position, removes the Link element, and
deletes the associated Link annotation from the page's `/Annots`
array. The element is destroyed, so no receipt is written. Only
valid on `Link` elements; applying it elsewhere raises an error.

```text
Instruction := "!UNLINK"
```

Typical use: pair with `InvalidLinkUriCheck`, which flags Link
elements whose `/A /URI` is not a plausible web address. Review
the `LINK_URI` scribbles via `--dump-tree`, rewrite legitimate
offenders as `!UNLINK`, then rerun the tool.

**Example:**

| Scribble | Before | After |
|---|---|---|
| `!UNLINK` | `P[Link[Span[MCR₁], OBJR]]` | `P[Span[MCR₁]]` *(annotation also removed from page)* |
