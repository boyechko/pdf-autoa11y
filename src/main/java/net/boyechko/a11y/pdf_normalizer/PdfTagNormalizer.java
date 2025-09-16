package net.boyechko.a11y.pdf_normalizer;

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
    private final PrintStream output;
    private PdfStructElem docTitle;
    private int changeCount;
    private int warningCount;

    // Constructor with custom output
    public PdfTagNormalizer(PdfDocument doc, PrintStream output) {
        this.document = doc;
        this.root = doc.getStructTreeRoot();
        this.output = output;
        this.docTitle = null;
        this.changeCount = 0;
        this.warningCount = 0;
    }

    // Constructor with default output (backwards compatibility)
    public PdfTagNormalizer(PdfDocument doc) {
        this(doc, System.out);
    }

    public PdfTagNormalizer(String src) throws IOException {
        this(new PdfDocument(new PdfReader(src)), System.out);
    }

    public PdfTagNormalizer(String src, PrintStream output) throws IOException {
        this(new PdfDocument(new PdfReader(src)), output);
    }

    public int getChangeCount() {
        return changeCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    /**
     * Processes the PDF structure, normalizing tags and displaying changes.
     * Outputs a summary of changes and warnings.
     */
    public void processAndDisplayChanges() {
        if (root != null) {
            changeCount = 0;
            warningCount = 0;

            for (Object kid : root.getKids()) {
                if (kid instanceof PdfStructElem) {
                    processElement((PdfStructElem) kid, 0);
                }
            }
        } else {
            output.println("No accessibility tags found - document may need initial tagging."); // Changed from System.out
        }
    }

    private void processElement(PdfStructElem elem, int level) {
        PdfName role = elem.getRole();
        PdfName mappedRole = getMappedRole(role);
        PdfName parentRole = elem.getParent() != null ? elem.getParent().getRole() : null;
        String comment = "";

        // Special handling for lists (they're more complex and handle their own display)
        if (PdfName.L.equals(role) || PdfName.L.equals(mappedRole)) {
            processList(elem, level);
            return;
        }

        // Handle other tags
        switch (mappedRole != null ? mappedRole.getValue() : role.getValue()) {
            case "Document", "Part", "Art", "Div",
                 "H2", "H3", "H4", "H5", "H6", "BlockQuote", "P",
                 "Caption", "Figure", "Formula", "Link", "Note", "Reference", "Span",
                 "Table", "TR", "TH", "TD" -> {
                // No special handling needed, just print
            }
            case "Sect" -> {
                comment = handleSect(elem, level);
            }
            case "H1" -> {
                comment = handleH1(elem);
            }
            case "LBody", "Lbl" -> {
                if (!PdfName.LI.equals(parentRole)) {
                    warningCount++;
                    comment = "unexpected " + role.getValue() + " outside LI";
                    setVisualMarker(elem, comment);
                    escalateWarning(elem);
                    printElement(elem, level, comment);
                }
            }
            default -> {
                // Unknown or unexpected tag
                warningCount++;
                comment = "unexpected tag: " + role.getValue();
                setVisualMarker(elem, comment);
                escalateWarning(elem);
                printElement(elem, level, comment);
            }
        }

       // Print the element with any comment
        printElement(elem, level, comment);

        // Process children
        for (Object kid : elem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElement((PdfStructElem) kid, level + 1);
            }
        }
    }

    private String handleSect(PdfStructElem elem, int level) {
        if (level > 0) {
            return ""; // No change needed for nested Sect
        }
        elem.setRole(PdfName.Document);
        changeCount++;
        return "changed to Document";
    }

    private String handleH1(PdfStructElem elem) {
        if (this.docTitle == null) {
            this.docTitle = elem;
            return "first H1, treating as document title";
        } else {
            elem.setRole(PdfName.H2);
            changeCount++;
            return "extra H1 demoted to H2";
        }
    }

    private PdfName getMappedRole(PdfName role) {
        PdfDictionary roleMap = this.root.getRoleMap();

        if (roleMap != null) {
            return roleMap.getAsName(role);
        }
        return null;
    }

    private void printElement(PdfStructElem elem, int level, String comment) {
        PdfName mappedRole = getMappedRole(elem.getRole());
        String role = null;
        if (mappedRole != null) {
            role = mappedRole.getValue() + " (mapped from " + elem.getRole().getValue() + ")";
        } else {
            role = elem.getRole().getValue();
        }

        String tagOutput = INDENT.repeat(level) + "- " + role;
        if (comment.isEmpty()) {
            output.println(tagOutput); // Changed from System.out
        } else {
            // Pad to column 40, or use minimum spacing if already longer
            int currentLength = tagOutput.length();
            String padding = currentLength < DISPLAY_COLUMN_WIDTH ?
                " ".repeat(DISPLAY_COLUMN_WIDTH - currentLength) : "  ";
            output.println(tagOutput + padding + "; " + comment); // Changed from System.out
        }
    }

    private void processList(PdfStructElem listElem, int level) {
        // Create a copy to iterate over safely
        List<IStructureNode> kidsCopy = new ArrayList<>(listElem.getKids());

        printElement(listElem, level, "");

        for (IStructureNode kid : kidsCopy) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem kidElem = (PdfStructElem) kid;
                PdfName kidRole = kidElem.getRole();

                if (PdfName.LI.equals(kidRole)) {
                    processListItem(kidElem, level + 1);
                } else if (PdfName.P.equals(kidRole)) {
                    wrapInLI(listElem, kidElem, level);
                } else if (PdfName.Caption.equals(kidRole)) {
                    // Allow Caption in lists without changes
                    printElement(kidElem, level + 1, "");
                } else {
                    // Unexpected child in L
                    warningCount++;
                    String warnComment = "unexpected child in L: " + kidRole.getValue();
                    setVisualMarker(kidElem, warnComment);
                    escalateWarning(kidElem);
                    printElement(kidElem, level + 1, warnComment);
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
        printElement(newLI, level + 1, comment);
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
            escalateWarning(liElem);
        } else if (result.isChange) {
            // Count changes based on the comment content
            if (result.comment.contains("P+P")) {
                changeCount += 2;
            } else if (result.comment.contains("converted")) {
                changeCount++;
            }
            printElement(liElem, level, result.comment);
        } else if (!result.comment.isEmpty()) {
            // Just a comment, no change or warning
            printElement(liElem, level, result.comment);
        }

        // Recurse into children to handle nested elements
        for (Object kid : liElem.getKids()) {
            if (kid instanceof PdfStructElem) {
                processElement((PdfStructElem) kid, level + 1);
            }
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
        final boolean isChange;
        final boolean isWarning;

        NormalizationResult(String comment, boolean isChange, boolean isWarning) {
            this.comment = comment;
            this.isChange = isChange;
            this.isWarning = isWarning;
        }

        static NormalizationResult change(String comment) {
            return new NormalizationResult(comment, true, false);
        }

        static NormalizationResult warning(String comment) {
            return new NormalizationResult(comment, false, true);
        }

        static NormalizationResult comment(String comment) {
            return new NormalizationResult(comment, false, false);
        }

        static NormalizationResult noChange() {
            return new NormalizationResult("", false, false);
        }
    }

    private NormalizationResult handleSingleListItemChild(PdfStructElem child) {
        PdfName childRole = child.getRole();

        if (PdfName.P.equals(childRole)) {
            PdfStructElem parentLI = (PdfStructElem) child.getParent();

            // Create new LBody and immediately add it to parent
            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            parentLI.addKid(newLBody);  // Add to parent first!

            // Now move the P
            parentLI.removeKid(child);
            newLBody.addKid(child);

            return NormalizationResult.change("enclosed P in LBody, missing Lbl");
        } else if (!PdfName.Lbl.equals(childRole) && !PdfName.LBody.equals(childRole)) {
            return NormalizationResult.warning("unexpected single child: " + childRole.getValue());
        } else if (PdfName.Lbl.equals(childRole)) {
            return NormalizationResult.comment("missing LBody");
        } else {
            return NormalizationResult.comment("missing Lbl");
        }
    }

    private NormalizationResult handleTwoListItemChildren(PdfStructElem first, PdfStructElem second) {
        PdfName firstRole = first.getRole();
        PdfName secondRole = second.getRole();

        // Perfect case
        if (PdfName.Lbl.equals(firstRole) && PdfName.LBody.equals(secondRole)) {
            return NormalizationResult.noChange(); // No comment needed
        }

        // Case: P + LBody -> convert P to Lbl
        if (PdfName.P.equals(firstRole) && PdfName.LBody.equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            return NormalizationResult.change("converted P+Body to Lbl+LBody");
        }

        // Case: P + P -> convert first to Lbl, second to LBody
        if (PdfName.P.equals(firstRole) && PdfName.P.equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            second.setRole(PdfName.LBody);
            return NormalizationResult.change("converted P+P to Lbl+LBody");
        }

        // Other problematic cases
        return NormalizationResult.warning("unexpected children: " + firstRole.getValue() + "+" + secondRole.getValue());
    }

    // Recursively escalate a warning message up to the Document level
    private String escalateWarning(PdfStructElem elem, String text) {
        PdfStructElem parent = (PdfStructElem) elem.getParent();
        if (!PdfName.Document.equals(parent.getRole())) {
            setVisualMarker(parent, text);
            escalateWarning(parent, text);
        }
        return text;
    }

    private String escalateWarning(PdfStructElem elem) {
        return escalateWarning(elem, "attention needed");
    }

    private void setVisualMarker(PdfStructElem elem, String text) {
        elem.put(PdfName.T, new PdfString(text));
    }
}
