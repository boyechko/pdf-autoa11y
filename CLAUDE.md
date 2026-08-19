# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with
code in this repository.

## Project Overview

PDF-Auto-A11y is a Java-based PDF accessibility remediation tool that validates
and automatically fixes common PDF/UA-1 tag structure issues using iText PDF
library.

Project codename: PurpleElephant

## Build and Development Commands

```bash
# Build
mvn clean package

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=TagSingleChildFixTest

# Run a single test method
mvn test -Dtest=TagSingleChildFixTest#testMethodName

# Run integration tests (disabled by default)
mvn test -Dgroups=GoalDriven \
         -Dmaven-surefire-plugin.excludedGroups= \
         -DargLine="-Dorg.slf4j.simpleLogger.defaultLogLevel=info"

# Format code (Google Java Format - AOSP style)
mvn spotless:apply

# Run CLI via Maven
mvn exec:java -Dexec.args="inputs/input.pdf outputs/"
mvn exec:java -Dexec.args="-vv inputs/input.pdf outputs/"  # log-like output
mvn exec:java -Dexec.args="--dump-tree inputss/input.pdf"  # print structure tree with MCRs and exit

# After build, it's also possible to use wrapper scripts
./pdf-autoa11y inputs/input.pdf outputs/

# Choosing which checks run (comma-separated class names; also accept a space
# instead of `=`). Prefer --only-checks when working on a single check: it
# selects from *all* checks, optional ones included, and never goes stale as
# checks are added to ProcessingDefaults.
./pdf-autoa11y --only-checks=BadlySplitLinkCheck inputs/input.pdf
./pdf-autoa11y --skip-checks=NeedlessNestingCheck,MissingPagePartsCheck inputs/input.pdf
./pdf-autoa11y --include-checks=WrapWebCapturesCheck inputs/input.pdf  # defaults + optional

# Edit the PDF outline (bookmarks): dump to text, edit, then apply back
./pdf-autoa11y --dump-outline input.pdf > outline.txt
./pdf-autoa11y --apply-outline=outline.txt input.pdf outputs/

# Edit /T scribbles via the tree diagram: dump to text, annotate, then apply back.
# Append a quoted scribble after an element's `Role #objNum` to set it; matching
# is keyed on the object number, so box-drawing/indentation need not be preserved.
./pdf-autoa11y --dump-tree input.pdf > tree.txt
./pdf-autoa11y --annotate-tree=tree.txt input.pdf outputs/

# For bulk scribbling, drive the annotation with sed keyed on object number.
# Anchor the number with `$` so it matches the struct-elem line (not <link #...>
# refs or longer-number substrings) and stays idempotent on re-runs:
sed -i '' '/#8018$/s/$/ "__!ARTIFACT"/; /#8019$/s/$/ "__!ARTIFACT"/' tree.txt
./pdf-autoa11y --annotate-tree=tree.txt input.pdf outputs/
```

## Architecture

### Package Structure

```
net.boyechko.pdf.autoa11y/
├── checks/          # Check implementations (LanguageSetCheck, MistaggedArtifactCheck, etc.)
├── core/            # ProcessingService orchestration, ProcessingListener, ProcessingDefaults
├── document/        # Utilities for extracting data from PDF (Content, DocContext, PdfCustodian, StructTree, etc.)
├── fixes/           # Isue fixes (NeedlessNestingFix, BadlyMappedLigatureFix, etc.)
├── issue/           # Issue, IssueFix, IssueLoc, IssueType, IssueSev, IssueList
├── ui/              # UI entry points (Cli, Gui) and shared reporting (LoggingListener, FormattedListener)
├── validation/      # RuleEngine, Rule interface, PatternMatcher
```

### Package Dependencies

UI depends on core; core depends on validation, fixes, issue; validation
depends on checks, fixes, issue; fixes/checks depend on issue and document.
IssueFix lives in `issue/` to prevent circular dependencies since Issue
references IssueFix.

## Code Style

- Google Java Format with AOSP style
- Java 21 features: sealed interfaces, pattern matching
- All output should go through LoggingListener or FormattedListener
- Write one-line Javadoc comments for methods if not obvious from name
- Wrap committed markdown/plain text files (README.md, CONTRIBUTING.md, etc.) at 80 characters
- Prefer coding approach championed by Agile, Clean Code, Martin Fowler, Robert Martin, Kent Beck
- Organize source code following "newspaper metaphor"
- Suggest design patterns from GoF 1995 if applicable

## Tests

1. Write tests before implementation (following Test-Driven Development)
2. Don't write brittle tests asserting specific error message text
3. Names of test methods should be descriptive of what they test
   (e.g. `blankDocumentIsNotImageOnly` in ImageOnlyDocumentCheckTest.java)

## Key Resources

- Tag schema: `src/main/resources/tagschema-PDF-UA1.yaml`
- Frequent PDF inputs: `inputs/` directory
- Frequent PDF outputs: `outputs/` directory
- iText 9.5.0 kernel sources: `.itext-sources/` (gitignored, read with
  the `Read` tool — no Bash needed)

# Adding New Rules/Fixes

1. Ensure fixes are idempotent

## Changelog Discipline

When a change is user-visible, contributor-visible, or affects documented
behavior, consider whether `CHANGELOG.md` should be updated.

Examples that usually warrant a changelog entry:
- new checks, fixes, CLI options, reports, or workflows
- behavior changes that affect output, remediation, validation, or defaults
- bug fixes with user-visible impact
- dependency upgrades with meaningful behavioral or compatibility impact

Changelog entries should follow Keep a Changelog style:
- prefer high-level, user-facing summaries
- group entries under `Added`, `Changed`, `Fixed`, or `Removed`
- avoid raw commit-log wording and internal refactor details unless they are
  externally relevant

Before wrapping up substantial work, explicitly state either:
- that `CHANGELOG.md` was updated
- or why no changelog entry is needed

## Releasing

See `docs/releasing.md` for the release workflow. In brief: the version lives
only in the `CHANGELOG.md` header and an annotated git tag (`pom.xml` stays at
`1.0-SNAPSHOT`); a release is a changelog roll plus a `vX.Y.Z` tag dated to the
release date.
