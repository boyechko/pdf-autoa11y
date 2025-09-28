package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final int ELEMENT_NAME_WIDTH = 30;
    private static final int PAGE_NUM_WIDTH = 10;
    private static final int OBJ_NUM_WIDTH = 20;
    // element, page, obj num, issues
    private static final String ROW_FORMAT =
        "%-" + ELEMENT_NAME_WIDTH + "s" +
        "%-" + PAGE_NUM_WIDTH + "s" +
        "%" + OBJ_NUM_WIDTH + "d" +
        "%s%n";

    private final TagSchema schema;
    private PrintStream output;
    private PdfStructTreeRoot root;
    private List<Issue> issues;

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

        walk(root);

        return List.copyOf(issues);
    }

    private void walk(PdfStructTreeRoot root) {
        String path = "/";
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;
        int index = 1;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem) {
                walk((PdfStructElem) kid, path, index, 0);
                index++;
            }
        }
    }

    private void walk(PdfStructElem node, String path, int index, int level) {
        walk(node, path, index, level, null);
    }

    private void walk(PdfStructElem node, String path, int index, int level, List<String> inheritedIssues) {
        String role = mappedRole(node);
        TagSchema.Rule rule = schema.roles.get(role);
        String parentRole = (parentOf(node) == null) ? null : mappedRole(parentOf(node));
        path = path + role + "[" + index + "]/";

        // Collect issues for this element
        List<String> elementIssues = new ArrayList<>();

        // Add any inherited issues from parent (like "not allowed under X")
        if (inheritedIssues != null) {
            elementIssues.addAll(inheritedIssues);
        }

        if (rule != null && rule.parentMustBe != null && parentRole != null && !rule.parentMustBe.equals(parentRole)) {
            issues.add(new Issue(IssueType.TAG_WRONG_PARENT,
                    IssueSeverity.ERROR,
                    new IssueLocation(node, path),
                    "Parent must be "+rule.parentMustBe+" but is "+parentRole));
            if (output != null) {
                elementIssues.add("✗ Parent must be "+rule.parentMustBe+" but is "+parentRole);
            }
        }

        List<PdfStructElem> kids = structKidsOf(node);
        List<String> kidRoles = kids.stream().map(this::mappedRole).toList();

        if (rule != null) {
            if (rule.minChildren != null && kidRoles.size() < rule.minChildren) {
                issues.add(new Issue(IssueType.TAG_WRONG_CHILD_COUNT,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
                        "Has "+kidRoles.size()+" kids; min is "+rule.minChildren));
                elementIssues.add("✗ Has "+kidRoles.size()+" kids; min is "+rule.minChildren);
            }
            if (rule.maxChildren != null && kidRoles.size() > rule.maxChildren) {
                issues.add(new Issue(IssueType.TAG_WRONG_CHILD_COUNT,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
                        "Has "+kidRoles.size()+" kids; max is "+rule.maxChildren));
                elementIssues.add("✗ Has "+kidRoles.size()+" kids; max is "+rule.maxChildren);
            }
        }

        // Create a map of kid-specific issues to pass down
        List<List<String>> kidSpecificIssues = new ArrayList<>();
        for (int i = 0; i < kids.size(); i++) {
            kidSpecificIssues.add(new ArrayList<>());
        }

        if (rule != null && rule.allowedChildren != null && !rule.allowedChildren.isEmpty()) {
            for (int i=0;i<kidRoles.size();i++) {
                String kidRole = kidRoles.get(i);
                if (!rule.allowedChildren.contains(kidRole)) {
                    IssueFix fix = TagMultipleChildrenFix.createIfApplicable(node, kids)
                        .orElse(TagSingleChildFix.createIfApplicable(kids.get(i), node)
                        .orElse(null));
                    if (fix == null) {
                        logger.debug("No automatic fix available for kid role "+kidRole+" under parent role "+role);
                    }

                    issues.add(new Issue(IssueType.TAG_WRONG_CHILD,
                            IssueSeverity.ERROR,
                            new IssueLocation(kids.get(i), path),
                            "Kid #"+i+" role '"+kidRole+"' not allowed under "+role,
                            fix));
                    // Pass this issue down to the specific kid instead of showing at parent
                    kidSpecificIssues.get(i).add("✗ Role '"+kidRole+"' not allowed under "+role);
                }
            }
        }

        if (rule != null && rule.childPattern != null) {
            PatternMatcher pm = PatternMatcher.compile(rule.childPattern);
            if (pm != null && !pm.fullMatch(kidRoles)) {
                // Create IssueFix for automatic structure correction
                IssueFix fix = null;

                issues.add(new Issue(IssueType.TAG_WRONG_CHILD_PATTERN,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
                        "Kids "+kidRoles+" do not match pattern '"+rule.childPattern+"'",
                        fix));
                elementIssues.add("✗ Kids "+kidRoles+" do not match pattern '"+rule.childPattern+"'");
            }
        }

        printElement(node, level, elementIssues, output);

        int i = 1;
        for (int kidIndex = 0; kidIndex < kids.size(); kidIndex++) {
            PdfStructElem kid = kids.get(kidIndex);
            List<String> kidIssues = kidSpecificIssues.get(kidIndex);
            walk(kid, path, i, level + 1, kidIssues.isEmpty() ? null : kidIssues);
            i++;
        }
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

    private void printElement(PdfStructElem node, int level, List<String> issues, PrintStream output) {
        String role = mappedRole(node);

        if ("Span".equals(role) && issues.isEmpty()) {
            return;
        }

        String elementName = INDENT.repeat(level) + "- " + role;
        int pageNum = getPageNumber(node);
        String pageString = (pageNum == 0) ? "" : "(p. " + String.valueOf(pageNum) + ")";
        String issuesText = issues.isEmpty() ? "" : String.join("; ", issues);
        output.printf(ROW_FORMAT, elementName, pageString, getObjNum(node), issuesText);
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
}
