# PDF-Auto-A11y

**PDF-Auto-A11y** is a Java-based PDF accessibility remediation tool that
validates and automatically fixes common tag structure issues in PDF documents.
Designed for accessibility professionals, it handles the repetitive structural
corrections that typically require manual work in Adobe Acrobat or other
remediation tools.

## What It Does

PDF-Auto-A11y performs validation and automatic remediation across two areas:

### Tag Structure Fixes (Automatic)

The tool detects and automatically corrects invalid tag hierarchies that
violate PDF/UA-1 standards:

**List Structure Issues:**
- **Missing LI wrappers** - Wraps orphaned content (P, Div, Figure, Span,
  LBody) directly under L elements in proper LI containers
- **Incomplete list items** - Fixes LI elements missing required LBody
  structure (e.g., `LI > P` becomes `LI > Lbl + LBody > P`)
- **Flattened list hierarchies** - Restructures alternating `L > Lbl, P, Lbl,
  P` patterns into proper `L > LI > Lbl + LBody > P` structure
- **Flattened list bodies** - Wraps alternating `L > Lbl, LBody, Lbl, LBody`
  patterns into proper `L > LI > Lbl + LBody` structure
- **Misidentified labels** - Converts P elements to Lbl when found alongside
  LBody in list items

**Label/Figure Issues:**
- **Figure-as-bullet** - Converts `Lbl > Figure` patterns to proper Lbl
  elements with ActualText for decorative bullets

### Document-Level Fixes (Automatic)

The tool automatically sets required PDF/UA-1 document properties:
- **Document language** - Sets the Lang entry in the document catalog
  (defaults to en-US)
- **Tab order** - Configures logical tab order (Tabs/S) for all pages
- **Tagged PDF marker** - Sets the MarkInfo/Marked flag to indicate the
  document is tagged

**Note:** The tool validates but cannot fix missing structure trees - documents
must already have tag structure present.

## Use Cases

This tool is useful when:

- You have PDFs with list structure issues from Word, InDesign, or other
  authoring tools
- You need to process multiple documents consistently
- You want to identify structural issues before manual remediation
- You want to automate the repetitive parts of accessibility work so you can
  focus on tasks that require human judgment
- You need to manually tag long lists, but don't want to manually create four
  tags per list item

## Requirements

- Java 21 or higher
- Maven 3.x (for building)

## Installation

Clone the repository:

```bash
git clone https://github.com/boyechko/pdf-autoa11y.git
cd pdf-autoa11y
```

Build with Maven:

```bash
mvn clean package
```

This creates two executable scripts in the project root:
- `pdf-autoa11y` - Command-line interface
- `pdf-autoa11y-gui` - Graphical interface

## Usage

### Command Line

Basic usage:

```bash
./pdf-autoa11y input.pdf
# Produces: input_autoa11y.pdf
```

With options:

```bash
./pdf-autoa11y [-a] [-q|-v|-vv] [-f] [-p password] [-r[=report]] <input.pdf> [output.pdf]
```

Options:
- `-a, --analyze` - Analyze only (no remediation or output PDF)
- `-q, --quiet` - Only show errors and final status
- `-v, --verbose` - Show detailed tag structure during validation
- `-vv, --debug` - Show all debug information including logs
- `-f, --force` - Force save even if no fixes were applied
- `-p, --password` - Password for encrypted PDFs
- `-r, --report` - Save output to a report file (auto-named from input).
  Use `-r=<file>` or `--report=<file>` to specify a custom path.

If the output path or report path is an existing directory, the tool
generates the filename automatically inside that directory.

**Verbosity Levels:**
- **Quiet** (`-q`): Minimal output - only errors and the output file path
- **Normal** (default): Summary of validation, fixes, and compliance checks
- **Verbose** (`-v`): Includes detailed tag structure tree with all elements
- **Debug** (`-vv`): Everything including internal debug logs

Examples:

```bash
# Process with password
./pdf-autoa11y -p mypassword document.pdf

# Specify output path
./pdf-autoa11y input.pdf output/fixed.pdf

# Output to a directory (produces output/document_autoa11y.pdf)
./pdf-autoa11y document.pdf output/

# Generate a report file (produces document_autoa11y.txt alongside the PDF)
./pdf-autoa11y -r document.pdf

# Generate a report in a specific directory
./pdf-autoa11y -r document.pdf output/

# Specify a custom report path
./pdf-autoa11y --report=my_report.txt document.pdf

# Analyze only - detect issues without remediating
./pdf-autoa11y -a -v document.pdf

# Analyze and save the analysis to a report file
./pdf-autoa11y -a -r document.pdf

# Quiet mode for scripting
./pdf-autoa11y -q document.pdf

# Verbose mode to see tag structure
./pdf-autoa11y -v document.pdf

# Debug mode with all information
./pdf-autoa11y -vv document.pdf

# Force save with verbose output
./pdf-autoa11y -v -f document.pdf
```

### GUI Application

Launch the graphical interface:

```bash
./pdf-autoa11y-gui
```

The GUI allows you to:
1. Drag and drop PDF files or browse to select them
2. Enter a password if needed
3. View processing output in real-time
4. Save the remediated PDF to your chosen location

## Features

- **Automatic fixes** for common tag structure issues
- **Automatic document-level fixes** for language, tab order, and tagged PDF
  marker
- **Detailed reporting** showing detected issues, applied fixes, and remaining
  problems
- **Password support** for encrypted PDFs
- **Batch processing** via command line
- **Real-time feedback** in GUI mode
- **Page number references** for issues requiring manual review
- **Preserves encryption** when processing password-protected files

## Output

The tool provides a structured workflow report:

**Phase 1: Tag Structure Validation**
```
[1/4] Validating tag structure...
  ✗ L > P (expected L > LI > LBody > P)
  ✗ LI > P (expected LI > Lbl + LBody > P)
```

**Phase 2: Automatic Fixes**
```
[2/4] Applying automatic fixes...
  ✓ Wrapped P in LI->LBody under L object #47 (p. 3)
  ✓ Changed P to Lbl in LI object #52 (p. 3)
```

**Phase 3: Re-validation** (if fixes were applied)
```
[3/4] Re-validating tag structure...
  ✓ No issues found
```

**Phase 4: Document-Level Checks**
```
[4/4] Checking document-level compliance...
  ✓ Language Set
  ✓ Tab Order
  ✓ Tag Structure Present
  ✓ Tagged PDF
```

**Summary**
```
──────────────────────────────────────────────────
Summary
──────────────────────────────────────────────────
Issues detected: 5
Issues resolved: 5
Remaining issues: 0
```

### Understanding the Output

- **✓** indicates a check passed or a fix was successfully applied
- **✗** indicates an issue was detected
- **⚠** indicates an issue requires manual review
- Object numbers and page references help locate issues in Acrobat
- The summary shows your remediation progress at a glance

## Development

Run tests:

```bash
mvn test
```

Persist test PDFs for review (defaults to in-memory PDFs when not set):

```bash
mvn test -Dpdf.autoa11y.testOutputDir=/tmp/pdf-autoa11y-tests
```

Run with Maven exec plugin:

```bash
mvn exec:java -Dexec.args="input.pdf"
```

Code formatting (Google Java Format - AOSP style):

```bash
mvn spotless:apply
```

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0).

### Why AGPL?

This project uses [iText PDF](https://itextpdf.com/) library, which is licensed
under AGPL-3.0. As a derivative work, this project must also be licensed under
AGPL-3.0 to comply with iText's license terms.

### What This Means

- You are free to use, modify, and distribute this software
- If you modify this software and make it available over a network (e.g., as a
  web service), you must make your modified source code available under AGPL-3.0
- Any software that incorporates this code must also be licensed under AGPL-3.0

For commercial use without AGPL restrictions, you would need to obtain a
commercial license for iText PDF.

See the LICENSE file for full license text.
