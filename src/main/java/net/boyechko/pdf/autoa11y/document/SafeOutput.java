/*
 * PDF-Auto-A11y - Automated PDF Accessibility Remediation
 * Copyright (C) 2026 Richard Boyechko
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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Stages output to a sibling temp file and atomically renames it on commit. Lets callers safely
 * pass the same path for input and output, and ensures partial writes never replace an existing
 * output file.
 */
public final class SafeOutput implements AutoCloseable {
    private static final String TEMP_SUFFIX = ".autoa11y-tmp";

    private final Path finalOutput;
    private final Path workingPath;
    private boolean committed;

    private SafeOutput(Path finalOutput, Path workingPath) {
        this.finalOutput = finalOutput;
        this.workingPath = workingPath;
    }

    /** Creates the parent directory if needed and clears any stale temp from a prior run. */
    public static SafeOutput at(Path finalOutput) throws IOException {
        Path parent = finalOutput.getParent();
        if (parent == null) {
            parent = Paths.get(".");
        }
        Files.createDirectories(parent);
        Path tmp = parent.resolve(finalOutput.getFileName() + TEMP_SUFFIX);
        Files.deleteIfExists(tmp);
        return new SafeOutput(finalOutput, tmp);
    }

    /** Path callers should write to. */
    public Path workingPath() {
        return workingPath;
    }

    /** Atomically renames the working file onto the final output path. */
    public void commit() throws IOException {
        try {
            Files.move(
                    workingPath,
                    finalOutput,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(workingPath, finalOutput, StandardCopyOption.REPLACE_EXISTING);
        }
        committed = true;
    }

    /** Deletes the working file unless commit() already moved it into place. */
    @Override
    public void close() {
        if (!committed) {
            try {
                Files.deleteIfExists(workingPath);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
