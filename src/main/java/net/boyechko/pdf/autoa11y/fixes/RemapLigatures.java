/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.io.font.cmap.CMapLocationFromBytes;
import com.itextpdf.io.font.cmap.CMapParser;
import com.itextpdf.io.font.cmap.CMapToUnicode;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfType0Font;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueMsg;

/** Repairs broken ligature mappings in a font's ToUnicode CMap. */
public class RemapLigatures implements IssueFix {
    private static final int P_LIGATURE_REMAP = 20;
    private static final int BATCH_SIZE = 100;

    private static final Map<Integer, String> LIGATURE_CODEPOINT_EXPANSIONS =
            Map.of(
                    0xFB00, "ff",
                    0xFB01, "fi",
                    0xFB02, "fl",
                    0xFB03, "ffi",
                    0xFB04, "ffl",
                    0xFB05, "st",
                    0xFB06, "st");

    // Empirical Arial subset CIDs seen in production PDFs.
    private static final Map<Integer, String> ARIAL_CID_EXPANSIONS =
            Map.of(
                    0x00BF, "fi",
                    0x1087, "ff",
                    0x108B, "st");

    private final int fontObjNum;
    private final String fontName;
    private final Map<Integer, String> replacementByCode;

    public RemapLigatures(int fontObjNum, String fontName, Map<Integer, String> replacementByCode) {
        this.fontObjNum = fontObjNum;
        this.fontName = fontName;
        this.replacementByCode = Map.copyOf(replacementByCode);
    }

    @Override
    public int priority() {
        return P_LIGATURE_REMAP;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        PdfObject fontObj = ctx.doc().getPdfObject(fontObjNum);
        if (!(fontObj instanceof PdfDictionary fontDict)) {
            return;
        }

        PdfStream toUnicodeStream = fontDict.getAsStream(PdfName.ToUnicode);
        if (toUnicodeStream == null) {
            return;
        }

        CMapToUnicode cMap = parseToUnicode(toUnicodeStream);
        Map<Integer, char[]> mappings = extractMappings(cMap);

        boolean changed = false;
        for (Map.Entry<Integer, String> entry : replacementByCode.entrySet()) {
            int code = entry.getKey();
            char[] current = mappings.get(code);
            if (current == null) {
                continue;
            }
            String currentText = new String(current);
            String replacement = entry.getValue();
            if (!replacement.equals(currentText)) {
                mappings.put(code, replacement.toCharArray());
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        PdfType0Font type0Font = loadType0Font(fontDict);
        byte[] updated = buildToUnicodeCMapBytes(mappings, cMap.getCodeSpaceRanges(), type0Font);
        toUnicodeStream.setData(updated);
    }

    public static Map<Integer, String> findBrokenMappings(PdfDictionary fontDict) {
        PdfStream toUnicode = fontDict.getAsStream(PdfName.ToUnicode);
        if (toUnicode == null) {
            return Map.of();
        }

        CMapToUnicode cmap;
        try {
            cmap = parseToUnicode(toUnicode);
        } catch (Exception e) {
            return Map.of();
        }

        boolean arialLike = isArialLike(fontDict);
        Map<Integer, String> corrections = new LinkedHashMap<>();
        for (Integer code : cmap.getCodes()) {
            char[] mappedChars = cmap.lookup(code);
            if (mappedChars == null || mappedChars.length == 0) {
                continue;
            }

            String current = new String(mappedChars);
            String replacement = null;
            if (mappedChars.length == 1) {
                replacement = LIGATURE_CODEPOINT_EXPANSIONS.get((int) mappedChars[0]);
                if (replacement == null && arialLike) {
                    String arialReplacement = ARIAL_CID_EXPANSIONS.get(code);
                    if (arialReplacement != null && arialReplacement.charAt(0) == mappedChars[0]) {
                        replacement = arialReplacement;
                    }
                }
            }

            if (replacement != null && !replacement.equals(current)) {
                corrections.put(code, replacement);
            }
        }
        return corrections;
    }

    public static String fontName(PdfDictionary fontDict) {
        PdfName baseFont = fontDict.getAsName(PdfName.BaseFont);
        return baseFont != null ? baseFont.getValue() : "unknown";
    }

    private static boolean isArialLike(PdfDictionary fontDict) {
        String name = fontName(fontDict);
        return name.toLowerCase(Locale.ROOT).contains("arial");
    }

    private static CMapToUnicode parseToUnicode(PdfStream toUnicodeStream) throws Exception {
        CMapToUnicode cMap = new CMapToUnicode();
        CMapParser.parseCid(
                "ToUnicode", cMap, new CMapLocationFromBytes(toUnicodeStream.getBytes()));
        return cMap;
    }

    private static Map<Integer, char[]> extractMappings(CMapToUnicode cMap) {
        Map<Integer, char[]> mappings = new TreeMap<>();
        for (Integer code : cMap.getCodes()) {
            char[] mapped = cMap.lookup(code);
            if (mapped != null) {
                mappings.put(code, mapped.clone());
            }
        }
        return mappings;
    }

    private static PdfType0Font loadType0Font(PdfDictionary fontDict) {
        try {
            PdfFont font = PdfFontFactory.createFont(fontDict);
            if (font instanceof PdfType0Font type0) {
                return type0;
            }
        } catch (Exception ignored) {
            // Fallback path below does not require a loaded font program.
        }
        return null;
    }

    private static byte[] buildToUnicodeCMapBytes(
            Map<Integer, char[]> mappings, List<byte[]> codeSpaceRanges, PdfType0Font type0Font) {
        List<byte[]> ranges = normalizedCodeSpaceRanges(codeSpaceRanges, mappings.keySet());
        int defaultCodeWidth = ranges.get(0).length;

        StringBuilder builder = new StringBuilder();
        builder.append("/CIDInit /ProcSet findresource begin\n");
        builder.append("12 dict begin\n");
        builder.append("begincmap\n");
        builder.append(
                "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n");
        builder.append("/CMapName /Adobe-Identity-UCS def\n");
        builder.append("/CMapType 2 def\n");
        builder.append(ranges.size() / 2).append(" begincodespacerange\n");
        for (int i = 0; i + 1 < ranges.size(); i += 2) {
            builder.append("<")
                    .append(toHex(ranges.get(i)))
                    .append("> <")
                    .append(toHex(ranges.get(i + 1)))
                    .append(">\n");
        }
        builder.append("endcodespacerange\n");

        List<Map.Entry<Integer, char[]>> entries = new ArrayList<>(mappings.entrySet());
        for (int start = 0; start < entries.size(); start += BATCH_SIZE) {
            int end = Math.min(entries.size(), start + BATCH_SIZE);
            builder.append(end - start).append(" beginbfchar\n");
            for (int i = start; i < end; i++) {
                Map.Entry<Integer, char[]> entry = entries.get(i);
                byte[] sourceCodeBytes =
                        sourceCodeBytes(entry.getKey(), type0Font, defaultCodeWidth);
                builder.append("<")
                        .append(toHex(sourceCodeBytes))
                        .append("> <")
                        .append(toUtf16Hex(entry.getValue()))
                        .append(">\n");
            }
            builder.append("endbfchar\n");
        }

        builder.append("endcmap\n");
        builder.append("CMapName currentdict /CMap defineresource pop\n");
        builder.append("end\n");
        builder.append("end\n");
        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static List<byte[]> normalizedCodeSpaceRanges(
            List<byte[]> originalRanges, Set<Integer> sourceCodes) {
        if (originalRanges != null
                && originalRanges.size() >= 2
                && originalRanges.size() % 2 == 0) {
            return originalRanges;
        }

        int width = 1;
        for (Integer code : sourceCodes) {
            if (code != null && code > 0xFF) {
                width = 2;
                break;
            }
        }
        byte[] low = new byte[width];
        byte[] high = new byte[width];
        for (int i = 0; i < high.length; i++) {
            high[i] = (byte) 0xFF;
        }
        return List.of(low, high);
    }

    private static byte[] sourceCodeBytes(
            int sourceCode, PdfType0Font type0Font, int defaultWidth) {
        if (type0Font != null) {
            try {
                return type0Font.getCmap().getCmapBytes(sourceCode);
            } catch (Exception ignored) {
                // Fallback below.
            }
        }

        byte[] bytes = new byte[defaultWidth];
        int value = sourceCode;
        for (int i = defaultWidth - 1; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return bytes;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format(Locale.ROOT, "%02X", b & 0xFF));
        }
        return out.toString();
    }

    private static String toUtf16Hex(char[] chars) {
        StringBuilder out = new StringBuilder(chars.length * 4);
        for (char c : chars) {
            out.append(String.format(Locale.ROOT, "%04X", (int) c));
        }
        return out.toString();
    }

    @Override
    public String describe() {
        return "Remapped ligatures in font";
    }

    @Override
    public String describe(DocContext ctx) {
        return "Remapped " + replacementByCode.size() + " ligature mapping(s) in font " + fontName;
    }

    @Override
    public IssueMsg describeLocated(DocContext ctx) {
        return new IssueMsg(describe(ctx), IssueLoc.atObj(fontObjNum, null, IssueLoc.ObjKind.FONT));
    }

    @Override
    public boolean invalidates(IssueFix otherFix) {
        if (otherFix instanceof RemapLigatures other) {
            return this.fontObjNum == other.fontObjNum;
        }
        return false;
    }

    @Override
    public String groupLabel() {
        return "ligature mappings corrected";
    }
}
