/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Optional;
import net.boyechko.pdf.autoa11y.core.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract sealed class TagSingleChildFix implements IssueFix
        permits TagSingleChildFix.WrapInLI, TagSingleChildFix.TreatLblFigureAsBullet {

    private static final Logger logger = LoggerFactory.getLogger(TagSingleChildFix.class);

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
    public PdfStructElem getKid() {
        return kid;
    }

    public String getKidRole() {
        return kid.getRole().getValue();
    }

    public PdfStructElem getParent() {
        return parent;
    }

    public String getParentRole() {
        return parent.getRole().getValue();
    }

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

        @Override
        public String describe(DocumentContext ctx) {
            int parentObjNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            int kidObjNum = kid.getPdfObject().getIndirectReference().getObjNumber();
            int pageNum = ctx.getPageNumber(parentObjNum);
            String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";

            return "Replace Lbl object #"
                    + parentObjNum
                    + pageInfo
                    + " with its Figure object #"
                    + kidObjNum
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
            return "Wrapped "
                    + getKidRole()
                    + " in "
                    + wrappedIn
                    + " under L object #"
                    + parent.getPdfObject().getIndirectReference().getObjNumber();
        }

        @Override
        public String describe(DocumentContext ctx) {
            int objNum = parent.getPdfObject().getIndirectReference().getObjNumber();
            int pageNum = ctx.getPageNumber(objNum);
            String pageInfo = (pageNum > 0) ? " (p. " + pageNum + ")" : "";

            return "Wrapped "
                    + getKidRole()
                    + " in "
                    + wrappedIn
                    + " under L object #"
                    + objNum
                    + pageInfo;
        }
    }
}
