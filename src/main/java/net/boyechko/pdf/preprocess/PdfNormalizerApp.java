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
        String src  = "inputs/" + file;
        String dest = "outputs/" + file;
        Files.createDirectories(Paths.get(dest).getParent());

        ReaderProperties readerProps = new ReaderProperties();
        if (args.length == 2) {
            String password = args[1];
            readerProps.setPassword(password.getBytes());
        }
        PdfReader pdfReader = new PdfReader(src, readerProps);

        try (PdfDocument pdfDoc = new PdfDocument(pdfReader, new PdfWriter(dest))) {
            System.out.println("Number of pages: " + pdfDoc.getNumberOfPages());
            PdfTagNormalizer processor = new PdfTagNormalizer(pdfDoc);
            processor.processAndDisplayChanges();
        }
    }
}