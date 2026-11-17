// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

/**
 * A structure element's reference to a whole PDF object, such as an annotation or an XObject: an
 * adapter over iText's {@link PdfObjRef}. It marks no content and has no MCID. Two are equal when
 * they stand for the same entry of the same element.
 */
public final class ObjectRef implements StructNode {
    private final PdfObjRef objRef;

    private ObjectRef(PdfObjRef objRef) {
        this.objRef = objRef;
    }

    public static ObjectRef of(PdfObjRef objRef) {
        return new ObjectRef(objRef);
    }

    /** The underlying iText reference, for operations ObjectRef does not yet offer. */
    public PdfObjRef objRef() {
        return objRef;
    }

    @Override
    public int page() {
        return StructTree.pageOf(objRef);
    }

    @Override
    public Tag parent() {
        return Tag.of((PdfStructElem) objRef.getParent());
    }

    @Override
    public ObjectRef moveTo(Tag newParent) {
        StructTree.moveKid(objRef, (PdfStructElem) objRef.getParent(), newParent.elem());
        return (ObjectRef) newParent.kids().getLast();
    }

    @Override
    public void insertBefore(Tag sibling) {
        sibling.precedeWithContent(objRef);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ObjectRef that
                && StructTree.isSame(objRef.getPdfObject(), that.objRef.getPdfObject());
    }

    @Override
    public int hashCode() {
        var ref = objRef.getPdfObject().getIndirectReference();
        return ref != null ? ref.getObjNumber() : System.identityHashCode(objRef.getPdfObject());
    }

    @Override
    public String toString() {
        return Format.node(objRef);
    }
}
