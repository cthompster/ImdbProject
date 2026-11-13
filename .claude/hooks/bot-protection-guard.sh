#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit): enforce CLAUDE.md's hard rule that the suite never bypasses or evades
# IMDb's bot protection. Blocks edits to the suite's code, build, config and helper files (listed in
# lib-evasion.sh) that add a pattern from evasion-patterns.txt (stealth flags, user-agent spoofing, webdriver hiding, CAPTCHA solvers).
# Comments are ignored, so code may still explain the rule. Changes made through Bash are caught later,
# when Claude commits (git-guard.sh) or ends its turn (stop-checks.sh).
# Exit 2 blocks the edit and shows the reason to Claude.
set -u

if ! command -v jq >/dev/null 2>&1; then
  echo "bot-protection-guard hook is disabled: jq was not found." >&2
  exit 1
fi
. "$(dirname "$0")/lib-evasion.sh"

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null) \
  || { echo "bot-protection-guard hook: could not read its input." >&2; exit 1; }
evasion_is_suite_file "$file" || exit 0
if ! evasion_ready; then
  echo "bot-protection-guard hook is disabled: .claude/hooks/evasion-patterns.txt is missing or empty." >&2
  exit 1
fi

# The text being added: Write content, Edit new_string, or every MultiEdit new_string.
hits=$(printf '%s' "$input" \
  | jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")' \
  | evasion_hits)

if [ -n "$hits" ]; then
  echo "Blocked by .claude/hooks/bot-protection-guard.sh: this edit to ${file##*/hellocucumber/} adds bot-protection evasion ($hits)." >&2
  echo "CLAUDE.md's hard rule: never bypass or evade IMDb's bot protection (no stealth flags, user-agent spoofing or CAPTCHA solvers). A person solves CAPTCHAs; BasePage waits for them." >&2
  exit 2
fi
exit 0
