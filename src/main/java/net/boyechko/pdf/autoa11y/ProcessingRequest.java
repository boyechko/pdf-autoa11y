package net.boyechko.pdf.autoa11y;

import java.io.*;
import java.nio.file.*;

public class ProcessingRequest {
    private final Path inputPath;
    private final Path outputPath;
    private final String password;
    private final PrintStream outputStream;

    public ProcessingRequest(Path inputPath, Path outputPath, String password, PrintStream outputStream) {
        this.inputPath = inputPath;
        this.outputPath = outputPath;
        this.password = password;
        this.outputStream = outputStream;
    }

    // Getters
    public Path getInputPath() { return inputPath; }
    public Path getOutputPath() { return outputPath; }
    public String getPassword() { return password; }
    public PrintStream getOutputStream() { return outputStream; }
}