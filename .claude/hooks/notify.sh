#!/usr/bin/env bash
# Notification: show a macOS banner when Claude Code is waiting for you (for example during a live run,
# when you may be away from the screen). Personal hook, configured in .claude/settings.local.json.
msg=$(jq -r '.message // "Claude Code needs your attention"' | tr '"\\' "' ")
command -v osascript >/dev/null 2>&1 || exit 0
osascript -e "display notification \"$msg\" with title \"Claude Code: ImdbProject\"" >/dev/null 2>&1 || true
exit 0
