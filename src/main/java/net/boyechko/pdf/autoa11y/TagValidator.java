package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import net.boyechko.pdf.autoa11y.fixes.TagMultipleChildrenFix;
import net.boyechko.pdf.autoa11y.fixes.TagSingleChildFix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagValidator {
    private static final Logger logger = LoggerFactory.getLogger(TagValidator.class);

    private static final String INDENT = "  ";
    private static final int INDEX_WIDTH = 5;
    private static final int ELEMENT_NAME_WIDTH = 30;
    private static final int PAGE_NUM_WIDTH = 10;
    private static final int OBJ_NUM_WIDTH = 6;
    // element, page, obj num, issues
    private static final String ROW_FORMAT =
        "%-" + INDEX_WIDTH + "s " +
        "%-" + ELEMENT_NAME_WIDTH + "s " +
        "%-" + PAGE_NUM_WIDTH + "s " +
        "%-" + OBJ_NUM_WIDTH + "s " +
        "%s%n";

    private final TagSchema schema;
    private final PrintStream output;
    private PdfStructTreeRoot root;
    private List<Issue> issues = new ArrayList<>();
    private int globalIndex = 1;

    TagValidator(TagSchema schema, PrintStream output) {
        this.schema = schema;
        this.output = output;
    }

    TagValidator(TagSchema schema) {
        this.schema = schema;
        this.output = System.out;
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
        if (output != null) {
            output.printf(ROW_FORMAT, "Index", "Element", "Page", "Obj#", "Issues");
            output.printf(ROW_FORMAT,
                "-".repeat(INDEX_WIDTH),
                "-".repeat(ELEMENT_NAME_WIDTH),
                "-".repeat(PAGE_NUM_WIDTH),
                "-".repeat(OBJ_NUM_WIDTH),
                "-".repeat(6)); // "Issues" is 6 chars:w
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
        walk(node, path, globalIndex++, level, null);
    }

    private void walk(PdfStructElem node, String path, int index, int level, List<String> inheritedIssues) {
        int currentIndex = this.globalIndex++;

        String role = mappedRole(node);
        TagSchema.Rule rule = schema.roles.get(role);
        String parentRole = parentOf(node) != null ? mappedRole(parentOf(node)) : null;
        List<PdfStructElem> children = structKidsOf(node);
        List<String> childRoles = children.stream().map(this::mappedRole).toList();
        path = path + role;
        String location = path + "[" + currentIndex + "]";

        List<String> elementIssues = new ArrayList<>();
        if (inheritedIssues != null) {
            elementIssues.addAll(inheritedIssues);
        }

        // Run each validation
        validateUnknownRole(node, location, role, rule, elementIssues);
        validateParentRule(node, location, role, rule, parentRole, elementIssues);
        validateChildCount(node, location, role, rule, childRoles, elementIssues);
        validateAllowedChildren(node, location, role, rule, children, childRoles, elementIssues);
        validateChildPattern(node, location, role, rule, childRoles, elementIssues);

        // Output and recurse
        printElement(node, currentIndex, level, elementIssues);
        for (PdfStructElem child : children) {
            walk(child, path + ".", level + 1);
        }
    }

    private void validateUnknownRole(PdfStructElem node, String path, String role,
                                     TagSchema.Rule rule, List<String> elementIssues) {
        if (rule == null) {
            String message = "unknown role";
            issues.add(new Issue(
                IssueType.TAG_UNKNOWN_ROLE,
                IssueSeverity.ERROR,
                new IssueLocation(node, path),
                message
            ));
            elementIssues.add(message);
        }
    }

    private void validateParentRule(PdfStructElem node, String path, String role,
                                    TagSchema.Rule rule, String parentRole,
                                    List<String> elementIssues) {
        if (rule == null || rule.getParentMustBe() == null) return;
        if (parentRole == null) return;

        if (!rule.getParentMustBe().contains(parentRole)) {
            String message = "parent must be " + formatRole(rule.getParentMustBe()) +
                           " but is " + formatRole(parentRole);
            issues.add(new Issue(
                IssueType.TAG_WRONG_PARENT,
                IssueSeverity.ERROR,
                new IssueLocation(node, path),
                message
            ));
            elementIssues.add(message);
        }
    }

    private void validateChildCount(PdfStructElem node, String path, String role,
                                    TagSchema.Rule rule, List<String> childRoles,
                                    List<String> elementIssues) {
        if (rule == null) return;

        if (rule.getMinChildren() != null && childRoles.size() < rule.getMinChildren()) {
            String message = formatRole(role) + " has " + childRoles.size() +
                           " kids; min is " + rule.getMinChildren();
            issues.add(new Issue(
                IssueType.TAG_WRONG_CHILD_COUNT,
                IssueSeverity.ERROR,
                new IssueLocation(node, path),
                message
            ));
            elementIssues.add(message);
        }

        if (rule.getMaxChildren() != null && childRoles.size() > rule.getMaxChildren()) {
            String message = formatRole(role) + " has " + childRoles.size() +
                           " kids; max is " + rule.getMaxChildren();
            issues.add(new Issue(
                IssueType.TAG_WRONG_CHILD_COUNT,
                IssueSeverity.ERROR,
                new IssueLocation(node, path),
                message
            ));
            elementIssues.add(message);
        }
    }

    private void validateAllowedChildren(PdfStructElem node, String path, String role,
                                        TagSchema.Rule rule, List<PdfStructElem> children,
                                        List<String> childRoles, List<String> elementIssues) {
        if (rule == null || rule.getAllowedChildren() == null) return;
        if (rule.getAllowedChildren().isEmpty()) return;

        boolean multiFixCreated = false;

        for (int i = 0; i < childRoles.size(); i++) {
            String childRole = childRoles.get(i);
            if (!rule.getAllowedChildren().contains(childRole)) {
                IssueFix fix = createChildFix(node, children, i, multiFixCreated, role, childRole);

                String message = formatRole(childRole) + " not allowed under " + formatRole(role);
                issues.add(new Issue(
                    IssueType.TAG_WRONG_CHILD,
                    IssueSeverity.ERROR,
                    new IssueLocation(children.get(i), path),
                    message,
                    fix
                ));

                if (!elementIssues.contains("(child issues)")) {
                    elementIssues.add("(child issues)");
                }

                if (fix instanceof TagMultipleChildrenFix) {
                    multiFixCreated = true;
                }
            }
        }
    }

    private void validateChildPattern(PdfStructElem node, String path, String role,
                                      TagSchema.Rule rule, List<String> childRoles,
                                      List<String> elementIssues) {
        if (rule == null || rule.getChildPattern() == null) return;

        PatternMatcher pm = PatternMatcher.compile(rule.getChildPattern());
        if (pm != null && !pm.fullMatch(childRoles)) {
            String message = "kids " + childRoles + " do not match pattern '" +
                           rule.getChildPattern() + "'";
            issues.add(new Issue(
                IssueType.TAG_WRONG_CHILD_PATTERN,
                IssueSeverity.ERROR,
                new IssueLocation(node, path),
                message
            ));
            elementIssues.add(message);
        }
    }

    private IssueFix createChildFix(PdfStructElem parent, List<PdfStructElem> children,
                                   int childIndex, boolean multiFixCreated,
                                   String parentRole, String childRole) {
        if (multiFixCreated) {
            logger.debug("Fix already created for parent {}; no further fix for kid {}",
                       formatRole(parentRole), formatRole(childRole));
            return null;
        }

        return TagMultipleChildrenFix.createIfApplicable(parent, children)
            .or(() -> TagSingleChildFix.createIfApplicable(children.get(childIndex), parent))
            .orElseGet(() -> {
                logger.debug("No automatic fix available for kid {} under parent {}",
                           formatRole(childRole), formatRole(parentRole));
                return null;
            });
    }

    private void printElement(PdfStructElem node, int index, int level, List<String> issues) {
        String role = node.getRole().getValue();

        if ("Span".equals(role) && (structKidsOf(node).size() == 0) && issues.isEmpty()) {
            return;
        }

        String paddedIndex = String.format("%" + INDEX_WIDTH + "d", index);
        String elementName = INDENT.repeat(level) + "- " + role;
        int pageNum = getPageNumber(node);
        String pageString = (pageNum == 0) ? "" : "(p. " + String.valueOf(pageNum) + ")";
        String issuesText = issues.isEmpty() ? "" : "✗ " + String.join("; ", issues);
        output.printf(ROW_FORMAT, paddedIndex, elementName, pageString, getObjNum(node), issuesText);
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
        return (p instanceof PdfStructElem) ? (PdfStructElem)p : null;
    }

    private List<PdfStructElem> structKidsOf(PdfStructElem n) {
        List<IStructureNode> kids = n.getKids();
        if (kids == null) return List.of();
        List<PdfStructElem> out = new ArrayList<>();
        for (IStructureNode k : kids) {
            if (k instanceof PdfStructElem) out.add((PdfStructElem)k);
        }
        return out;
    }
}
