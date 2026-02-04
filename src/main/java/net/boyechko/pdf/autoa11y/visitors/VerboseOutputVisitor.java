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
package net.boyechko.pdf.autoa11y.visitors;

import java.util.function.Consumer;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.issues.IssueList;
import net.boyechko.pdf.autoa11y.validation.StructureTreeVisitor;
import net.boyechko.pdf.autoa11y.validation.VisitorContext;

/** Outputs a tabular listing of the structure tree during traversal. */
public class VerboseOutputVisitor implements StructureTreeVisitor {

    private static final String INDENT = "  ";
    private static final int INDEX_WIDTH = 5;
    private static final int ELEMENT_NAME_WIDTH = 30;
    private static final int PAGE_NUM_WIDTH = 10;
    private static final int OBJ_NUM_WIDTH = 6;
    private static final int CONTENT_SUMMARY_WIDTH = 30;

    private static final String ROW_FORMAT =
            String.format(
                    "%%-%ds %%-%ds %%-%ds %%-%ds %%s%%n",
                    INDEX_WIDTH, ELEMENT_NAME_WIDTH, PAGE_NUM_WIDTH, OBJ_NUM_WIDTH);

    private final Consumer<String> output;
    private boolean headerPrinted = false;

    public VerboseOutputVisitor(Consumer<String> output) {
        this.output = output;
    }

    @Override
    public String name() {
        return "Verbose Structure Tree Traversal";
    }

    @Override
    public String description() {
        return "Outputs a tabular listing of the structure tree during traversal";
    }

    @Override
    public void beforeTraversal(VisitorContext ctx) {
        printHeader();
    }

    @Override
    public boolean enterElement(VisitorContext ctx) {
        if (!headerPrinted) {
            printHeader();
        }

        // Skip empty Span elements to reduce output noise.
        if ("Span".equals(ctx.role()) && ctx.children().isEmpty()) {
            return true;
        }

        printElement(ctx);
        return true;
    }

    @Override
    public IssueList getIssues() {
        return new IssueList();
    }

    private void printHeader() {
        if (headerPrinted) return;
        headerPrinted = true;

        output.accept(String.format(ROW_FORMAT, "Index", "Element", "Page", "Obj#", "Content"));
        output.accept(
                String.format(
                        ROW_FORMAT,
                        "-".repeat(INDEX_WIDTH),
                        "-".repeat(ELEMENT_NAME_WIDTH),
                        "-".repeat(PAGE_NUM_WIDTH),
                        "-".repeat(OBJ_NUM_WIDTH),
                        "-".repeat(CONTENT_SUMMARY_WIDTH)));
    }

    private void printElement(VisitorContext ctx) {
        String paddedIndex = String.format("%" + INDEX_WIDTH + "d", ctx.globalIndex());
        String elementName = INDENT.repeat(ctx.depth()) + "- " + ctx.role();
        int pageNum = ctx.getPageNumber();
        String pageString = (pageNum == 0) ? "" : "(p. " + pageNum + ")";

        String mcrSummary = McidTextExtractor.getMcrContentSummary(ctx.node(), ctx.doc(), pageNum);
        mcrSummary = (mcrSummary == null || mcrSummary.isEmpty()) ? "" : mcrSummary;

        output.accept(
                String.format(
                        ROW_FORMAT,
                        paddedIndex,
                        elementName,
                        pageString,
                        ctx.getObjNum(),
                        mcrSummary));
    }
}
