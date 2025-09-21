package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TagValidator {
    private static final int DISPLAY_COLUMN_WIDTH = 40;
    private static final String INDENT = "  ";
    private static final Logger logger = LoggerFactory.getLogger(TagValidator.class);

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
            issues.add(new Issue(IssueType.TAG_PARENT_MISMATCH,
                    IssueSeverity.ERROR,
                    new IssueLocation(node, path),
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
                        new IssueLocation(node, path),
                        "Has "+childRoles.size()+" children; min is "+rule.minChildren));
                elementIssues.add("✗ Has "+childRoles.size()+" children; min is "+rule.minChildren);
            }
            if (rule.maxChildren != null && childRoles.size() > rule.maxChildren) {
                issues.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
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
                    // Create IssueFix for automatic wrapping
                    IssueFix fix = null;
                    if ("L".equals(role) && "P".equals(cr)) {
                        fix = new WrapInProperContainer(kids.get(i), node, role, cr);
                    } else {
                        logger.info("No automatic fix available for child role "+cr+" under parent role "+role);
                    }

                    issues.add(new Issue(IssueType.TAG_ILLEGAL_CHILD,
                            IssueSeverity.ERROR,
                            new IssueLocation(kids.get(i), path),
                            "Child #"+i+" role '"+cr+"' not allowed under "+role,
                            fix));
                    // Pass this issue down to the specific child instead of showing at parent
                    childSpecificIssues.get(i).add("✗ Role '"+cr+"' not allowed under "+role);
                }
            }
        }

        if (rule != null && rule.childPattern != null) {
            PatternMatcher pm = PatternMatcher.compile(rule.childPattern);
            if (pm != null && !pm.fullMatch(childRoles)) {
                // Create IssueFix for automatic structure correction
                IssueFix fix = null;
                if ("L".equals(role) && allChildrenAreP(childRoles)) {
                    fix = new FixListStructure(node, kids, role, childRoles);
                }

                issues.add(new Issue(IssueType.TAG_ORDER_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
                        "Children "+childRoles+" do not match pattern '"+rule.childPattern+"'",
                        fix));
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

    private boolean allChildrenAreP(List<String> childRoles) {
        return childRoles.stream().allMatch("P"::equals);
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

    // IssueFix implementations for automatic tag structure fixes

    private static class FixListStructure implements IssueFix {
        private final PdfStructElem listElement;
        private final List<PdfStructElem> children;
        private final String role;
        private final List<String> childRoles;

        FixListStructure(PdfStructElem listElement, List<PdfStructElem> children, String role, List<String> childRoles) {
            this.listElement = listElement;
            this.children = List.copyOf(children);
            this.role = role;
            this.childRoles = List.copyOf(childRoles);
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public void apply(ProcessingContext ctx) throws Exception {
            // For L elements with all P children, wrap pairs in LI->LBody structure
            if ("L".equals(role) && allChildrenAreP() && (childRoles.size() % 2 == 0)) {
                wrapPairsOfPInLI(ctx.doc());
            }
            // TODO: Add other pattern fixes as needed
        }

        @Override
        public String describe() {
            return "wrapped P elements in LI->LBody structure for list " + role;
        }

        @Override
        public boolean invalidates(IssueFix otherFix) {
            // If this fix operates on L with P children, it invalidates individual WrapInProperContainer
            // fixes that target any of the same child elements
            if (otherFix instanceof WrapInProperContainer) {
                WrapInProperContainer wrapper = (WrapInProperContainer) otherFix;
                return "L".equals(role) && "L".equals(wrapper.parentRole) &&
                       wrapper.parent.equals(listElement) && children.contains(wrapper.child);
            }
            return false;
        }

        private boolean allChildrenAreP() {
            return childRoles.stream().allMatch("P"::equals);
        }

        private void wrapPairsOfPInLI(PdfDocument document) throws Exception {
            for (int i = 0; i < children.size(); i += 2) {
                PdfStructElem p1 = (PdfStructElem) children.get(i);
                PdfStructElem p2 = (PdfStructElem) children.get(i + 1);

                PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
                listElement.addKid(newLI);

                PdfStructElem newLbl = new PdfStructElem(document, PdfName.Lbl);
                newLI.addKid(newLbl);
                listElement.removeKid(p1);
                newLbl.addKid(p1);

                PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
                newLI.addKid(newLBody);
                listElement.removeKid(p2);
                newLBody.addKid(p2);
            }
        }
    }

    private static class WrapInProperContainer implements IssueFix {
        final PdfStructElem child;
        final PdfStructElem parent;
        final String parentRole;
        private final String childRole;

        WrapInProperContainer(PdfStructElem child, PdfStructElem parent, String parentRole, String childRole) {
            this.child = child;
            this.parent = parent;
            this.parentRole = parentRole;
            this.childRole = childRole;
        }

        @Override
        public int priority() {
            return 15;
        }

        @Override
        public void apply(ProcessingContext ctx) throws Exception {
            if ("L".equals(parentRole) && "P".equals(childRole)) {
                // Wrap P in LI->LBody
                wrapPInLILBody(ctx.doc());
            }
            // TODO: Add other wrapping cases as needed
        }

        @Override
        public String describe() {
            return "wrapped " + childRole + " in proper container under " + parentRole;
        }

        private void wrapPInLILBody(PdfDocument document) throws Exception {
            PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
            parent.addKid(newLI);

            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            newLI.addKid(newLBody);

            parent.removeKid(child);
            newLBody.addKid(child);
        }
    }
}
