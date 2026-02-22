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
package net.boyechko.pdf.autoa11y.rules;

import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import net.boyechko.pdf.autoa11y.document.DocumentContext;
import net.boyechko.pdf.autoa11y.issues.*;
import net.boyechko.pdf.autoa11y.validation.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects scanned/image-only PDFs that have no structure tree, no extractable text, and image
 * content on their pages. These documents require OCR before any accessibility remediation can
 * proceed.
 */
public class ImageOnlyDocumentRule implements Rule {
    private static final Logger logger = LoggerFactory.getLogger(ImageOnlyDocumentRule.class);

    @Override
    public String name() {
        return "Image Only Document Rule";
    }

    @Override
    public String passedMessage() {
        return "Document is not an image-only document";
    }

    @Override
    public String failedMessage() {
        return "Document is an image-only document with no text";
    }

    @Override
    public IssueList findIssues(DocumentContext ctx) {
        if (ctx.doc().getStructTreeRoot() != null) {
            return new IssueList();
        }

        if (hasExtractableText(ctx.doc())) {
            return new IssueList();
        }

        if (!hasImageContent(ctx.doc())) {
            return new IssueList();
        }

        String message =
                "This PDF has no structure tree and no extractable text content."
                        + " It appears to be an image-only document."
                        + " OCR is required before accessibility remediation can proceed.";

        Issue issue = new Issue(IssueType.IMAGE_ONLY_DOCUMENT, IssueSeverity.FATAL, message, null);
        return new IssueList(issue);
    }

    /**
     * Checks whether any page in the document has extractable text. Short-circuits on the first
     * page that yields non-blank text.
     */
    static boolean hasExtractableText(PdfDocument doc) {
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            try {
                String text =
                        PdfTextExtractor.getTextFromPage(
                                doc.getPage(i), new SimpleTextExtractionStrategy());
                if (text != null && !text.isBlank()) {
                    logger.debug("Found extractable text on page {}", i);
                    return true;
                }
            } catch (Exception e) {
                logger.debug("Failed to extract text from page {}: {}", i, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Checks whether any page has image XObjects in its resources. This distinguishes scanned
     * documents (which have page-sized images) from blank or empty PDFs.
     */
    static boolean hasImageContent(PdfDocument doc) {
        for (int i = 1; i <= doc.getNumberOfPages(); i++) {
            PdfPage page = doc.getPage(i);
            if (page.getResources() == null) {
                continue;
            }
            for (PdfName name : page.getResources().getResourceNames(PdfName.XObject)) {
                PdfObject xObj = page.getResources().getResourceObject(PdfName.XObject, name);
                if (xObj instanceof PdfDictionary dict
                        && PdfName.Image.equals(dict.getAsName(PdfName.Subtype))) {
                    logger.debug("Found image XObject on page {}", i);
                    return true;
                }
            }
        }
        return false;
    }
}
