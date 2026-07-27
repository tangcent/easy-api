#!/usr/bin/env bash
# get_rule_detail.sh — CLI mirror of the in-plugin GetRuleDetailTool (by-key path)
#
# Fetches the full per-key rule recipe from the bundled `ai/rules/<key>.md`
# catalog file. The catalog is kept in sync with `src/main/resources/ai/rules/`
# by the `syncAgentCatalog` Gradle task. The YAML front-matter header is
# stripped; only the markdown body is printed to stdout — same contract as
# the in-plugin tool's by-key path (`get_rule_detail(key=...)`).
#
# Usage:  ./get_rule_detail.sh <key>
#   ./get_rule_detail.sh postman.test
#   ./get_rule_detail.sh method.additional.header
#
# Unknown key → "error: unknown rule key: <key>" on stderr, exit 1.
set -euo pipefail

if [ $# -lt 1 ] || [ -z "${1:-}" ]; then
    echo "Usage: $0 <key>" >&2
    echo "  List available keys with: $0 --list   (or scripts/list_rule_details.sh)" >&2
    exit 1
fi

# ── --list shortcut ──────────────────────────────────────────────────────
if [ "$1" = "--list" ]; then
    exec "$(dirname "$0")/list_rule_details.sh"
fi

KEY="$1"

# ── locate the bundled ai/rules/ folder ──────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RULES_DIR="$SCRIPT_DIR/../ai/rules"

if [ ! -d "$RULES_DIR" ]; then
    echo "error: bundled catalog not found at: $RULES_DIR" >&2
    echo "  Run ./gradlew syncAgentCatalog from the easy-api repo to populate it." >&2
    exit 1
fi

FILE="$RULES_DIR/$KEY.md"
if [ ! -f "$FILE" ]; then
    echo "error: unknown rule key: $KEY" >&2
    echo "  Available keys:" >&2
    ls -1 "$RULES_DIR" | sed 's/\.md$//' | sed 's/^/    /' >&2
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
