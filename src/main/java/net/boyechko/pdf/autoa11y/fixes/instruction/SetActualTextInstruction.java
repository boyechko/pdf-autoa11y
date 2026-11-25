// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import static net.boyechko.pdf.autoa11y.fixes.instruction.ScribbledInstruction.receipt;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;

/**
 * {@code !SET_ACTUAL_TEXT <text>}: sets the element's /ActualText. Since scribbles must stay
 * ASCII-safe, {@code \}{@code uXXXX} escapes in the text are decoded, e.g. {@code \}{@code u2212}
 * for the minus sign; the receipt keeps the escaped form.
 */
record SetActualTextInstruction(String text) implements ScribbledInstruction {

    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9A-Fa-f]{4})");

    @Override
    public String apply(DocContext ctx, PdfStructElem element) {
        element.setActualText(new PdfString(unescape(text), PdfEncodings.UNICODE_BIG));
        return receipt("SET_ACTUAL_TEXT " + text);
    }

    private static String unescape(String text) {
        return UNICODE_ESCAPE
                .matcher(text)
                .replaceAll(
                        m ->
                                Matcher.quoteReplacement(
                                        String.valueOf((char) Integer.parseInt(m.group(1), 16))));
    }
}
