package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.*;

public class PdfTagNormalizer {
    private final PdfDocument pdfDoc;
    private final PdfStructTreeRoot root;

    // Add a constructor that takes a PdfDocument
    public PdfTagNormalizer(PdfDocument doc) {
        this.pdfDoc = doc;
        this.root = doc.getStructTreeRoot();
    }

    // Keep existing constructor for read-only operations
    public PdfTagNormalizer(String src) throws IOException {
        this(new PdfDocument(new PdfReader(src)));
    }

   public void analyzePdf() {
        if (root != null) {
            System.out.println("Tag structure found in the document:");
            displayTagStructure(root, 0);
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }

    private void displayTagStructure(PdfStructTreeRoot root, int level) {
        for (Object kid : root.getKids()) {
            if (kid instanceof PdfStructElem) {
                displayTagStructure((PdfStructElem) kid, level);
            }
        }
    }

    private void displayTagStructure(PdfStructElem elem, int level) {
        String indent = "  ".repeat(level);
        String role = elem.getRole().getValue();

            System.out.println(indent + "- " + role);

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                displayTagStructure((PdfStructElem) kid, level + 1);
            }
        }
    }

    public void processAndDisplayChanges() {
        if (root != null) {
            System.out.println("Processing PDF structure and displaying changes:");
            for (Object kid : root.getKids()) {
                if (kid instanceof PdfStructElem) {
                    processElementWithDisplay((PdfStructElem) kid, 0);
                }
            }
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }

    private void processElementWithDisplay(PdfStructElem elem, int level) {
        String indent = "  ".repeat(level);
        String comment = "";
        String role = elem.getRole().getValue();

        // Change top-level <Sect> to <Document>
        if ("Sect".equals(role) && level == 0) {
            elem.setRole(new PdfName("Document"));
            comment = "changed to Document";
        }

        // Change rogue TextBox tags to <Div>
        if ("TextBox".equals(role)) {
            elem.setRole(new PdfName("Div"));
            comment = "changed to Div";
        }

        // Demote <H1> tags
        if ("H1".equals(role)) {
            elem.setRole(new PdfName("H2"));
            comment = "demoted to H2";
        }

        // Change P elememnt in <LI><P>...</P><LBody>...</LBody></LI> to Lbl
        if ("P".equals(role) && shouldConvertPToLbl(elem)) {
            elem.setRole(new PdfName("Lbl"));
            comment = "changed to Lbl";
        }

        // Print the element with any comment
        String tagOutput = indent + "- " + role;
        if (comment.isEmpty()) {
            System.out.println(tagOutput);
        } else {
            // Pad to column 40, or use minimum spacing if already longer
            int targetColumn = 40;
            int currentLength = tagOutput.length();
            String padding = currentLength < targetColumn ? 
                " ".repeat(targetColumn - currentLength) : "  ";
            System.out.println(tagOutput + padding + "; " + comment);
        }

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElementWithDisplay((PdfStructElem) kid, level + 1);
            }
        }
    }

    private boolean shouldConvertPToLbl(PdfStructElem elem) {
        PdfStructElem parent = (PdfStructElem) elem.getParent();

        if (!"LI".equals(parent.getRole().getValue())) {
            return false;
        }

        // Check if LI has exactly 2 children: P and LBody
        Object[] liKids = parent.getKids().toArray();
        if (liKids.length != 2) {
        return false;
        }

        PdfStructElem firstChild = (PdfStructElem) liKids[0];
        PdfStructElem secondChild = (PdfStructElem) liKids[1];

        // Check if this P element is the first child and second is LBody
        return "P".equals(firstChild.getRole().getValue()) &&
            "LBody".equals(secondChild.getRole().getValue());
    }

}
