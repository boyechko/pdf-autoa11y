#!/usr/bin/env bash
# Cut a goal-driven test fixture out of a working PDF.
#
# Extracts a page range into src/test/resources/<base>_<range>.pdf, compresses
# it, checks that the named check actually fires on it, then remediates and
# writes the matching .goal.txt. The GoalDrivenIntegrationTest discovers the
# case from the goal file's name, so no test code needs editing.
#
# The firing check is the point of the script: a fixture cut from an already
# remediated copy has no defect left to detect, and silently becomes a test
# that asserts nothing. Extract from the *pre-run* archive of the working
# document, not from the working document itself.
#
# Usage: tools/extract-fixture.sh SOURCE_PDF PAGE_RANGE CHECK [BASE]
#
#   SOURCE_PDF  PDF to cut from, e.g. catalog/catalog_20260820T0837.pdf
#   PAGE_RANGE  single page (112) or inclusive range (89-90)
#   CHECK       check class name, e.g. IrregularTocCheck
#   BASE        fixture basename, default: SOURCE_PDF's name up to the first _
#
# Examples:
#   tools/extract-fixture.sh catalog/catalog_20260820T0837.pdf 89-90 IrregularTocCheck
#   tools/extract-fixture.sh catalog/catalog.pdf 112 MistaggedListCheck
#
# Pass -f as the first argument to overwrite an existing fixture.
set -euo pipefail
cd "$(dirname "$0")/.."

FORCE=0
if [ "${1:-}" = "-f" ]; then
    FORCE=1
    shift
fi

SOURCE="${1:?usage: extract-fixture.sh SOURCE_PDF PAGE_RANGE CHECK [BASE]}"
RANGE="${2:?page range, e.g. 89-90}"
CHECK="${3:?check class name, e.g. IrregularTocCheck}"
BASE="${4:-$(basename "$SOURCE" .pdf | cut -d_ -f1)}"

[ -f "$SOURCE" ] || { echo "no such file: $SOURCE" >&2; exit 1; }

FIRST="${RANGE%%-*}"
LAST="${RANGE##*-}"
case "$FIRST$LAST" in
    *[!0-9]*) echo "page range must be N or N-M, got: $RANGE" >&2; exit 1 ;;
esac
[ "$FIRST" -le "$LAST" ] || { echo "range runs backwards: $RANGE" >&2; exit 1; }

# Fixture names pad to three digits so they sort alongside the existing ones.
if [ "$FIRST" = "$LAST" ]; then
    LABEL=$(printf '%03d' "$FIRST")
else
    LABEL="$(printf '%03d' "$FIRST")-$(printf '%03d' "$LAST")"
fi

RESOURCES=src/test/resources
FIXTURE="$RESOURCES/${BASE}_${LABEL}.pdf"
GOAL="$RESOURCES/${BASE}_${LABEL}.${CHECK}.goal.txt"

if [ -e "$FIXTURE" ] && [ "$FORCE" -eq 0 ]; then
    echo "$FIXTURE already exists; pass -f to overwrite" >&2
    exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

# ExtractPages runs straight from source (Java 11+ single-file launcher) and
# needs only iText on the classpath, not the project's own classes — so this
# works without a prior `mvn package`. The classpath is cached under target/
# and refreshed only when pom.xml is newer.
CLASSPATH_CACHE=target/tools-classpath.txt
if [ ! -f "$CLASSPATH_CACHE" ] || [ pom.xml -nt "$CLASSPATH_CACHE" ]; then
    echo "==> refreshing classpath cache"
    mvn -q dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_CACHE"
fi

echo "==> extracting pages $FIRST-$LAST from $SOURCE"
java -cp "$(cat "$CLASSPATH_CACHE")" tools/ExtractPages.java \
    "$SOURCE" "$WORK/raw.pdf" "$FIRST" "$LAST"

# Object numbers get renumbered here, which the goal comparison normalises away.
echo "==> compressing"
if command -v mutool >/dev/null 2>&1; then
    mutool clean -gggz "$WORK/raw.pdf" "$WORK/clean.pdf" >/dev/null 2>&1
    printf '    %s B -> %s B\n' \
        "$(wc -c <"$WORK/raw.pdf" | tr -d ' ')" \
        "$(wc -c <"$WORK/clean.pdf" | tr -d ' ')"
else
    echo "    mutool not found; keeping the uncompressed extract" >&2
    cp "$WORK/raw.pdf" "$WORK/clean.pdf"
fi

echo "==> checking that $CHECK fires"
if ./pdf-autoa11y -a --only-checks="$CHECK" "$WORK/clean.pdf" 2>&1 | grep -q ': no issues'; then
    echo >&2
    echo "$CHECK found nothing on pages $FIRST-$LAST of $SOURCE." >&2
    echo "A fixture with no defect asserts nothing. Two usual causes:" >&2
    echo "  - the source is already remediated (use the pre-run archive)" >&2
    echo "  - the page range misses the content you meant" >&2
    exit 1
fi

echo "==> remediating and writing the goal"
mkdir -p "$WORK/out"
./pdf-autoa11y --only-checks="$CHECK" "$WORK/clean.pdf" "$WORK/out/" >/dev/null 2>&1
REMEDIATED=$(find "$WORK/out" -name '*.pdf' -print -quit)
[ -n "$REMEDIATED" ] || { echo "remediation produced no PDF" >&2; exit 1; }

cp "$WORK/clean.pdf" "$FIXTURE"
./pdf-autoa11y --dump-tree=plain "$REMEDIATED" >"$GOAL"

echo
echo "fixture  $FIXTURE ($(wc -c <"$FIXTURE" | tr -d ' ') B)"
echo "goal     $GOAL ($(wc -l <"$GOAL" | tr -d ' ') lines)"
echo
echo "Review the goal before committing — it records what the tool does now,"
echo "not independently of it. Then: mvn test -Dgroups=GoalDriven \\"
echo "  -Dmaven-surefire-plugin.excludedGroups= -Dtest=GoalDrivenIntegrationTest"
