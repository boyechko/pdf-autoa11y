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
./pdf-autoa11y --skip-checks=NeedlessNestingCheck,MissingPagePartsCheck inputs/input.pdf
```

## Architecture

### Package Structure

```
net.boyechko.pdf.autoa11y/
├── checks/          # Check implementations (LanguageSetCheck, MistaggedArtifactCheck, etc.)
├── core/            # ProcessingService orchestration, ProcessingListener, ProcessingDefaults
├── document/        # Utilities for extracting data from PDF (Content, DocContext, PdfCustodian, StructTree, etc.)
├── fixes/           # Isue fixes (FlattenNesting, RemapLigatures, etc.)
├── issue/           # Issue, IssueFix, IssueLoc, IssueType, IssueSev, IssueList
├── ui/              # UI modules shared by CLI and GUI; LoggingListener, FormattedListener
├── ui/cli/          # CLI entry point and listener
├── ui/gui/          # GUI entry point and listener
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

# Adding New Rules/Fixes

1. Ensure fixes are idempotent
