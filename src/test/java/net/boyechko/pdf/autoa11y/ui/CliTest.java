// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
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
    void treeQueriesAcceptEitherOptionSyntaxAndDoNotCreateAnOutputPdf() throws Cli.CLIException {
        for (String[] arguments :
                new String[][] {{"--query-tree=//L", input}, {"--query-tree", "//L", input}}) {
            CLIConfig config = Cli.parseArguments(arguments);

            assertEquals("//L", config.treeQuery());
            assertNull(config.outputPath());
        }
    }

    @Test
    void treeQueryWithoutAFormatDefaultsToTheBoxDiagram() throws Cli.CLIException {
        CLIConfig config = Cli.parseArguments(new String[] {"--query-tree=//L", input});

        assertEquals(Cli.TreeFormat.BOX, config.treeFormat());
    }

    @Test
    void treeQueryKeepsAnExplicitlyChosenFormatInEitherArgumentOrder() throws Cli.CLIException {
        for (String[] arguments :
                new String[][] {
                    {"--dump-tree=roles", "--query-tree=//L", input},
                    {"--query-tree=//L", "--dump-tree=roles", input}
                }) {
            assertEquals(Cli.TreeFormat.ROLES, Cli.parseArguments(arguments).treeFormat());
        }
    }

    @Test
    void rolesIsATreeFormatThatNeedsNoOutputPdf() throws Cli.CLIException {
        CLIConfig config = Cli.parseArguments(new String[] {"--dump-tree=roles", input});

        assertEquals(Cli.TreeFormat.ROLES, config.treeFormat());
        assertNull(config.outputPath());
    }

    @Test
    void bareDumpTreeMeansTheBoxDiagram() throws Cli.CLIException {
        CLIConfig config = Cli.parseArguments(new String[] {"--dump-tree", input});

        assertEquals(Cli.TreeFormat.BOX, config.treeFormat());
    }

    @Test
    void remediationRunHasNoTreeFormat() throws Cli.CLIException {
        assertNull(Cli.parseArguments(new String[] {input, output}).treeFormat());
    }

    @Test
    void conflictingTreeFormatsAreRejected() {
        for (String[] arguments :
                new String[][] {
                    {"--dump-tree=xml", "--dump-tree=plain", input},
                    {"--dump-tree", "--dump-tree=roles", input}
                }) {
            assertThrows(Cli.CLIException.class, () -> Cli.parseArguments(arguments));
        }
    }

    @Test
    void unknownOptionsAreRejectedRatherThanTakenAsFiles() {
        assertThrows(
                Cli.CLIException.class,
                () -> Cli.parseArguments(new String[] {"--dump-roles", input}));
    }

    @Test
    void missingOrBlankTreeQueriesAreRejected() {
        assertThrows(
                Cli.CLIException.class, () -> Cli.parseArguments(new String[] {"--query-tree"}));
        assertThrows(
                Cli.CLIException.class,
                () -> Cli.parseArguments(new String[] {"--query-tree= ", input}));
    }

    @Test
    void queriesAnXmlExportAndPrintsTheWholeMatchingList() throws IOException {
        Path xml = tempDir.resolve("input.tree.xml");
        Files.writeString(
                xml,
                """
                <StructureTree version="1" root="element">
                  <Document role="Document" id="1" page="1">
                    <Art role="Art" id="4291" page="1">
                      <L role="L" id="10" page="1">
                        <LI role="LI" id="11" page="1">
                          <LBody role="LBody" id="12" page="1">
                            <H3 role="H3" id="13" page="1">
                              <Content page="1" mcid="0" kinds="text">Program overview</Content>
                            </H3>
                          </LBody>
                        </LI>
                        <LI role="LI" id="14" page="2">
                          <LBody role="LBody" id="15" page="2">
                            <P role="P" id="16" page="2">
                              <Content page="2" mcid="0" kinds="text">Following details</Content>
                            </P>
                          </LBody>
                        </LI>
                      </L>
                    </Art>
                    <L role="L" id="30"/>
                  </Document>
                </StructureTree>
                """);
        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Cli.main(
                    new String[] {
                        "--dump-tree=plain",
                        "--query-tree",
                        "//Art[@id='4291']//L[LI/LBody/H3]",
                        xml.toString()
                    });
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("L #10"));
        assertTrue(result.contains("Program overview"));
        assertTrue(result.contains("Page 2"));
        assertTrue(result.contains("Following details"));
        assertFalse(result.contains("L #30"));
        assertFalse(result.contains("Art #4291"));
    }

    @Test
    void rolesQueryPrintsOnlyTheRoleNamesOfTheMatchingSubtree() {
        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Cli.main(
                    new String[] {
                        "--dump-tree=roles",
                        "--query-tree=//L",
                        "src/test/resources/tagged_baseline.pdf"
                    });
        } finally {
            System.setOut(originalOut);
        }

        var lines = output.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals("L", lines.getFirst());
        assertTrue(lines.contains("  LI"));
        assertTrue(lines.stream().noneMatch(line -> line.contains("#")));
        assertFalse(lines.contains("Document"));
    }

    @Test
    void queriesAPdfDirectlyAndPrintsOnlyTheMatchingSubtree() {
        var output = new ByteArrayOutputStream();
        var originalOut = System.out;
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Cli.main(
                    new String[] {
                        "--dump-tree=plain",
                        "--query-tree",
                        "//L",
                        "src/test/resources/tagged_baseline.pdf"
                    });
        } finally {
            System.setOut(originalOut);
        }

        String result = output.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("L #61"));
        assertTrue(result.contains("LI #63"));
        assertTrue(result.contains("LBody #68"));
        assertFalse(result.contains("Document #55"));
        assertFalse(result.contains("Table #69"));
    }

    @Test
    void xmlTreeExportIsReadOnly() throws Cli.CLIException {
        CLIConfig config = Cli.parseArguments(new String[] {"--dump-tree=xml", input});

        assertEquals(Cli.TreeFormat.XML, config.treeFormat());
        assertNull(config.outputPath());
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
