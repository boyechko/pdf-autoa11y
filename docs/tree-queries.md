# Structure Tree Exports and Queries

Export the structure tree directly from a PDF:

```bash
./pdf-autoa11y --dump-tree=xml catalog/catalog.pdf > catalog/catalog.tree.xml
```

The export contains full text, rather than the shortened excerpts shown in
the human-readable diagram. It preserves the reading order of structure
elements, marked-content references, and annotation references. It does not
modify the PDF.

## Finding Patterns in Context

To find lists inside `Art #4291` with an H3, H4, or H5 directly inside an
item's LBody:

```bash
query="//Art[@id='4291']//L[LI/LBody/*[self::H3 or self::H4 or self::H5]]"
./pdf-autoa11y --query-tree="$query" catalog/catalog.tree.xml
```

The same command accepts `catalog/catalog.pdf` directly. Exporting XML first
avoids extracting PDF content again for every query. XML input filenames
must end in `.xml`.

Each matching element prints as a complete subtree, including text, object
numbers, annotations, and page separators. Several matching headings in one
list select that list only once. Results follow document order. A match
inside another match appears only within that match's subtree, so query
results never overlap.

Use `--dump-tree=plain` for plain indentation, or `--dump-tree=xml` to export
the matches for further queries:

```bash
./pdf-autoa11y --query-tree="$query" --dump-tree=plain catalog/catalog.tree.xml
./pdf-autoa11y --query-tree="$query" --dump-tree=xml catalog/catalog.tree.xml \
  > matching-lists.tree.xml
```

Queries use XPath 1.0 and must select structure elements. A query with no
matches succeeds with no diagram output; XML output contains an empty
container. Malformed queries, scalar results such as `count(//L)`, and
selections of attributes, content references, or the `StructureTree`
wrapper are errors.

Useful expressions:

```xpath
//L
//Art[@id='4291']//L
//*[@role and @id='65848']
//L[LI/LBody/H3]
//L[LI/LBody//H3]
//L[.//Content[contains(., 'Health Studies')]]
//*[@role and contains(@scribble, '!BREAK_OUT')]
```

`/` selects direct children; `//` searches descendants. For example,
`LI/LBody/H3` requires H3 to be an immediate child of LBody, whereas
`LI/LBody//H3` also finds headings inside intervening wrappers. Text
predicates search the full exported text, even when the diagram excerpt
is shortened.

## XML Format

Exports use a `StructureTree` wrapper with `version="1"`. Its `root` attribute
is `element` for a single root element, or `container` for query results or
a structure root without a Document element.

```xml
<StructureTree version="1" root="element">
  <Document role="Document" id="4278" page="189">
    <Art role="Art" id="4291" page="189">
      <L role="L" id="65848" page="189">
        <LI role="LI" id="65849" page="189">
          <LBody role="LBody" id="65850" page="189">
            <H3 role="H3" id="19356" page="189">
              <Content mcid="19" kinds="text" page="189">Program of Study</Content>
            </H3>
          </LBody>
        </LI>
      </L>
    </Art>
  </Document>
</StructureTree>
```

Each structural element uses its mapped role as its XML tag and `role`
attribute. `rawRole` records the original role when it differs. A role that
cannot be used as an XML name, or that is one of the format's own tags
(`StructureTree`, `Content`, `ObjectReference`), uses `StructElem` as its
tag, retaining the mapped role in `role`.

Optional attributes include `id` (PDF object number), `scribble` (including
the `__` prefix), `title`, and table-cell `scope`. An element written inline
as a direct object, rather than as an indirect object, has no object number
and so no `id`. An element's `page` is its first content page; unknown pages
are omitted.

`Content` children carry the page-scoped `mcid`, space-separated `kinds`
(`text`, `image`, `path`), and full text. An orphaned reference carries
`orphaned="true"`. `ObjectReference` children carry the annotation's `id`,
`subtype`, and page. Link destinations use `destinationType` (`uri`, `page`,
or `named`) and `destination`; preserved original destinations use
`originalUriType` and `originalUri`.

Characters XML 1.0 cannot represent are replaced with U+FFFD. The snapshot
contains the data needed for tree inspection; it is not a complete PDF
serialization or a format for applying edits back to a PDF.
