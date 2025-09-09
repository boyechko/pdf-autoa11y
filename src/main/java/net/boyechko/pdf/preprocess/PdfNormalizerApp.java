package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import java.io.*;
import java.nio.file.*;

public class PdfNormalizerApp {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java PdfNormalizerApp <filename> [password]");
            System.err.println("Example: java PdfNormalizerApp input.pdf abracadabra");
            System.exit(1);
        }

        String file = args[0];
        String password = args.length > 1 ? args[1] : null;
        String src = "inputs/" + file;
        String dest = "outputs/" + file;
        Files.createDirectories(Paths.get(dest).getParent());

        // First, try to open and check if password-protected
        ReaderProperties readerProps = new ReaderProperties();
        if (password != null) {
            readerProps.setPassword(password.getBytes());
        }

        try {
            // Open for reading to check encryption properties
            PdfReader testReader = new PdfReader(src, readerProps);
            PdfDocument testDoc = new PdfDocument(testReader);
            
            int permissions = testReader.getPermissions();
            int cryptoMode = testReader.getCryptoMode();
            boolean isEncrypted = testReader.isEncrypted();
            
            testDoc.close();
            
            // Now open for processing with proper writer properties
            PdfReader pdfReader = new PdfReader(src, readerProps);
            WriterProperties writerProps = new WriterProperties();
            
            if (isEncrypted && password != null) {
                writerProps.setStandardEncryption(
                    null, // user password (null means same as owner)
                    password.getBytes(), // owner password
                    permissions,
                    cryptoMode
                );
            }
            
            PdfWriter pdfWriter = new PdfWriter(dest, writerProps);
            
            try (PdfDocument pdfDoc = new PdfDocument(pdfReader, pdfWriter)) {
                System.out.println("Number of pages: " + pdfDoc.getNumberOfPages());
                PdfTagNormalizer processor = new PdfTagNormalizer(pdfDoc);
                processor.processAndDisplayChanges();
            }
            
        } catch (Exception e) {
            if (password == null && e.getMessage().contains("Bad user password")) {
                System.err.println("The PDF is password-protected. Please provide a password as the second argument.");
                System.exit(1);
            } else {
                throw e;
            }
        }
    }
}