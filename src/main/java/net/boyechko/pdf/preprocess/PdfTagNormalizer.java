package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PdfTagNormalizer {
    private static final int DISPLAY_COLUMN_WIDTH = 40;
    private static final String INDENT = "  ";

    private final PdfDocument document;
    private final PdfStructTreeRoot root;
    private PdfStructElem docTitle;
    private int changeCount;
    private int warningCount;

    public PdfTagNormalizer(PdfDocument doc) {
        this.document = doc;
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
                    processElement((PdfStructElem) kid, 0);
                }
            }
            
            // Print simple summary
            System.out.println("\nTotal changes made: " + changeCount);
            System.out.println("Total warnings raised: " + warningCount);
        } else {
            System.out.println("No tag structure found in the document.");
        }
    }

    private void processElement(PdfStructElem elem, int level) {
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

        // Normalize lists
        if ("L".equals(role)) {
            processList(elem, level);
            return; // L processing handles its own display
        }

        // Warn about empty elements
        if (elem.getKids() == null || elem.getKids().isEmpty()) {
            comment = "empty " + role;
        }

        // Print the element with any comment
        logPdfStructure(elem, level, comment);

        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElement((PdfStructElem) kid, level + 1);
            }
        }
    }

    private void logPdfStructure(PdfStructElem elem, int level, String comment) {
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

    private void processList(PdfStructElem listElem, int level) {
        // Create a copy to iterate over safely
        List<IStructureNode> kidsCopy = new ArrayList<>(listElem.getKids());

        logPdfStructure(listElem, level, kidsCopy.size() + " children");
        
        for (IStructureNode kid : kidsCopy) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem kidElem = (PdfStructElem) kid;
                if ("LI".equals(kidElem.getRole().getValue())) {
                    processListItem(kidElem, level + 1);
                } else if ("P".equals(kidElem.getRole().getValue())) {
                    wrapInLI(listElem, kidElem, level);
                } else {
                    // Unexpected child in L
                    warningCount++;
                    String warnComment = "unexpected child in L: " + kidElem.getRole().getValue();
                    setVisualMarker(kidElem, warnComment);
                    escalateWarning(kidElem, "attention needed");
                    logPdfStructure(kidElem, level + 1, warnComment);
                }
            }
        }
    }
    
    private void wrapInLI(PdfStructElem listElem, PdfStructElem kidElem, int level) {
        // Create new LI and immediately add it to parent
        PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
        listElem.addKid(newLI);  // Add to parent first!
        
        // Create new LBody and add to LI
        PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
        newLI.addKid(newLBody);

        // Now move the child
        listElem.removeKid(kidElem);
        newLBody.addKid(kidElem);
        
        changeCount++;
        String comment = "enclosed unexpected " + kidElem.getRole().getValue() + " in LI";
        logPdfStructure(newLI, level + 1, comment);
    }

    /**
     * Normalizes LI elements to have proper Lbl and LBody structure.
     * Expected: <LI><Lbl>...</Lbl><LBody>...</LBody></LI>
     * 
     * @param liElem The LI element to normalize
     * @return A comment string describing what was done, or empty if no changes
     */
    private void processListItem(PdfStructElem liElem, int level) {
        NormalizationResult result = processListItemInternal(liElem);
        
        if (result.isWarning) {
            warningCount++;
            setVisualMarker(liElem, result.comment);
            escalateWarning(liElem, "attention needed");
        } else if (!result.comment.isEmpty()) {
            // Count changes based on the comment content
            if (result.comment.contains("P+P")) {
                changeCount += 2;
            } else if (result.comment.contains("converted")) {
                changeCount++;
            }
            logPdfStructure(liElem, level, result.comment);
        }
    }

    private NormalizationResult processListItemInternal(PdfStructElem liElem) {
        Object[] liKids = liElem.getKids().toArray();

        if (liKids.length == 0) {
            return NormalizationResult.warning("empty LI");
        }

        // Filter to only structure elements
        PdfStructElem[] structKids = new PdfStructElem[liKids.length];
        int structCount = 0;
        for (Object kid : liKids) {
            if (kid instanceof PdfStructElem) {
                structKids[structCount++] = (PdfStructElem) kid;
            }
        }

        return switch (structCount) {
            case 0 -> NormalizationResult.warning("LI contains no structure elements");
            case 1 -> handleSingleListItemChild(structKids[0]);
            case 2 -> handleTwoListItemChildren(structKids[0], structKids[1]);
            default -> NormalizationResult.warning("too many children (" + structCount + ")");
        };
    }

    private static class NormalizationResult {
        final String comment;
        final boolean isWarning;
        
        NormalizationResult(String comment, boolean isWarning) {
            this.comment = comment;
            this.isWarning = isWarning;
        }
        
        static NormalizationResult change(String comment) {
            return new NormalizationResult(comment, false);
        }
        
        static NormalizationResult warning(String comment) {
            return new NormalizationResult(comment, true);
        }
        
        static NormalizationResult noChange() {
            return new NormalizationResult("", false);
        }
    }

    private NormalizationResult handleSingleListItemChild(PdfStructElem child) {
        String childRole = child.getRole().getValue();
        if ("P".equals(childRole)) {
            PdfStructElem parentLI = (PdfStructElem) child.getParent();
            
            // Create new LBody and immediately add it to parent
            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            parentLI.addKid(newLBody);  // Add to parent first!
            
            // Now move the P
            parentLI.removeKid(child);
            newLBody.addKid(child);
            
            return NormalizationResult.change("enclosed P in LBody, missing Lbl");
        } else if (!"Lbl".equals(childRole) && !"LBody".equals(childRole)) {
            return NormalizationResult.warning("unexpected single child: " + childRole);
        } else if ("Lbl".equals(childRole)) {
            return NormalizationResult.warning("missing LBody");
        } else {
            return NormalizationResult.warning("missing Lbl");
        }
    }

    private NormalizationResult handleTwoListItemChildren(PdfStructElem first, PdfStructElem second) {
        String firstRole = first.getRole().getValue();
        String secondRole = second.getRole().getValue();

        // Perfect case
        if ("Lbl".equals(firstRole) && "LBody".equals(secondRole)) {
            return NormalizationResult.noChange(); // No comment needed
        }

        // Case: P + LBody -> convert P to Lbl
        if ("P".equals(firstRole) && "LBody".equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            return NormalizationResult.change("converted P+Body to Lbl+LBody");
        }

        // Case: P + P -> convert first to Lbl, second to LBody
        if ("P".equals(firstRole) && "P".equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            second.setRole(PdfName.LBody);
            return NormalizationResult.change("converted P+P to Lbl+LBody");
        }

        // Other problematic cases
        return NormalizationResult.warning("unexpected children: " + firstRole + "+" + secondRole);
    }

    // Recursively escalate a warning message up to the Document level
    private String escalateWarning(PdfStructElem elem, String text) {
        PdfStructElem parent = (PdfStructElem) elem.getParent();
        if (parent.getRole() != PdfName.Document) {
            setVisualMarker(parent, text);
            escalateWarning(parent, text);
        }
        return text;
    }

    private void setVisualMarker(PdfStructElem elem, String text) {
        elem.put(PdfName.T, new PdfString(text));
    }
}
