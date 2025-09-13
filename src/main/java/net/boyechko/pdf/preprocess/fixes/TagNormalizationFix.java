package net.boyechko.pdf.preprocess.fixes;

import net.boyechko.pdf.preprocess.*;
import com.itextpdf.kernel.pdf.PdfDocument;
import java.io.PrintStream;

public class TagNormalizationFix implements PdfAccessibilityFix {

    @Override
    public OperationResult execute(PdfDocument document, PrintStream output) {
        output.println("Tag structure analysis and fixes:");
        output.println("────────────────────────────────────────");

        PdfTagNormalizer normalizer = new PdfTagNormalizer(document, output);
        normalizer.processAndDisplayChanges();

        return OperationResult.changesAndWarnings(
            normalizer.getChangeCount(),
            normalizer.getWarningCount(),
            "Tag structure normalization complete"
        );
    }

    @Override
    public String getName() {
        return "Tag Structure Normalization";
    }

    @Override
    public String getDescription() {
        return "Fixes malformed lists, heading hierarchy, and document structure";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
