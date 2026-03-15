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

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.Objects;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.fixes.schema.*;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/**
 * Base class for fixes that correct tag-schema violations detected by SchemaValidationCheck. Houses
 * the factory methods and strategy lists for all schema fix variants.
 */
public abstract class SchemaValidationFix implements IssueFix {

    @FunctionalInterface
    interface ChildFixFactory {
        IssueFix tryCreate(PdfStructElem kid, PdfStructElem parent);
    }

    @FunctionalInterface
    interface ChildrenFixFactory {
        IssueFix tryCreate(PdfStructElem parent, List<PdfStructElem> kids);
    }

    private static final List<ChildFixFactory> CHILD_FIXES =
            List.of(
                    ExtractLBodyToList::tryCreate,
                    WrapInLI::tryCreate,
                    WrapInLBody::tryCreate,
                    TreatLblFigureAsBullet::tryCreate);

    private static final List<ChildrenFixFactory> CHILDREN_FIXES =
            List.of(
                    WrapPairsOfLblPInLI::tryCreate,
                    WrapPairsOfLblLBodyInLI::tryCreate,
                    ChangePToLblInLI::tryCreate);

    protected final PdfStructElem parent;

    protected SchemaValidationFix(PdfStructElem parent) {
        this.parent = parent;
    }

    /** Tries each single-child fix strategy in order; returns the first match or null. */
    public static IssueFix createForChild(PdfStructElem kid, PdfStructElem parent) {
        return CHILD_FIXES.stream()
                .map(f -> f.tryCreate(kid, parent))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Tries each multiple-children fix strategy in order; returns the first match or null. */
    public static IssueFix createForChildren(PdfStructElem parent, List<PdfStructElem> kids) {
        return CHILDREN_FIXES.stream()
                .map(f -> f.tryCreate(parent, kids))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public PdfStructElem getParent() {
        return parent;
    }

    public String getParentRole() {
        return parent.getRole().getValue();
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.atElem(ctx, parent));
    }
}
