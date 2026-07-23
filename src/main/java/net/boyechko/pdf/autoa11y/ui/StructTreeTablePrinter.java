// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import java.util.function.Consumer;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

/** Outputs a tabular listing of the structure tree during traversal. */
public class StructTreeTablePrinter extends StructTreeCheck {

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

    public StructTreeTablePrinter(Consumer<String> output) {
        this.output = output;
    }

    @Override
    public String name() {
        return "Verbose Output Visitor";
    }

    @Override
    public String description() {
        return "Outputs a tabular listing of the structure tree during traversal";
    }

    @Override
    public void beforeTraversal(DocContext docCtx) {
        printHeader();
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
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

    private void printElement(StructTreeContext ctx) {
        String paddedIndex = String.format("%" + INDEX_WIDTH + "d", ctx.globalIndex());
        String elementName = INDENT.repeat(ctx.depth()) + "- " + ctx.role();
        int pageNum = ctx.getPageNumber();
        String pageString = (pageNum == 0) ? "" : "(p. " + pageNum + ")";

        String mcrText = Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNum);
        mcrText = (mcrText == null || mcrText.isEmpty()) ? "" : mcrText;
        String truncated = Format.truncate(mcrText, CONTENT_SUMMARY_WIDTH);

        output.accept(
                String.format(
                        ROW_FORMAT,
                        paddedIndex,
                        elementName,
                        pageString,
                        ctx.getObjNum(),
                        truncated));
    }
}
