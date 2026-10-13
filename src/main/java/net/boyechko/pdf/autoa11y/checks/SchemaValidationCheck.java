// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.TagSchema;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.PatternMatcher;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;

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

    private final TagSchema schema = TagSchema.loadDefault();
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
            markDocChanged();
        }
    }

    @Override
    public void afterTraversal(DocContext docCtx) {
        // Any issue implies a scribble was written by the validators during traversal.
        if (!issues.isEmpty()) {
            markDocChanged();
        }
    }

    @Override
    public boolean enterElement(Visit visit) {
        validateUnknownRole(visit);
        validateParentRule(visit);
        validateChildCount(visit);
        validateAllowedChildren(visit);
        validateChildPattern(visit);

        return true; // Always continue to children
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    private void validateUnknownRole(Visit visit) {
        /* If the schema rule is null, the role is not defined in the schema. */
        if (ruleFor(visit) == null) {
            String message =
                    String.format(
                            "%s role is not defined in schema",
                            formatRole(StructTree.mappedRole(visit.node())));
            scribbleIssue(visit.node(), message);
            issues.add(
                    new Issue(IssueType.TAG_UNKNOWN_ROLE, IssueSev.ERROR, visit.locus(), message));
        }
    }

    private void validateParentRule(Visit visit) {
        TagSchema.Rule rule = ruleFor(visit);
        if (rule == null || rule.getParentMustBe() == null) return;
        if (visit.parentRole() == null) return;

        if (!rule.getParentMustBe().contains(visit.parentRole())) {
            String message =
                    "parent must be "
                            + formatRole(rule.getParentMustBe())
                            + " but is "
                            + formatRole(visit.parentRole());
            scribbleIssue(visit.node(), message);
            issues.add(
                    new Issue(IssueType.TAG_WRONG_PARENT, IssueSev.ERROR, visit.locus(), message));
        }
    }

    private void validateChildCount(Visit visit) {
        TagSchema.Rule rule = ruleFor(visit);
        if (rule == null) return;

        int childCount = visit.childRoles().size();

        if (rule.getMinChildren() != null && childCount < rule.getMinChildren()) {
            String message =
                    formatRole(visit.role())
                            + " has "
                            + childCount
                            + " kids; min is "
                            + rule.getMinChildren();
            scribbleIssue(visit.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            visit.locus(),
                            message));
        }

        if (rule.getMaxChildren() != null && childCount > rule.getMaxChildren()) {
            String message =
                    formatRole(visit.role())
                            + " has "
                            + childCount
                            + " kids; max is "
                            + rule.getMaxChildren();
            scribbleIssue(visit.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            visit.locus(),
                            message));
        }
    }

    private void validateAllowedChildren(Visit visit) {
        TagSchema.Rule rule = ruleFor(visit);
        if (rule == null || rule.getAllowedChildren() == null) return;
        if (rule.getAllowedChildren().isEmpty()) return;

        for (int i = 0; i < visit.childRoles().size(); i++) {
            String childRole = visit.childRoles().get(i);
            if (!rule.getAllowedChildren().contains(childRole)) {
                String message =
                        formatRole(childRole) + " not allowed under " + formatRole(visit.role());
                scribbleIssue(visit.children().get(i), message);
                issues.add(
                        new Issue(
                                IssueType.TAG_WRONG_CHILD,
                                IssueSev.ERROR,
                                Locus.atElem(visit.docCtx(), visit.children().get(i)),
                                message));
            }
        }
    }

    private void validateChildPattern(Visit visit) {
        TagSchema.Rule rule = ruleFor(visit);
        if (rule == null || rule.getChildPattern() == null) return;

        PatternMatcher pm = PatternMatcher.compile(rule.getChildPattern());
        if (pm != null && !pm.fullMatch(visit.childRoles())) {
            String message =
                    "kids "
                            + visit.childRoles()
                            + " do not match pattern '"
                            + rule.getChildPattern()
                            + "'";
            scribbleIssue(visit.node(), message);
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_PATTERN,
                            IssueSev.ERROR,
                            visit.locus(),
                            message));
        }
    }

    /** Returns the schema rule for the element's role, or null if the role is not in the schema. */
    private TagSchema.Rule ruleFor(Visit visit) {
        return schema.roles.get(visit.mappedRole());
    }

    private void scribbleIssue(PdfStructElem elem, String message) {
        StructTree.addToolScribble(elem, CHECK_SCRIBBLE_PREFIX + " " + message);
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
