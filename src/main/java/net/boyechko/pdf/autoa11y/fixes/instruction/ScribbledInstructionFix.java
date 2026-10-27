// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.fixes.instruction;

import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.Locus;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.issue.IssueFix;

/** Carries out the instruction scribbled in the structure element's /T key. */
public class ScribbledInstructionFix implements IssueFix {

    /**
     * Scribble segment head identifying this fix's own /T output (e.g. "INST SET_ROLE P -> H2").
     * Segments with this head are cleared before each run so receipts name the last run's work
     * only.
     */
    public static final String INSTRUCTION_TAG = "INST";

    /** The scribbled syntax of each instruction, paired with how to build it from a match. */
    private static final List<Syntax> SYNTAXES =
            List.of(
                    new Syntax(
                            "!ADD_CHILD(?:REN)?\\s+(.+)", m -> new AddChildInstruction(m.group(1))),
                    new Syntax("!ADD_PARENTS?\\s+(.+)", m -> new AddParentInstruction(m.group(1))),
                    new Syntax("!ARTIFACT", m -> new ArtifactInstruction()),
                    new Syntax("!BREAK_OUT", m -> new BreakOutInstruction()),
                    new Syntax("!FLATTEN", m -> new FlattenInstruction()),
                    new Syntax("!MERGE", m -> new MergeInstruction()),
                    new Syntax("!REORDER_KIDS", m -> new ReorderKidsInstruction()),
                    new Syntax("!SET_ROLE\\s+(\\S+)", m -> new SetRoleInstruction(m.group(1))),
                    new Syntax(
                            "!SPLIT_LINES(?:\\s+([\\d,x]+))?",
                            m -> new SplitLinesInstruction(m.group(1))),
                    new Syntax("!UNLINK", m -> new UnlinkInstruction()));

    private final String instruction;
    private final PdfStructElem element;

    public ScribbledInstructionFix(PdfStructElem element, String instruction) {
        this.element = element;
        this.instruction = instruction;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        ScribbledInstruction parsed = parse(instruction);
        if (parsed == null) {
            throw new IllegalArgumentException("Unsupported instruction: " + instruction);
        }
        // setToolScribble replaces /T wholesale, so the scribbled instruction is cleared here.
        String receipt = parsed.apply(ctx, element);
        if (receipt != null) {
            StructTree.setToolScribble(element, receipt);
        }
    }

    /** Returns true if the instruction operates on the entire subtree, not just the element. */
    public static boolean isSubtreeInstruction(String instruction) {
        ScribbledInstruction parsed = parse(instruction);
        return parsed != null && parsed.coversSubtree();
    }

    /** Returns the instruction the scribble segment spells out, or null when it names none. */
    private static ScribbledInstruction parse(String instruction) {
        for (Syntax syntax : SYNTAXES) {
            Matcher m = syntax.pattern().matcher(instruction);
            if (m.matches()) {
                return syntax.build().apply(m);
            }
        }
        return null;
    }

    private record Syntax(Pattern pattern, Function<Matcher, ScribbledInstruction> build) {
        Syntax(String regex, Function<Matcher, ScribbledInstruction> build) {
            this(Pattern.compile(regex), build);
        }
    }

    @Override
    public String describe() {
        return "Carried out scribbled instruction '" + instruction + "'";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(Locus.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "Scribbled instruction fixes";
    }
}
