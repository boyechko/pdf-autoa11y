// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.io.source.PdfTokenizer;
import com.itextpdf.io.source.RandomAccessFileOrArray;
import com.itextpdf.io.source.RandomAccessSourceFactory;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfLiteral;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.canvas.parser.util.PdfCanvasParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Interprets parsed content-stream operations, notably marked-content (BDC/BMC/EMC) operands. */
public final class ContentStream {

    private ContentStream() {}

    /** True if the parsed operands end with the given operator literal. */
    public static boolean isOperator(List<PdfObject> operands, String op) {
        if (operands.isEmpty()) {
            return false;
        }
        return isLiteral(operands.get(operands.size() - 1), op);
    }

    /** True if the object is the given operator or keyword literal. */
    public static boolean isLiteral(PdfObject object, String literalText) {
        return object instanceof PdfLiteral literal && literalText.equals(literal.toString());
    }

    /** Returns a BDC operation's MCID, or null when absent or not a BDC. */
    public static Integer mcidOfBdc(
            List<PdfObject> operands, PdfPage page, PdfResources resources) {
        if (!isOperator(operands, "BDC") || operands.size() < 3) {
            return null;
        }
        return resolveMcid(resolvePropertiesOperand(operands, page), resources);
    }

    /** Returns a BDC operation's tag name (e.g. /P), or null when not a BDC. */
    public static PdfName tagOfBdc(List<PdfObject> operands) {
        if (!isOperator(operands, "BDC") || !(operands.get(0) instanceof PdfName tag)) {
            return null;
        }
        return tag;
    }

    private static PdfObject resolvePropertiesOperand(List<PdfObject> operands, PdfPage page) {
        // BDC uses tag + properties + operator.
        if (operands.size() == 3) {
            return operands.get(1);
        }

        // Some producers emit an indirect reference: /Tag objNum genNum R BDC
        if (operands.size() == 5
                && operands.get(1) instanceof PdfNumber objNum
                && operands.get(2) instanceof PdfNumber
                && isLiteral(operands.get(3), "R")) {
            return page.getDocument().getPdfObject(objNum.intValue());
        }

        return null;
    }

    private static Integer resolveMcid(PdfObject propertiesOperand, PdfResources resources) {
        if (propertiesOperand instanceof PdfDictionary dict) {
            return dict.getAsInt(PdfName.MCID);
        }
        if (propertiesOperand instanceof PdfName name && resources != null) {
            PdfObject propertiesObj = resources.getProperties(name);
            if (propertiesObj instanceof PdfDictionary propertiesDict) {
                return propertiesDict.getAsInt(PdfName.MCID);
            }
        }
        return null;
    }

    // == Marked-content block splitting ==================================

    /** Operators that move the text position to a new line. */
    private static final Set<String> LINE_OPS = Set.of("Td", "TD", "T*", "Tm");

    /** Operators that show text. */
    private static final Set<String> SHOW_OPS = Set.of("Tj", "TJ");

    /** Operators that both move to the next line and show text. */
    private static final Set<String> NEXT_LINE_SHOW_OPS = Set.of("'", "\"");

    /** Operators that paint content, which must not end up outside marked-content blocks. */
    private static final Set<String> PAINT_OPS =
            Set.of("S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "sh", "Do", "BI", "ID", "EI");

    private static final byte[] NO_BYTES = new byte[0];

    /** One text object (BT...ET) inside the block: positions just after the BT and at the ET. */
    public record TextSpan(int afterBt, int etStart) {}

    /** A line's effective font: its /Font resource name and its size scaled by the text matrix. */
    private record EffFont(PdfName font, double size) {
        boolean differsFrom(EffFont other) {
            boolean sameName = font == null ? other.font == null : font.equals(other.font);
            return !sameName || size != other.size;
        }
    }

    /**
     * The block's operator geometry: the opening BDC's bytes and byte range, the closing EMC's byte
     * range, the text objects and show operators inside the block, and hazards that rule out
     * relocating the marked-content boundaries.
     */
    public record BlockShape(
            byte[] bdcBytes,
            int bdcStart,
            int bdcEnd,
            int emcStart,
            int emcEnd,
            List<TextSpan> textSpans,
            List<Integer> showOffsets,
            boolean irregular,
            boolean paintsOutsideText) {}

    /**
     * One marked-content block's split plan: its stream, tag, line-split offsets, the subset of
     * those offsets where the effective font (resource or size) changes from the previous line,
     * whether its BDC opened inside a text object (and where that text object ends), and the
     * block's geometry.
     */
    public record SplitPlan(
            PdfStream stream,
            PdfName tag,
            List<Integer> splitOffsets,
            List<Integer> fontChangeOffsets,
            boolean bdcInsideText,
            int bdcTextEnd,
            BlockShape shape) {}

    /** A byte-level stream edit: replaces deleteLen bytes at offset with the given text. */
    public record Edit(int offset, int deleteLen, byte[] text) {}

    /** Locates the MCID's BDC...EMC block on the page and plans the line splits. */
    public static SplitPlan planLineSplit(PdfPage page, int targetMcid) throws IOException {
        for (int i = 0; i < page.getContentStreamCount(); i++) {
            PdfStream stream = page.getContentStream(i);
            SplitPlan plan = planSplitInStream(stream, page, targetMcid);
            if (plan != null) {
                return plan;
            }
        }
        throw new IllegalStateException(
                "Marked-content block for MCID "
                        + targetMcid
                        + " not found on page "
                        + page.getDocument().getPageNumber(page));
    }

    private static SplitPlan planSplitInStream(PdfStream stream, PdfPage page, int targetMcid)
            throws IOException {
        byte[] contentBytes = stream.getBytes();
        if (contentBytes == null || contentBytes.length == 0) {
            return null;
        }

        PdfResources resources = page.getResources();
        RandomAccessFileOrArray source =
                new RandomAccessFileOrArray(
                        new RandomAccessSourceFactory().createSource(contentBytes));
        try (PdfTokenizer tokenizer = new PdfTokenizer(source)) {
            PdfCanvasParser parser = new PdfCanvasParser(tokenizer, resources);
            List<PdfObject> operands = new ArrayList<>();
            boolean insideText = false;

            // Text state carried up to the BDC: the font resource and size persist across BT/ET, so
            // a block that shows text without its own Tf inherits them from here.
            TextPos seed = new TextPos();

            while (true) {
                int opStart = (int) tokenizer.getPosition();
                parser.parse(operands);
                if (operands.isEmpty()) {
                    return null; // stream ended before the block started
                }
                Integer mcid = mcidOfBdc(operands, page, resources);
                if (mcid != null && mcid == targetMcid) {
                    PdfName tag = tagOfBdc(operands);
                    int bdcEnd = (int) tokenizer.getPosition();
                    return scanBlock(
                            stream,
                            tag,
                            contentBytes,
                            tokenizer,
                            parser,
                            insideText,
                            opStart,
                            bdcEnd,
                            seed.copy());
                }
                String op = operatorOf(operands);
                seed.apply(op, operands);
                if ("BT".equals(op)) {
                    insideText = true;
                } else if ("ET".equals(op)) {
                    insideText = false;
                }
            }
        } finally {
            source.close();
        }
    }

    /**
     * Scans the found block to its closing EMC, collecting the line-split offsets plus the operator
     * geometry needed to splice without interleaving BT/ET and BDC/EMC pairs.
     */
    private static SplitPlan scanBlock(
            PdfStream stream,
            PdfName tag,
            byte[] contentBytes,
            PdfTokenizer tokenizer,
            PdfCanvasParser parser,
            boolean bdcInsideText,
            int bdcStart,
            int bdcEnd,
            TextPos pos)
            throws IOException {
        List<PdfObject> operands = new ArrayList<>();
        int depth = 1;
        boolean lineHasShownText = false;
        int pendingSplit = -1;
        List<Integer> splits = new ArrayList<>();

        boolean insideText = bdcInsideText;
        int bdcTextEnd = Integer.MAX_VALUE;
        List<TextSpan> textSpans = new ArrayList<>();
        int openSpanStart = -1;
        List<Integer> showOffsets = new ArrayList<>();
        boolean irregular = false;
        boolean paintsOutsideText = false;

        // Per-line font tracking: one EffFont is captured at each line's first show, and adjacent
        // lines are later diffed to find the font-change offsets. A "line" is only started when the
        // text baseline drops (prevLineY), so a horizontal or in-place reposition around an inline
        // font change (e.g. a bolded word) does not count as a new line.
        List<EffFont> lineFonts = new ArrayList<>();
        boolean lineFontCaptured = false;
        double prevLineY = Double.NaN;

        while (true) {
            int opStart = (int) tokenizer.getPosition();
            parser.parse(operands);
            if (operands.isEmpty()) {
                return null; // stream ended before the block closed
            }

            String op = operatorOf(operands);
            pos.apply(op, operands);

            if ("BDC".equals(op) || "BMC".equals(op)) {
                depth++;
                irregular = true; // nested blocks defeat boundary relocation
            } else if ("EMC".equals(op)) {
                if (--depth == 0) {
                    if (insideText && !bdcInsideText) {
                        irregular = true; // block closes inside a text object it did not open in
                    }
                    BlockShape shape =
                            new BlockShape(
                                    Arrays.copyOfRange(contentBytes, bdcStart, bdcEnd),
                                    bdcStart,
                                    bdcEnd,
                                    opStart,
                                    (int) tokenizer.getPosition(),
                                    List.copyOf(textSpans),
                                    List.copyOf(showOffsets),
                                    irregular,
                                    paintsOutsideText);
                    return new SplitPlan(
                            stream,
                            tag,
                            splits,
                            fontChangeOffsets(splits, lineFonts),
                            bdcInsideText,
                            bdcTextEnd,
                            shape);
                }
            } else if ("BT".equals(op)) {
                if (insideText) {
                    irregular = true;
                }
                insideText = true;
                openSpanStart = (int) tokenizer.getPosition();
            } else if ("ET".equals(op)) {
                insideText = false;
                if (openSpanStart >= 0) {
                    textSpans.add(new TextSpan(openSpanStart, opStart));
                    openSpanStart = -1;
                } else if (bdcInsideText && bdcTextEnd == Integer.MAX_VALUE) {
                    bdcTextEnd = opStart;
                } else {
                    irregular = true;
                }
            } else if (SHOW_OPS.contains(op)) {
                if (insideText) {
                    showOffsets.add(opStart);
                } else {
                    paintsOutsideText = true;
                }
                if (pendingSplit >= 0) {
                    // A pending reposition is only a line break if it dropped the baseline;
                    // otherwise it stayed on the line (a horizontal or in-place move).
                    if (droppedBelow(pos.baselineY(), prevLineY)) {
                        splits.add(pendingSplit);
                        prevLineY = pos.baselineY();
                        lineFontCaptured = false;
                    }
                    pendingSplit = -1;
                } else if (Double.isNaN(prevLineY)) {
                    prevLineY = pos.baselineY();
                }
                if (!lineFontCaptured) {
                    lineFonts.add(new EffFont(pos.font(), pos.effSize()));
                    lineFontCaptured = true;
                }
                lineHasShownText = true;
            } else if (NEXT_LINE_SHOW_OPS.contains(op)) {
                if (insideText) {
                    showOffsets.add(opStart);
                } else {
                    paintsOutsideText = true;
                }
                if (pendingSplit >= 0) {
                    splits.add(pendingSplit);
                    pendingSplit = -1;
                    prevLineY = pos.baselineY();
                    lineFontCaptured = false;
                } else if (depth == 1 && lineHasShownText) {
                    splits.add(opStart);
                    prevLineY = pos.baselineY();
                    lineFontCaptured = false;
                } else if (Double.isNaN(prevLineY)) {
                    prevLineY = pos.baselineY();
                }
                if (!lineFontCaptured) {
                    lineFonts.add(new EffFont(pos.font(), pos.effSize()));
                    lineFontCaptured = true;
                }
                lineHasShownText = true;
            } else if (depth == 1 && LINE_OPS.contains(op) && lineHasShownText) {
                // Split before the first positioning operator of the run; further
                // positioning operators belong to the same upcoming line.
                if (pendingSplit < 0) {
                    pendingSplit = opStart;
                    lineHasShownText = false;
                }
            } else if (!insideText && PAINT_OPS.contains(op)) {
                paintsOutsideText = true;
            }
        }
    }

    /**
     * Returns the subset of line-split offsets whose line's effective font differs from the
     * previous line's, i.e. the boundaries where a run of same-font lines ends. Empty when the
     * per-line fonts do not align with the splits (one more line than splits) or nothing changes.
     */
    private static List<Integer> fontChangeOffsets(List<Integer> splits, List<EffFont> lineFonts) {
        List<Integer> changes = new ArrayList<>();
        if (lineFonts.size() != splits.size() + 1) {
            return changes;
        }
        for (int i = 1; i < lineFonts.size(); i++) {
            if (lineFonts.get(i).differsFrom(lineFonts.get(i - 1))) {
                changes.add(splits.get(i - 1));
            }
        }
        return changes;
    }

    /** Returns the numeric operand at the index, or 0 when it is not a number. */
    private static double numberAt(List<PdfObject> operands, int index) {
        return operands.get(index) instanceof PdfNumber number ? number.doubleValue() : 0.0;
    }

    /** A baseline this much lower (in user-space points) starts a new line. */
    private static final double LINE_DROP_MIN = 1.0;

    /** True when {@code y} sits at least {@link #LINE_DROP_MIN} below a known {@code prevY}. */
    private static boolean droppedBelow(double y, double prevY) {
        return !Double.isNaN(prevY) && y < prevY - LINE_DROP_MIN;
    }

    /**
     * Text-positioning state tracked while scanning: the current font resource and size (for the
     * effective glyph size) and the text baseline's y in user space (to tell a real new line from a
     * horizontal or in-place move). {@code Td}/{@code TD} translate in text space, so their
     * vertical step is scaled by the text matrix; {@code Tm} sets the baseline absolutely; {@code
     * BT} resets the matrix (but font and leading persist).
     */
    private static final class TextPos {
        private PdfName font;
        private double fontSize = 0;
        private double matrixScale = 1; // hypot of the matrix column, for effective glyph size
        private double verticalScale = 1; // signed matrix d, for baseline math
        private double leading = 0;
        private double baselineY = 0;

        void apply(String op, List<PdfObject> operands) {
            switch (op) {
                case "Tf" -> {
                    if (operands.size() >= 3) {
                        if (operands.get(0) instanceof PdfName f) {
                            font = f;
                        }
                        fontSize = numberAt(operands, 1);
                    }
                }
                case "Tm" -> {
                    if (operands.size() >= 7) {
                        matrixScale = Math.hypot(numberAt(operands, 2), numberAt(operands, 3));
                        verticalScale = numberAt(operands, 3);
                        baselineY = numberAt(operands, 5);
                    }
                }
                case "Td", "TD" -> {
                    if (operands.size() >= 3) {
                        baselineY += numberAt(operands, 1) * verticalScale;
                        if ("TD".equals(op)) {
                            leading = -numberAt(operands, 1);
                        }
                    }
                }
                case "TL" -> {
                    if (operands.size() >= 2) {
                        leading = numberAt(operands, 0);
                    }
                }
                case "T*", "'", "\"" -> baselineY -= leading * verticalScale;
                case "BT" -> {
                    matrixScale = 1;
                    verticalScale = 1;
                    baselineY = 0;
                }
                default -> {}
            }
        }

        TextPos copy() {
            TextPos c = new TextPos();
            c.font = font;
            c.fontSize = fontSize;
            c.matrixScale = matrixScale;
            c.verticalScale = verticalScale;
            c.leading = leading;
            c.baselineY = baselineY;
            return c;
        }

        PdfName font() {
            return font;
        }

        double effSize() {
            return fontSize * matrixScale;
        }

        double baselineY() {
            return baselineY;
        }
    }

    /** Returns the operator literal of a parsed operation. */
    private static String operatorOf(List<PdfObject> operands) {
        return operands.get(operands.size() - 1).toString();
    }

    /**
     * Builds the byte edits realizing one block's MCID switches, refusing shapes that cannot be
     * spliced without interleaving operator pairs or orphaning painted content. When the block's
     * BDC opened inside a text object, every splice must stay in that text object and the plain
     * EMC/BDC splice is legal; otherwise the boundaries are relocated around the text objects.
     */
    public static List<Edit> blockEditsFor(
            SplitPlan plan, List<Integer> spliceOffsets, IntUnaryOperator mcidOf) {
        if (plan.bdcInsideText()) {
            if (spliceOffsets.get(spliceOffsets.size() - 1) >= plan.bdcTextEnd()) {
                throw new IllegalStateException(
                        "Splitting the "
                                + plan.tag().getValue()
                                + " block would splice outside the text object enclosing its BDC");
            }
            List<Edit> edits = new ArrayList<>();
            for (int i = 0; i < spliceOffsets.size(); i++) {
                String marker = "\nEMC " + bdcMarker(plan.tag(), mcidOf.applyAsInt(i)) + "\n";
                edits.add(new Edit(spliceOffsets.get(i), 0, ascii(marker)));
            }
            return edits;
        }
        BlockShape shape = plan.shape();
        if (shape.irregular()) {
            throw new IllegalStateException(
                    "Splitting the "
                            + plan.tag().getValue()
                            + " block requires relocating its marked-content boundaries, but its"
                            + " operator structure is too irregular");
        }
        if (shape.paintsOutsideText()) {
            throw new IllegalStateException(
                    "Splitting the "
                            + plan.tag().getValue()
                            + " block would leave painted content outside any marked-content"
                            + " block");
        }
        return relocatedEdits(plan, spliceOffsets, mcidOf);
    }

    /**
     * Rebuilds the block's marked-content boundaries to hug its text objects: the original BDC/EMC
     * pair (opened outside any text object) is dropped, each text object opens the current item's
     * marked content just inside its BT and closes it at its ET, and each splice switches to its
     * new MCID. A marked-content block is only opened where it would cover a show operator, so
     * unsplit slivers produce no empty duplicate-MCID blocks.
     */
    private static List<Edit> relocatedEdits(
            SplitPlan plan, List<Integer> splices, IntUnaryOperator mcidOf) {
        BlockShape shape = plan.shape();
        List<Edit> edits = new ArrayList<>();
        edits.add(new Edit(shape.bdcStart(), shape.bdcEnd() - shape.bdcStart(), NO_BYTES));
        edits.add(new Edit(shape.emcStart(), shape.emcEnd() - shape.emcStart(), NO_BYTES));

        boolean open = false;
        boolean[] opened = new boolean[splices.size() + 1];
        int segment = 0; // 0 = the original MCID's segment; i+1 = splice i's segment
        int splice = 0;
        int show = 0;
        List<Integer> shows = shape.showOffsets();
        for (TextSpan span : shape.textSpans()) {
            int pendingOpen = span.afterBt();
            while (splice < splices.size() && splices.get(splice) <= span.afterBt()) {
                segment = ++splice;
            }
            while (show < shows.size() && shows.get(show) < span.etStart()) {
                int showOffset = shows.get(show);
                while (splice < splices.size() && splices.get(splice) <= showOffset) {
                    if (open) {
                        edits.add(new Edit(splices.get(splice), 0, ascii("\nEMC\n")));
                        open = false;
                    }
                    pendingOpen = splices.get(splice);
                    segment = ++splice;
                }
                if (!open) {
                    if (opened[segment]) {
                        throw new IllegalStateException(
                                "Splitting the "
                                        + plan.tag().getValue()
                                        + " block is not possible: an item's lines sit in separate"
                                        + " text objects");
                    }
                    opened[segment] = true;
                    byte[] marker =
                            segment == 0
                                    ? wrapInNewlines(shape.bdcBytes())
                                    : ascii(
                                            "\n"
                                                    + bdcMarker(
                                                            plan.tag(),
                                                            mcidOf.applyAsInt(segment - 1))
                                                    + "\n");
                    edits.add(new Edit(pendingOpen, 0, marker));
                    open = true;
                }
                show++;
            }
            while (splice < splices.size() && splices.get(splice) < span.etStart()) {
                if (open) {
                    edits.add(new Edit(splices.get(splice), 0, ascii("\nEMC\n")));
                    open = false;
                }
                segment = ++splice;
            }
            if (open) {
                edits.add(new Edit(span.etStart(), 0, ascii("\nEMC\n")));
                open = false;
            }
        }
        return edits;
    }

    /** Renders a BDC operation opening the tag's block with the given MCID. */
    private static String bdcMarker(PdfName tag, int mcid) {
        return "/" + tag.getValue() + " <</MCID " + mcid + ">> BDC";
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    /** Returns the bytes with a newline on each side, keeping moved operators token-safe. */
    private static byte[] wrapInNewlines(byte[] bytes) {
        byte[] wrapped = new byte[bytes.length + 2];
        wrapped[0] = '\n';
        System.arraycopy(bytes, 0, wrapped, 1, bytes.length);
        wrapped[wrapped.length - 1] = '\n';
        return wrapped;
    }

    /** Applies the byte edits to the stream, lowest offset first. */
    public static void applyEdits(PdfStream stream, List<Edit> edits) {
        List<Edit> ordered = new ArrayList<>(edits);
        ordered.sort(Comparator.comparingInt(Edit::offset)); // stable: same-offset edits keep order

        byte[] contentBytes = stream.getBytes();
        ByteArrayOutputStream rewritten = new ByteArrayOutputStream(contentBytes.length + 256);
        int lastCopied = 0;
        for (Edit edit : ordered) {
            rewritten.write(contentBytes, lastCopied, edit.offset() - lastCopied);
            rewritten.writeBytes(edit.text());
            lastCopied = edit.offset() + edit.deleteLen();
        }
        rewritten.write(contentBytes, lastCopied, contentBytes.length - lastCopied);
        stream.setData(rewritten.toByteArray());
    }
}
