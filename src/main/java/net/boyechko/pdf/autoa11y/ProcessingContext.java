package net.boyechko.pdf.autoa11y;

import java.io.*;
import java.util.*;
import com.itextpdf.kernel.pdf.*;

public class ProcessingContext {
    private final PdfDocument doc;
    private final PrintStream out;
    private final List<TagIssue> issues = new ArrayList<>();

    public ProcessingContext(PdfDocument doc, PrintStream out) {
        this.doc = doc; this.out = out;
    }
    public PdfDocument doc() { return doc; }
    public PrintStream out() { return out; }

    public void report(TagIssue issue) { issues.add(issue); }
    public List<TagIssue> issues() { return Collections.unmodifiableList(issues); }
}