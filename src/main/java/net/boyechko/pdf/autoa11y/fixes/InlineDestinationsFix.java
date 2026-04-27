/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.DocValue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replaces named destination references in annotations and outlines with explicit page destinations
 * (fit-page form).
 */
public class InlineDestinationsFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(InlineDestinationsFix.class);

    private static final int P_INLINE_DESTS = 30;

    private int replaced = 0;
    private int orphaned = 0;

    @Override
    public int priority() {
        return P_INLINE_DESTS;
    }

    @Override
    public void apply(DocContext ctx) {
        PdfDocument doc = ctx.doc();
        Map<PdfString, PdfObject> destEntries =
                doc.getCatalog().getNameTree(PdfName.Dests).getNames();
        inlineInAnnotations(doc, destEntries);
        inlineInOutlines(doc, destEntries);
        inlineInOpenAction(doc, destEntries);
    }

    @Override
    public String describe() {
        return "Inlined "
                + replaced
                + " named-destination references"
                + (orphaned > 0 ? " (" + orphaned + " orphan refs left in place)" : "");
    }

    private void inlineInAnnotations(PdfDocument doc, Map<PdfString, PdfObject> destEntries) {
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            PdfArray annots = doc.getPage(i).getPdfObject().getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int j = 0; j < annots.size(); j++) {
                PdfDictionary annot = annots.getAsDictionary(j);
                if (annot != null) inlineRefsInDict(annot, doc, destEntries);
            }
        }
    }

    private void inlineInOutlines(PdfDocument doc, Map<PdfString, PdfObject> destEntries) {
        PdfDictionary outlines = doc.getCatalog().getPdfObject().getAsDictionary(PdfName.Outlines);
        if (outlines == null) return;
        walkOutlineSiblings(
                outlines.getAsDictionary(PdfName.First), doc, destEntries, new HashSet<>());
    }

    private void walkOutlineSiblings(
            PdfDictionary item,
            PdfDocument doc,
            Map<PdfString, PdfObject> destEntries,
            Set<PdfIndirectReference> visited) {
        while (item != null) {
            PdfIndirectReference ref = item.getIndirectReference();
            if (ref != null && !visited.add(ref)) return;
            inlineRefsInDict(item, doc, destEntries);
            walkOutlineSiblings(item.getAsDictionary(PdfName.First), doc, destEntries, visited);
            item = item.getAsDictionary(PdfName.Next);
        }
    }

    private void inlineInOpenAction(PdfDocument doc, Map<PdfString, PdfObject> destEntries) {
        PdfObject openAction = doc.getCatalog().getPdfObject().get(PdfName.OpenAction);
        if (openAction instanceof PdfDictionary actionOrDest) {
            inlineRefsInDict(actionOrDest, doc, destEntries);
        }
    }

    /** Inlines /A /D and /Dest entries in a dict if they're named destination references. */
    private void inlineRefsInDict(
            PdfDictionary dict, PdfDocument doc, Map<PdfString, PdfObject> destEntries) {
        PdfDictionary action = dict.getAsDictionary(PdfName.A);
        if (action != null && PdfName.GoTo.equals(action.getAsName(PdfName.S))) {
            inlineNamedRef(action, PdfName.D, doc, destEntries);
        }
        PdfObject directDest = dict.get(PdfName.Dest);
        if (directDest instanceof PdfName || directDest instanceof PdfString) {
            inlineNamedRef(dict, PdfName.Dest, doc, destEntries);
        }
    }

    /**
     * Replaces a named destination reference at {@code key} in {@code container} with a fit-page
     * explicit destination. Looks the name up in {@code destEntries} (the /Dests map) and resolves
     * the entry to a page number. No-op if the value is not a name/string, the name isn't in
     * /Dests, or the entry doesn't resolve to a page.
     */
    private void inlineNamedRef(
            PdfDictionary container,
            PdfName key,
            PdfDocument doc,
            Map<PdfString, PdfObject> destEntries) {
        PdfObject value = container.get(key);
        PdfString lookupKey;
        if (value instanceof PdfName n) {
            lookupKey = new PdfString(n.getValue());
        } else if (value instanceof PdfString s) {
            lookupKey = s;
        } else {
            return;
        }
        PdfObject destEntry = destEntries.get(lookupKey);
        if (destEntry == null) {
            orphaned++;
            logger.debug("Orphan named destination reference: {}", value);
            return;
        }
        DocValue.Destination resolved = DocValue.resolveDestination(destEntry);
        if (resolved instanceof DocValue.Destination.GoToPage gp) {
            PdfExplicitDestination newDest =
                    PdfExplicitDestination.createFit(doc.getPage(gp.pageNum()));
            container.put(key, newDest.getPdfObject());
            replaced++;
        } else {
            orphaned++;
            logger.debug("Named destination did not resolve to a page: {}", value);
        }
    }
}
