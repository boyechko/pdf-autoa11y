package net.boyechko.pdf.preprocess.fixes;

import net.boyechko.pdf.preprocess.*;
import com.itextpdf.kernel.pdf.*;
import java.io.PrintStream;

public class TabOrderFix implements PdfAccessibilityFix {

    @Override
    public OperationResult execute(PdfDocument document, PrintStream output) {
        int pageCount = document.getNumberOfPages();

        for (int i = 1; i <= pageCount; i++) {
            document.getPage(i).setTabOrder(PdfName.S);
        }

        output.println("✓ Set tab order to structure order for all " + pageCount + " pages");
        return OperationResult.success("Tab order set for " + pageCount + " pages");
    }

    @Override
    public String getName() {
        return "Tab Order";
    }

    @Override
    public String getDescription() {
        return "Sets tab order to follow document structure";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }
}
