// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfLiteral;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfResources;
import java.util.List;

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
}
