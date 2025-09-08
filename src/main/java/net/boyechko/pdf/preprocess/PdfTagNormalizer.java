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

        if ("L".equals(role)) {
            if (hasDesiredListStructure(elem)) {
                System.out.println(indent + "- " + role + " 🎯 [Desired list structure found]");
            } else {
                System.out.println(indent + "- " + role);
            }
        } else {
            System.out.println(indent + "- " + role);
        }

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

    public void normalizeListStructures() {
        if (root != null) {
            System.out.println("Normalizing list structures in document...");
            for (Object kid : root.getKids()) {
                if (kid instanceof PdfStructElem) {
                    processListStructures((PdfStructElem) kid);
                }
            }
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }
    
    private void processListStructures(PdfStructElem elem) {
        String role = elem.getRole().getValue();

        if ("L".equals(role) && hasDesiredListStructure(elem)) {
            convertPToLbl(elem);
        }

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processListStructures((PdfStructElem) kid);
            }
        }
    }

    private void convertPToLbl(PdfStructElem listElem) {
        for (Object kid : listElem.getKids()) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem listItem = (PdfStructElem) kid;
                if ("LI".equals(listItem.getRole().getValue())) {
                    Object[] liKids = listItem.getKids().toArray();
                    if (liKids.length == 2 && 
                        liKids[0] instanceof PdfStructElem) {
                        PdfStructElem pElem = (PdfStructElem) liKids[0];
                        if ("P".equals(pElem.getRole().getValue())) {
                            pElem.setRole(new PdfName("Lbl"));
                            System.out.println("Converted P to Lbl in list item");
                        }
                    }
                }
            }
        }
    }

    public void demoteH1Tags() {
        if (root != null) {
            System.out.println("Demoting H1 tags to H2 in document...");
            for (Object kid : root.getKids()) {
                if (kid instanceof PdfStructElem) {
                    processAndDemoteH1((PdfStructElem) kid);
                }
            }
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }

    private void processAndDemoteH1(PdfStructElem elem) {
        // Check the role of the current element
        if ("H1".equals(elem.getRole().getValue())) {
            elem.setRole(new PdfName("H2"));
            System.out.println("Demoted H1 to H2.");
        }

        // Recursively process the children
        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processAndDemoteH1((PdfStructElem) kid);
            }
        }
    }

}
