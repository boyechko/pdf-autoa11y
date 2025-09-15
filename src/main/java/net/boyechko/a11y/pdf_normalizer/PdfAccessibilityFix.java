package net.boyechko.a11y.pdf_normalizer;

import com.itextpdf.kernel.pdf.PdfDocument;
import java.io.PrintStream;

public interface PdfAccessibilityFix {
    /**
     * Execute this processing step
     * @param document The PDF document to process
     * @param output Where to write progress messages
     * @return Result of the processing step
     */
    OperationResult execute(PdfDocument document, PrintStream output);

    /**
     * @return Human-readable name of this step
     */
    String getName();

    /**
     * @return Description of what this step does
     */
    String getDescription();

    /**
     * @return Whether this step is enabled by default
     */
    boolean isEnabledByDefault();
}
