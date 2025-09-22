package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.List;
import java.util.Optional;

public abstract sealed class TagWrongChildFix implements IssueFix
    permits TagWrongChildFix.WrapPInLILBody,
            TagWrongChildFix.WrapSpanInLBody,
            TagWrongChildFix.WrapPairsOfPInLI {

    protected final PdfStructElem kid;
    protected final PdfStructElem parent;
    protected final List<PdfStructElem> allKids;

    protected TagWrongChildFix(PdfStructElem kid, PdfStructElem parent, List<PdfStructElem> allKids) {
        this.kid = kid;
        this.parent = parent;
        this.allKids = allKids != null ? List.copyOf(allKids) : List.of();
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent, List<PdfStructElem> allKids) {
        String kidRole = kid.getRole().getValue();
        String parentRole = parent.getRole().getValue();

        if ("L".equals(parentRole) && allKids.stream().allMatch(k -> "P".equals(k.getRole().getValue()))) {
            return Optional.of(new WrapPairsOfPInLI(kid, parent, allKids));
        } else if ("L".equals(parentRole) && "P".equals(kidRole)) {
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
    public List<PdfStructElem> getAllKids() { return List.copyOf(allKids); }
    public String getParentRole() { return parent.getRole().getValue(); }

    public static final class WrapPInLILBody extends TagWrongChildFix {
        private WrapPInLILBody(PdfStructElem kid, PdfStructElem parent) {
            super(kid, parent, null);
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
            super(kid, parent, null);
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

    public static final class WrapPairsOfPInLI extends TagWrongChildFix {
        private WrapPairsOfPInLI(PdfStructElem kid, PdfStructElem parent, List<PdfStructElem> allKids) {
            super(kid, parent, allKids);
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            for (int i = 0; i < allKids.size(); i += 2) {
                PdfStructElem p1 = allKids.get(i);
                PdfStructElem p2 = allKids.get(i + 1);

                PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
                parent.addKid(newLI);

                PdfStructElem newLbl = new PdfStructElem(ctx.doc(), PdfName.Lbl);
                newLI.addKid(newLbl);
                parent.removeKid(p1);
                newLbl.addKid(p1);

                PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
                newLI.addKid(newLBody);
                parent.removeKid(p2);
                newLBody.addKid(p2);
            }
        }

        @Override
        public int priority() {
            return 25; // More complex content fix
        }

        @Override
        public String describe() {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            return "Wrapped pairs of P in Lbl+LBody for L object #" + objNum;
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            // If this fix operates on L with P kids, it invalidates individual
            // WrapInProperContainer fixes that target any of the same kid
            // elements
            if (otherFix instanceof TagWrongChildFix) {
                TagWrongChildFix wrapper = (TagWrongChildFix) otherFix;
                return "L".equals(wrapper.getParentRole()) &&
                    wrapper.getParent().equals(parent) &&
                    allKids.contains(wrapper.getKid());
            }
            return false;
        }
    }



}
