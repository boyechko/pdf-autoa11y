package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

import java.util.List;
import java.util.Optional;

public final class FixListStructure implements IssueFix {
    private final PdfStructElem listElement;
    private final List<PdfStructElem> kids;
    private final String role;
    private final List<String> kidRoles;

    private FixListStructure(PdfStructElem listElement, List<PdfStructElem> kids, String role, List<String> kidRoles) {
        this.listElement = listElement;
        this.kids = List.copyOf(kids);
        this.role = role;
        this.kidRoles = List.copyOf(kidRoles);
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem node, List<PdfStructElem> kids, String role, List<String> kidRoles) {
        if ("L".equals(role) && kidRoles.stream().allMatch("P"::equals)) {
            return Optional.of(new FixListStructure(node, kids, role, kidRoles));
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 20; // Structure fix
    }

    @Override
    public void apply(DocumentContext ctx) throws Exception {
        // For L elements with all P kids, wrap pairs in LI->LBody structure
        if ("L".equals(role) && allKidsAreP() && (kidRoles.size() % 2 == 0)) {
            wrapPairsOfPInLI(ctx.doc());
        }
        // TODO: Add other pattern fixes as needed
    }

    @Override
    public String describe() {
        int objNum = listElement.getPdfObject().getIndirectReference().getObjNumber();
        return "Fixed list structure for list object #" + objNum;
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        // If this fix operates on L with P kids, it invalidates individual WrapInProperContainer
        // fixes that target any of the same kid elements
        if (otherFix instanceof WrapInProperContainer) {
            WrapInProperContainer wrapper = (WrapInProperContainer) otherFix;
            return "L".equals(role) && "L".equals(wrapper.getParentRole()) &&
                   wrapper.getParent().equals(listElement) && kids.contains(wrapper.getKid());
        }
        return false;
    }

    private boolean allKidsAreP() {
        return kidRoles.stream().allMatch("P"::equals);
    }

    private void wrapPairsOfPInLI(PdfDocument document) throws Exception {
        for (int i = 0; i < kids.size(); i += 2) {
            PdfStructElem p1 = kids.get(i);
            PdfStructElem p2 = kids.get(i + 1);

            PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
            listElement.addKid(newLI);

            PdfStructElem newLbl = new PdfStructElem(document, PdfName.Lbl);
            newLI.addKid(newLbl);
            listElement.removeKid(p1);
            newLbl.addKid(p1);

            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            newLI.addKid(newLBody);
            listElement.removeKid(p2);
            newLBody.addKid(p2);
        }
    }
}