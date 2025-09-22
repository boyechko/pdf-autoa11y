package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.Optional;

public abstract sealed class TagWrongChildFix implements IssueFix
    permits TagWrongChildFix.WrapPInLILBody, TagWrongChildFix.WrapSpanInLBody {

    protected final PdfStructElem kid;
    protected final PdfStructElem parent;

    protected TagWrongChildFix(PdfStructElem kid, PdfStructElem parent) {
        this.kid = kid;
        this.parent = parent;
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent) {
        String kidRole = kid.getRole().getValue();
        String parentRole = parent.getRole().getValue();
        if ("L".equals(parentRole) && "P".equals(kidRole)) {
            return Optional.of(new WrapPInLILBody(kid, parent));
        } else if ("LI".equals(parentRole) && "Span".equals(kidRole)) {
            return Optional.of(new WrapSpanInLBody(kid, parent));
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 30; // Content fix
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getKid() { return kid; }
    public PdfStructElem getParent() { return parent; }
    public String getParentRole() { return parent.getRole().getValue(); }

    public static final class WrapPInLILBody extends TagWrongChildFix {
        private WrapPInLILBody(PdfStructElem kid, PdfStructElem parent) {
            super(kid, parent);
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
            parent.addKid(newLI);

            PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
            newLI.addKid(newLBody);

            parent.removeKid(kid);
            newLBody.addKid(kid);
        }

        @Override
        public String describe() {
            return "Wrapped P element in LI->LBody container under L object #"
                   + parent.getPdfObject().getIndirectReference().getObjNumber();
        }
    }

    public static final class WrapSpanInLBody extends TagWrongChildFix {
        private WrapSpanInLBody(PdfStructElem kid, PdfStructElem parent) {
            super(kid, parent);
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
            parent.addKid(newLBody);

            parent.removeKid(kid);
            newLBody.addKid(kid);
        }

        @Override
        public String describe() {
            return "Wrapped Span element in LBody container under LI object #"
                   + parent.getPdfObject().getIndirectReference().getObjNumber();
        }
    }
}
