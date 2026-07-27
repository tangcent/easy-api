#!/usr/bin/env bash
# get_detection_prompt.sh — CLI mirror of the in-plugin GetDetectionPromptTool
#
# Fetches the full detection recipe for one detection family by id, from the
# bundled `ai/detection/<id>.md` catalog file. The catalog is kept in sync
# with `src/main/resources/ai/detection/` by the `syncAgentCatalog` Gradle
# task. The YAML front-matter header is stripped; only the markdown body is
# printed to stdout — same contract as the in-plugin tool.
#
# Usage:  ./get_detection_prompt.sh <id>
#   ./get_detection_prompt.sh spring-filters-interceptors
#   ./get_detection_prompt.sh static-auth
#
# Unknown id → "error: unknown detection id: <id>" on stderr, exit 1.
set -euo pipefail

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
    echo "Usage: $0 <id>" >&2
    echo "  List available ids with: $0 --list   (or scripts/list_detections.sh)" >&2
    exit 1
fi

# ── --list shortcut ──────────────────────────────────────────────────────
if [ "$1" = "--list" ]; then
    exec "$(dirname "$0")/list_detections.sh"
fi

ID="$1"

# ── locate the bundled ai/detection/ folder ──────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DETECTION_DIR="$SCRIPT_DIR/../ai/detection"

if [ ! -d "$DETECTION_DIR" ]; then
    echo "error: bundled catalog not found at: $DETECTION_DIR" >&2
    echo "  Run ./gradlew syncAgentCatalog from the easy-api repo to populate it." >&2
    exit 1
fi

FILE="$DETECTION_DIR/$ID.md"
if [ ! -f "$FILE" ]; then
    echo "error: unknown detection id: $ID" >&2
    echo "  Available ids:" >&2
    ls -1 "$DETECTION_DIR" | sed 's/\.md$//' | sed 's/^/    /' >&2
    exit 1
fi

# ── strip the YAML front-matter (between the first two `---` lines) ──────
# The header is `---\n<yaml>\n---\n`; the body follows. Print everything
# after the second `---` line.
awk '
    /^---[[:space:]]*$/ {
        count++
        next
    }
    count >= 2 { print }
' "$FILE"
