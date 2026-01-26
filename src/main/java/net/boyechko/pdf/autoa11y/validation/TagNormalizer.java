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
package net.boyechko.pdf.autoa11y.validation;

import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import com.itextpdf.kernel.pdf.tagging.PdfStructTreeRoot;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class TagNormalizer {
    private static final int DISPLAY_COLUMN_WIDTH = 40;
    private static final String INDENT = "  ";

    private final PdfDocument document;
    private final PdfStructTreeRoot root;
    private final PrintStream output;
    private int changeCount;
    private int warningCount;

    // Constructor with custom output
    public TagNormalizer(PdfDocument doc, PrintStream output) {
        this.document = doc;
        this.root = doc.getStructTreeRoot();
        this.output = output;
        this.changeCount = 0;
        this.warningCount = 0;
    }

    // Constructor with default output (backwards compatibility)
    public TagNormalizer(PdfDocument doc) {
        this(doc, System.out);
    }

    public TagNormalizer(String src) throws IOException {
        this(new PdfDocument(new PdfReader(src)), System.out);
    }

    public TagNormalizer(String src, PrintStream output) throws IOException {
        this(new PdfDocument(new PdfReader(src)), output);
    }

    public int getChangeCount() {
        return changeCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

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
            output.println("No accessibility tags found - document may need initial tagging.");
        }
    }

    private void processElement(PdfStructElem elem, int level) {
        PdfName role = elem.getRole();
        PdfName mappedRole = getMappedRole(role);
        PdfName parentRole = elem.getParent() != null ? elem.getParent().getRole() : null;
        String comment = "";

        switch (mappedRole != null ? mappedRole.getValue() : role.getValue()) {
            case "Document",
                    "Sect",
                    "Part",
                    "Art",
                    "Div",
                    "H1",
                    "H2",
                    "H3",
                    "H4",
                    "H5",
                    "H6",
                    "BlockQuote",
                    "P",
                    "Caption",
                    "Figure",
                    "Formula",
                    "Link",
                    "Note",
                    "Reference",
                    "Span",
                    "Table",
                    "TR",
                    "TH",
                    "TD" -> {
                // No special handling needed, just print
            }
            case "L" -> {
                processList(elem, level);
                return; // processList handles printing
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
            case "Aside" -> {
                comment = handleAside(elem);
            }
            default -> {
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

    private String handleAside(PdfStructElem elem) {
        this.root.getRoleMap().put(PdfName.Aside, PdfName.Div);
        changeCount++;
        return "added role mapping Aside -> Div";
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
        String role = mappedRole != null ? mappedRole.getValue() : elem.getRole().getValue();

        String tagOutput = INDENT.repeat(level) + "- " + role;
        if (comment.isEmpty()) {
            output.println(tagOutput);
        } else {
            int currentLength = tagOutput.length();
            String padding =
                    currentLength < DISPLAY_COLUMN_WIDTH
                            ? " ".repeat(DISPLAY_COLUMN_WIDTH - currentLength)
                            : "  ";
            output.println(tagOutput + padding + "; " + comment);
        }
    }

    private void processList(PdfStructElem listElem, int level) {
        List<IStructureNode> kidsCopy = new ArrayList<>(listElem.getKids());

        if (everyStructChild(kidsCopy, PdfName.P) && kidsCopy.size() % 2 == 0) {
            printElement(listElem, level, "converted even number of P to LIs");
            wrapPairsOfPInLI(listElem, kidsCopy, level);
            return;
        }

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
                    printElement(kidElem, level + 1, "");
                } else {
                    warningCount++;
                    String warnComment = "unexpected child in L: " + kidRole.getValue();
                    setVisualMarker(kidElem, warnComment);
                    escalateWarning(kidElem);
                    printElement(kidElem, level + 1, warnComment);
                }
            }
        }
    }

    private boolean everyStructChild(List<IStructureNode> kids, PdfName role) {
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem) {
                PdfStructElem kidElem = (PdfStructElem) kid;
                if (!role.equals(kidElem.getRole())) {
                    return false;
                }
            }
        }
        return true;
    }

    private void wrapPairsOfPInLI(PdfStructElem listElem, List<IStructureNode> kids, int level) {
        for (int i = 0; i < kids.size(); i += 2) {
            PdfStructElem p1 = (PdfStructElem) kids.get(i);
            PdfStructElem p2 = (PdfStructElem) kids.get(i + 1);

            PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
            listElem.addKid(newLI);

            PdfStructElem newLbl = new PdfStructElem(document, PdfName.Lbl);
            newLI.addKid(newLbl);
            listElem.removeKid(p1);
            newLbl.addKid(p1);

            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            newLI.addKid(newLBody);
            listElem.removeKid(p2);
            newLBody.addKid(p2);

            changeCount += 2;
            printElement(newLI, level + 1, "");
        }
    }

    private void wrapInLI(PdfStructElem listElem, PdfStructElem kidElem, int level) {
        PdfStructElem newLI = new PdfStructElem(document, PdfName.LI);
        listElem.addKid(newLI);

        PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
        newLI.addKid(newLBody);

        listElem.removeKid(kidElem);
        newLBody.addKid(kidElem);

        changeCount++;
        String comment = "enclosed unexpected " + kidElem.getRole().getValue() + " in LI";
        printElement(newLI, level + 1, comment);
    }

    private void processListItem(PdfStructElem liElem, int level) {
        NormalizationResult result = processListItemInternal(liElem);

        if (result.isWarning) {
            warningCount++;
            setVisualMarker(liElem, result.comment);
            escalateWarning(liElem);
        } else if (result.isChange) {
            if (result.comment.contains("P+P")) {
                changeCount += 2;
            } else if (result.comment.contains("converted")) {
                changeCount++;
            }
            printElement(liElem, level, result.comment);
        } else if (!result.comment.isEmpty()) {
            printElement(liElem, level, result.comment);
        }

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

            PdfStructElem newLBody = new PdfStructElem(document, PdfName.LBody);
            parentLI.addKid(newLBody);

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

    private NormalizationResult handleTwoListItemChildren(
            PdfStructElem first, PdfStructElem second) {
        PdfName firstRole = first.getRole();
        PdfName secondRole = second.getRole();

        if (PdfName.Lbl.equals(firstRole) && PdfName.LBody.equals(secondRole)) {
            return NormalizationResult.noChange();
        }

        if (PdfName.P.equals(firstRole) && PdfName.LBody.equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            return NormalizationResult.change("converted P+Body to Lbl+LBody");
        }

        if (PdfName.P.equals(firstRole) && PdfName.P.equals(secondRole)) {
            first.setRole(PdfName.Lbl);
            second.setRole(PdfName.LBody);
            return NormalizationResult.change("converted P+P to Lbl+LBody");
        }

        return NormalizationResult.warning(
                "unexpected children: " + firstRole.getValue() + "+" + secondRole.getValue());
    }

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
