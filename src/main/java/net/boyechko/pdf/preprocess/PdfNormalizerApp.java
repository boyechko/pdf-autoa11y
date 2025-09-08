package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import java.io.*;
import java.nio.file.*;

public class PdfNormalizerApp {
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java PdfNormalizerApp <filename>");
            System.err.println("Example: java PdfNormalizerApp input.pdf");
            System.exit(1);
        }

        String file = args[0];
        String src  = "inputs/" + file;
        String dest = "outputs/" + file;

        // Ensure output dir exists
        Files.createDirectories(Paths.get(dest).getParent());

        // Normal, safe stamping: read src, write dest (different files)
        try (PdfDocument pdf = new PdfDocument(new PdfReader(src), new PdfWriter(dest))) {
            PdfTagNormalizer processor = new PdfTagNormalizer(pdf);
            processor.processAndDisplayChanges();
        }
    }
}