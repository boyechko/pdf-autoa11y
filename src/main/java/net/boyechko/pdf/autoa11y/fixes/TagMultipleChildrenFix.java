package net.boyechko.pdf.autoa11y.fixes;

import java.util.List;
import java.util.Optional;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

public abstract sealed class TagMultipleChildrenFix implements IssueFix
    permits TagMultipleChildrenFix.WrapPairsOfLblPInLI,
            TagMultipleChildrenFix.WrapPairsOfLblLBodyInLI,
            TagMultipleChildrenFix.ChangePToLblInLI {

    protected static final Logger logger = LoggerFactory.getLogger(TagMultipleChildrenFix.class);

    protected final PdfStructElem parent;
    protected final List<PdfStructElem> kids;

    protected TagMultipleChildrenFix(PdfStructElem parent, List<PdfStructElem> kids) {
        this.parent = parent;
        this.kids = kids != null ? List.copyOf(kids) : List.of();
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem parent, List<PdfStructElem> kids) {
        // Try each subclass factory method
        return WrapPairsOfLblPInLI.tryCreate(parent, kids)
            .or(() -> WrapPairsOfLblLBodyInLI.tryCreate(parent, kids))
            .or(() -> ChangePToLblInLI.tryCreate(parent, kids));
    }

    @Override
    public int priority() {
        return 20; // Structure fix - higher priority than single child fixes
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getParent() { return parent; }
    public List<PdfStructElem> getKids() { return kids; }
    public String getParentRole() { return parent.getRole().getValue(); }

    public static final class ChangePToLblInLI extends TagMultipleChildrenFix {
        private ChangePToLblInLI(PdfStructElem parent, List<PdfStructElem> kids) {
            super(parent, kids);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
            String parentRole = parent.getRole().getValue();
            // There should be exactly two kids, one of which is LBody and the other P
            if ("LI".equals(parentRole) && kids.size() == 2) {
                String kid1Role = kids.get(0).getRole().getValue();
                String kid2Role = kids.get(1).getRole().getValue();

                if (("P".equals(kid1Role) && "LBody".equals(kid2Role)) ||
                    ("LBody".equals(kid1Role) && "P".equals(kid2Role))) {
                    return Optional.of(new ChangePToLblInLI(parent, kids));
                }
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            for (PdfStructElem p : kids) {
                if ("P".equals(p.getRole().getValue())) {
                    p.setRole(PdfName.Lbl);
                }
            }
        }

        @Override
        public String describe() {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            return "Changed P to Lbl in LI object #" + objNum;
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            // If this fix operates on LI with P kids, it invalidates individual
            // TagSingleChildFix that target any of the same kid elements
            if (otherFix instanceof TagSingleChildFix) {
                TagSingleChildFix singleFix = (TagSingleChildFix) otherFix;
                return "LI".equals(singleFix.getParentRole()) &&
                    singleFix.getParent().equals(parent) &&
                    kids.contains(singleFix.getKid());
            }
            return false;
        }
    }

    public static final class WrapPairsOfLblPInLI extends TagMultipleChildrenFix {
        private WrapPairsOfLblPInLI(PdfStructElem parent, List<PdfStructElem> kids) {
            super(parent, kids);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
            String parentRole = parent.getRole().getValue();

            // Check if parent is L and we have alternating Lbl/P pattern
            if ("L".equals(parentRole) && kids.size() >= 2 && kids.size() % 2 == 0) {
                // Verify alternating Lbl/P pattern
                for (int i = 0; i < kids.size(); i += 2) {
                    String lblRole = kids.get(i).getRole().getValue();
                    String pRole = kids.get(i + 1).getRole().getValue();

                    if (!"Lbl".equals(lblRole) || !"P".equals(pRole)) {
                        return Optional.empty();
                    }
                }
                return Optional.of(new WrapPairsOfLblPInLI(parent, kids));
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            // Process pairs of Lbl/P elements
            logger.debug("Applying WrapPairsOfLblPInLI to L object #"
                        + parent.getPdfObject().getIndirectReference().getObjNumber()
                        + " with " + kids.size() + " kids");
            for (int i = 0; i < kids.size(); i += 2) {
                PdfStructElem lbl = kids.get(i);
                PdfStructElem p = kids.get(i + 1);

                PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
                parent.addKid(newLI);

                // Move the Lbl directly under LI
                parent.removeKid(lbl);
                newLI.addKid(lbl);

                // Create LBody and move P under it
                PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
                newLI.addKid(newLBody);
                parent.removeKid(p);
                newLBody.addKid(p);
            }
        }

        @Override
        public String describe() {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            return "Wrapped pairs of Lbl/P in LI elements for L object #" + objNum;
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

    public static final class WrapPairsOfLblLBodyInLI extends TagMultipleChildrenFix {
        private WrapPairsOfLblLBodyInLI(PdfStructElem parent, List<PdfStructElem> kids) {
            super(parent, kids);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem parent, List<PdfStructElem> kids) {
            String parentRole = parent.getRole().getValue();

            // Check if parent is L and we have alternating Lbl/LBody pattern
            if ("L".equals(parentRole) && kids.size() >= 2 && kids.size() % 2 == 0) {
                // Verify alternating Lbl/LBody pattern
                for (int i = 0; i < kids.size(); i += 2) {
                    String lblRole = kids.get(i).getRole().getValue();
                    String lBodyRole = kids.get(i + 1).getRole().getValue();

                    if (!"Lbl".equals(lblRole) || !"LBody".equals(lBodyRole)) {
                        return Optional.empty();
                    }
                }
                return Optional.of(new WrapPairsOfLblLBodyInLI(parent, kids));
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            // Process pairs of Lbl/P elements
            logger.debug("Applying WrapPairsOfLblLBodyInLI to L object #"
                        + parent.getPdfObject().getIndirectReference().getObjNumber()
                        + " with " + kids.size() + " kids");
            for (int i = 0; i < kids.size(); i += 2) {
                PdfStructElem lbl = kids.get(i);
                PdfStructElem lBody = kids.get(i + 1);

                PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
                parent.addKid(newLI);

                parent.removeKid(lbl);
                newLI.addKid(lbl);

                parent.removeKid(lBody);
                newLI.addKid(lBody);
            }
        }

        @Override
        public String describe() {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            return "Wrapped pairs of Lbl/LBody in LI elements for L object #" + objNum;
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            // If this fix operates on L with LBody kids, it invalidates individual
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
