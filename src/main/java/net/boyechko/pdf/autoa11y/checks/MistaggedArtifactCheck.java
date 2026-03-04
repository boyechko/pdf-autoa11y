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
package net.boyechko.pdf.autoa11y.checks;

import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.tagging.IStructureNode;
import com.itextpdf.kernel.pdf.tagging.PdfMcr;
import com.itextpdf.kernel.pdf.tagging.PdfObjRef;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.boyechko.pdf.autoa11y.document.Content;
import net.boyechko.pdf.autoa11y.fixes.ConvertToArtifact;
import net.boyechko.pdf.autoa11y.issue.Issue;
import net.boyechko.pdf.autoa11y.issue.IssueFix;
import net.boyechko.pdf.autoa11y.issue.IssueList;
import net.boyechko.pdf.autoa11y.issue.IssueSev;
import net.boyechko.pdf.autoa11y.issue.IssueType;
import net.boyechko.pdf.autoa11y.validation.StructTreeCheck;
import net.boyechko.pdf.autoa11y.validation.StructTreeContext;

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
    static final String DEFAULT_PATTERNS_RESOURCE = "/artifact_patterns.txt";

    /**
     * Optional system property for loading extra text artifact patterns from a file.
     *
     * <p>Expected file format: one rule per line as either {@code name=regex} or bare {@code
     * regex}. Empty lines and lines starting with {@code #} are ignored.
     */
    public static final String TEXT_ARTIFACT_PATTERN_FILE_PROPERTY =
            "autoa11y.mistaggedArtifact.patternFile";

    // Images above both thresholds are meaningful content images, not decorative
    static final float MEANINGFUL_MIN_WIDTH = 144f; // 2 inches
    static final float MEANINGFUL_MIN_HEIGHT = 72f; // 1 inch

    private static final Set<String> CHECKABLE_ROLES =
            Set.of("P", "Link", "Span", "Figure", "Lbl", "LBody");

    private final IssueList issues = new IssueList();
    private final List<TextArtifactRule> textArtifactRules;

    /** Loads built-in patterns from classpath, plus optional extras from system property. */
    public MistaggedArtifactCheck() {
        this(loadDefaultPlusConfigured());
    }

    /** Uses extra rules from the given pattern file (no built-in defaults). */
    public MistaggedArtifactCheck(Path textArtifactPatternFile) {
        this(loadTextArtifactRules(textArtifactPatternFile));
    }

    MistaggedArtifactCheck(List<TextArtifactRule> textArtifactRules) {
        this.textArtifactRules =
                textArtifactRules == null ? List.of() : List.copyOf(textArtifactRules);
    }

    @Override
    public boolean enterElement(StructTreeContext ctx) {
        if (!CHECKABLE_ROLES.contains(ctx.role())) {
            return true;
        }

        String textContent = getTextContent(ctx);
        if (matchesTextArtifactPattern(textContent)) {
            IssueFix fix = new ConvertToArtifact(ctx.node());
            String truncated =
                    textContent.length() > 40 ? textContent.substring(0, 39) + "…" : textContent;
            issues.add(
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Tagged content should be artifact: \"" + truncated + "\"",
                            fix));
            return false;
        }

        detectDecorativeMcrs(ctx);
        return true;
    }

    @Override
    public IssueList getIssues() {
        return issues;
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
    private void detectDecorativeMcrs(StructTreeContext ctx) {
        boolean isFigure = PdfName.Figure.equals(ctx.node().getRole());
        if (isFigure && ctx.node().getAlt() != null) {
            return;
        }

        List<IStructureNode> kids = ctx.node().getKids();
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
            int pageNum = ctx.doc().getPageNumber(pageDict);
            if (pageNum <= 0) {
                continue;
            }

            String mcidText = ctx.docCtx().getMcidText(pageNum, mcid);
            if (mcidText != null && !mcidText.isBlank()) {
                continue;
            }

            Map<Integer, Set<Content.ContentKind>> contentKinds =
                    ctx.docCtx()
                            .getOrComputeContentKinds(
                                    pageNum,
                                    () ->
                                            Content.extractContentKindsForPage(
                                                    ctx.doc().getPage(pageNum)));
            Set<Content.ContentKind> kinds = contentKinds.get(mcid);
            if (kinds == null
                    || kinds.contains(Content.ContentKind.TEXT)
                    || (!kinds.contains(Content.ContentKind.IMAGE)
                            && !kinds.contains(Content.ContentKind.PATH))) {
                continue;
            }

            Map<Integer, Rectangle> boundsByMcid =
                    ctx.docCtx()
                            .getOrComputeMcidBounds(
                                    pageNum,
                                    () -> Content.extractBoundsForPage(ctx.doc().getPage(pageNum)));
            Rectangle bounds = boundsByMcid.get(mcid);
            if (bounds != null && isMeaningfulSize(bounds)) {
                continue;
            }

            PdfPage page = ctx.doc().getPage(pageNum);
            Map<PdfPage, Set<Integer>> targetMcids = new LinkedHashMap<>();
            targetMcids.computeIfAbsent(page, k -> new LinkedHashSet<>()).add(mcid);
            IssueFix fix = new ConvertToArtifact(ctx.node(), targetMcids);
            issues.add(
                    new Issue(
                            IssueType.MISTAGGED_ARTIFACT,
                            IssueSev.WARNING,
                            locAtElem(ctx),
                            "Decorative graphic MCR should be artifact",
                            fix));
        }
    }

    public static boolean isMeaningfulSize(Rectangle bounds) {
        float width = Math.abs(bounds.getWidth());
        float height = Math.abs(bounds.getHeight());
        return width > MEANINGFUL_MIN_WIDTH && height > MEANINGFUL_MIN_HEIGHT;
    }

    private String getTextContent(StructTreeContext ctx) {
        int pageNumber = ctx.getPageNumber();
        if (pageNumber == 0) {
            return "";
        }
        return Content.getTextForElement(ctx.node(), ctx.docCtx(), pageNumber);
    }

    private static List<TextArtifactRule> loadTextArtifactRules(Path patternFile) {
        if (patternFile == null) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(patternFile);
            ArrayList<TextArtifactRule> loaded = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String name = "line-" + (i + 1);
                String regex = line;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    name = line.substring(0, eq).trim();
                    regex = line.substring(eq + 1).trim();
                }
                if (name.isEmpty() || regex.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Invalid text artifact rule at " + patternFile + ":" + (i + 1));
                }
                loaded.add(textRule(name, regex));
            }
            return loaded;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to load text artifact rules from "
                            + patternFile
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static List<TextArtifactRule> loadDefaultPlusConfigured() {
        List<TextArtifactRule> rules =
                new ArrayList<>(loadTextArtifactRulesFromResource(DEFAULT_PATTERNS_RESOURCE));
        Path extraFile = configuredPatternFile();
        if (extraFile != null) {
            rules.addAll(loadTextArtifactRules(extraFile));
        }
        return rules;
    }

    private static List<TextArtifactRule> loadTextArtifactRulesFromResource(String resourcePath) {
        try (InputStream in = MistaggedArtifactCheck.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + resourcePath);
            }
            List<String> lines =
                    new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                            .lines()
                            .toList();
            ArrayList<TextArtifactRule> loaded = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String name = "line-" + (i + 1);
                String regex = line;
                int eq = line.indexOf('=');
                if (eq > 0) {
                    name = line.substring(0, eq).trim();
                    regex = line.substring(eq + 1).trim();
                }
                if (name.isEmpty() || regex.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Invalid text artifact rule at " + resourcePath + ":" + (i + 1));
                }
                loaded.add(textRule(name, regex));
            }
            return loaded;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to load text artifact rules from "
                            + resourcePath
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static Path configuredPatternFile() {
        String configuredPath = System.getProperty(TEXT_ARTIFACT_PATTERN_FILE_PROPERTY);
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        return Path.of(configuredPath);
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
