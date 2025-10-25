package net.boyechko.pdf.autoa11y.fixes;

import java.util.List;
import java.util.Optional;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.DocumentContext;
import net.boyechko.pdf.autoa11y.IssueFix;

public abstract sealed class TagSingleChildFix implements IssueFix
    permits TagSingleChildFix.WrapInLI, TagSingleChildFix.TreatLblFigureAsBullet {

    protected final PdfStructElem kid;
    protected final PdfStructElem parent;

    protected TagSingleChildFix(PdfStructElem kid, PdfStructElem parent) {
        this.kid = kid;
        this.parent = parent;
    }

    public static Optional<IssueFix> createIfApplicable(PdfStructElem kid, PdfStructElem parent) {
        // Try each subclass factory method
        return WrapInLI.tryCreate(kid, parent)
            .or(() -> TreatLblFigureAsBullet.tryCreate(kid, parent));
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public String describe() {
        return "Fix a single child, " + getKidRole() + ", under its parent " + getParentRole();
    }

    // Getters for invalidation logic in other fixes
    public PdfStructElem getKid() { return kid; }
    public String getKidRole() { return kid.getRole().getValue(); }
    public PdfStructElem getParent() { return parent; }
    public String getParentRole() { return parent.getRole().getValue(); }

    public static final class TreatLblFigureAsBullet extends TagSingleChildFix {
        private TreatLblFigureAsBullet(PdfStructElem kid, PdfStructElem parent) {
            super(kid, parent);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem kid, PdfStructElem parent) {
            String kidRole = kid.getRole().getValue();
            String parentRole = parent.getRole().getValue();
            if ("Lbl".equals(parentRole) && "Figure".equals(kidRole)) {
                return Optional.of(new TreatLblFigureAsBullet(kid, parent));
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            PdfStructElem lbl = parent;
            PdfStructElem figure = kid;
            PdfStructElem li = (PdfStructElem) lbl.getParent();
            if (figure.getActualText() == null || figure.getAlt() == null) {
                // Move Figure out from under Lbl
                lbl.setRole(PdfName.Artifact);
                lbl.removeKid(figure);
                li.addKid(0, figure);

                // Remove now-empty Lbl
                li.removeKid(lbl);

                // Change Figure to Lbl
                figure.setRole(PdfName.Lbl);
                figure.setActualText(new PdfString("Bullet"));
            } else {
                throw new Exception("Figure already has ActualText or AltText");
            }
        }

        @Override
        public String describe() {
            return "Replace Lbl object #"
                   + parent.getPdfObject().getIndirectReference().getObjNumber()
                   + " with its Figure object #"
                   + kid.getPdfObject().getIndirectReference().getObjNumber()
                   + " as a bullet label";
        }
    }

    public static final class WrapInLI extends TagSingleChildFix {
        private static final List<String> validKidRoles =
            List.of("Div", "Figure", "LBody", "P", "Span");
        private String wrappedIn = "";

        private WrapInLI(PdfStructElem kid, PdfStructElem parent) {
            super(kid, parent);
        }

        public static Optional<IssueFix> tryCreate(PdfStructElem kid, PdfStructElem parent) {
            String kidRole = kid.getRole().getValue();
            String parentRole = parent.getRole().getValue();
            if ("L".equals(parentRole) && validKidRoles.contains(kidRole)) {
                return Optional.of(new WrapInLI(kid, parent));
            }
            return Optional.empty();
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            PdfStructElem newLI = new PdfStructElem(ctx.doc(), PdfName.LI);
            parent.addKid(newLI);

            if (getKidRole().equals("LBody")) {
                newLI.addKid(kid);
                parent.removeKid(kid);
                wrappedIn = "LI";
                return;
            }
            PdfStructElem newLBody = new PdfStructElem(ctx.doc(), PdfName.LBody);
            newLI.addKid(newLBody);

            parent.removeKid(kid);
            newLBody.addKid(kid);
            wrappedIn = "LI->LBody";
        }

        @Override
        public String describe() {
            return "Wrapped " + getKidRole() + " in " + wrappedIn + " under L object #"
                   + parent.getPdfObject().getIndirectReference().getObjNumber();
        }
    }
}
