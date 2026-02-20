/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2025 Richard Boyechko
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import com.itextpdf.kernel.exceptions.BadPasswordException;
import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfUAConformance;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import java.io.IOException;
import java.nio.file.Path;
import net.boyechko.pdf.autoa11y.PdfTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Test suite for PdfCustodian pipeline methods. */
public class PdfCustodianTest extends PdfTestBase {

    private static final String PASSWORD = "password";

    private Path clearPdf;
    private Path encryptedPdf;

    @BeforeEach
    void createFixtures() throws IOException {
        clearPdf = createClearPdf(testOutputPath("fixture_clear.pdf"));
        encryptedPdf = createEncryptedPdf(testOutputPath("fixture_encrypted.pdf"), PASSWORD);
    }

    // ── Fixture helpers ────────────────────────────────────────────

    private static Path createClearPdf(Path output) throws IOException {
        try (PdfDocument doc = new PdfDocument(new PdfWriter(output.toString()))) {
            doc.addNewPage();
        }
        return output;
    }

    private static Path createEncryptedPdf(Path output, String ownerPassword) throws IOException {
        WriterProperties props = new WriterProperties();
        props.setStandardEncryption(
                null,
                ownerPassword.getBytes(),
                EncryptionConstants.ALLOW_PRINTING | EncryptionConstants.ALLOW_SCREENREADERS,
                EncryptionConstants.ENCRYPTION_AES_256
                        | EncryptionConstants.DO_NOT_ENCRYPT_METADATA);
        try (PdfDocument doc = new PdfDocument(new PdfWriter(output.toString(), props))) {
            doc.addNewPage();
        }
        return output;
    }

    // ── openTempForModification ────────────────────────────────────

    @Test
    void openTempForModificationSetsPdfUaConformance() throws Exception {
        Path output = testOutputPath("temp_ua_conformance.pdf");
        try (PdfDocument doc = PdfCustodian.openTempForModification(clearPdf, output)) {
            // close writes the output
        }

        try (PdfDocument result = new PdfDocument(new PdfReader(output.toString()))) {
            assertEquals(
                    PdfUAConformance.PDF_UA_1,
                    result.getConformance().getUAConformance(),
                    "Output should declare PDF/UA-1 conformance");
        }
    }

    // ── isEncrypted ────────────────────────────────────────────────

    @Test
    void isEncryptedTrueForEncryptedWithPassword() throws Exception {
        PdfCustodian custodian = new PdfCustodian(encryptedPdf, PASSWORD);
        assertTrue(custodian.isEncrypted());
    }

    @Test
    void isEncryptedFalseForUnencryptedPdf() throws Exception {
        PdfCustodian custodian = new PdfCustodian(clearPdf);
        assertFalse(custodian.isEncrypted());
    }

    @Test
    void isEncryptedFalseWhenNoPasswordProvided() throws Exception {
        // With null password, isEncrypted() returns false due to the
        // `&& password != null` guard, even if the PDF is encrypted.
        // iText may throw if it can't open the file without a password,
        // so we also accept BadPasswordException as valid behavior.
        try {
            PdfCustodian custodian = new PdfCustodian(encryptedPdf, null);
            assertFalse(custodian.isEncrypted(), "Should return false when no password provided");
        } catch (BadPasswordException e) {
            // Also acceptable — iText blocks opening without password
        }
    }
}
