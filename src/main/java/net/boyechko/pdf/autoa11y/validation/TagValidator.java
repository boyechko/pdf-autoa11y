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
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.boyechko.pdf.autoa11y.content.McidTextExtractor;
import net.boyechko.pdf.autoa11y.fixes.child.TagSingleChildFix;
import net.boyechko.pdf.autoa11y.fixes.children.TagMultipleChildrenFix;
import net.boyechko.pdf.autoa11y.issues.Issue;
import net.boyechko.pdf.autoa11y.issues.IssueFix;
import net.boyechko.pdf.autoa11y.issues.IssueLocation;
import net.boyechko.pdf.autoa11y.issues.IssueSeverity;
import net.boyechko.pdf.autoa11y.issues.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagValidator {
    private static final Logger logger = LoggerFactory.getLogger(TagValidator.class);

    private static final String INDENT = "  ";
    private static final int INDEX_WIDTH = 5;
    private static final int ELEMENT_NAME_WIDTH = 30;
    private static final int PAGE_NUM_WIDTH = 10;
    private static final int OBJ_NUM_WIDTH = 6;
    private static final int CONTENT_SUMMARY_WIDTH = 30;
    private static final int ISSUES_WIDTH = 6; // For header separator only; actual issues longer

    private static final String ROW_FORMAT =
            String.format(
                    "%%-%ds %%-%ds %%-%ds %%-%ds %%-%ds %%s%%n",
                    INDEX_WIDTH,
                    ELEMENT_NAME_WIDTH,
                    PAGE_NUM_WIDTH,
                    OBJ_NUM_WIDTH,
                    CONTENT_SUMMARY_WIDTH);

    private final TagSchema schema;
    private final Consumer<String> verboseOutput;
    private PdfStructTreeRoot root;
    private List<Issue> issues = new ArrayList<>();
    private int globalIndex = 1;

    /** Encapsulates all data needed during tag tree validation walk. */
    private record ValidationContext(
            PdfStructElem node,
            String path,
            String role,
            TagSchema.Rule rule,
            String parentRole,
            List<PdfStructElem> children,
            List<String> childRoles,
            int level,
            int index) {
        static ValidationContext create(
                TagValidator validator, PdfStructElem node, String path, int level, int index) {
            String role = validator.mappedRole(node);
            TagSchema.Rule rule = validator.schema.roles.get(role);
            PdfStructElem parent = validator.parentOf(node);
            String parentRole = parent != null ? validator.mappedRole(parent) : null;
            List<PdfStructElem> children = validator.structKidsOf(node);
            List<String> childRoles = children.stream().map(validator::mappedRole).toList();

            return new ValidationContext(
                    node, path, role, rule, parentRole, children, childRoles, level, index);
        }
    }

    public TagValidator(TagSchema schema, Consumer<String> verboseOutput) {
        this.schema = schema;
        this.verboseOutput = verboseOutput;
    }

    public TagValidator(TagSchema schema) {
        this.schema = schema;
        this.verboseOutput = null;
    }

    public List<Issue> validate(PdfStructTreeRoot root) {
        this.root = root;
        this.issues = new ArrayList<>();
        this.globalIndex = 1;

        printHeader();
        walk(root);

        return List.copyOf(issues);
    }

    private void printHeader() {
        if (verboseOutput != null) {
            verboseOutput.accept(
                    String.format(
                            ROW_FORMAT, "Index", "Element", "Page", "Obj#", "Content", "Issues"));
            verboseOutput.accept(
                    String.format(
                            ROW_FORMAT,
                            "-".repeat(INDEX_WIDTH),
                            "-".repeat(ELEMENT_NAME_WIDTH),
                            "-".repeat(PAGE_NUM_WIDTH),
                            "-".repeat(OBJ_NUM_WIDTH),
                            "-".repeat(CONTENT_SUMMARY_WIDTH),
                            "-".repeat(ISSUES_WIDTH)));
        }
    }

    private void walk(PdfStructTreeRoot root) {
        String path = "/";
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem) {
                walk((PdfStructElem) kid, path, 0);
            }
        }
    }

    private void walk(PdfStructElem node, String path, int level) {
        int currentIndex = this.globalIndex++;

        ValidationContext ctx =
                ValidationContext.create(
                        this,
                        node,
                        path + mappedRole(node) + "[" + currentIndex + "]",
                        level,
                        currentIndex);

        List<String> elementIssues = new ArrayList<>();

        // Run each validation
        validateUnknownRole(ctx, elementIssues);
        validateParentRule(ctx, elementIssues);
        validateChildCount(ctx, elementIssues);
        validateAllowedChildren(ctx, elementIssues);
        validateChildPattern(ctx, elementIssues);

        // Output and recurse
        printElement(ctx, elementIssues);
        for (PdfStructElem child : ctx.children()) {
            walk(child, path + ctx.role() + ".", level + 1);
        }
    }

    private void validateUnknownRole(ValidationContext ctx, List<String> elementIssues) {
        if (ctx.rule() == null) {
            String message = "unknown role";
            issues.add(
                    new Issue(
                            IssueType.TAG_UNKNOWN_ROLE,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node(), ctx.path()),
                            message));
            elementIssues.add(message);
        }
    }

    private void validateParentRule(ValidationContext ctx, List<String> elementIssues) {
        if (ctx.rule() == null || ctx.rule().getParentMustBe() == null) return;
        if (ctx.parentRole() == null) return;

        if (!ctx.rule().getParentMustBe().contains(ctx.parentRole())) {
            String message =
                    "parent must be "
                            + formatRole(ctx.rule().getParentMustBe())
                            + " but is "
                            + formatRole(ctx.parentRole());
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_PARENT,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node(), ctx.path()),
                            message));
            elementIssues.add(message);
        }
    }

    private void validateChildCount(ValidationContext ctx, List<String> elementIssues) {
        if (ctx.rule() == null) return;

        if (ctx.rule().getMinChildren() != null
                && ctx.childRoles().size() < ctx.rule().getMinChildren()) {
            String message =
                    formatRole(ctx.role())
                            + " has "
                            + ctx.childRoles().size()
                            + " kids; min is "
                            + ctx.rule().getMinChildren();
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node(), ctx.path()),
                            message));
            elementIssues.add(message);
        }

        if (ctx.rule().getMaxChildren() != null
                && ctx.childRoles().size() > ctx.rule().getMaxChildren()) {
            String message =
                    formatRole(ctx.role())
                            + " has "
                            + ctx.childRoles().size()
                            + " kids; max is "
                            + ctx.rule().getMaxChildren();
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_COUNT,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node(), ctx.path()),
                            message));
            elementIssues.add(message);
        }
    }

    private void validateAllowedChildren(ValidationContext ctx, List<String> elementIssues) {
        if (ctx.rule() == null || ctx.rule().getAllowedChildren() == null) return;
        if (ctx.rule().getAllowedChildren().isEmpty()) return;

        boolean multiFixCreated = false;

        for (int i = 0; i < ctx.childRoles().size(); i++) {
            String childRole = ctx.childRoles().get(i);
            if (!ctx.rule().getAllowedChildren().contains(childRole)) {
                IssueFix fix = createChildFix(ctx, i, multiFixCreated, childRole);

                String message =
                        formatRole(childRole) + " not allowed under " + formatRole(ctx.role());
                issues.add(
                        new Issue(
                                IssueType.TAG_WRONG_CHILD,
                                IssueSeverity.ERROR,
                                new IssueLocation(ctx.children().get(i), ctx.path()),
                                message,
                                fix));

                if (!elementIssues.contains(message)) {
                    elementIssues.add(message);
                }

                if (fix instanceof TagMultipleChildrenFix) {
                    multiFixCreated = true;
                }
            }
        }
    }

    private void validateChildPattern(ValidationContext ctx, List<String> elementIssues) {
        if (ctx.rule() == null || ctx.rule().getChildPattern() == null) return;

        PatternMatcher pm = PatternMatcher.compile(ctx.rule().getChildPattern());
        if (pm != null && !pm.fullMatch(ctx.childRoles())) {
            String message =
                    "kids "
                            + ctx.childRoles()
                            + " do not match pattern '"
                            + ctx.rule().getChildPattern()
                            + "'";
            issues.add(
                    new Issue(
                            IssueType.TAG_WRONG_CHILD_PATTERN,
                            IssueSeverity.ERROR,
                            new IssueLocation(ctx.node(), ctx.path()),
                            message));
            elementIssues.add(message);
        }
    }

    private IssueFix createChildFix(
            ValidationContext ctx, int childIndex, boolean multiFixCreated, String childRole) {
        if (multiFixCreated) {
            logger.debug(
                    "Fix already created for parent {}; no further fix for kid {}",
                    formatRole(ctx.role()),
                    formatRole(childRole));
            return null;
        }

        return TagMultipleChildrenFix.createIfApplicable(ctx.node(), ctx.children())
                .or(
                        () ->
                                TagSingleChildFix.createIfApplicable(
                                        ctx.children().get(childIndex), ctx.node()))
                .orElseGet(
                        () -> {
                            logger.debug(
                                    "No automatic fix available for kid {} under parent {}",
                                    formatRole(childRole),
                                    formatRole(ctx.role()));
                            return null;
                        });
    }

    private void printElement(ValidationContext ctx, List<String> issues) {
        if (verboseOutput == null) {
            return;
        }

        // Skip empty Span elements without issues
        if ("Span".equals(ctx.role()) && ctx.children().isEmpty() && issues.isEmpty()) {
            return;
        }

        // Add MCR content summary for elements with marked content
        String mcrSummary =
                McidTextExtractor.getMcrContentSummary(
                        ctx.node(), root.getDocument(), getPageNumber(ctx.node()));

        String paddedIndex = String.format("%" + INDEX_WIDTH + "d", ctx.index());
        String elementName = INDENT.repeat(ctx.level()) + "- " + ctx.role();
        int pageNum = getPageNumber(ctx.node());
        String pageString = (pageNum == 0) ? "" : "(p. " + String.valueOf(pageNum) + ")";
        mcrSummary = (mcrSummary == null || mcrSummary.isEmpty()) ? "" : mcrSummary;
        String issuesText = issues.isEmpty() ? "" : "✗ " + String.join("; ", issues);
        verboseOutput.accept(
                String.format(
                        ROW_FORMAT,
                        paddedIndex,
                        elementName,
                        pageString,
                        getObjNum(ctx.node()),
                        mcrSummary,
                        issuesText));
    }

    private int getPageNumber(PdfStructElem node) {
        PdfDictionary dict = node.getPdfObject();
        PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);

        if (pg == null) {
            return 0;
        }
        PdfDocument doc = root.getDocument();
        return doc.getPageNumber(pg);
    }

    private int getObjNum(PdfStructElem node) {
        return node.getPdfObject().getIndirectReference().getObjNumber();
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

    private String mappedRole(PdfStructElem n) {
        PdfDictionary roleMap = this.root.getRoleMap();
        PdfName role = n.getRole();

        if (roleMap != null) {
            PdfName mappedRole = roleMap.getAsName(role);
            return (mappedRole != null) ? mappedRole.getValue() : role.getValue();
        }
        return role.getValue();
    }

    private PdfStructElem parentOf(PdfStructElem n) {
        IStructureNode p = n.getParent();
        return (p instanceof PdfStructElem) ? (PdfStructElem) p : null;
    }

    private List<PdfStructElem> structKidsOf(PdfStructElem n) {
        List<IStructureNode> kids = n.getKids();
        if (kids == null) return List.of();
        List<PdfStructElem> out = new ArrayList<>();
        for (IStructureNode k : kids) {
            if (k instanceof PdfStructElem) out.add((PdfStructElem) k);
        }
        return out;
    }
}
