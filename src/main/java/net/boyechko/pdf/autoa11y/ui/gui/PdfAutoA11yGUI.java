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
package net.boyechko.pdf.autoa11y.ui.gui;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import net.boyechko.pdf.autoa11y.core.ProcessingResult;
import net.boyechko.pdf.autoa11y.core.ProcessingService;
import net.boyechko.pdf.autoa11y.core.VerbosityLevel;
import net.boyechko.pdf.autoa11y.document.PdfCustodian;
import net.boyechko.pdf.autoa11y.ui.ProcessingReporter;

public class PdfAutoA11yGUI extends JFrame {
    private JLabel dropLabel;
    private JTextArea outputArea;
    private JButton processButton;
    private JButton saveButton;
    private JButton saveReportButton;
    private JTextField passwordField;
    private File selectedFile;
    private File tempResultFile;

    public PdfAutoA11yGUI() {
        initializeGUI();
    }

    private void initializeGUI() {
        setTitle("PDF Auto A11y");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        addWindowListener(
                new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        if (tempResultFile != null && tempResultFile.exists()) {
                            tempResultFile.delete();
                        }
                    }
                });

        // Top panel - file drop area
        JPanel dropPanel = createDropPanel();
        add(dropPanel, BorderLayout.NORTH);

        // Center panel - output text (gets stretched)
        JPanel outputPanel = createOutputPanel();
        add(outputPanel, BorderLayout.CENTER);

        // Bottom panel - results/actions
        JPanel resultsPanel = createResultsPanel();
        add(resultsPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null); // Center on screen
        setVisible(true);
    }

    private JPanel createDropPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new TitledBorder("1. Drop PDF File Here"));

        // Create a wrapper to center the fixed-sized label
        JPanel dropWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));

        dropLabel = new JLabel("Drop a PDF file here or click to browse", JLabel.CENTER);
        dropLabel.setFont(dropLabel.getFont().deriveFont(14f));
        dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2, 5, 5, false));
        dropLabel.setOpaque(true);
        dropLabel.setBackground(Color.LIGHT_GRAY);
        dropLabel.setMinimumSize(new Dimension(300, 100));
        dropLabel.setPreferredSize(new Dimension(300, 100));
        dropLabel.setMaximumSize(new Dimension(300, 100));

        // Enable drag and drop
        new DropTarget(dropLabel, new FileDropHandler());

        // Enable click to browse
        dropLabel.addMouseListener(
                new java.awt.event.MouseAdapter() {
                    public void mouseClicked(java.awt.event.MouseEvent evt) {
                        browseForFile();
                    }
                });

        dropWrapper.add(dropLabel);
        panel.add(dropWrapper);

        // Password field
        JPanel passwordPanel = new JPanel(new FlowLayout());
        passwordPanel.add(new JLabel("Password (if needed):"));
        passwordField = new JTextField(15);
        passwordPanel.add(passwordField);

        // Process button
        panel.add(Box.createVerticalStrut(10));
        processButton = new JButton("Process PDF");
        processButton.setEnabled(false);
        processButton.addActionListener(e -> processPDF());
        passwordPanel.add(processButton);
        panel.add(passwordPanel, BorderLayout.SOUTH);

        // Move focus to drop panel
        panel.setFocusable(true);
        panel.requestFocusInWindow();

        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(1000, 400));
        panel.setBorder(new TitledBorder("2. Processing Output"));

        outputArea = new JTextArea(20, 80);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setEditable(false);

        JScrollPane scrollPane = new JScrollPane(outputArea);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        panel.setBorder(new TitledBorder("3. Results"));

        saveButton = new JButton("Save Result");
        saveButton.setEnabled(false);
        saveButton.addActionListener(
                e -> {
                    if (tempResultFile != null && tempResultFile.exists()) {
                        showSaveDialog();
                    } else {
                        JOptionPane.showMessageDialog(
                                PdfAutoA11yGUI.this, "No processed file available to save.");
                    }
                });

        saveReportButton = new JButton("Save Report");
        saveReportButton.setEnabled(false);
        saveReportButton.addActionListener(e -> saveReport());

        panel.add(saveButton);
        panel.add(saveReportButton);
        return panel;
    }

    private class FileDropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);

                @SuppressWarnings("unchecked")
                List<File> files =
                        (List<File>)
                                dtde.getTransferable()
                                        .getTransferData(DataFlavor.javaFileListFlavor);

                if (!files.isEmpty()) {
                    File file = files.get(0);
                    if (file.getName().toLowerCase().endsWith(".pdf")) {
                        setSelectedFile(file);
                    } else {
                        JOptionPane.showMessageDialog(
                                PdfAutoA11yGUI.this, "Please select a PDF file");
                    }
                }
                dtde.dropComplete(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                        PdfAutoA11yGUI.this, "Error handling dropped file: " + e.getMessage());
            }
        }
    }

    private void browseForFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("."));
        chooser.setFileFilter(
                new javax.swing.filechooser.FileNameExtensionFilter("PDF Files", "pdf"));

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
        saveButton.setEnabled(false);
        saveReportButton.setEnabled(false);
        tempResultFile = null;
        outputArea.setText("Processing " + selectedFile.getName() + "...\n");

        PrintStream textAreaStream = createTextAreaPrintStream();
        ProcessingReporter reporter =
                new ProcessingReporter(textAreaStream, VerbosityLevel.VERBOSE);

        SwingWorker<ProcessingResult, String> worker =
                new SwingWorker<ProcessingResult, String>() {

                    @Override
                    protected ProcessingResult doInBackground() throws Exception {
                        String password =
                                passwordField.getText().length() > 0
                                        ? passwordField.getText()
                                        : null;

                        PdfCustodian docFactory = new PdfCustodian(selectedFile.toPath(), password);
                        ProcessingService service =
                                new ProcessingService.ProcessingServiceBuilder()
                                        .withPdfCustodian(docFactory)
                                        .withListener(reporter)
                                        .withVerbosityLevel(VerbosityLevel.VERBOSE)
                                        .build();

                        ProcessingResult result = service.remediate();
                        return result;
                    }

                    @Override
                    protected void process(List<String> chunks) {}

                    @Override
                    protected void done() {
                        try {
                            ProcessingResult result = get();

                            if (result.totalIssuesResolved() == 0) {
                                reporter.onInfo("No changes made; output file not created");
                                tempResultFile = null;
                                saveButton.setEnabled(false);
                            } else {
                                tempResultFile = result.tempOutputFile().toFile();
                                saveButton.setEnabled(true);
                            }
                            // Report is always available after processing
                            saveReportButton.setEnabled(true);
                        } catch (Exception e) {
                            reporter.onError(e.getMessage());
                        }
                        processButton.setEnabled(true);
                    }
                };

        worker.execute();
    }

    private PrintStream createTextAreaPrintStream() {
        return new PrintStream(
                new OutputStream() {
                    @Override
                    public void write(int b) {
                        SwingUtilities.invokeLater(
                                () -> outputArea.append(String.valueOf((char) b)));
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        String text = new String(b, off, len);
                        SwingUtilities.invokeLater(() -> outputArea.append(text));
                    }
                },
                true);
    }

    private void showSaveDialog() {
        if (tempResultFile == null || !tempResultFile.exists()) {
            JOptionPane.showMessageDialog(this, "No processed file available to save.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(selectedFile.getParentFile());

        // Generate output path
        String filename = selectedFile.getName();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }
        String newname = filename.replaceFirst("(_auto)?_a11y?$", "") + "_autoa11y.pdf";
        chooser.setSelectedFile(new File(newname));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File targetFile = chooser.getSelectedFile();
            // Copy temp file to selected location
            if (targetFile.exists()) {
                int response =
                        JOptionPane.showConfirmDialog(
                                this,
                                "File exists. Overwrite?",
                                "Confirm Overwrite",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) {
                    // User chose not to overwrite
                    return;
                }
            }
            try {
                Files.copy(
                        tempResultFile.toPath(),
                        targetFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(
                        this, "File saved to: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        }
    }

    private void saveReport() {
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "No input file selected to derive report name.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(selectedFile.getParentFile());

        String filename = selectedFile.getName();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            filename = filename.substring(0, dotIndex);
        }
        String reportName = filename.replaceFirst("(_auto)?_a11y?$", "") + "_autoa11y.txt";
        chooser.setSelectedFile(new File(reportName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File targetFile = chooser.getSelectedFile();
            try {
                Files.writeString(
                        targetFile.toPath(), outputArea.getText(), StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(
                        this, "Report saved to: " + targetFile.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error saving report: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // macOS integration to make the app appear in task switcher
        System.setProperty("apple.awt.application.name", "PDF Auto A11y");
        try {
            Class<?> appClass = Class.forName("com.apple.eawt.Application");
            Object app = appClass.getMethod("getApplication").invoke(null);
            // Optionally set dock icon badge to null
            app.getClass().getMethod("setDockIconBadge", String.class).invoke(app, (String) null);
        } catch (Exception e) {
            // Not on macOS or library not available, ignore
        }

        SwingUtilities.invokeLater(
                () -> {
                    try {
                        UIManager.setLookAndFeel(UIManager.getLookAndFeel());
                    } catch (Exception e) {
                        // Use default look and feel
                    }
                    new PdfAutoA11yGUI();
                });
    }
}
