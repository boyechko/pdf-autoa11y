package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.List;
import java.util.Optional;

public abstract sealed class TagWrongChildPatternFix implements IssueFix
    permits TagWrongChildPatternFix.WrapPairsOfPInLI {

    protected final PdfStructElem listElement;
    protected final List<PdfStructElem> kids;
    protected final String role = "L";
    protected final List<String> kidRoles;

    protected TagWrongChildPatternFix(PdfStructElem listElement, List<PdfStructElem> kids, List<String> kidRoles) {
        this.listElement = listElement;
        this.kids = List.copyOf(kids);
        this.kidRoles = List.copyOf(kidRoles);
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem node, List<PdfStructElem> kids, List<String> kidRoles) {
        if (kidRoles.stream().allMatch("P"::equals)) {
            return Optional.of(new WrapPairsOfPInLI(node, kids, kidRoles));

        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 20; // Structure fix
    }

    public static final class WrapPairsOfPInLI extends TagWrongChildPatternFix {
        private WrapPairsOfPInLI(PdfStructElem listElement, List<PdfStructElem> kids, List<String> kidRoles) {
            super(listElement, kids, kidRoles);
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            for (int i = 0; i < kids.size(); i += 2) {
                PdfStructElem p1 = kids.get(i);
                PdfStructElem p2 = kids.get(i + 1);

                PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
                listElement.addKid(newLI);

                PdfStructElem newLbl = new PdfStructElem(ctx.doc(), PdfName.Lbl);
                newLI.addKid(newLbl);
                listElement.removeKid(p1);
                newLbl.addKid(p1);

                PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
                newLI.addKid(newLBody);
                listElement.removeKid(p2);
                newLBody.addKid(p2);
            }
        }

        @Override
        public String describe() {
            int objNum = listElement.getPdfObject().getIndirectReference().getObjNumber();
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
                    wrapper.getParent().equals(listElement) &&
                    kids.contains(wrapper.getKid());
            }
            return false;
        }
    }
}
