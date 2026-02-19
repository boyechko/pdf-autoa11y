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

import com.itextpdf.kernel.pdf.EncryptionConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfUAConformance;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.ReaderProperties;
import com.itextpdf.kernel.pdf.WriterProperties;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Factory for opening PDF documents with appropriate encryption and metadata settings. */
public final class PdfCustodian {
    private static final Logger logger = LoggerFactory.getLogger(PdfCustodian.class);

    private static final int DEFAULT_PERMISSIONS =
            EncryptionConstants.ALLOW_PRINTING
                    | EncryptionConstants.ALLOW_FILL_IN
                    | EncryptionConstants.ALLOW_MODIFY_ANNOTATIONS
                    | EncryptionConstants.ALLOW_SCREENREADERS;

    private static final int DEFAULT_CRYPTO_MODE =
            EncryptionConstants.ENCRYPTION_AES_256 | EncryptionConstants.DO_NOT_ENCRYPT_METADATA;

    private final Path inputPath;
    private final String password;
    private final ReaderProperties readerProps;
    private EncryptionInfo encryptionInfo;

    private record EncryptionInfo(int permissions, int cryptoMode, boolean isEncrypted) {}

    public PdfCustodian(Path inputPath, String password) {
        this.inputPath = inputPath;
        this.password = password;
        this.readerProps = new ReaderProperties();
        if (password != null) {
            this.readerProps.setPassword(password.getBytes());
        }
    }

    public PdfCustodian(Path inputPath) {
        this(inputPath, null);
    }

    public PdfDocument openForReading() throws IOException {
        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        return new PdfDocument(pdfReader);
    }

    public PdfDocument openForModification(Path outputPath) throws IOException {
        analyzeEncryptionIfNeeded();

        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        WriterProperties writerProps = buildWriterProperties();
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    /** Opens the original (possibly encrypted) input and writes to output WITHOUT encryption. */
    public PdfDocument decryptToTemp(Path outputPath) throws IOException {
        analyzeEncryptionIfNeeded();

        PdfReader pdfReader = new PdfReader(inputPath.toString(), readerProps);
        WriterProperties writerProps = new WriterProperties();
        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    /** Opens an unencrypted temp file for modification and writes to a new temp. */
    public static PdfDocument openTempForModification(Path inputPath, Path outputPath)
            throws IOException {
        PdfReader pdfReader = new PdfReader(inputPath.toString());
        WriterProperties writerProps = new WriterProperties();
        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        PdfWriter pdfWriter = new PdfWriter(outputPath.toString(), writerProps);

        return new PdfDocument(pdfReader, pdfWriter);
    }

    /**
     * Copies an unencrypted temp file to the final output with the original encryption settings.
     */
    public void reencrypt(Path inputPath, Path outputPath) throws IOException {
        analyzeEncryptionIfNeeded();

        try (PdfReader pdfReader = new PdfReader(inputPath.toString());
                PdfWriter pdfWriter =
                        new PdfWriter(outputPath.toString(), buildWriterProperties());
                PdfDocument doc = new PdfDocument(pdfReader, pdfWriter)) {
            // Document is saved on close — no modifications needed.
        }
    }

    /** Returns whether the original PDF is encrypted and needs re-encryption on final output. */
    public boolean isEncrypted() throws IOException {
        analyzeEncryptionIfNeeded();
        return encryptionInfo.isEncrypted() && password != null;
    }

    private void analyzeEncryptionIfNeeded() throws IOException {
        if (encryptionInfo != null) {
            return;
        }

        try (PdfReader testReader = new PdfReader(inputPath.toString(), readerProps);
                PdfDocument testDoc = new PdfDocument(testReader)) {
            logger.debug("PDF Encryption Analysis:");
            logger.debug("  Encrypted: {}", testReader.isEncrypted());
            logger.debug("  Permissions: {}", testReader.getPermissions());
            logger.debug("  Crypto Mode: {}", testReader.getCryptoMode());

            encryptionInfo =
                    new EncryptionInfo(
                            testReader.getPermissions(),
                            testReader.getCryptoMode(),
                            testReader.isEncrypted());
        }
    }

    private WriterProperties buildWriterProperties() {
        WriterProperties writerProps = new WriterProperties();

        if (encryptionInfo.isEncrypted() && password != null) {
            // Preserve original encryption settings
            writerProps.setStandardEncryption(
                    null,
                    password.getBytes(),
                    encryptionInfo.permissions(),
                    encryptionInfo.cryptoMode());
        } else if (password != null) {
            // Apply default encryption for newly protected file
            writerProps.setStandardEncryption(
                    null, password.getBytes(), DEFAULT_PERMISSIONS, DEFAULT_CRYPTO_MODE);
        }

        writerProps.addPdfUaXmpMetadata(PdfUAConformance.PDF_UA_1);
        return writerProps;
    }
}
