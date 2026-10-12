// SPDX-FileCopyrightText: 2026 Richard Boyechko <code@boyechko.net>
// SPDX-License-Identifier: AGPL-3.0-or-later
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import com.itextpdf.kernel.pdf.tagging.PdfStructElem;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.document.Format;
import net.boyechko.pdf.autoa11y.fixes.MistaggedArtifactFix;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.Visit;
import org.yaml.snakeyaml.Yaml;

/** Visitor that detects tagged content that should be artifacts. */
public class MistaggedArtifactCheck extends StructTreeCheck {
    @Override
    public String name() {
        return "Mistagged Artifact Check";
    }

    @Override
    public String description() {
        return "Decorative or noisy content should be artifacted";
    }

    /** Classpath resource containing built-in text artifact patterns. */
    static final String DEFAULT_PATTERNS_RESOURCE = "/artifact_patterns.yaml";

    // Images above both thresholds are meaningful content images, not decorative
    static final float MEANINGFUL_MIN_WIDTH = 144f; // 2 inches
    static final float MEANINGFUL_MIN_HEIGHT = 72f; // 1 inch

    private static final Set<String> CHECKABLE_ROLES =
            Set.of("P", "Link", "Span", "Figure", "Lbl", "LBody");

    private final IssueList issues = new IssueList();
    private final List<TextArtifactRule> textArtifactRules;

    /** Loads built-in patterns from the classpath YAML resource. */
    public MistaggedArtifactCheck() {
        this(loadFromResource(DEFAULT_PATTERNS_RESOURCE));
    }

    /** Uses the given name-to-regex map as artifact patterns (replaces defaults). */
    public MistaggedArtifactCheck(Map<String, String> patterns) {
        this(rulesFromMap(patterns));
    }

    MistaggedArtifactCheck(List<TextArtifactRule> textArtifactRules) {
        this.textArtifactRules =
                textArtifactRules == null ? List.of() : List.copyOf(textArtifactRules);
    }

    @Override
    public boolean enterElement(Visit visit) {
        if (!CHECKABLE_ROLES.contains(visit.role())) {
            return true;
        }

        String textContent = getTextContent(visit);
        if (matchesTextArtifactPattern(textContent)) {
            IssueFix fix = new MistaggedArtifactFix(visit.node());
            issues.add(
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            visit.locus(),
                            "Tagged content should be artifact: \""
                                    + Format.truncate(textContent)
                                    + "\"",
                            fix));
            return false;
        }

        if (isSpaceOnlyLeaf(visit, textContent)) {
            issues.add(
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            visit.locus(),
                            "Space-only element should be artifact",
                            new MistaggedArtifactFix(visit.node())));
            return false;
        }

        detectDecorativeMcrs(visit);
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
    }

    /**
     * Detects leaf elements whose only children are MCRs containing nothing but whitespace glyphs.
     * These are typically InDesign typographic spacers that got tagged instead of artifacted.
     */
    private boolean isSpaceOnlyLeaf(Visit visit, String textContent) {
        if (textContent != null && !textContent.isBlank()) {
            return false;
        }

        List<IStructureNode> kids = visit.node().getKids();
        if (kids == null || kids.isEmpty()) {
            return false;
        }

        boolean hasMcr = false;
        for (IStructureNode kid : kids) {
            if (kid instanceof PdfStructElem || kid instanceof PdfObjRef) {
                return false;
            }
            if (kid instanceof PdfMcr) {
                hasMcr = true;
            }
        }
        if (!hasMcr) {
            return false;
        }

        // Verify no MCR has meaningful visual content (image, path, or non-whitespace text)
        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfMcr mcr)) {
                continue;
            }
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }
            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                continue;
            }
            int pageNum = visit.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }
            Content.McidContent mc = visit.docCtx().getMcidContent(pageNum).get(mcid);
            if (mc != null && !mc.kinds().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesTextArtifactPattern(String textContent) {
        if (textContent == null || textContent.isEmpty()) {
            return false;
        }
        for (TextArtifactRule rule : textArtifactRules) {
            if (rule.pattern().matcher(textContent).find()) {
                return true;
            }
        }
        return false;
    }

    /** Detects individual decorative MCRs (images/paths without text, below size thresholds). */
    private void detectDecorativeMcrs(Visit visit) {
        boolean isFigure = PdfName.Figure.equals(visit.node().getRole());
        if (isFigure && visit.node().getAlt() != null) {
            return;
        }

        List<IStructureNode> kids = visit.node().getKids();
        if (kids == null) {
            return;
        }

        for (IStructureNode kid : kids) {
            if (!(kid instanceof PdfMcr mcr) || kid instanceof PdfObjRef) {
                continue;
            }
            int mcid = mcr.getMcid();
            if (mcid < 0) {
                continue;
            }

            PdfDictionary pageDict = mcr.getPageObject();
            if (pageDict == null) {
                continue;
            }
            int pageNum = visit.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }

            String mcidText = visit.docCtx().getMcidText(pageNum, mcid);
            if (mcidText != null && !mcidText.isBlank()) {
                continue;
            }

            Content.McidContent mc = visit.docCtx().getMcidContent(pageNum).get(mcid);
            if (mc == null
                    || mc.kinds().contains(Content.ContentKind.TEXT)
                    || (!mc.kinds().contains(Content.ContentKind.IMAGE)
                            && !mc.kinds().contains(Content.ContentKind.PATH))) {
                continue;
            }

            Map<Integer, Rectangle> boundsByMcid =
                    visit.docCtx()
                            .getOrComputeMcidBounds(
                                    pageNum,
                                    () ->
                                            Content.extractBoundsForPage(
                                                    visit.doc().getPage(pageNum)));
            Rectangle bounds = boundsByMcid.get(mcid);
            if (bounds != null && isMeaningfulSize(bounds)) {
                continue;
            }

            PdfPage page = visit.doc().getPage(pageNum);
            Map<PdfPage, Set<Integer>> targetMcids = new LinkedHashMap<>();
            targetMcids.computeIfAbsent(page, k -> new LinkedHashSet<>()).add(mcid);
            IssueFix fix = new MistaggedArtifactFix(visit.node(), targetMcids);
            issues.add(
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            visit.locus(),
                            "Decorative graphic MCR should be artifact",
                            fix));
        }
    }

    public static boolean isMeaningfulSize(Rectangle bounds) {
        float width = Math.abs(bounds.getWidth());
        float height = Math.abs(bounds.getHeight());
        return width > MEANINGFUL_MIN_WIDTH && height > MEANINGFUL_MIN_HEIGHT;
    }

    private String getTextContent(Visit visit) {
        int pageNumber = visit.page();
        if (pageNumber == 0) {
            return "";
        }
        return Content.getTextForElement(visit.node(), visit.docCtx(), pageNumber);
    }

    @SuppressWarnings("unchecked")
    private static List<TextArtifactRule> loadFromResource(String resourcePath) {
        try (InputStream in = MistaggedArtifactCheck.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            Map<String, String> data = new Yaml().load(in);
            if (data == null) {
                return List.of();
            }
            return rulesFromMap(data);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to load artifact patterns from " + resourcePath + ": " + e.getMessage(),
                    e);
        }
    }

    private static List<TextArtifactRule> rulesFromMap(Map<String, String> patterns) {
        List<TextArtifactRule> rules = new ArrayList<>();
        for (Map.Entry<String, String> entry : patterns.entrySet()) {
            rules.add(textRule(entry.getKey(), entry.getValue()));
        }
        return rules;
    }

    static TextArtifactRule textRule(String name, String regex) {
        return new TextArtifactRule(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    record TextArtifactRule(String name, Pattern pattern) {
        TextArtifactRule {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            if (pattern == null) {
                throw new IllegalArgumentException("pattern must not be null");
            }
        }
    }
}
