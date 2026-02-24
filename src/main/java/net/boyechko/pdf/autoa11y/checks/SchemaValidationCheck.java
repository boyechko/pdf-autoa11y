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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.Set;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.fixes.child.TagSingleChildFix;
import net.boyechko.pdf.autoa11y.fixes.children.TagMultipleChildrenFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.PatternMatcher;
import net.boyechko.pdf.autoa11y.validation.StructTreeChecker;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;
import net.boyechko.pdf.autoa11y.validation.TagSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class SchemaValidationCheck implements StructTreeChecker {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidationCheck.class);

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
            issues.add(
                    new Issue(
                            IssueType.TAG_UNKNOWN_ROLE,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            String.format(
                                    "%s role is not defined in schema",
                                    formatRole(StructTree.mappedRole(ctx.node())))));
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
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_PARENT,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            message));
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
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            message));
        }

        if (rule.getMaxChildren() != null && childCount > rule.getMaxChildren()) {
            String message =
                    formatRole(ctx.role())
                            + " has "
                            + childCount
                            + " kids; max is "
                            + rule.getMaxChildren();
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            message));
        }
    }

    private void validateAllowedChildren(StructTreeContext ctx) {
        TagSchema.Rule rule = ctx.schemaRule();
        if (rule == null || rule.getAllowedChildren() == null) return;
        if (rule.getAllowedChildren().isEmpty()) return;

        boolean multiFixCreated = false;

        for (int i = 0; i < ctx.childRoles().size(); i++) {
            String childRole = ctx.childRoles().get(i);
            if (!rule.getAllowedChildren().contains(childRole)) {
                IssueFix fix = createChildFix(ctx, i, multiFixCreated, childRole);

                String message =
                        formatRole(childRole) + " not allowed under " + formatRole(ctx.role());
                issues.add(
                        new Issue(
                                IssueType.TAG_WRONG_CHILD,
                                IssueSev.ERROR,
                                IssueLoc.atElem(ctx.children().get(i)),
                                message,
                                fix));

                if (fix instanceof TagMultipleChildrenFix) {
                    multiFixCreated = true;
                }
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
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_PATTERN,
                            IssueSev.ERROR,
                            IssueLoc.atElem(ctx.node()),
                            message));
        }
    }

    private IssueFix createChildFix(
            StructTreeContext ctx, int childIndex, boolean multiFixCreated, String childRole) {
        if (multiFixCreated) {
            logger.debug(
                    "Fix already created for parent {}; no further fix for kid {}",
                    formatRole(ctx.role()),
                    formatRole(childRole));
            return null;
        }

        PdfStructElem childNode = ctx.children().get(childIndex);

        IssueFix multi = TagMultipleChildrenFix.createIfApplicable(ctx.node(), ctx.children());
        if (multi != null) return multi;

        IssueFix single = TagSingleChildFix.createIfApplicable(childNode, ctx.node());
        if (single != null) return single;

        logger.trace(
                "No automatic fix available for kid {} under parent {}",
                formatRole(childRole),
                formatRole(ctx.role()));
        return null;
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
