#!/usr/bin/env bash
# Extract a page range from a PDF into a new tagged PDF.
#
# A thin wrapper over tools/ExtractPages.java: it resolves the iText classpath,
# parses the page range and picks a default output name. No fixture bookkeeping
# and no remediation — for that, see tools/extract-fixture.sh, which builds on
# this script.
#
# Usage: tools/extract-pages.sh [-q] SOURCE_PDF PAGE_RANGE [OUTPUT_PDF]
#
#   SOURCE_PDF  PDF to cut from
#   PAGE_RANGE  single page (112) or inclusive range (89-90)
#   OUTPUT_PDF  default: <source basename>_<range>.pdf in the current directory
#   -q          suppress progress messages
#
# Progress goes to stderr, the output path to stdout, so callers can capture
# the path (or discard it) without losing the messages.
#
# The extract is then run through tools/StripFontPrograms.java: it would
# otherwise carry the source's whole-document font subsets, which make up most
# of a few-page fixture. Dropping the font programs leaves text extraction
# intact, unlike re-subsetting (`mutool clean -S`), which corrupts it.
#
# Examples:
#   tools/extract-pages.sh catalog.pdf 89-90
#   tools/extract-pages.sh catalog.pdf 112 /tmp/page112.pdf
set -euo pipefail

QUIET=0
while getopts ':q' opt; do
    case "$opt" in
        q) QUIET=1 ;;
        *) echo "usage: extract-pages.sh [-q] SOURCE_PDF PAGE_RANGE [OUTPUT_PDF]" >&2; exit 2 ;;
    esac
done
shift $((OPTIND - 1))

SOURCE="${1:?usage: extract-pages.sh [-q] SOURCE_PDF PAGE_RANGE [OUTPUT_PDF]}"
RANGE="${2:?page range, e.g. 89-90}"

[ -f "$SOURCE" ] || { echo "no such file: $SOURCE" >&2; exit 1; }

FIRST="${RANGE%%-*}"
LAST="${RANGE##*-}"
case "$FIRST$LAST" in
    ''|*[!0-9]*) echo "page range must be N or N-M, got: $RANGE" >&2; exit 1 ;;
esac
[ "$FIRST" -le "$LAST" ] || { echo "range runs backwards: $RANGE" >&2; exit 1; }

# Page labels pad to three digits so extracts of the same document sort in order.
if [ "$FIRST" = "$LAST" ]; then
    LABEL=$(printf '%03d' "$FIRST")
else
    LABEL="$(printf '%03d' "$FIRST")-$(printf '%03d' "$LAST")"
fi
OUTPUT="${3:-$(basename "$SOURCE" .pdf)_${LABEL}.pdf}"

REPO=$(cd "$(dirname "$0")/.." && pwd)

# ExtractPages runs straight from source (Java 11+ single-file launcher) and
# needs only iText on the classpath, not the project's own classes — so this
# works without a prior `mvn package`. The classpath is cached under target/
# and refreshed only when pom.xml is newer.
CLASSPATH_CACHE="$REPO/target/tools-classpath.txt"
if [ ! -f "$CLASSPATH_CACHE" ] || [ "$REPO/pom.xml" -nt "$CLASSPATH_CACHE" ]; then
    [ "$QUIET" -eq 1 ] || echo "==> refreshing classpath cache" >&2
    (cd "$REPO" && mvn -q dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_CACHE")
fi

FULL=$(mktemp)
trap 'rm -f "$FULL"' EXIT

[ "$QUIET" -eq 1 ] || echo "==> extracting pages $FIRST-$LAST from $SOURCE" >&2
java -cp "$(cat "$CLASSPATH_CACHE")" "$REPO/tools/ExtractPages.java" \
    "$SOURCE" "$FULL" "$FIRST" "$LAST" >/dev/null

[ "$QUIET" -eq 1 ] || echo "==> stripping embedded font programs" >&2
java -cp "$(cat "$CLASSPATH_CACHE")" "$REPO/tools/StripFontPrograms.java" \
    "$FULL" "$OUTPUT" >/dev/null

echo "$OUTPUT"
