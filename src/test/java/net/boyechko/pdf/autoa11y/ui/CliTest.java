// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.boyechko.pdf.autoa11y.ui.Cli.CLIConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @TempDir Path tempDir;
    private String input;
    private String output;

    @BeforeEach
    void createInputFile() throws IOException {
        input = Files.createFile(tempDir.resolve("in.pdf")).toString();
        output = tempDir.resolve("out.pdf").toString();
    }

    @Test
    void checkSuffixIsOptional() throws Cli.CLIException {
        CLIConfig config =
                Cli.parseArguments(
                        new String[] {"--only-checks=ScribbledInstruction", input, output});

        assertEquals(Set.of("ScribbledInstructionCheck"), config.onlyChecks());
    }

    @Test
    void fullCheckNameIsStillAccepted() throws Cli.CLIException {
        CLIConfig config =
                Cli.parseArguments(
                        new String[] {"--only-checks=ScribbledInstructionCheck", input, output});

        assertEquals(Set.of("ScribbledInstructionCheck"), config.onlyChecks());
    }

    @Test
    void suffixIsSuppliedForEveryNameInTheList() throws Cli.CLIException {
        CLIConfig config =
                Cli.parseArguments(
                        new String[] {
                            "--skip-checks", "NeedlessNesting,MissingPagePartsCheck", input, output
                        });

        assertEquals(Set.of("NeedlessNestingCheck", "MissingPagePartsCheck"), config.skipChecks());
    }

    @Test
    void unknownCheckNameIsRejectedAsTyped() {
        Cli.CLIException e =
                assertThrows(
                        Cli.CLIException.class,
                        () ->
                                Cli.parseArguments(
                                        new String[] {
                                            "--only-checks=MistagedList", input, output
                                        }));

        assertTrue(e.getMessage().contains("MistagedList"), e.getMessage());
    }

    @Test
    void checkListNamesBothDefaultAndOptionalChecks() {
        String listing = Cli.checkListMessage();

        assertTrue(listing.contains("MistaggedListCheck"), listing);
        assertTrue(listing.contains("WrapWebCapturesCheck"), listing);
    }

    @Test
    void nameEndingInCheckIsNotDoubleSuffixed() throws Cli.CLIException {
        CLIConfig config =
                Cli.parseArguments(
                        new String[] {"--include-checks=WrapWebCapturesCheck", input, output});

        assertEquals(Set.of("WrapWebCapturesCheck"), config.includeChecks());
    }
}
