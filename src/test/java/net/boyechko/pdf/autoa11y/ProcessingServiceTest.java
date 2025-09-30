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
        } catch (Exception e) {
            assertTrue(false, "Did not expect exception for encrypted PDF with password");
        }
    }
}
