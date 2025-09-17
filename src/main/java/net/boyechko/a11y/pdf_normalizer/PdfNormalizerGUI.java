package net.boyechko.a11y.pdf_normalizer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.file.Files;
import java.util.List;

public class PdfNormalizerGUI extends JFrame {
    private JLabel dropLabel;
    private JTextArea outputArea;
    private JButton processButton;
    private JTextField passwordField;
    private File selectedFile;

    public PdfNormalizerGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("PDF Accessibility Normalizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top panel - file drop area
        JPanel dropPanel = createDropPanel();
        add(dropPanel, BorderLayout.NORTH);

        // Center panel - controls
        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.CENTER);

        // Bottom panel - output text
        JPanel outputPanel = createOutputPanel();
        add(outputPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null); // Center on screen
        setVisible(true);
    }

    private JPanel createDropPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new TitledBorder("1. Drop PDF File Here"));
        panel.setPreferredSize(new Dimension(300, 300));

        dropLabel = new JLabel("Drop a PDF file here or click to browse", JLabel.CENTER);
        dropLabel.setFont(dropLabel.getFont().deriveFont(14f));
        dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 5, 5, false));
        dropLabel.setOpaque(true);
        dropLabel.setBackground(Color.LIGHT_GRAY);

        // Enable drag and drop
        new DropTarget(dropLabel, new FileDropHandler());

        // Enable click to browse
        dropLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                browseForFile();
            }
        });

        panel.add(dropLabel, BorderLayout.CENTER);

        // Password field
        JPanel passwordPanel = new JPanel(new FlowLayout());
        passwordPanel.add(new JLabel("Password (if needed):"));
        passwordField = new JTextField(15);
        passwordPanel.add(passwordField);
        panel.add(passwordPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(600, 400));
        panel.setBorder(new TitledBorder("2. Processing Output"));

        outputArea = new JTextArea(20, 60);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(outputArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new FlowLayout());

        processButton = new JButton("Process PDF");
        processButton.setEnabled(false);
        processButton.addActionListener(e -> processPDF());

        panel.add(processButton);

        return panel;
    }

    private class FileDropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);

                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) dtde.getTransferable()
                    .getTransferData(DataFlavor.javaFileListFlavor);

                if (!files.isEmpty()) {
                    File file = files.get(0);
                    if (file.getName().toLowerCase().endsWith(".pdf")) {
                        setSelectedFile(file);
                    } else {
                        JOptionPane.showMessageDialog(PdfNormalizerGUI.this,
                            "Please select a PDF file");
                    }
                }
                dtde.dropComplete(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(PdfNormalizerGUI.this,
                    "Error handling dropped file: " + e.getMessage());
            }
        }
    }

    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "PDF Files", "pdf"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setSelectedFile(chooser.getSelectedFile());
        }
    }

    private void setSelectedFile(File file) {
        selectedFile = file;
        dropLabel.setText("Selected: " + file.getName());
        dropLabel.setBackground(Color.WHITE);
        processButton.setEnabled(true);
    }

    private void processPDF() {
        if (selectedFile == null) return;

        processButton.setEnabled(false);
        outputArea.setText("Processing " + selectedFile.getName() + "...\n");

        SwingWorker<PdfProcessingService.ProcessingResult, String> worker =
            new SwingWorker<PdfProcessingService.ProcessingResult, String>() {

            private File tempOutputFile;

            @Override
            protected PdfProcessingService.ProcessingResult doInBackground() throws Exception {
                // Create custom PrintStream for GUI output
                PrintStream guiOutput = new PrintStream(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        publish(String.valueOf((char) b));
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        publish(new String(b, off, len));
                    }
                });

                String password = passwordField.getText().length() > 0 ?
                    passwordField.getText() : null;

                // Create temporary file for output
                tempOutputFile = File.createTempFile("pdf_normalized_", ".pdf");
                tempOutputFile.deleteOnExit(); // Clean up if something goes wrong

                // Use the service with temp file
                PdfProcessingService service = new PdfProcessingService();
                PdfProcessingService.ProcessingRequest request =
                    new PdfProcessingService.ProcessingRequest(
                        selectedFile.toPath(),
                        tempOutputFile.toPath(),
                        password,
                        guiOutput
                    );

                return service.processPdf(request);
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    outputArea.append(chunk);
                }
            }

            @Override
            protected void done() {
                try {
                    PdfProcessingService.ProcessingResult result = get();
                    if (result.isSuccess()) {
                        outputArea.append("\n=== PROCESSING COMPLETE ===\n");
                        outputArea.append("Changes applied: " + result.getChangeCount() + "\n");
                        if (result.getWarningCount() > 0) {
                            outputArea.append("Warnings: " + result.getWarningCount() + "\n");
                        }

                        // Show save dialog and handle the file move
                        showSaveDialog(tempOutputFile);
                    } else {
                        outputArea.append("\nError: " + result.getErrorMessage() + "\n");
                        // Clean up temp file on error
                        if (tempOutputFile != null && tempOutputFile.exists()) {
                            tempOutputFile.delete();
                        }
                    }
                } catch (Exception e) {
                    outputArea.append("\nError: " + e.getMessage() + "\n");
                    // Clean up temp file on error
                    if (tempOutputFile != null && tempOutputFile.exists()) {
                        tempOutputFile.delete();
                    }
                }
                processButton.setEnabled(true);
            }
        };

        worker.execute();
    }

    private void showSaveDialog(File tempFile) {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(selectedFile.getParentFile());
        chooser.setSelectedFile(new File(selectedFile.getName()));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File targetFile = chooser.getSelectedFile();
            // Move temp file to selected location
            if (targetFile.exists()) {
                int response = JOptionPane.showConfirmDialog(this,
                    "File exists. Overwrite?", "Confirm Overwrite",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) {
                    // User chose not to overwrite
                    tempFile.delete();
                    return;
                }
            }
            try {
                Files.move(tempFile.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(this,
                    "File saved to: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error saving file: " + e.getMessage());
            }
        } else {
            // User canceled, delete temp file
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public static void main(String[] args) {
        // macOS integration to make the app appear in task switcher
        System.setProperty("apple.awt.application.name", "PDF Normalizer");
        try {
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object app = appClass.getMethod("getApplication").invoke(null);
            // Optionally set dock icon badge to null
            app.getClass().getMethod("setDockIconBadge", String.class).invoke(app, (String) null);
        } catch (Exception e) {
            // Not on macOS or library not available, ignore
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            } catch (Exception e) {
                // Use default look and feel
            }
            new PdfNormalizerGUI();
        });
    }
}
