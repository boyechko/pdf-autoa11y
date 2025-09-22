package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.Optional;

public abstract sealed class WrapInProperContainer implements IssueFix
    permits WrapInProperContainer.WrapPInLILBody, WrapInProperContainer.WrapSpanInLBody {

    protected final PdfStructElem kid;
    protected final PdfStructElem parent;
    protected final String parentRole;
    protected final String kidRole;

    protected WrapInProperContainer(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
        this.kid = kid;
        this.parent = parent;
        this.parentRole = parentRole;
        this.kidRole = kidRole;
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
        if ("L".equals(parentRole) && "P".equals(kidRole)) {
            return Optional.of(new WrapPInLILBody(kid, parent, parentRole, kidRole));
        } else if ("LI".equals(parentRole) && "Span".equals(kidRole)) {
            return Optional.of(new WrapSpanInLBody(kid, parent, parentRole, kidRole));
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
    public String getParentRole() { return parentRole; }

    public static final class WrapPInLILBody extends WrapInProperContainer {
        private WrapPInLILBody(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
            super(kid, parent, parentRole, kidRole);
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

    public static final class WrapSpanInLBody extends WrapInProperContainer {
        private WrapSpanInLBody(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
            super(kid, parent, parentRole, kidRole);
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
