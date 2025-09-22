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
            issues.add(new Issue(IssueType.TAG_PARENT_MISMATCH,
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
                issues.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
                        IssueSeverity.ERROR,
                        new IssueLocation(node, path),
                        "Has "+kidRoles.size()+" kids; min is "+rule.minChildren));
                elementIssues.add("✗ Has "+kidRoles.size()+" kids; min is "+rule.minChildren);
            }
            if (rule.maxChildren != null && kidRoles.size() > rule.maxChildren) {
                issues.add(new Issue(IssueType.TAG_CARDINALITY_VIOLATION,
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
                String cr = kidRoles.get(i);
                if (!rule.allowedChildren.contains(cr)) {
                    // Create IssueFix for automatic wrapping
                    IssueFix fix = null;
                    if ("L".equals(role) && "P".equals(cr)) {
                        fix = new WrapInProperContainer(kids.get(i), node, role, cr);
                    } else {
                        logger.info("No automatic fix available for kid role "+cr+" under parent role "+role);
                    }

                    issues.add(new Issue(IssueType.TAG_ILLEGAL_CHILD,
                            IssueSeverity.ERROR,
                            new IssueLocation(kids.get(i), path),
                            "Kid #"+i+" role '"+cr+"' not allowed under "+role,
                            fix));
                    // Pass this issue down to the specific kid instead of showing at parent
                    kidSpecificIssues.get(i).add("✗ Role '"+cr+"' not allowed under "+role);
                }
            }
        }

        if (rule != null && rule.childPattern != null) {
            PatternMatcher pm = PatternMatcher.compile(rule.childPattern);
            if (pm != null && !pm.fullMatch(kidRoles)) {
                // Create IssueFix for automatic structure correction
                IssueFix fix = null;
                if ("L".equals(role) && allKidsAreP(kidRoles)) {
                    fix = new FixListStructure(node, kids, role, kidRoles);
                }

                issues.add(new Issue(IssueType.TAG_ORDER_VIOLATION,
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

    private boolean allKidsAreP(List<String> kidRoles) {
        return kidRoles.stream().allMatch("P"::equals);
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

    // IssueFix implementations for automatic tag structure fixes

    private static class FixListStructure implements IssueFix {
        private final PdfStructElem listElement;
        private final List<PdfStructElem> kids;
        private final String role;
        private final List<String> kidRoles;

        FixListStructure(PdfStructElem listElement, List<PdfStructElem> kids, String role, List<String> kidRoles) {
            this.listElement = listElement;
            this.kids = List.copyOf(kids);
            this.role = role;
            this.kidRoles = List.copyOf(kidRoles);
        }

        @Override
        public int priority() {
            return 10;
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            // For L elements with all P kids, wrap pairs in LI->LBody structure
            if ("L".equals(role) && allKidsAreP() && (kidRoles.size() % 2 == 0)) {
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
            // If this fix operates on L with P kids, it invalidates individual WrapInProperContainer
            // fixes that target any of the same kid elements
            if (otherFix instanceof WrapInProperContainer) {
                WrapInProperContainer wrapper = (WrapInProperContainer) otherFix;
                return "L".equals(role) && "L".equals(wrapper.parentRole) &&
                       wrapper.parent.equals(listElement) && kids.contains(wrapper.kid);
            }
            return false;
        }

        private boolean allKidsAreP() {
            return kidRoles.stream().allMatch("P"::equals);
        }

        private void wrapPairsOfPInLI(PdfDocument document) throws Exception {
            for (int i = 0; i < kids.size(); i += 2) {
                PdfStructElem p1 = (PdfStructElem) kids.get(i);
                PdfStructElem p2 = (PdfStructElem) kids.get(i + 1);

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
        final PdfStructElem kid;
        final PdfStructElem parent;
        final String parentRole;
        private final String kidRole;

        WrapInProperContainer(PdfStructElem kid, PdfStructElem parent, String parentRole, String kidRole) {
            this.kid = kid;
            this.parent = parent;
            this.parentRole = parentRole;
            this.kidRole = kidRole;
        }

        @Override
        public int priority() {
            return 15;
        }

        @Override
        public void apply(DocumentContext ctx) throws Exception {
            if ("L".equals(parentRole) && "P".equals(kidRole)) {
                // Wrap P in LI->LBody
                wrapPInLILBody(ctx.doc());
            }
            // TODO: Add other wrapping cases as needed
        }

        @Override
        public String describe() {
            return "wrapped " + kidRole + " in proper container under " + parentRole;
        }

        private void wrapPInLILBody(PdfDocument document) throws Exception {
            PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
            parent.addKid(newLI);

            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            newLI.addKid(newLBody);

            parent.removeKid(kid);
            newLBody.addKid(kid);
        }
    }
}
