#!/usr/bin/env bash
# list_detections.sh — CLI mirror of the in-plugin Reactive detection index
#                      (SystemPromptBuilder.indexMessage("detection"))
#
# Lists every detection family in the bundled `ai/detection/` catalog, in
# lexicographic-by-filename order (the in-plugin order is also filename-driven
# via `catalog-manifest.txt`; for the external skill we don't ship the
# manifest, so lexicographic is the natural fallback).
#
# For each detection, prints:  id — title: cue
# (same shape as the in-plugin index message that's prepended to every
# Reactive turn's system prompt).
#
# Usage:  ./list_detections.sh
set -euo pipefail

# ── locate the bundled ai/detection/ folder ──────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DETECTION_DIR="$SCRIPT_DIR/../ai/detection"

if [ ! -d "$DETECTION_DIR" ]; then
    echo "error: bundled catalog not found at: $DETECTION_DIR" >&2
    echo "  Run ./gradlew syncAgentCatalog from the easy-api repo to populate it." >&2
    exit 1
fi

# ── iterate files in lexicographic order ─────────────────────────────────
# Each file's YAML front-matter header carries `id`, `title`, `cue` (all
# required by the catalog spec). Parse them with awk (no yq dependency).
shopt -s nullglob
FILES=( "$DETECTION_DIR"/*.md )
if [ ${#FILES[@]} -eq 0 ]; then
    echo "(no detection files in bundled catalog)" >&2
    exit 0
fi

for FILE in "${FILES[@]}"; do
    # Extract the YAML front-matter block (between the first two `---` lines).
    # Then pull id/title/cue out of it. Values may be unquoted scalars.
    awk '
        /^---[[:space:]]*$/ {
            count++
            next
        }
        count == 1 {
            # Print the YAML line; the outer shell parses key: value.
            print
        }
    ' "$FILE" | awk -F': ' '
        /^id:/    { id = $0;    sub(/^id:[[:space:]]*/, "", id);    gsub(/^"|"$/, "", id) }
        /^title:/ { title = $0; sub(/^title:[[:space:]]*/, "", title); gsub(/^"|"$/, "", title) }
        /^cue:/   { cue = $0;   sub(/^cue:[[:space:]]*/, "", cue);   gsub(/^"|"$/, "", cue) }
        END {
            if (id != "" && title != "" && cue != "") {
                printf "%s — %s: %s\n", id, title, cue
            }
        }
    '
done
