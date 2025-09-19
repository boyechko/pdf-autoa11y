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
        List<Issue> out = new ArrayList<>();

        output.println("Tag structure validation:");
        output.println("────────────────────────────────────────");

        if (root == null || root.getKids() == null) {
            output.println("No accessibility tags found");
            return out;
        }

        walk(root, out);

        if (out.isEmpty()) {
            output.println("✓ No issues found in tag structure");
        } else {
            output.println("Tag issues found: " + out.size());
        }

        return out;
    }

    private void walk(PdfStructTreeRoot root, List<Issue> out) {
        String path = "/";
        List<IStructureNode> kids = root.getKids();
        if (kids == null) return;
        int index = 1;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem) {
                walk((PdfStructElem) kid, path, index, 0, out);
                index++;
            }
        }
   }

    private void walk(PdfStructElem node, String path, int index, int level, List<Issue> out) {
        String role = mappedRole(node);
        TagSchema.Rule rule = schema.roles.get(role);
        String parentRole = (parentOf(node) == null) ? null : mappedRole(parentOf(node));
        path = path + role + "[" + index + "]/";

        // Collect issues for this element
        List<String> elementIssues = new ArrayList<>();

        if (rule != null && rule.parentMustBe != null && parentRole != null && !rule.parentMustBe.equals(parentRole)) {
            out.add(new Issue(IssueType.TAG_PARENT_MISMATCH,
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
                out.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Has "+childRoles.size()+" children; min is "+rule.minChildren));
                elementIssues.add("✗ Has "+childRoles.size()+" children; min is "+rule.minChildren);
            }
            if (rule.maxChildren != null && childRoles.size() > rule.maxChildren) {
                out.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Has "+childRoles.size()+" children; max is "+rule.maxChildren));
                elementIssues.add("✗ Has "+childRoles.size()+" children; max is "+rule.maxChildren);
            }
        }

        if (rule != null && rule.allowedChildren != null && !rule.allowedChildren.isEmpty()) {
            for (int i=0;i<childRoles.size();i++) {
                String cr = childRoles.get(i);
                if (!rule.allowedChildren.contains(cr)) {
                    out.add(new Issue(IssueType.TAG_ILLEGAL_CHILD,
                            IssueSeverity.ERROR,
                            new IssueLocation(null, path),
                            "Child #"+i+" role '"+cr+"' not allowed under "+role));
                    elementIssues.add("✗ Child #"+i+" role '"+cr+"' not allowed under "+role);
                }
            }
        }

        if (rule != null && rule.childPattern != null) {
            PatternMatcher pm = PatternMatcher.compile(rule.childPattern);
            if (pm != null && !pm.fullMatch(childRoles)) {
                out.add(new Issue(IssueType.TAG_ORDER_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(null, path),
                        "Children "+childRoles+" do not match pattern '"+rule.childPattern+"'"));
                elementIssues.add("✗ Children "+childRoles+" do not match pattern '"+rule.childPattern+"'");
            }
        }

        printElement(role, level, elementIssues, output);

        int i = 1;
        for (PdfStructElem kid : kids) {
            walk(kid, path, i, level + 1, out);
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
