package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

public final class TagValidator {
    private static final int DISPLAY_COLUMN_WIDTH = 40;
    private static final String INDENT = "  ";

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
        this.issues = new ArrayList<>(); // Reset for new validation

        output.println("Tag structure validation:");
        output.println("────────────────────────────────────────");

        if (root == null || root.getKids() == null) {
            output.println("No accessibility tags found");
            return List.copyOf(issues); // Return immutable copy
        }

        walk(root);

        if (issues.isEmpty()) {
            output.println("✓ No issues found in tag structure");
        } else {
            output.println("Tag issues found: " + issues.size());
        }

        return List.copyOf(issues); // Return immutable copy
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
            issues.add(new Issue(IssueType.TAG_PARENT_MISMATCH,
                    IssueSeverity.ERROR,
                    new IssueLocation(null, path),
                    "Parent must be "+rule.parentMustBe+" but is "+parentRole));
            if (output != null) {
                elementIssues.add("✗ Parent must be "+rule.parentMustBe+" but is "+parentRole);
            }
        }

        List<PdfStructElem> kids = childrenOf(node);
        List<String> childRoles = kids.stream().map(this::mappedRole).toList();

        if (rule != null) {
            if (rule.minChildren != null && childRoles.size() < rule.minChildren) {
                issues.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Has "+childRoles.size()+" children; min is "+rule.minChildren));
                elementIssues.add("✗ Has "+childRoles.size()+" children; min is "+rule.minChildren);
            }
            if (rule.maxChildren != null && childRoles.size() > rule.maxChildren) {
                issues.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Has "+childRoles.size()+" children; max is "+rule.maxChildren));
                elementIssues.add("✗ Has "+childRoles.size()+" children; max is "+rule.maxChildren);
            }
        }

        // Create a map of child-specific issues to pass down
        List<List<String>> childSpecificIssues = new ArrayList<>();
        for (int i = 0; i < kids.size(); i++) {
            childSpecificIssues.add(new ArrayList<>());
        }

        if (rule != null && rule.allowedChildren != null && !rule.allowedChildren.isEmpty()) {
            for (int i=0;i<childRoles.size();i++) {
                String cr = childRoles.get(i);
                if (!rule.allowedChildren.contains(cr)) {
                    issues.add(new Issue(IssueType.TAG_ILLEGAL_CHILD,
                            IssueSeverity.ERROR,
                            new IssueLocation(null, path),
                            "Child #"+i+" role '"+cr+"' not allowed under "+role));
                    // Pass this issue down to the specific child instead of showing at parent
                    childSpecificIssues.get(i).add("✗ Role '"+cr+"' not allowed under "+role);
                }
            }
        }

        if (rule != null && rule.childPattern != null) {
            PatternMatcher pm = PatternMatcher.compile(rule.childPattern);
            if (pm != null && !pm.fullMatch(childRoles)) {
                issues.add(new Issue(IssueType.TAG_ORDER_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Children "+childRoles+" do not match pattern '"+rule.childPattern+"'"));
                elementIssues.add("✗ Children "+childRoles+" do not match pattern '"+rule.childPattern+"'");
            }
        }

        printElement(role, level, elementIssues, output);

        int i = 1;
        for (int kidIndex = 0; kidIndex < kids.size(); kidIndex++) {
            PdfStructElem kid = kids.get(kidIndex);
            List<String> kidIssues = childSpecificIssues.get(kidIndex);
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

    private List<PdfStructElem> childrenOf(PdfStructElem n) {
        List<IStructureNode> kids = n.getKids();
        if (kids == null) return List.of();
        List<PdfStructElem> out = new ArrayList<>();
        for (IStructureNode k : kids) {
            if (k instanceof PdfStructElem) out.add((PdfStructElem)k);
        }
        return out;
    }

    private void printElement(String role, int level, List<String> issues, java.io.PrintStream output) {
        String tagOutput = INDENT.repeat(level) + "- " + role;

        if (issues.isEmpty()) {
            output.println(tagOutput);
        } else {
            String comment = String.join("; ", issues);
            int currentLength = tagOutput.length();
            String padding = currentLength < DISPLAY_COLUMN_WIDTH ?
                " ".repeat(DISPLAY_COLUMN_WIDTH - currentLength) : "  ";
            output.println(tagOutput + padding + "; " + comment);
        }
    }
}
