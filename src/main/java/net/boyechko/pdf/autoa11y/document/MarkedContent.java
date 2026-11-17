// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * A structure element's reference to a marked-content sequence on a page: an adapter over iText's
 * {@link PdfMcr}, for the references that carry an MCID. Two are equal when they stand for the same
 * entry of the same element, which holds only for references read from the tree.
 */
public final class MarkedContent implements StructNode {
    private final PdfMcr mcr;

    private MarkedContent(PdfMcr mcr) {
        this.mcr = mcr;
    }

    public static MarkedContent of(PdfMcr mcr) {
        return new MarkedContent(mcr);
    }

    /** The underlying iText reference, for operations MarkedContent does not yet offer. */
    public PdfMcr mcr() {
        return mcr;
    }

    public int mcid() {
        return mcr.getMcid();
    }

    @Override
    public int page() {
        return StructTree.pageOf(mcr);
    }

    @Override
    public Tag parent() {
        return Tag.of((PdfStructElem) mcr.getParent());
    }

    @Override
    public MarkedContent moveTo(Tag newParent) {
        newParent.adopt(mcr, -1);
        return (MarkedContent) newParent.kids().getLast();
    }

    @Override
    public void insertBefore(Tag sibling) {
        sibling.precedeWithContent(mcr);
    }

    @Override
    public void remove() {
        parent().removeContent(mcr);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MarkedContent that
                && StructTree.isSame(mcr.getPdfObject(), that.mcr.getPdfObject());
    }

    @Override
    public int hashCode() {
        var ref = mcr.getPdfObject().getIndirectReference();
        return ref != null ? ref.getObjNumber() : System.identityHashCode(mcr.getPdfObject());
    }

    @Override
    public String toString() {
        return Format.node(mcr);
    }
}
