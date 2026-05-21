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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests SafeOutput's stage-then-rename contract and rollback semantics. */
public class SafeOutputTest {

    @Test
    void commitMovesWorkingFileOntoFinalPath(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");
        Path workingPath;

        try (SafeOutput out = SafeOutput.at(finalPath)) {
            workingPath = out.workingPath();
            Files.writeString(out.workingPath(), "new bytes");
            assertNotEquals(finalPath, out.workingPath(), "working path must be distinct");
            out.commit();
        }

        assertEquals("new bytes", Files.readString(finalPath));
        assertFalse(Files.exists(workingPath), "working file should be gone after commit");
    }

    @Test
    void commitReplacesExistingFinalFile(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");
        Files.writeString(finalPath, "OLD");

        try (SafeOutput out = SafeOutput.at(finalPath)) {
            Files.writeString(out.workingPath(), "NEW");
            out.commit();
        }

        assertEquals("NEW", Files.readString(finalPath));
    }

    @Test
    void closeWithoutCommitDeletesWorkingFile(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");
        Path workingPath;

        try (SafeOutput out = SafeOutput.at(finalPath)) {
            workingPath = out.workingPath();
            Files.writeString(out.workingPath(), "partial");
            // no commit
        }

        assertFalse(Files.exists(workingPath), "working file should be cleaned up");
        assertFalse(Files.exists(finalPath), "final path should never have appeared");
    }

    @Test
    void writerFailureLeavesExistingFinalFileIntact(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");
        Files.writeString(finalPath, "ORIGINAL");
        Path[] workingHolder = new Path[1];

        IOException thrown =
                assertThrows(
                        IOException.class,
                        () -> {
                            try (SafeOutput out = SafeOutput.at(finalPath)) {
                                workingHolder[0] = out.workingPath();
                                Files.writeString(out.workingPath(), "TORN");
                                throw new IOException("simulated writer failure");
                            }
                        });
        assertEquals("simulated writer failure", thrown.getMessage());

        assertEquals(
                "ORIGINAL",
                Files.readString(finalPath),
                "existing output must not be touched when writer fails");
        assertFalse(Files.exists(workingHolder[0]), "working file should be cleaned up on failure");
    }

    @Test
    void atCreatesMissingParentDirectories(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("nested/deeper/out.pdf");

        try (SafeOutput out = SafeOutput.at(finalPath)) {
            Files.writeString(out.workingPath(), "ok");
            out.commit();
        }

        assertEquals("ok", Files.readString(finalPath));
    }

    @Test
    void atClearsStaleTempFromPriorRun(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");

        // Discover where SafeOutput stages its working file, then plant stale data there
        // to simulate a prior crashed run.
        Path workingPath;
        try (SafeOutput probe = SafeOutput.at(finalPath)) {
            workingPath = probe.workingPath();
        }
        Files.writeString(workingPath, "stale garbage from a crashed run");
        assertTrue(Files.exists(workingPath), "precondition: stale file is present");

        try (SafeOutput out = SafeOutput.at(finalPath)) {
            assertFalse(
                    Files.exists(out.workingPath()),
                    "stale working file should be cleared by at()");
            Files.writeString(out.workingPath(), "fresh");
            out.commit();
        }

        assertEquals("fresh", Files.readString(finalPath));
    }

    @Test
    void closeAfterCommitIsNoOp(@TempDir Path tmp) throws IOException {
        Path finalPath = tmp.resolve("out.pdf");

        SafeOutput out = SafeOutput.at(finalPath);
        Files.writeString(out.workingPath(), "committed");
        out.commit();
        out.close(); // close() after commit() must not delete the now-final file

        assertEquals("committed", Files.readString(finalPath));
    }
}
