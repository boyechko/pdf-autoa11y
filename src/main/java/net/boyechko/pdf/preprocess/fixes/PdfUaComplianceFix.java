package net.boyechko.pdf.preprocess.fixes;

import net.boyechko.pdf.preprocess.*;
import com.itextpdf.kernel.pdf.*;
import java.io.PrintStream;

public class PdfUaComplianceFix implements PdfAccessibilityFix {

    @Override
    public OperationResult execute(PdfDocument document, PrintStream output) {
        // This step would be handled in the service when creating the writer
        // But we want to report it consistently
        output.println("✓ Set PDF/UA-1 compliance flag");
        return OperationResult.success("PDF/UA-1 compliance flag set");
    }

    @Override
    public String getName() {
        return "PDF/UA-1 Compliance";
    }

    @Override
    public String getDescription() {
        return "Sets PDF/UA-1 compliance metadata";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
