// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.pdf.PdfConformance;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.xmp.XMPMeta;
import java.lang.reflect.Field;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.issue.*;
import net.boyechko.pdf.autoa11y.validation.DocumentCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Detects and strips false PDF/UA conformance claims from XMP metadata. */
public class PdfUaConformanceCheck extends DocumentCheck {
    private static final Logger logger = LoggerFactory.getLogger(PdfUaConformanceCheck.class);

    private static final String PDFUAID_NS = "http://www.aiim.org/pdfua/ns/id/";

    @Override
    public String name() {
        return "PDF/UA Conformance Check";
    }

    @Override
    public String description() {
        return "Detects and strips false PDF/UA conformance claims from XMP metadata";
    }

    @Override
    public String passedMessage() {
        return "Document does not claim PDF/UA conformance";
    }

    @Override
    public String failedMessage() {
        return "Document claims PDF/UA conformance that has not been verified";
    }

    @Override
    public IssueList findIssues(DocContext ctx) {
        if (ctx.doc().getConformance().getUAConformance() == null) {
            return new IssueList();
        }

        IssueFix fix =
                new IssueFix() {
                    @Override
                    public String describe() {
                        return "Removed unlikely PDF/UA conformance claim from XMP metadata";
                    }

                    @Override
                    public void apply(DocContext c) throws Exception {
                        stripPdfUaXmp(c.doc());
                    }
                };

        Issue issue =
                new Issue(IssueType.FALSE_PDFUA_CONFORMANCE, IssueSev.ERROR, failedMessage(), fix);
        return new IssueList(issue);
    }

    private static void stripPdfUaXmp(PdfDocument doc) throws Exception {
        // Clear iText's cached conformance so it won't re-stamp XMP on save
        clearCachedConformance(doc);

        XMPMeta xmpMeta = doc.getXmpMetadata();
        if (xmpMeta == null) {
            return;
        }

        xmpMeta.deleteProperty(PDFUAID_NS, "part");
        xmpMeta.deleteProperty(PDFUAID_NS, "rev");
        doc.setXmpMetadata(xmpMeta);
        logger.debug("Stripped PDF/UA conformance claim from XMP metadata");
    }

    /** iText caches conformance on open and re-stamps XMP on save; clear via reflection. */
    private static void clearCachedConformance(PdfDocument doc) throws Exception {
        Field field = PdfDocument.class.getDeclaredField("pdfConformance");
        field.setAccessible(true);
        field.set(doc, new PdfConformance());
    }
}
