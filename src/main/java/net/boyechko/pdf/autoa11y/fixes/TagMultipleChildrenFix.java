package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.List;
import java.util.Optional;

public abstract sealed class TagMultipleChildrenFix implements IssueFix
    permits TagMultipleChildrenFix.WrapPairsOfPInLI {

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;

    protected TagMultipleChildrenFix(PdfStructElem parent, List<PdfStructElem> kids) {
        this.parent = parent;
        this.kids = kids != null ? List.copyOf(kids) : List.of();
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem parent, List<PdfStructElem> kids) {
        // Try each subclass factory method
        return WrapPairsOfPInLI.tryCreate(parent, kids);
    }

    @Override
    public int priority() {
        return 20; // Structure fix - higher priority than single child fixes
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getParent() { return parent; }
    public List<PdfStructElem> getKids() { return kids; }
    public String getParentRole() { return parent.getRole().getValue(); }

    public static final class WrapPairsOfPInLI extends TagMultipleChildrenFix {
        private WrapPairsOfPInLI(PdfStructElem parent, List<PdfStructElem> kids) {
            super(parent, kids);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
            String parentRole = parent.getRole().getValue();

            if ("L".equals(parentRole) && kids.stream().allMatch(k -> "P".equals(k.getRole().getValue()))) {
                return Optional.of(new WrapPairsOfPInLI(parent, kids));
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            for (int i = 0; i < kids.size(); i += 2) {
                PdfStructElem p1 = kids.get(i);
                PdfStructElem p2 = kids.get(i + 1);

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
        public String describe() {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            return "Wrapped pairs of P in Lbl+LBody for L object #" + objNum;
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            // If this fix operates on L with P kids, it invalidates individual
            // TagSingleChildFix that target any of the same kid elements
            if (otherFix instanceof TagSingleChildFix) {
                TagSingleChildFix singleFix = (TagSingleChildFix) otherFix;
                return "L".equals(singleFix.getParentRole()) &&
                    singleFix.getParent().equals(parent) &&
                    kids.contains(singleFix.getKid());
            }
            return false;
        }
    }



}
