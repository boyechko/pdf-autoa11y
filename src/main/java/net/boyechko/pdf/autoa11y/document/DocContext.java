// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import com.itextpdf.kernel.pdf.PdfDocument;

/** Provides access to the opened PDF document and its content. */
public class DocContext {
    private final PdfDocument doc;
    private final PageContent pages;
    private final ReadingOrder readingOrder;

    public DocContext(PdfDocument doc) {
        this.doc = doc;
        this.pages = new PageContent(doc);
        this.readingOrder = new ReadingOrder(doc, pages);
    }

    public PdfDocument doc() {
        return doc;
    }

    /** Per-page marked-content facts, extracted lazily and shared across checks and fixes. */
    public PageContent pages() {
        return pages;
    }

    /** Reading order of structure elements, cached and shared between a check and its fixes. */
    public ReadingOrder readingOrder() {
        return readingOrder;
    }
}
