// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.PatternMatcher;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import net.boyechko.pdf.autoa11y.validation.TagSchema;

/**
 * Visitor that validates structure tree elements against the tag schema. Performs five validations:
 *
 * <ol>
 *   <li>Unknown role - element role not defined in schema
 *   <li>Parent rule - element appears under disallowed parent
 *   <li>Child count - element has too few or too many children
 *   <li>Allowed children - element contains children not in allowed list
 *   <li>Child pattern - children don't match required sequence pattern
 * </ol>
 */
public class SchemaValidationCheck extends StructTreeCheck {

    /** Tag prefix written to /T on elements with schema violations and cleared on each run. */
    static final String CHECK_SCRIBBLE_PREFIX = "SCHEMA";

    private final IssueList issues = new IssueList();

    @Override
    public String name() {
        return "Schema Validation Check";
    }

    @Override
    public String description() {
        return "Validates structure tree elements against the tag schema";
    }

    @Override
    public void beforeTraversal(DocContext docCtx) {
        PdfStructTreeRoot root = docCtx.doc().getStructTreeRoot();
        if (root != null && StructTree.clearScribbleSegmentsInTree(root, CHECK_SCRIBBLE_PREFIX)) {
            docCtx.markDirty();
        }
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        // Any issue implies a scribble was written by the validators during traversal.
        if (!issues.isEmpty()) {
            docCtx.markDirty();
        }
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        validateUnknownRole(ctx);
        validateParentRule(ctx);
        validateChildCount(ctx);
        validateAllowedChildren(ctx);
        validateChildPattern(ctx);

        return true; // Always continue to children
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void validateUnknownRole(StructTreeContext ctx) {
        /* If the schema rule is null, the role is not defined in the schema. */
        if (ctx.schemaRule() == null) {
            String message =
                    String.format(
                            "%s role is not defined in schema",
                            formatRole(StructTree.mappedRole(ctx.node())));
            scribbleIssue(ctx.node(), message);
            issues.add(
                    new Issue(IssueType.TAG_UNKNOWN_ROLE, IssueSev.ERROR, locAtElem(ctx), message));
        }
    }

    private void validateParentRule(StructTreeContext ctx) {
        TagSchema.Rule rule = ctx.schemaRule();
        if (rule == null || rule.getParentMustBe() == null) return;
        if (ctx.parentRole() == null) return;

        if (!rule.getParentMustBe().contains(ctx.parentRole())) {
            String message =
                    "parent must be "
                            + formatRole(rule.getParentMustBe())
                            + " but is "
                            + formatRole(ctx.parentRole());
            scribbleIssue(ctx.node(), message);
            issues.add(
                    new Issue(IssueType.TAG_WRONG_PARENT, IssueSev.ERROR, locAtElem(ctx), message));
        }
    }

    private void validateChildCount(StructTreeContext ctx) {
        TagSchema.Rule rule = ctx.schemaRule();
        if (rule == null) return;

        int childCount = ctx.childRoles().size();

        if (rule.getMinChildren() != null && childCount < rule.getMinChildren()) {
            String message =
                    formatRole(ctx.role())
                            + " has "
                            + childCount
                            + " kids; min is "
                            + rule.getMinChildren();
            scribbleIssue(ctx.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            locAtElem(ctx),
                            message));
        }

        if (rule.getMaxChildren() != null && childCount > rule.getMaxChildren()) {
            String message =
                    formatRole(ctx.role())
                            + " has "
                            + childCount
                            + " kids; max is "
                            + rule.getMaxChildren();
            scribbleIssue(ctx.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            locAtElem(ctx),
                            message));
        }
    }

    private void validateAllowedChildren(StructTreeContext ctx) {
        TagSchema.Rule rule = ctx.schemaRule();
        if (rule == null || rule.getAllowedChildren() == null) return;
        if (rule.getAllowedChildren().isEmpty()) return;

        for (int i = 0; i < ctx.childRoles().size(); i++) {
            String childRole = ctx.childRoles().get(i);
            if (!rule.getAllowedChildren().contains(childRole)) {
                String message =
                        formatRole(childRole) + " not allowed under " + formatRole(ctx.role());
                scribbleIssue(ctx.children().get(i), message);
                issues.add(
                        new Issue(
                                IssueType.TAG_WRONG_CHILD,
                                IssueSev.ERROR,
                                locAtElem(ctx, ctx.children().get(i)),
                                message));
            }
        }
    }

    private void validateChildPattern(StructTreeContext ctx) {
        TagSchema.Rule rule = ctx.schemaRule();
        if (rule == null || rule.getChildPattern() == null) return;

        PatternMatcher pm = PatternMatcher.compile(rule.getChildPattern());
        if (pm != null && !pm.fullMatch(ctx.childRoles())) {
            String message =
                    "kids "
                            + ctx.childRoles()
                            + " do not match pattern '"
                            + rule.getChildPattern()
                            + "'";
            scribbleIssue(ctx.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_PATTERN,
                            IssueSev.ERROR,
                            locAtElem(ctx),
                            message));
        }
    }

    private void scribbleIssue(PdfStructElem elem, String message) {
        StructTree.addScribble(elem, CHECK_SCRIBBLE_PREFIX + " " + message);
    }

    private String formatRole(String role) {
        return "<" + role + ">";
    }

    private String formatRole(Set<String> roles) {
        if (roles.size() == 1) {
            return formatRole(roles.iterator().next());
        }
        return "<" + String.join("|", roles) + ">";
    }
}
