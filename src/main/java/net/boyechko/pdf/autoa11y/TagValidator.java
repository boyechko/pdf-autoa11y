package net.boyechko.pdf.autoa11y;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;

// --- Schema types ---
final class RoleRule {
    String parentMustBe;
    Set<String> allowedChildren = Set.of();
    String childPattern;
    Integer minChildren;
    Integer maxChildren;
    Set<String> requiredChildren = Set.of();
}

final class TagSchema {
    Map<String, RoleRule> roles = new HashMap<>();
    static TagSchema minimalLists() {
        TagSchema s = new TagSchema();
        RoleRule L = new RoleRule();
        L.allowedChildren = Set.of("LI");
        L.childPattern = "LI+";
        L.minChildren = 1;
        s.roles.put("L", L);

        RoleRule LI = new RoleRule();
        LI.parentMustBe = "L";
        LI.allowedChildren = Set.of("Lbl", "LBody");
        LI.childPattern = "Lbl* LBody";
        LI.minChildren = 1;
        LI.maxChildren = 2;
        s.roles.put("LI", LI);

        RoleRule Lbl = new RoleRule();
        Lbl.parentMustBe = "LI";
        s.roles.put("Lbl", Lbl);

        RoleRule LBody = new RoleRule();
        LBody.parentMustBe = "LI";
        s.roles.put("LBody", LBody);

        return s;
    }
}

// --- PatternMatcher (same as before) ---
final class PatternMatcher {
    private static sealed interface Node permits PatternMatcher.Seq, PatternMatcher.Atom, PatternMatcher.Opt, PatternMatcher.Star, PatternMatcher.Plus, PatternMatcher.Group {
        boolean match(List<String> input, int[] idx);
    }
    private static final class Seq implements Node {
        final List<Node> parts;
        Seq(List<Node> parts) { this.parts = parts; }
        public boolean match(List<String> in, int[] i) {
            int start = i[0];
            for (Node n : parts) if (!n.match(in, i)) { i[0] = start; return false; }
            return true;
        }
    }
    private static final class Atom implements Node {
        final String sym;
        Atom(String s) { this.sym = s; }
        public boolean match(List<String> in, int[] i) {
            if (i[0] < in.size() && in.get(i[0]).equals(sym)) { i[0]++; return true; }
            return false;
        }
    }
    private static final class Opt implements Node {
        final Node inner;
        Opt(Node n){ this.inner = n; }
        public boolean match(List<String> in, int[] i){ int save=i[0]; if(!inner.match(in,i)) i[0]=save; return true; }
    }
    private static final class Star implements Node {
        final Node inner;
        Star(Node n){ this.inner = n; }
        public boolean match(List<String> in, int[] i){ while(inner.match(in,i)){} return true; }
    }
    private static final class Plus implements Node {
        final Node inner;
        Plus(Node n){ this.inner = n; }
        public boolean match(List<String> in, int[] i){ if(!inner.match(in,i)) return false; while(inner.match(in,i)){} return true; }
    }
    private static final class Group implements Node {
        final Node inner;
        Group(Node n){ this.inner = n; }
        public boolean match(List<String> in, int[] i){ return inner.match(in,i); }
    }

    private final Node root;
    private PatternMatcher(Node root){ this.root = root; }

    static PatternMatcher compile(String pattern) {
        if (pattern == null || pattern.isBlank()) return null;
        return new PatternMatcher(new Parser(pattern).parse());
    }

    boolean fullMatch(List<String> seq) {
        int[] i = {0};
        boolean ok = root.match(seq, i);
        return ok && i[0] == seq.size();
    }

    private static final class Parser {
        final List<String> toks; int p=0;
        Parser(String s){ this.toks = lex(s); }
        Node parse() {
            List<Node> seq = new ArrayList<>();
            while (p < toks.size()) {
                String t = toks.get(p);
                if (")".equals(t)) break;
                Node n = parseAtom();
                while (p < toks.size()) {
                    String op = toks.get(p);
                    if ("?".equals(op)) { p++; n = new Opt(n); }
                    else if ("*".equals(op)) { p++; n = new Star(n); }
                    else if ("+".equals(op)) { p++; n = new Plus(n); }
                    else break;
                }
                seq.add(n);
            }
            return seq.size()==1 ? seq.get(0) : new Seq(seq);
        }
        private Node parseAtom() {
            String t = toks.get(p++);
            if ("(".equals(t)) {
                Node inner = parse();
                expect(")");
                return new Group(inner);
            }
            return new Atom(t);
        }
        private void expect(String s) {
            if (p>=toks.size() || !toks.get(p).equals(s)) throw new IllegalArgumentException("Expected '"+s+"'");
            p++;
        }
        private static List<String> lex(String s) {
            List<String> out = new ArrayList<>();
            StringBuilder buf = new StringBuilder();
            for (int i=0;i<s.length();i++){
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) { flush(buf,out); continue; }
                if ("()?*+".indexOf(c)>=0) { flush(buf,out); out.add(String.valueOf(c)); continue; }
                buf.append(c);
            }
            flush(buf,out);
            return out;
        }
        private static void flush(StringBuilder b,List<String> o){ if(b.length()>0){ o.add(b.toString()); b.setLength(0);} }
    }
}

final class TagValidator {
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
        RoleRule rule = schema.roles.get(role);
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
