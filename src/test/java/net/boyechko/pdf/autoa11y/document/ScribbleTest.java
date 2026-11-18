// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.document;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.boyechko.pdf.autoa11y.document.DocValue.Scribble;
import org.junit.jupiter.api.Test;

class ScribbleTest {

    // == Parsing ===========================================================

    @Test
    void emptyScribbleHasNoSegments() {
        assertTrue(Scribble.EMPTY.isEmpty());
        assertEquals(List.of(), Scribble.EMPTY.segments());
        assertFalse(Scribble.EMPTY.toolAuthored());
    }

    @Test
    void rawValueWithoutPrefixIsNotAScribble() {
        assertEquals(Scribble.EMPTY, Scribble.fromRaw("Chapter 1"));
        assertEquals(Scribble.EMPTY, Scribble.fromRaw(null));
    }

    @Test
    void prefixWithNothingAfterItIsNotAScribble() {
        assertEquals(Scribble.EMPTY, Scribble.fromRaw(Scribble.PREFIX));
    }

    @Test
    void prefixIsStrippedOnlyFromTheStart() {
        Scribble scribble = Scribble.fromRaw(Scribble.PREFIX + "see Chapter__1");

        assertEquals("see Chapter__1", scribble.body());
        assertEquals(Scribble.PREFIX + "see Chapter__1", scribble.rawValue());
    }

    @Test
    void toolAuthoredScribbleStripsMarkFromBody() {
        Scribble scribble = Scribble.tool("!SET_ROLE H1");

        assertTrue(scribble.toolAuthored());
        assertEquals("!SET_ROLE H1", scribble.body());
        assertEquals(Scribble.PREFIX + Scribble.TOOL_MARK + "!SET_ROLE H1", scribble.rawValue());
    }

    @Test
    void userAuthoredScribbleHasNoToolMark() {
        Scribble scribble = Scribble.user("!SET_ROLE H1");

        assertFalse(scribble.toolAuthored());
        assertEquals("!SET_ROLE H1", scribble.body());
    }

    @Test
    void segmentsSplitBodyAndExcludeToolMark() {
        Scribble scribble = Scribble.tool("?H3 (expected H2)" + Scribble.SEPARATOR + "23.75pt");

        assertTrue(scribble.toolAuthored());
        assertEquals(List.of("?H3 (expected H2)", "23.75pt"), scribble.segments());
    }

    // == Verification ======================================================

    @Test
    void okScribbleIsVerified() {
        assertFalse(Scribble.EMPTY.isVerified(), "no scribble means not verified");
        assertTrue(Scribble.user(Scribble.VERIFIED_TOKEN).isVerified());
    }

    @Test
    void okWithTrailingNoteIsNotVerified() {
        assertFalse(
                Scribble.user("OK reviewed 2026-07-09").isVerified(),
                "notes belong in their own segment");
    }

    @Test
    void okInLaterSegmentIsVerified() {
        assertTrue(Scribble.user("check spacing" + Scribble.SEPARATOR + "OK").isVerified());
    }

    @Test
    void toolAuthoredOkIsNotVerified() {
        assertFalse(Scribble.tool("OK").isVerified(), "verification is a user-only mark");
    }

    @Test
    void okAsWordPrefixIsNotVerified() {
        assertFalse(Scribble.user("OKAY but check figures").isVerified());
    }

    // == Instructions ======================================================

    @Test
    void instructionIsTheFirstSegmentCarryingTheMark() {
        Scribble scribble =
                Scribble.user(
                        "reviewed" + Scribble.SEPARATOR + "!MERGE" + Scribble.SEPARATOR + "!HOIST");

        assertEquals("!MERGE", scribble.instruction());
    }

    @Test
    void toolAuthoredInstructionStillCounts() {
        assertEquals("!SET_ROLE H2", Scribble.tool("!SET_ROLE H2").instruction());
    }

    @Test
    void scribbleWithoutInstructionSegmentHasNoInstruction() {
        assertNull(Scribble.user("check figures").instruction());
        assertNull(Scribble.EMPTY.instruction());
    }

    // == Withers ===========================================================

    @Test
    void withOnEmptyScribbleIsToolAuthored() {
        Scribble scribble = Scribble.EMPTY.with("MERGE lists");

        assertTrue(scribble.toolAuthored());
        assertEquals(List.of("MERGE lists"), scribble.segments());
    }

    @Test
    void withKeepsUserAuthorshipOfExistingScribble() {
        Scribble scribble = Scribble.user("reviewed by hand").with("MERGE lists");

        assertFalse(scribble.toolAuthored(), "the user's authorship survives a tool append");
        assertEquals(List.of("reviewed by hand", "MERGE lists"), scribble.segments());
    }

    @Test
    void withDoesNotRepeatAnIdenticalSegment() {
        Scribble once = Scribble.EMPTY.with("MERGE lists");

        assertEquals(once, once.with("MERGE lists"));
    }

    @Test
    void withLeavesTheReceiverUnchanged() {
        Scribble original = Scribble.tool("ITEMS 2");
        original.with("SPLIT");

        assertEquals(List.of("ITEMS 2"), original.segments());
    }

    @Test
    void withoutDropsSegmentsByHeadOnly() {
        Scribble scribble =
                Scribble.tool("SPLIT sublist" + Scribble.SEPARATOR + "ITEMS 2").without("ITEMS");

        assertEquals(List.of("SPLIT sublist"), scribble.segments());
        assertTrue(scribble.toolAuthored());
    }

    @Test
    void withoutMatchesHeadsIgnoringCase() {
        Scribble scribble =
                Scribble.tool("SPLIT sublist" + Scribble.SEPARATOR + "ITEMS 2").without("items");

        assertEquals(List.of("SPLIT sublist"), scribble.segments());
    }

    @Test
    void withoutDoesNotMatchAHeadPrefix() {
        Scribble scribble = Scribble.tool("ITEMSET 2").without("ITEMS");

        assertEquals(List.of("ITEMSET 2"), scribble.segments());
    }

    @Test
    void withoutEverySegmentIsEmpty() {
        assertEquals(Scribble.EMPTY, Scribble.tool("ITEMS 2").without("ITEMS"));
    }

    @Test
    void withoutUnknownHeadIsEqualToTheOriginal() {
        Scribble original = Scribble.user("check figures");

        assertEquals(original, original.without("ITEMS"));
    }

    // == Object number =====================================================

    @Test
    void objNumOnEmptyScribbleIsToolAuthoredAndAlone() {
        Scribble scribble = Scribble.EMPTY.withObjNum(65848);

        assertTrue(scribble.toolAuthored());
        assertEquals(List.of("65848"), scribble.segments());
    }

    @Test
    void objNumLeadsTheSegmentsAlreadyThere() {
        Scribble scribble = Scribble.tool("MERGE lists" + Scribble.SEPARATOR + "items 2");

        assertEquals(
                List.of("65848", "MERGE lists", "items 2"), scribble.withObjNum(65848).segments());
    }

    @Test
    void objNumReplacesAStaleLeadingNumber() {
        Scribble scribble = Scribble.tool("31" + Scribble.SEPARATOR + "items 2");

        assertEquals(List.of("65848", "items 2"), scribble.withObjNum(65848).segments());
    }

    @Test
    void objNumIsAddedOnceWhenAppliedTwice() {
        Scribble once = Scribble.tool("items 2").withObjNum(65848);

        assertEquals(once, once.withObjNum(65848));
    }

    @Test
    void objNumKeepsAUserScribbleUserAuthored() {
        Scribble scribble = Scribble.user("STOPPED").withObjNum(52);

        assertFalse(scribble.toolAuthored());
        assertEquals(List.of("52", "STOPPED"), scribble.segments());
    }

    @Test
    void numberLaterInTheScribbleIsNotTakenForTheObjNum() {
        Scribble scribble = Scribble.user("STOPPED" + Scribble.SEPARATOR + "31");

        assertEquals(List.of("52", "STOPPED", "31"), scribble.withObjNum(52).segments());
    }

    @Test
    void missingObjNumLeavesTheScribbleAsItIs() {
        Scribble scribble = Scribble.tool("items 2");

        assertEquals(scribble, scribble.withObjNum(-1));
    }

    // == Rendering =========================================================

    @Test
    void emptyScribbleRendersAsNothing() {
        assertEquals("", Scribble.EMPTY.toString());
    }

    @Test
    void scribbleRendersQuotedWithPrefix() {
        assertEquals("\"__:MERGE lists\"", Scribble.tool("MERGE lists").toString());
    }
}
