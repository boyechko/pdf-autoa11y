# PDF-Auto-A11y

**PDF-Auto-A11y** is a lightweight Java tool that automates common, repetitive
tasks in PDF accessibility remediation. It's designed to save time and reduce
human error when fixing tag structures, headings, lists, and other accessibility
issues.

## Features

- Automates repetitive accessibility fixes in PDFs
- Normalizes tag structures (headings, lists, etc.)
- Reduces manual work in tools like Adobe Acrobat
- Extensible for future rules and transformations

## Why PDF-Auto-A11y?

Accessibility remediation often involves tedious manual corrections. PDF-Auto-
A11y provides a framework to apply systematic fixes programmatically, freeing up
your time to focus on the complex cases that require human judgment.

## Installation

Clone the repository:

```bash
git clone https://github.com/your-username/pdf-autoa11y.git
cd pdf-autoa11y
```

Build with Maven:

```bash
mvn package
```

Usage:

```bash
java -jar target/pdf-autoa11y.jar [-p password] input.pdf
# will produce input_autoa11y.pdf
```
