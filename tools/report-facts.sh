#!/usr/bin/env bash
#
# Figures for a status report, read out of the repository rather than recalled.
# Everything the header and the tiles of docs/report/template.html need.
#
#   tools/report-facts.sh            # read what is already there
#   tools/report-facts.sh --run      # run the suite and lint first
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [ "${1:-}" = "--run" ]; then
    ./gradlew :app:testDebugUnitTest :app:lintRelease --no-daemon >/dev/null 2>&1 \
        && echo "suite and lint: ran" || echo "suite or lint: FAILED — report that, not a number"
fi

printf 'date          %s\n' "$(date '+%-d %B %Y')"
printf 'main          %s  (%s commits)\n' "$(git rev-parse --short HEAD)" "$(git rev-list --count HEAD)"
printf 'adrs          %s\n' "$(ls docs/adr/[0-9]*.md 2>/dev/null | wc -l)"
printf 'presets       %s\n' "$(grep -oP '^\s+t\("\K[a-z0-9_]+' \
    app/src/main/java/io/github/deeplow/nobstuner/model/TuningCatalog.kt 2>/dev/null | sort -u | wc -l)"
printf 'main sources  %s kotlin files\n' "$(git ls-files 'app/src/main/**/*.kt' | wc -l)"

python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = s = 0
for p in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    r = ET.parse(p).getroot()
    t += int(r.get("tests")); s += int(r.get("skipped"))
    f += int(r.get("failures")) + int(r.get("errors"))
print(f"tests         {t} passing, {f} failed, {s} skipped" if t else
      "tests         no results — run with --run")
PY

report="app/build/intermediates/lint_intermediate_text_report/release/lintReportRelease/lint-results-release.txt"
if [ -f "$report" ]; then
    n=$(grep -cE '^(Warning|Error):' "$report" 2>/dev/null || true)
    [ "${n:-0}" -eq 0 ] && echo "lint          clean" || echo "lint          $n finding(s)"
else
    echo "lint          no report — run with --run"
fi

aab=$(ls -S app/build/outputs/bundle/release/*.aab 2>/dev/null | head -1)
[ -n "$aab" ] && printf 'bundle        %s\n' "$(du -h "$aab" | cut -f1)" \
              || echo "bundle        not built"

echo
echo "branches"
git for-each-ref --format='%(refname:short)' refs/heads | grep -v '^main$' | while read -r b; do
    printf '  %-34s %s ahead, %s behind\n' "$b" \
        "$(git rev-list --count main.."$b")" "$(git rev-list --count "$b"..main)"
done
