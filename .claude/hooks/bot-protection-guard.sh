#!/usr/bin/env bash
# PreToolUse (Edit|Write|MultiEdit): enforce CLAUDE.md's hard rule that the suite never bypasses or evades
# IMDb's bot protection. Blocks edits to the suite's code, build or config files that add stealth flags,
# user-agent spoofing, webdriver hiding or CAPTCHA-solver libraries. A person solves CAPTCHAs.
# Exit 2 blocks the edit and shows the reason to Claude.
set -u

input=$(cat)
file=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')
case "$file" in
  */hellocucumber/*.java|*/hellocucumber/pom.xml|*/hellocucumber/*.properties) ;;
  *) exit 0 ;;
esac

# The text being added: Write content, Edit new_string, or every MultiEdit new_string.
added=$(printf '%s' "$input" | jq -r '[.tool_input.content, .tool_input.new_string, (.tool_input.edits // [] | .[].new_string)] | map(select(. != null)) | join("\n")')

pattern='AutomationControlled|excludeSwitches|enable-automation|useAutomationExtension|navigator\.webdriver|--user-agent|general\.useragent\.override|setUserAgent|Network\.setUserAgentOverride|undetected[-_]?chrome|selenium-stealth|puppeteer-extra|stealth[-_]?plugin|2captcha|anti-?captcha|capsolver|capmonster|deathbycaptcha|captcha[-_ ]?solver'
hits=$(printf '%s' "$added" | grep -oiE "$pattern" | sort -u | paste -sd, -)

if [ -n "$hits" ]; then
  echo "Blocked by .claude/hooks/bot-protection-guard.sh: this edit to ${file##*/hellocucumber/} adds bot-protection evasion ($hits)." >&2
  echo "CLAUDE.md's hard rule: never bypass or evade IMDb's bot protection (no stealth flags, user-agent spoofing or CAPTCHA solvers). A person solves CAPTCHAs; BasePage waits for them." >&2
  exit 2
fi
exit 0
