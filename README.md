# PDF-Auto-A11y

PDF-Auto-A11y is a Java-based PDF accessibility remediation tool that validates
and automatically fixes common tagging and document-structure issues in tagged
PDFs. It is designed for accessibility professionals who want to automate the
repetitive structural corrections that would otherwise be done manually in
Acrobat or similar remediation tools.

## Quick Start

Requirements:

- Java 21+
- Maven 3.x

Clone the repository and run the CLI wrapper:

```bash
git clone https://github.com/boyechko/pdf-autoa11y.git
cd pdf-autoa11y

./pdf-autoa11y input.pdf
# writes: input_autoa11y.pdf
```

Launch the GUI:

```bash
./pdf-autoa11y-gui
```

The wrapper scripts compile the project on first run and download Maven
dependencies as needed. If you prefer to build explicitly first:

```bash
mvn clean package
```

## What It Does

PDF-Auto-A11y runs a pipeline of checks against the input PDF. Each check gets
its own pipeline step so intermediate results can be inspected. Checks cover
document-level metadata and annotations as well as structure-tree validation
and repair.

By default, the tool can automatically remediate issues such as:

- Missing document language (`Lang`, defaulting to `en-US`)
- Missing logical tab order (`Tabs/S`)
- Missing tagged-PDF marker (`MarkInfo/Marked`)
- Missing `Document` root element
- Untagged link annotations by creating `Link` tags
- Unexpected pushbutton widget annotations
- Broken ligature mappings
- Unverified PDF/UA conformance claims in XMP metadata
- Common list-structure problems such as missing `LI` / `LBody`, flattened list
  hierarchies, and mislabeled bullets
- Needless nesting, empty tags, some mistagged artifacts, and related structure
  cleanup

Some issues are detected but not automatically fixed. For example:

- Missing alt text on figures
- PDFs that are image-only and need OCR first
- PDFs with no structure tree at all

Documents must already be tagged. The tool validates missing structure trees,
but it does not create a full structure tree for an untagged PDF.

## When It Helps

This tool is useful when:

- You have tagged PDFs with recurring list or tag-shape issues from Word,
  InDesign, or similar authoring tools
- You want consistent remediation across many files
- You want to identify structural issues before manual review
- You want to automate the repetitive fixes and focus manual effort on issues
  that require judgment

## Usage

### CLI

```bash
./pdf-autoa11y [options] <input.pdf> [output.pdf|output_dir/]
```

If `output` is omitted, the result is written next to the input as
`<base>_autoa11y.pdf`. If `output` is a directory, the output filename is
generated inside that directory.

Common options:

- `-a, --analyze` analyze only; do not write an output PDF
- `-r, --report` save the console output to a text report file
- `-r=<file>`, `--report=<file>` write the report to a custom path
- `-p, --password <pw>` open encrypted PDFs
- `-q, --quiet` show only errors and final status
- `-v, --verbose` show detailed processing information
- `-vv, --debug` show full debug output
- `-t, --print-tree` print the structure tree during processing
- `--dump-tree` print the structure tree with MCRs/annotations and exit
- `--dump-roles` print the structure tree roles and exit
- `--skip-checks <a,b,c>` skip specific checks
- `--only-checks <a,b,c>` run only specific checks
- `-f, --force` write an output PDF even if no fixes were applied

Run `./pdf-autoa11y --help` for the full CLI help text.

Examples:

```bash
# Remediate using the default output name
./pdf-autoa11y document.pdf

# Analyze only and save a report next to the input
./pdf-autoa11y -a -r document.pdf

# Choose both output and report paths
./pdf-autoa11y --report=out/report.txt document.pdf out/fixed.pdf

# Write output into a directory
./pdf-autoa11y document.pdf out/

# Process an encrypted PDF
./pdf-autoa11y -p mypassword document.pdf

# Inspect the structure tree without remediating
./pdf-autoa11y --dump-tree document.pdf
```

### GUI

```bash
./pdf-autoa11y-gui
```

The GUI lets you select a PDF, enter a password if needed, view processing
output in real time, and save the remediated PDF.

## Reports and Output

Use `-r/--report` to save the same console output to a text file.

If you pass `-r/--report` without a path, the report defaults to
`<base>_autoa11y.txt`:

- In analyze mode, the report is written next to the input PDF
- In remediation mode, the report is written next to the output PDF

Analysis and remediation can report different issue counts. Analysis scans the
original document in a single pass, while remediation applies fixes
sequentially, so earlier fixes may resolve issues before later checks run.

The output includes:

- Checks that passed
- Issues found
- Fixes applied
- Warnings that still require manual review
- Page references or object references when available

## Temporary Pipeline Files

Remediation runs as a stepwise pipeline and writes intermediate PDFs to a temp
directory. By default, that location is:

- `/tmp/pdf-autoa11y/pipeline/<input-filename>/`

You can override the base directory with either:

- `AUTOA11Y_PIPELINE_DIR=/path/to/dir`
- `-Dautoa11y.pipeline.dir=/path/to/dir`

Intermediate files are currently kept by default, which is useful for debugging
and inspecting each processing step.

## Project Docs

- `CHANGELOG.md` for release notes
- `CONTRIBUTING.md` for development workflow
- `docs/sidecar.md` for per-PDF sidecar config reference
- `docs/checks-and-fixes.md` for the full list of checks and fixes
- `docs/architecture-decisions.md` for architecture decision records

## Development

Run tests:

```bash
mvn test
```

Persist test PDFs for review:

```bash
mvn test -Dpdf.autoa11y.testOutputDir=/tmp/pdf-autoa11y-tests
```

Run the app through Maven:

```bash
mvn exec:java -Dexec.args="input.pdf"
```

Apply code formatting:

```bash
mvn spotless:apply
```

## License

This project is licensed under the GNU Affero General Public License v3.0
(AGPL-3.0).

This project uses iText PDF, which is also licensed under AGPL. As a derivative
work, PDF-Auto-A11y must remain under AGPL-compatible terms.

In practical terms:

- You may use, modify, and distribute this software
- If you modify it and make it available over a network, you must also make the
  modified source available under AGPL-3.0
- Software incorporating this code must also comply with AGPL obligations

See `LICENSE` for the full license text.
