// Split a footnote marker ("*" plus adjacent spaces) off the start or end of a
// marked-content block into its own Lbl, for when Acrobat can't cut the text
// run finely enough.
//
//   tools/run SplitOffLbl <in.pdf> <out.pdf> <page> <mcid>
//
// The cut is made inside the Tj/TJ string, so no glyph moves. The new Lbl is
// inserted beside the block's MCR in the same parent; move it into its
// Reference or Note in Acrobat. Run again on the output to split another block.
// Refuses a block with a "*" at neither end or both, or one where a BT/ET or
// nested marked content sits between the marker and the block's edge.
import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfType0Font;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfMcrNumber;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SplitOffLbl {
    /** One parsed operation, with its byte span and the font in effect when it ran. */
    record Op(int start, int end, String name, List<PdfObject> operands, PdfFont font) {}

    /** The label and remaining elements of a show op, cut at the label's end of it. */
    record Cut(boolean leading, List<PdfObject> label, List<PdfObject> rest) {}

    private static final Set<String> NESTING_OPS = Set.of("BT", "ET", "BDC", "BMC", "EMC");

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: SplitOffLbl <in.pdf> <out.pdf> <page> <mcid>");
            System.exit(2);
        }
        int pageNum = Integer.parseInt(args[2]);
        int mcid = Integer.parseInt(args[3]);
        try (PdfDocument doc = new PdfDocument(new PdfReader(args[0]), new PdfWriter(args[1]))) {
            PdfPage page = doc.getPage(pageNum);
            for (int i = 0; i < page.getContentStreamCount(); i++) {
                PdfStream stream = page.getContentStream(i);
                List<Op> block = blockOf(stream, page, mcid);
                if (block != null) {
                    split(doc, page, stream, block, mcid);
                    return;
                }
            }
            throw new IllegalStateException("MCID " + mcid + " not found on page " + pageNum);
        } catch (Exception e) {
            // PdfWriter has already written an unchanged copy; don't leave it to be chained on.
            Files.deleteIfExists(Path.of(args[1]));
            throw e;
        }
    }

    /** Returns the ops from the MCID's BDC through its matching EMC, or null if absent. */
    private static List<Op> blockOf(PdfStream stream, PdfPage page, int mcid) throws Exception {
        byte[] bytes = stream.getBytes();
        PdfResources resources = page.getResources();
        PdfDictionary fonts = resources.getResource(PdfName.Font);
        Map<PdfName, PdfFont> fontCache = new HashMap<>();
        RandomAccessFileOrArray source =
                new RandomAccessFileOrArray(new RandomAccessSourceFactory().createSource(bytes));
        try (PdfTokenizer tokenizer = new PdfTokenizer(source)) {
            PdfCanvasParser parser = new PdfCanvasParser(tokenizer, resources);
            List<PdfObject> operands = new ArrayList<>();
            List<Op> block = null;
            PdfFont font = null;
            int depth = 0;
            while (true) {
                int start = (int) tokenizer.getPosition();
                parser.parse(operands);
                if (operands.isEmpty()) {
                    return null;
                }
                String name = operands.get(operands.size() - 1).toString();
                if (name.equals("Tf") && operands.get(0) instanceof PdfName fontName) {
                    font =
                            fontCache.computeIfAbsent(
                                    fontName,
                                    n -> PdfFontFactory.createFont(fonts.getAsDictionary(n)));
                }
                if (block == null) {
                    if (!Integer.valueOf(mcid).equals(mcidOfBdc(name, operands, resources))) {
                        continue;
                    }
                    block = new ArrayList<>();
                }
                block.add(
                        new Op(
                                start,
                                (int) tokenizer.getPosition(),
                                name,
                                new ArrayList<>(operands),
                                font));
                if (name.equals("BDC") || name.equals("BMC")) {
                    depth++;
                } else if (name.equals("EMC") && --depth == 0) {
                    return block;
                }
            }
        }
    }

    /** Returns a BDC's MCID from its inline or named property list, or null. */
    private static Integer mcidOfBdc(
            String name, List<PdfObject> operands, PdfResources resources) {
        if (!name.equals("BDC") || operands.size() != 3) {
            return null;
        }
        PdfObject props = operands.get(1);
        if (props instanceof PdfName propsName) {
            props = resources.getProperties(propsName);
        }
        return props instanceof PdfDictionary dict ? dict.getAsInt(PdfName.MCID) : null;
    }

    private static void split(
            PdfDocument doc, PdfPage page, PdfStream stream, List<Op> block, int mcid) {
        List<Integer> shows = new ArrayList<>();
        for (int i = 0; i < block.size(); i++) {
            String name = block.get(i).name();
            // Spaces-only show ops at either edge are left out, so the marker is found past them
            // and they go into the Lbl alongside it.
            if ((name.equals("Tj") || name.equals("TJ")) && !showsOnlySpaces(block.get(i))) {
                shows.add(i);
            }
        }
        if (shows.isEmpty()) {
            throw new IllegalStateException("MCID " + mcid + " shows no visible text with Tj/TJ");
        }
        int first = shows.get(0);
        int last = shows.get(shows.size() - 1);
        Cut lead = cut(block.get(first), true);
        Cut trail = cut(block.get(last), false);
        if ((lead == null) == (trail == null)) {
            throw new IllegalStateException(
                    "MCID "
                            + mcid
                            + ": need a '*' at exactly one end; found "
                            + (lead == null ? "neither" : "both"));
        }
        Cut cut = lead != null ? lead : trail;
        int showIdx = lead != null ? first : last;
        // The new EMC/BDC pair lands at the show op, so nothing between it and the block's
        // edge may open or close a text object or nested marked content.
        List<Op> between =
                cut.leading()
                        ? block.subList(1, showIdx)
                        : block.subList(showIdx + 1, block.size() - 1);
        for (Op op : between) {
            if (NESTING_OPS.contains(op.name())) {
                throw new IllegalStateException(
                        "MCID "
                                + mcid
                                + ": "
                                + op.name()
                                + " between the label and the block edge");
            }
        }

        int newMcid = nextMcid(page);
        PdfStructElem lbl = insertLbl(doc, page, mcid, cut.leading(), newMcid);

        byte[] bytes = stream.getBytes();
        Op bdc = block.get(0);
        Op show = block.get(showIdx);
        String oldBdc =
                new String(bytes, bdc.start(), bdc.end() - bdc.start(), StandardCharsets.ISO_8859_1)
                        .strip();
        String newBdc = "/Lbl <</MCID " + newMcid + ">> BDC";
        String showReplacement =
                cut.leading()
                        ? showOf(cut.label()) + "\nEMC\n" + oldBdc + "\n" + showOf(cut.rest())
                        : showOf(cut.rest()) + "\nEMC\n" + newBdc + "\n" + showOf(cut.label());
        ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length + 64);
        out.write(bytes, 0, bdc.start());
        if (cut.leading()) {
            out.writeBytes(latin1("\n" + newBdc + "\n"));
        } else {
            out.write(bytes, bdc.start(), bdc.end() - bdc.start());
        }
        out.write(bytes, bdc.end(), show.start() - bdc.end());
        out.writeBytes(latin1("\n" + showReplacement + "\n"));
        out.write(bytes, show.end(), bytes.length - show.end());
        stream.setData(out.toByteArray());

        PdfStructElem parent = (PdfStructElem) lbl.getParent();
        System.out.printf(
                "Split '%s' off MCID %d into Lbl #%d (MCID %d), %s it in %s #%d%n",
                decode(show.font(), cut.label()),
                mcid,
                lbl.getPdfObject().getIndirectReference().getObjNumber(),
                newMcid,
                cut.leading() ? "before" : "after",
                parent.getRole().getValue(),
                parent.getPdfObject().getIndirectReference().getObjNumber());
    }

    /**
     * Cuts "*" plus adjacent spaces off the start (or end) of the show op's first (or last) string,
     * or returns null when that end isn't a label.
     */
    private static Cut cut(Op show, boolean leading) {
        List<PdfObject> elems = new ArrayList<>();
        PdfObject arg = show.operands().get(0);
        if (arg instanceof PdfArray array) {
            array.forEach(elems::add);
        } else {
            elems.add(arg);
        }
        int e = -1;
        for (int i = 0; i < elems.size(); i++) {
            if (elems.get(i) instanceof PdfString) {
                e = i;
                if (leading) {
                    break;
                }
            }
        }
        if (e < 0 || show.font() == null) {
            return null;
        }
        List<byte[]> codes = codesOf(show.font(), (PdfString) elems.get(e));
        int n = codes.size();
        int k; // number of codes in the label
        if (leading) {
            if (n == 0 || !text(show.font(), codes.get(0)).equals("*")) {
                return null;
            }
            k = 1;
            while (k < n && text(show.font(), codes.get(k)).isBlank()) {
                k++;
            }
        } else {
            k = 0;
            while (k < n && text(show.font(), codes.get(n - 1 - k)).isBlank()) {
                k++;
            }
            if (k == n || !text(show.font(), codes.get(n - 1 - k)).equals("*")) {
                return null;
            }
            k++;
        }
        int at = leading ? k : n - k;
        List<PdfObject> head = new ArrayList<>(elems.subList(0, e));
        addIfNonEmpty(head, join(codes.subList(0, at)));
        List<PdfObject> tail = new ArrayList<>();
        addIfNonEmpty(tail, join(codes.subList(at, n)));
        tail.addAll(elems.subList(e + 1, elems.size()));
        return leading ? new Cut(true, head, tail) : new Cut(false, tail, head);
    }

    /** Splits a string into character codes: two bytes each for a Type0 font, else one. */
    private static List<byte[]> codesOf(PdfFont font, PdfString string) {
        byte[] bytes = string.getValueBytes();
        int width = font instanceof PdfType0Font ? 2 : 1;
        List<byte[]> codes = new ArrayList<>();
        for (int i = 0; i + width <= bytes.length; i += width) {
            codes.add(Arrays.copyOfRange(bytes, i, i + width));
        }
        return codes;
    }

    private static String text(PdfFont font, byte[] code) {
        return font.decode(new PdfString(code));
    }

    private static boolean showsOnlySpaces(Op show) {
        if (show.font() == null) {
            return false;
        }
        PdfObject arg = show.operands().get(0);
        List<PdfObject> elems =
                arg instanceof PdfArray array ? array.subList(0, array.size()) : List.of(arg);
        return decode(show.font(), elems).isBlank();
    }

    private static String decode(PdfFont font, List<PdfObject> elems) {
        StringBuilder sb = new StringBuilder();
        for (PdfObject obj : elems) {
            if (obj instanceof PdfString s) {
                sb.append(font.decode(s));
            }
        }
        return sb.toString();
    }

    private static byte[] join(List<byte[]> codes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        codes.forEach(out::writeBytes);
        return out.toByteArray();
    }

    private static void addIfNonEmpty(List<PdfObject> elems, byte[] bytes) {
        if (bytes.length > 0) {
            elems.add(new PdfString(bytes));
        }
    }

    /** Renders elements as a hex-string Tj, or a TJ array when kerning numbers remain. */
    private static String showOf(List<PdfObject> elems) {
        if (elems.size() == 1 && elems.get(0) instanceof PdfString s) {
            return hex(s) + " Tj";
        }
        StringBuilder sb = new StringBuilder("[");
        for (PdfObject obj : elems) {
            sb.append(obj instanceof PdfString s ? hex(s) : " " + obj + " ");
        }
        return sb.append("] TJ").toString();
    }

    private static String hex(PdfString s) {
        StringBuilder sb = new StringBuilder("<");
        for (byte b : s.getValueBytes()) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.append('>').toString();
    }

    private static byte[] latin1(String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Returns an MCID unused both in the ParentTree and in the page's content streams. */
    private static int nextMcid(PdfPage page) {
        int next = page.getNextMcid();
        Pattern mcidPattern = Pattern.compile("/MCID\\s+(\\d+)");
        for (int i = 0; i < page.getContentStreamCount(); i++) {
            Matcher m =
                    mcidPattern.matcher(
                            new String(
                                    page.getContentStream(i).getBytes(),
                                    StandardCharsets.ISO_8859_1));
            while (m.find()) {
                next = Math.max(next, Integer.parseInt(m.group(1)) + 1);
            }
        }
        return next;
    }

    /** Inserts a new Lbl holding {@code newMcid} before or after the MCID's MCR. */
    private static PdfStructElem insertLbl(
            PdfDocument doc, PdfPage page, int mcid, boolean before, int newMcid) {
        PdfMcr mcr = doc.getStructTreeRoot().findMcrByMcid(page.getPdfObject(), mcid);
        if (mcr == null || !(mcr.getParent() instanceof PdfStructElem parent)) {
            throw new IllegalStateException("No struct element owns MCID " + mcid);
        }
        // Match by MCID: the ParentTree's MCR wrapper isn't the same object as the parent's kid.
        List<IStructureNode> kids = parent.getKids();
        int idx = -1;
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i) instanceof PdfMcr kid
                    && kid.getMcid() == mcid
                    && page.getPdfObject()
                            .getIndirectReference()
                            .equals(kid.getPageIndirectReference())) {
                idx = i;
            }
        }
        if (idx < 0) {
            throw new IllegalStateException("MCID " + mcid + " not among its owner's kids");
        }
        PdfStructElem lbl = new PdfStructElem(doc, PdfName.Lbl, page);
        parent.addKid(before ? idx : idx + 1, lbl);
        lbl.addKid(new PdfMcrNumber(new PdfNumber(newMcid), lbl));
        return lbl;
    }
}
