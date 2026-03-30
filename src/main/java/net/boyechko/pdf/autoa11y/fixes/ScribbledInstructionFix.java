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
package net.boyechko.pdf.autoa11y.fixes;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.DocContext;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.document.StructTree;
import net.boyechko.pdf.autoa11y.document.TreeDiagram.Node;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueLoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Carries out the instruction scribbled in the structure element's /T key. */
public class ScribbledInstructionFix implements IssueFix {
    private static final Logger logger = LoggerFactory.getLogger(ScribbledInstructionFix.class);

    private static final Pattern ADD_CHILD_PATTERN = Pattern.compile("!ADD_CHILD(?:REN)?\\s+(.+)");
    private static final Pattern ADD_PARENT_PATTERN = Pattern.compile("!ADD_PARENT\\s+(.+)");
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile("!ARTIFACT");

    private final String instruction;
    private final PdfStructElem element;

    public ScribbledInstructionFix(PdfStructElem element, String instruction) {
        this.element = element;
        this.instruction = instruction;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public void apply(DocContext ctx) throws Exception {
        Matcher addChild = ADD_CHILD_PATTERN.matcher(instruction);
        Matcher addParent = ADD_PARENT_PATTERN.matcher(instruction);
        Matcher artifact = ARTIFACT_PATTERN.matcher(instruction);

        if (addChild.matches()) {
            applyAddChild(ctx, addChild.group(1));
        } else if (addParent.matches()) {
            applyAddParent(ctx, addParent.group(1));
        } else if (artifact.matches()) {
            applyArtifact(ctx);
        } else {
            throw new IllegalArgumentException("Unsupported instruction: " + instruction);
        }
    }

    /** Parses the tag expression and adds the resulting structure as children of the element. */
    private void applyAddChild(DocContext ctx, String tagExpr) {
        List<Node<String>> nodes = Node.fromString(tagExpr);
        createStructElems(ctx.doc(), element, nodes);
        element.getPdfObject().remove(PdfName.T);
        StructTree.setScribble(element, "OK");
    }

    /** Delegates to MistaggedArtifactFix to convert the element's content to artifacts. */
    private void applyArtifact(DocContext ctx) throws Exception {
        new MistaggedArtifactFix(element).apply(ctx);
    }

    /** Parses the tag expression, wraps the element in the outermost parsed tag. */
    private void applyAddParent(DocContext ctx, String tagExpr) {
        PdfStructElem parent = (PdfStructElem) element.getParent();
        if (parent == null) {
            logger.warn("Cannot add parent: element has no parent");
            return;
        }

        List<Node<String>> nodes = Node.fromString(tagExpr);
        if (nodes.size() != 1) {
            throw new IllegalArgumentException(
                    "!ADD_PARENT requires exactly one wrapper tag, got: " + tagExpr);
        }
        String wrapperName = nodes.get(0).value();

        int index = StructTree.findKidIndex(parent, element);

        PdfStructElem wrapper = new PdfStructElem(ctx.doc(), new PdfName(wrapperName));
        parent.addKid(index, wrapper);

        parent.removeKid(element);
        wrapper.addKid(element);

        element.getPdfObject().remove(PdfName.T);
        StructTree.setScribble(element, "OK");
    }

    /** Creates PdfStructElem nodes from parsed role tree nodes and adds them to the parent. */
    private static void createStructElems(
            PdfDocument doc, PdfStructElem parent, List<Node<String>> nodes) {
        for (Node<String> node : nodes) {
            PdfStructElem elem = new PdfStructElem(doc, new PdfName(node.value()));
            parent.addKid(elem);
            if (!node.children().isEmpty()) {
                createStructElems(doc, elem, node.children());
            }
        }
    }

    @Override
    public String describe() {
        return "Carried out scribbled instruction '" + instruction + "'";
    }

    @Override
    public String describe(DocContext ctx) {
        return describe() + Format.loc(IssueLoc.atElem(ctx, element));
    }

    @Override
    public String groupLabel() {
        return "Scribbled instruction fixes";
    }
}
