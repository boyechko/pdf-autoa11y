package net.boyechko.pdf.preprocess;

import com.itextpdf.kernel.pdf.*;
import java.io.*;
import java.nio.file.*;

public class PdfNormalizerApp {
    public static void main(String[] args) throws IOException {
        String src  = "inputs/input.pdf";
        String dest = "results/normalized.pdf";

        // Ensure output dir exists
        Files.createDirectories(Paths.get(dest).getParent());

        // Normal, safe stamping: read src, write dest (different files)
        try (PdfDocument pdf = new PdfDocument(new PdfReader(src), new PdfWriter(dest))) {
            PdfTagNormalizer processor = new PdfTagNormalizer(pdf);
            processor.normalizeListStructures();
            processor.demoteH1Tags();

            System.out.println("\nVerifying modified structure:");
            processor.analyzePdf();
        }
    }
}