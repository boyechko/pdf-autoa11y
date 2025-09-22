package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.Optional;

public final class WrapInProperContainer implements IssueFix {
    private final PdfStructElem kid;
    private final PdfStructElem parent;
    private final String parentRole;
    private final String kidRole;

    private WrapInProperContainer(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
        this.kid = kid;
        this.parent = parent;
        this.parentRole = parentRole;
        this.kidRole = kidRole;
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
        if (("L".equals(parentRole) && "P".equals(kidRole)) || ("LI".equals(parentRole) && "Span".equals(kidRole))) {
            return Optional.of(new WrapInProperContainer(kid, parent, parentRole, kidRole));
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 30; // Content fix
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        if ("L".equals(parentRole) && "P".equals(kidRole)) {
            // Wrap P in LI->LBody
            wrapPInLILBody(ctx.doc());
        } else if ("LI".equals(parentRole) && "Span".equals(kidRole)) {
            // Wrap Span in LBody
            wrapSpanInLBody(ctx.doc());
        }
    }

    @Override
    public String describe() {
        return "Wrapped " + kidRole + " in proper container under " + parentRole;
    }

    private void wrapPInLILBody(PdfDocument document) throws Exception {
        PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
        parent.addKid(newLI);

        PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
        newLI.addKid(newLBody);

        parent.removeKid(kid);
        newLBody.addKid(kid);
    }

    private void wrapSpanInLBody(PdfDocument document) throws Exception {
        PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
        parent.addKid(newLBody);

        parent.removeKid(kid);
        newLBody.addKid(kid);
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getKid() { return kid; }
    public PdfStructElem getParent() { return parent; }
    public String getParentRole() { return parentRole; }
}