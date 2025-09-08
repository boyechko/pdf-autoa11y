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

    private boolean hasDesiredListStructure(PdfStructElem listElem) {
        for (Object kid : listElem.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem listItem = (PdfStructElem) kid;
                if ("LI".equals(listItem.getRole().getValue())) {
                    Object[] liKids = listItem.getKids().toArray();
                    
                    if (liKids.length != 2) {
                        return false;
                    }
                    
                    if (liKids[0] instanceof PdfStructElem && 
                        liKids[1] instanceof PdfStructElem) {
                        PdfStructElem firstChild = (PdfStructElem) liKids[0];
                        PdfStructElem secondChild = (PdfStructElem) liKids[1];
                        
                        if ("P".equals(firstChild.getRole().getValue()) &&
                            "LBody".equals(secondChild.getRole().getValue())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
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
        String role = elem.getRole().getValue();
        String comment = "";

        // Check if this is an H1 that should be demoted
        if ("H1".equals(role)) {
            elem.setRole(new PdfName("H2"));
            comment = "demoted to H2";
        }

        // Check if this is a P element in a list structure that should become Lbl
        if ("P".equals(role) && shouldConvertPToLbl(elem)) {
            elem.setRole(new PdfName("Lbl"));
            comment = "changed to Lbl";
        }

        // Print the element with any comment
        if (comment.isEmpty()) {
            System.out.println(indent + "- " + role);
        } else {
            // Use String.format to justify to column 40 (adjust as needed)
            System.out.printf("%s- %-30s ; %s%n", indent, role, comment);
        }

        // Process children recursively
        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElementWithDisplay((PdfStructElem) kid, level + 1);
            }
        }
    }

    private boolean shouldConvertPToLbl(PdfStructElem pElem) {
        // Check if this P element is the first child of an LI element
        // and the LI has the desired structure (P followed by LBody)
        PdfStructElem parent = (PdfStructElem) pElem.getParent();
        if (parent != null && "LI".equals(parent.getRole().getValue())) {
            Object[] liKids = parent.getKids().toArray();
            if (liKids.length == 2 && liKids[0] instanceof PdfStructElem) {
                PdfStructElem firstChild = (PdfStructElem) liKids[0];
                
                // Check if this P element is the first child by comparing roles and content
                if ("P".equals(firstChild.getRole().getValue())) {
                    // Check if the second child is LBody
                    if (liKids[1] instanceof PdfStructElem) {
                        PdfStructElem secondChild = (PdfStructElem) liKids[1];
                        if ("LBody".equals(secondChild.getRole().getValue())) {
                            // Also verify that the LI's parent is an L with desired structure
                            PdfStructElem listParent = (PdfStructElem) parent.getParent();
                            if (listParent != null && "L".equals(listParent.getRole().getValue()) && 
                                hasDesiredListStructure(listParent)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

}
