#!/usr/bin/env bash
# PreToolUse (Bash): enforce the git workflow in CLAUDE.md.
# - Never commit, push, merge or hard-reset on master directly (work on a branch, merge through a PR).
#   Allowed on master: `git merge --ff-only` (updating master after a PR merge) and
#   `git push ... --delete <branch>` (deleting a merged branch on GitHub).
# - Never force-add the git-ignored Chrome profile (it holds cookies and the CAPTCHA pass).
# Exit 2 blocks the command and shows the reason to Claude.
set -u

cmd=$(jq -r '.tool_input.command // empty')
[ -z "$cmd" ] && exit 0
case "$cmd" in *git*) ;; *) exit 0 ;; esac

block() {
  echo "Blocked by .claude/hooks/git-guard.sh: $1" >&2
  exit 2
}

# Force-adding chrome-profile, on any branch.
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+add[^;&|]*(-f|--force)[^;&|]*chrome-profile|git[[:space:]]+add[^;&|]*chrome-profile[^;&|]*(-f|--force)'; then
  block "hellocucumber/chrome-profile/ is git-ignored on purpose: it holds cookies and IMDb's aws-waf-token. Never commit it."
fi

dir="${CLAUDE_PROJECT_DIR:-.}"
branch=$(git -C "$dir" symbolic-ref --short -q HEAD 2>/dev/null)
[ "$branch" = "master" ] || exit 0

# A command that first creates or switches to another branch isn't acting on master.
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(checkout|switch)[^;&|]*[[:space:]](-b|-B|-c|-C|--create|--force-create)([[:space:]]|$)'; then
  exit 0
fi

# Check each part of a compound command separately.
parts=$(printf '%s' "$cmd" | awk '{ gsub(/&&|\|\||;/, "\n"); print }')
while IFS= read -r part; do
  if printf '%s' "$part" | grep -Eq '(^|[[:space:]])git[[:space:]]+((-C|-c|--git-dir|--work-tree)[[:space:]]+[^[:space:]]+[[:space:]]+|-[^[:space:]]*[[:space:]]+)*commit([[:space:]]|$)'; then
    block "you are on master. Create a branch first (git checkout -b <name>); master only changes through PRs."
  fi
  if printf '%s' "$part" | grep -Eq '(^|[[:space:]])git[[:space:]]+((-C|-c|--git-dir|--work-tree)[[:space:]]+[^[:space:]]+[[:space:]]+|-[^[:space:]]*[[:space:]]+)*push([[:space:]]|$)' \
     && ! printf '%s' "$part" | grep -Eq -- '(--delete|[[:space:]]-d([[:space:]]|$)|[[:space:]]:[^[:space:]]+)'; then
    block "you are on master. Push a feature branch and open a PR instead of pushing master."
  fi
  if printf '%s' "$part" | grep -Eq '(^|[[:space:]])git[[:space:]]+((-C|-c|--git-dir|--work-tree)[[:space:]]+[^[:space:]]+[[:space:]]+|-[^[:space:]]*[[:space:]]+)*merge([[:space:]]|$)' \
     && ! printf '%s' "$part" | grep -Eq -- '--ff-only'; then
    block "you are on master. Merge through a PR (gh pr merge --merge); locally only 'git merge --ff-only origin/master' is allowed."
  fi
  if printf '%s' "$part" | grep -Eq '(^|[[:space:]])git[[:space:]]+((-C|-c|--git-dir|--work-tree)[[:space:]]+[^[:space:]]+[[:space:]]+|-[^[:space:]]*[[:space:]]+)*reset[[:space:]].*--hard'; then
    block "you are on master. 'git reset --hard' would throw away work; ask the user first."
  fi
done <<< "$parts"

exit 0
