# Structure Tree Exports and Queries

Export the structure tree directly from a PDF:

```bash
./pdf-autoa11y --dump-tree=xml catalog/catalog.pdf > catalog/catalog.tree.xml
```

The export contains full text, rather than the shortened excerpts shown in
the human-readable diagram. It preserves the reading order of structure
elements, marked-content references, and annotation references. It does not
modify the PDF.

## XML Format

Exports use a `StructureTree` wrapper with `version="1"`. Its `root` attribute
is `element` for a single root element, or `container` for a structure root
without a Document element.

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
the `__` prefix), `title`, and table-cell `scope`. Elements stored directly
in the PDF have no `id`. An element's `page` is its first content page;
unknown pages are omitted.

`Content` children carry the page-scoped `mcid`, space-separated `kinds`
(`text`, `image`, `path`), and full text. An orphaned reference carries
`orphaned="true"`. `ObjectReference` children carry the annotation's `id`,
`subtype`, and page. Link destinations use `destinationType` (`uri`, `page`,
or `named`) and `destination`; preserved original destinations use
`originalUriType` and `originalUri`.

Characters XML 1.0 cannot represent are replaced with U+FFFD. The snapshot
contains the data needed for tree inspection; it is not a complete PDF
serialization or a format for applying edits back to a PDF.
