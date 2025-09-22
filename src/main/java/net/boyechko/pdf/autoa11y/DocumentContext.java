package net.boyechko.pdf.autoa11y;

import java.util.*;
import com.itextpdf.kernel.pdf.*;

public class DocumentContext {
    private final PdfDocument doc;

    public DocumentContext(PdfDocument doc) {
        this.doc = doc;
    }

    public PdfDocument doc() { return doc; }
}
