// Audit a PDF's ParentTree against the structure tree and the page content.
//
// Standalone (Java 11+ single-file source launcher; needs only iText):
//
//   java -cp "$(cat target/tools-classpath.txt)" \
//       tools/AuditParentTree.java <in.pdf>
//
// (target/tools-classpath.txt is written by tools/extract-pages.sh, or by
// `mvn -q dependency:build-classpath -Dmdep.outputFile=target/tools-classpath.txt`.)
//
// Prints one line per inconsistency, keyed by ParentTree number as in Acrobat
// preflight's "Inconsistent ParentTree mapping (ParentTree element N)":
//
//   - a page slot naming an element unreachable from the StructTreeRoot (e.g. a
//     structure tree left behind by re-tagging; iText re-seeds such elements on
//     every load, so they can keep winning MCIDs from live ones)
//   - a page slot whose element's /K does not hold that MCID
//   - an MCID the live tree claims that the ParentTree misses or assigns to
//     another element, or that several live elements claim
//   - MCIDs drawn but unclaimed, or indexed but not drawn ("drawn" means the
//     page's own content streams; MCIDs inside Form XObjects are not seen)
//   - annotation keys whose element holds no OBJR to the annotation
//
// Tally by kind of finding before and after a change, so no category is missed:
//
//   java -cp ... tools/AuditParentTree.java in.pdf \
//       | sed -E 's/^key [0-9]+ \(page [0-9]+\):? (MCID [0-9]+ )?//; s/#[0-9]+//g' \
//       | sort | uniq -c | sort -rn
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuditParentTree {
    private static final Pattern MCID = Pattern.compile("/MCID\\s+(\\d+)");

    private final Map<Integer, PdfObject> nums = new TreeMap<>();
    private final Set<PdfIndirectReference> live = new HashSet<>();
    // page objNum -> MCID -> live elements whose /K claim it
    private final Map<Integer, Map<Integer, List<PdfDictionary>>> claims = new HashMap<>();

    public static void main(String[] args) throws Exception {
        try (PdfDocument doc = new PdfDocument(new PdfReader(args[0]))) {
            new AuditParentTree().audit(doc);
        }
    }

    private void audit(PdfDocument doc) {
        PdfDictionary root = doc.getStructTreeRoot().getPdfObject();
        collectNums(root.getAsDictionary(PdfName.ParentTree));
        walk(root, null);
        System.out.println(
                "ParentTree keys: "
                        + nums.size()
                        + ", ParentTreeNextKey: "
                        + root.get(PdfName.ParentTreeNextKey));

        Set<Integer> seen = new HashSet<>();
        for (int p = 1; p <= doc.getNumberOfPages(); p++) {
            PdfPage page = doc.getPage(p);
            PdfDictionary pageDict = page.getPdfObject();
            PdfNumber structParents = pageDict.getAsNumber(PdfName.StructParents);
            if (structParents != null) {
                int key = structParents.intValue();
                seen.add(key);
                auditPageSlots(p, pageDict, key);
                auditPageClaims(p, page, key);
            }
            PdfArray annots = pageDict.getAsArray(PdfName.Annots);
            if (annots == null) continue;
            for (int i = 0; i < annots.size(); i++) {
                PdfDictionary annot = annots.getAsDictionary(i);
                PdfNumber key = annot == null ? null : annot.getAsNumber(PdfName.StructParent);
                if (key == null) continue;
                seen.add(key.intValue());
                auditAnnot(p, annot, key.intValue());
            }
        }
        for (int key : nums.keySet()) {
            if (!seen.contains(key)) {
                System.out.println(
                        "key "
                                + key
                                + ": not referenced by any page or annotation ("
                                + describe(nums.get(key))
                                + ")");
            }
        }
    }

    /** Checks each slot of a page's array names a live element that holds that MCID. */
    private void auditPageSlots(int p, PdfDictionary page, int key) {
        PdfObject value = nums.get(key);
        String tag = "key " + key + " (page " + p + ")";
        if (!(value instanceof PdfArray slots)) {
            System.out.println(tag + ": expected array, got " + describe(value));
            return;
        }
        for (int mcid = 0; mcid < slots.size(); mcid++) {
            if (!(slots.get(mcid) instanceof PdfDictionary elem)) continue;
            if (!live.contains(elem.getIndirectReference())) {
                System.out.println(
                        tag
                                + " MCID "
                                + mcid
                                + " -> "
                                + describe(elem)
                                + " is detached from the tree");
            }
            if (!holdsMcid(elem, page, mcid)) {
                System.out.println(
                        tag + " MCID " + mcid + " -> " + describe(elem) + " but its /K lacks it");
            }
        }
    }

    /** Compares the live tree's claims and the drawn MCIDs against a page's array. */
    private void auditPageClaims(int p, PdfPage page, int key) {
        PdfArray slots = nums.get(key) instanceof PdfArray a ? a : new PdfArray();
        Map<Integer, List<PdfDictionary>> pageClaims =
                claims.getOrDefault(
                        page.getPdfObject().getIndirectReference().getObjNumber(), Map.of());
        Set<Integer> drawn = drawnMcids(page);
        String tag = "key " + key + " (page " + p + ")";

        for (var entry : pageClaims.entrySet()) {
            int mcid = entry.getKey();
            List<PdfDictionary> owners = entry.getValue();
            if (owners.size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (PdfDictionary owner : owners) sb.append(' ').append(describe(owner));
                System.out.println(
                        tag + " MCID " + mcid + " claimed by " + owners.size() + ":" + sb);
            }
            PdfObject slot = mcid < slots.size() ? slots.get(mcid) : null;
            if (slot == null || slot.isNull()) {
                System.out.println(
                        tag
                                + " MCID "
                                + mcid
                                + " claimed by "
                                + describe(owners.get(0))
                                + " but ParentTree slot is "
                                + (mcid < slots.size() ? "null" : "past end"));
            } else if (owners.stream().noneMatch(owner -> same(owner, slot))) {
                System.out.println(
                        tag
                                + " MCID "
                                + mcid
                                + " claimed by "
                                + describe(owners.get(0))
                                + " but ParentTree says "
                                + describe(slot));
            }
            if (!drawn.contains(mcid)) {
                System.out.println(tag + " MCID " + mcid + " claimed but not drawn");
            }
        }
        for (int mcid : drawn) {
            if (!pageClaims.containsKey(mcid)) {
                System.out.println(tag + " MCID " + mcid + " drawn but unclaimed");
            }
        }
        for (int mcid = 0; mcid < slots.size(); mcid++) {
            PdfObject slot = slots.get(mcid);
            if (slot != null && !slot.isNull() && !drawn.contains(mcid)) {
                System.out.println(tag + " MCID " + mcid + " in ParentTree but not drawn");
            }
        }
    }

    /** Checks an annotation's key names an element holding an OBJR to it. */
    private void auditAnnot(int p, PdfDictionary annot, int key) {
        PdfObject value = nums.get(key);
        String tag = "key " + key + " (annot " + ref(annot) + " on page " + p + ")";
        if (!(value instanceof PdfDictionary elem)) {
            System.out.println(tag + ": expected element, got " + describe(value));
            return;
        }
        if (!holdsObjr(elem, annot)) {
            System.out.println(tag + " -> " + describe(elem) + " but its /K has no OBJR to it");
        }
    }

    private void collectNums(PdfDictionary node) {
        if (node == null) return;
        PdfArray pairs = node.getAsArray(PdfName.Nums);
        if (pairs != null) {
            for (int i = 0; i + 1 < pairs.size(); i += 2) {
                nums.put(pairs.getAsNumber(i).intValue(), pairs.get(i + 1));
            }
        }
        PdfArray kids = node.getAsArray(PdfName.Kids);
        if (kids == null) return;
        for (int i = 0; i < kids.size(); i++) collectNums(kids.getAsDictionary(i));
    }

    /** Records the live elements and their MCID claims, resolving inherited /Pg. */
    private void walk(PdfDictionary elem, PdfDictionary inheritedPg) {
        PdfDictionary pg = elem.getAsDictionary(PdfName.Pg);
        if (pg == null) pg = inheritedPg;
        for (PdfObject kid : kids(elem)) {
            if (kid instanceof PdfNumber mcid) {
                claim(pg, mcid.intValue(), elem);
            } else if (kid instanceof PdfDictionary dict) {
                PdfNumber mcid = dict.getAsNumber(PdfName.MCID);
                if (mcid != null) {
                    PdfDictionary mcrPg = dict.getAsDictionary(PdfName.Pg);
                    claim(mcrPg != null ? mcrPg : pg, mcid.intValue(), elem);
                } else if (dict.get(PdfName.S) != null
                        && dict.getIndirectReference() != null
                        && live.add(dict.getIndirectReference())) {
                    walk(dict, pg);
                }
            }
        }
    }

    private void claim(PdfDictionary pg, int mcid, PdfDictionary elem) {
        if (pg == null || pg.getIndirectReference() == null) return;
        claims.computeIfAbsent(pg.getIndirectReference().getObjNumber(), k -> new TreeMap<>())
                .computeIfAbsent(mcid, k -> new ArrayList<>())
                .add(elem);
    }

    private static Set<Integer> drawnMcids(PdfPage page) {
        String content = new String(page.getContentBytes(), StandardCharsets.ISO_8859_1);
        Matcher m = MCID.matcher(content);
        Set<Integer> result = new TreeSet<>();
        while (m.find()) result.add(Integer.parseInt(m.group(1)));
        return result;
    }

    private static boolean holdsMcid(PdfDictionary elem, PdfDictionary page, int mcid) {
        PdfDictionary elemPg = elem.getAsDictionary(PdfName.Pg);
        for (PdfObject kid : kids(elem)) {
            if (kid instanceof PdfNumber n && n.intValue() == mcid && same(elemPg, page)) {
                return true;
            }
            if (kid instanceof PdfDictionary dict
                    && dict.getAsNumber(PdfName.MCID) != null
                    && dict.getAsNumber(PdfName.MCID).intValue() == mcid) {
                PdfDictionary pg = dict.getAsDictionary(PdfName.Pg);
                if (same(pg != null ? pg : elemPg, page)) return true;
            }
        }
        return false;
    }

    private static boolean holdsObjr(PdfDictionary elem, PdfDictionary annot) {
        for (PdfObject kid : kids(elem)) {
            if (kid instanceof PdfDictionary dict
                    && PdfName.OBJR.equals(dict.getAsName(PdfName.Type))
                    && same(dict.getAsDictionary(PdfName.Obj), annot)) {
                return true;
            }
        }
        return false;
    }

    private static List<PdfObject> kids(PdfDictionary elem) {
        PdfObject k = elem.get(PdfName.K);
        if (k == null) return List.of();
        if (!(k instanceof PdfArray array)) return List.of(k);
        List<PdfObject> result = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) result.add(array.get(i));
        return result;
    }

    private static boolean same(PdfObject a, PdfObject b) {
        return a != null
                && b != null
                && a.getIndirectReference() != null
                && a.getIndirectReference().equals(b.getIndirectReference());
    }

    private static String ref(PdfObject o) {
        return o.getIndirectReference() == null
                ? "direct"
                : "#" + o.getIndirectReference().getObjNumber();
    }

    private static String describe(PdfObject o) {
        if (o == null) return "missing";
        if (o instanceof PdfDictionary d && d.get(PdfName.S) != null) {
            return d.get(PdfName.S) + " " + ref(d);
        }
        return o.getClass().getSimpleName();
    }
}
