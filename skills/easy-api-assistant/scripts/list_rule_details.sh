#!/usr/bin/env bash
# list_rule_details.sh — CLI mirror of the in-plugin Reactive rule-recipe index
#                        (SystemPromptBuilder.indexMessage("rules"))
#
# Lists every per-key rule recipe in the bundled `ai/rules/` catalog, in
# lexicographic-by-filename order. This is the per-key recipe index — it
# complements (does NOT replace) the bundled `docs/rule-keys.md` snapshot
# of `RuleKeys.kt`, which lists every supported rule key (including keys
# that have no per-key recipe file).
#
# For each rule recipe, prints:  key — title: cue
# (same shape as the in-plugin index message).
#
# Usage:  ./list_rule_details.sh
set -euo pipefail

# ── locate the bundled ai/rules/ folder ───────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RULES_DIR="$SCRIPT_DIR/../ai/rules"

if [ ! -d "$RULES_DIR" ]; then
    echo "error: bundled catalog not found at: $RULES_DIR" >&2
    echo "  Run ./gradlew syncAgentCatalog from the easy-api repo to populate it." >&2
    exit 1
fi

# ── iterate files in lexicographic order ─────────────────────────────────
# Each file's YAML front-matter header carries `id`, `key`, `title`, `cue`.
# `key` is the rule key this file documents (equals the filename stem by
# convention, but we read it from the header to be precise).
shopt -s nullglob
FILES=( "$RULES_DIR"/*.md )
if [ ${#FILES[@]} -eq 0 ]; then
    echo "(no rule-recipe files in bundled catalog)" >&2
    exit 0
fi

for FILE in "${FILES[@]}"; do
    # Extract the YAML front-matter block (between the first two `---` lines),
    # then pull key/title/cue out of it.
    awk '
        /^---[[:space:]]*$/ {
            count++
            next
        }
        count == 1 {
            print
        }
    ' "$FILE" | awk -F': ' '
        /^key:/    { key = $0;   sub(/^key:[[:space:]]*/, "", key);   gsub(/^"|"$/, "", key) }
        /^title:/  { title = $0; sub(/^title:[[:space:]]*/, "", title); gsub(/^"|"$/, "", title) }
        /^cue:/    { cue = $0;   sub(/^cue:[[:space:]]*/, "", cue);   gsub(/^"|"$/, "", cue) }
        END {
            if (key != "" && title != "" && cue != "") {
                printf "%s — %s: %s\n", key, title, cue
            }
        }
    '
done
