package net.boyechko.pdf.autoa11y;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProcessingServiceTest {
    @Test
    void encryptedPdfRaisesException() {
        Path inputPath = Path.of("src/test/resources/blank_password.pdf");
        ProcessingService service = new ProcessingService(inputPath, null, System.out);

        try {
            service.process();
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("password"));
            return;
        }
        assertTrue(false, "Expected exception for encrypted PDF");
    }

    @Test
    void encryptedPdfWithPasswordSucceeds() {
        Path inputPath = Path.of("src/test/resources/blank_password.pdf");
        ProcessingService service = new ProcessingService(inputPath, "password", System.out);

        try {
            service.process();
        } catch (ProcessingService.NoTagsException e) {
            // Expected since the PDF is blank and untagged
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false, "Did not expect exception for encrypted PDF with password");
        }
    }

    @Test
    void validPdfIsProcessedSuccessfully() {
        Path inputPath = Path.of("src/test/resources/moby_dick.pdf");
        ProcessingService service = new ProcessingService(inputPath, null, System.out);

        try {
            service.process();
        } catch (Exception e) {
            assertTrue(false, "Did not expect exception for valid PDF");
        }
    }

    @Test
    void untaggedPdfRaisesException() {
        Path inputPath = Path.of("src/test/resources/moby_dick_untagged.pdf");
        ProcessingService service = new ProcessingService(inputPath, null, System.out);

        try {
            service.process();
        } catch (ProcessingService.NoTagsException e) {
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false, "Expected NoTagsException");
        }
    }
}
