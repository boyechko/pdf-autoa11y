package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.*;

public class PdfTagNormalizer {
    private static final int DISPLAY_COLUMN_WIDTH = 40;
    private static final String INDENT = "  ";

    private final PdfStructTreeRoot root;
    private PdfStructElem docTitle;
    private int changeCount;
    private int warningCount;

    public PdfTagNormalizer(PdfDocument doc) {
        this.root = doc.getStructTreeRoot();
        this.docTitle = null;
        this.changeCount = 0;
        this.warningCount = 0;
    }

    public PdfTagNormalizer(String src) throws IOException {
        this(new PdfDocument(new PdfReader(src)));
    }

    /**
     * Processes the PDF structure, normalizing tags and displaying changes.
     * Outputs a summary of changes and warnings.
     */
    public void processAndDisplayChanges() {
        if (root != null) {
            System.out.println("Processing PDF structure and displaying changes:");
            changeCount = 0;
            
            for (Object kid : root.getKids()) {
                if (kid instanceof PdfStructElem) {
                    processElementWithDisplay((PdfStructElem) kid, 0);
                }
            }
            
            // Print simple summary
            System.out.println("\nTotal changes made: " + changeCount);
            System.out.println("Total warnings raised: " + warningCount);
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }

    private void processElementWithDisplay(PdfStructElem elem, int level) {
        String comment = "";
        String role = elem.getRole().getValue();

        // Change top-level <Sect> to <Document>
        if ("Sect".equals(role) && level == 0) {
            elem.setRole(PdfName.Document);
            comment = "changed to Document";
            changeCount++;
        }

        // Change rogue TextBox tags to <Div>
        if ("TextBox".equals(role)) {
            elem.setRole(PdfName.Div);
            comment = "changed to Div";
            changeCount++;
        }

        // Demote <H1> tags after the first
        if ("H1".equals(role) && this.docTitle == null) {
            // First H1 becomes Document Title
            this.docTitle = elem;
            comment = "first H1, treating as document title";
        } else if ("H1".equals(role)) {
            elem.setRole(PdfName.H2);
            comment = "demoted to H2";
            changeCount++;
        }

        // Normalize list items
        if ("LI".equals(role)) {
            comment = normalizeLI(elem);
        }

        // Warn about empty elements
        if (elem.getKids() == null || elem.getKids().isEmpty()) {
            comment = "empty " + role;
        }

        // Print the element with any comment
        printElement(elem, level, comment);

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElementWithDisplay((PdfStructElem) kid, level + 1);
            }
        }
    }

    private void printElement(PdfStructElem elem, int level, String comment) {
        String role = elem.getRole().getValue();
        String tagOutput = INDENT.repeat(level) + "- " + role;
        if (comment.isEmpty()) {
            System.out.println(tagOutput);
        } else {
            // Pad to column 40, or use minimum spacing if already longer
            int currentLength = tagOutput.length();
            String padding = currentLength < DISPLAY_COLUMN_WIDTH ? 
                " ".repeat(DISPLAY_COLUMN_WIDTH - currentLength) : "  ";
            System.out.println(tagOutput + padding + "; " + comment);
        }
    }

    /**
     * Normalizes LI elements to have proper Lbl and LBody structure.
     * Expected: <LI><Lbl>...</Lbl><LBody>...</LBody></LI>
     * 
     * @param liElem The LI element to normalize
     * @return A comment string describing what was done, or empty if no changes
     */
    private String normalizeLI(PdfStructElem liElem) {
        Object[] liKids = liElem.getKids().toArray();
        String warning = "";
        
        if (liKids.length == 0) {
            warning = "empty LI";
            warningCount++;
            escalateWarning(liElem, warning);
            return warning;
        }

        // Filter to only structure elements
        PdfStructElem[] structKids = new PdfStructElem[liKids.length];
        int structCount = 0;
        for (Object kid : liKids) {
            if (kid instanceof PdfStructElem) {
                structKids[structCount++] = (PdfStructElem) kid;
            }
        }

        if (structCount == 0) {
            warning = "LI contains no structure elements";
            warningCount++;
            escalateWarning(liElem, warning);
            return warning;
        }

        if (structCount == 1) {
            PdfStructElem child = structKids[0];
            String childRole = child.getRole().getValue();
            if ("P".equals(childRole)) {
                child.setRole(PdfName.LBody);
                changeCount++;
                warning = "converted P to LBody, missing Lbl";
            } else if (!"Lbl".equals(childRole) && !"LBody".equals(childRole)) {
                warning = "unexpected single child: " + childRole;
            } else if ("Lbl".equals(childRole)) {
                warning = "missing LBody";
            } else {
                warning = "missing Lbl";
            }
            warningCount++;
            escalateWarning(liElem, warning);
            return warning;
        }

        if (structCount == 2) {
            PdfStructElem first = structKids[0];
            PdfStructElem second = structKids[1];
            String firstRole = first.getRole().getValue();
            String secondRole = second.getRole().getValue();

            // Perfect case
            if ("Lbl".equals(firstRole) && "LBody".equals(secondRole)) {
                return ""; // No comment needed
            }

            // Case: P + LBody -> convert P to Lbl
            if ("P".equals(firstRole) && "LBody".equals(secondRole)) {
                first.setRole(PdfName.Lbl);
                changeCount++;
                return "converted P to Lbl";
            }

            // Case: P + P -> convert first to Lbl, second to LBody
            if ("P".equals(firstRole) && "P".equals(secondRole)) {
                first.setRole(PdfName.Lbl);
                second.setRole(PdfName.LBody);
                changeCount += 2;
                return "converted P+P to Lbl+LBody";
            }

            // Other problematic cases
            warning = "unexpected children: " + firstRole + "+" + secondRole;
            warningCount++;
            escalateWarning(liElem, warning);
            return warning;
        }

        if (structCount > 2) {
            warning = "too many children (" + structCount + ")";
            warningCount++;
            escalateWarning(liElem, warning);
            return warning;
        }

        return "";
    }

    private String escalateWarning(PdfStructElem elem, String text) {
        elem.put(PdfName.T, new PdfString(text));
        if (elem.getParent() instanceof PdfStructElem) {
            escalateWarning((PdfStructElem) elem.getParent(), text);
        }
        return text;
    }
}
