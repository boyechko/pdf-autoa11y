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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.fixes.RemapLigatures;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.Rule;

/** Detects fonts whose ToUnicode CMaps map ligatures to truncated text. */
public class BadlyMappedLigatureCheck implements Rule {

    @Override
    public String name() {
        return "Badly Mapped Ligature Check";
    }

    @Override
    public String passedMessage() {
        return "No broken ligature mappings found in fonts";
    }

    @Override
    public String failedMessage() {
        return "Some fonts contain broken ligature mappings";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        IssueList issues = new IssueList();

        Map<Integer, Integer> firstPageByFontObjNum = new LinkedHashMap<>();
        Map<Integer, PdfDictionary> fontsByObjNum = new LinkedHashMap<>();
        collectType0Fonts(ctx, fontsByObjNum, firstPageByFontObjNum);

        for (Map.Entry<Integer, PdfDictionary> entry : fontsByObjNum.entrySet()) {
            int fontObjNum = entry.getKey();
            PdfDictionary fontDict = entry.getValue();
            Map<Integer, String> replacements = RemapLigatures.findBrokenMappings(fontDict);
            if (replacements.isEmpty()) {
                continue;
            }

            int firstPage = firstPageByFontObjNum.getOrDefault(fontObjNum, 0);
            String fontName = RemapLigatures.fontName(fontDict);
            String message =
                    "Font "
                            + fontName
                            + " has "
                            + replacements.size()
                            + " broken ligature mapping(s)";
            Issue issue =
                    new Issue(
                            IssueType.LIGATURE_MAPPING_BROKEN,
                            IssueSev.WARNING,
                            IssueLoc.atObjNum(fontObjNum, firstPage > 0 ? firstPage : null),
                            message,
                            new RemapLigatures(fontObjNum, fontName, replacements));
            issues.add(issue);
        }

        return issues;
    }

    private void collectType0Fonts(
            DocumentContext ctx,
            Map<Integer, PdfDictionary> fontsByObjNum,
            Map<Integer, Integer> firstPageByFontObjNum) {
        for (int pageNum = 1; pageNum <= ctx.doc().getNumberOfPages(); pageNum++) {
            PdfPage page = ctx.doc().getPage(pageNum);
            PdfDictionary fontResource = page.getResources().getResource(PdfName.Font);
            if (fontResource == null) {
                continue;
            }

            for (PdfName fontResourceName : fontResource.keySet()) {
                PdfDictionary fontDict = fontResource.getAsDictionary(fontResourceName);
                if (fontDict == null) {
                    continue;
                }
                if (!PdfName.Type0.equals(fontDict.getAsName(PdfName.Subtype))) {
                    continue;
                }
                if (fontDict.getAsStream(PdfName.ToUnicode) == null) {
                    continue;
                }
                if (fontDict.getIndirectReference() == null) {
                    continue;
                }
                int objNum = fontDict.getIndirectReference().getObjNumber();
                fontsByObjNum.putIfAbsent(objNum, fontDict);
                firstPageByFontObjNum.putIfAbsent(objNum, pageNum);
            }
        }
    }
}
