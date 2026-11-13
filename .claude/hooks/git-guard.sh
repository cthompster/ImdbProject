#!/usr/bin/env bash
# PreToolUse (Bash): enforce the git workflow in CLAUDE.md. Exit 2 blocks the command and shows the reason.
#
# On any branch:
# - never push to master (e.g. `git push origin HEAD:master`); master only changes through PRs;
# - never stage the git-ignored Chrome profile (cookies and the CAPTCHA pass): no force-adding it or
#   force-adding broadly (`git add -f .`, `-A`, `hellocucumber`), and no commit while any of it is staged;
# - no commit while uncommitted changes to the suite add bot-protection evasion (evasion-patterns.txt),
#   which catches changes made through Bash or an IDE that bot-protection-guard.sh never saw.
# On master:
# - no commit, revert, cherry-pick, am, rebase, merge, pull, push, `reset --hard` or `clean -f`;
# - allowed: `merge --ff-only` / `pull --ff-only` (updating master after a PR merge),
#   `push --delete <branch>` / `push origin :<branch>` (deleting a merged branch), and any command
#   `&&` chain that starts by creating a new branch and doesn't switch back (`git checkout -b x && git commit`).
# Limitation: the whole command text is checked, so text that only mentions a blocked command (for
# example in a heredoc) can be blocked too. Put such text in a file instead.
set -u

if ! command -v jq >/dev/null 2>&1; then
  echo "git-guard hook is disabled: jq was not found." >&2
  exit 1
fi
. "$(dirname "$0")/lib-evasion.sh"
cmd=$(jq -r '.tool_input.command // empty' 2>/dev/null) || { echo "git-guard hook: could not read its input." >&2; exit 1; }
[ -z "$cmd" ] && exit 0
case "$cmd" in *git*) ;; *) exit 0 ;; esac

block() {
  echo "Blocked by .claude/hooks/git-guard.sh: $1" >&2
  exit 2
}

# `git` as a command word (also after "(", a quote or a path such as /usr/bin/git), then git's own
# options (-C <dir>, -c <k=v>, --no-pager, ...), then the subcommand.
G='(^|[^[:alnum:]_.-])git'
ARG="('[^']*'|\"[^\"]*\"|[^[:space:]]+)"
OPTS="([[:space:]]+((-C|-c|--git-dir|--work-tree)[[:space:]]+$ARG|-[^[:space:]]*))*"
is_sub() { printf '%s' "$1" | grep -Eq "$G$OPTS[[:space:]]+($2)([[:space:]]|\$)"; }
has() { printf '%s' "$1" | grep -Eq -- "$2"; }

dir="${CLAUDE_PROJECT_DIR:-.}"
branch=$(git -C "$dir" symbolic-ref --short -q HEAD 2>/dev/null)

# Check each part of a compound command separately.
parts=$(printf '%s' "$cmd" | awk '{ gsub(/&&|\|\||;/, "\n"); print }')
# The first part that runs git (an earlier `cd`, even into ~/git/..., doesn't count).
first=$(printf '%s\n' "$parts" | grep -E "$G([[:space:]]|\$)" | sed -n '1p')

# Exemption on master: a command that starts by creating a new branch isn't acting on master. Only for
# `&&` chains (with `;` or `||` the next part runs even if creating the branch failed), and not if a later
# part switches to another branch, such as back to master.
CREATE="(checkout|switch)[^;&|]*[[:space:]](-b|-B|-c|-C|--create|--force-create)([[:space:]]|\$)"
new_branch_first=0
if printf '%s' "$first" | grep -Eq "^[[:space:]]*git$OPTS[[:space:]]+$CREATE" \
   && ! printf '%s' "$cmd" | grep -Eq ';|\|\|'; then
  new_branch_first=1
  while IFS= read -r later; do
    if is_sub "$later" 'checkout|switch' && ! printf '%s' "$later" | grep -Eq "$CREATE"; then
      new_branch_first=0
    fi
  done <<< "$(printf '%s\n' "$parts" | awk -v f="$first" 'seen { print } $0 == f { seen = 1 }')"
fi

while IFS= read -r part; do
  # --- Any branch ---
  if is_sub "$part" 'push' && has "$part" '[[:space:]]\+?([^[:space:]]*:)?(refs/heads/)?master([[:space:]]|$)'; then
    block "this pushes to master. Push a feature branch and merge it through a PR."
  fi
  if is_sub "$part" 'add' && has "$part" '[[:space:]](-f|--force|-[[:alpha:]]*f[[:alpha:]]*)([[:space:]]|$)' \
     && has "$part" '(chrome-profile|[[:space:]](-A|--all|-[[:alpha:]]*A[[:alpha:]]*|\.|\./|\*|hellocucumber/?|hellocucumber/\*)([[:space:]]|$))'; then
    block "force-adding this path would stage hellocucumber/chrome-profile/, which is git-ignored on purpose (it holds cookies and IMDb's aws-waf-token). Add files by name without -f."
  fi
  if is_sub "$part" 'commit' && git -C "$dir" diff --cached --name-only 2>/dev/null | grep -q '^hellocucumber/chrome-profile/'; then
    block "files from hellocucumber/chrome-profile/ are staged. Unstage them (git restore --staged hellocucumber/chrome-profile) before committing."
  fi
  # Bot-protection evasion in any uncommitted change to the suite, however it was made (Edit, Bash, IDE).
  # Checks the working tree, the index and new files, because `git add -A && git commit` stages in the
  # same command, and staged content can differ from the file on disk.
  if is_sub "$part" 'commit'; then
    if ! evasion_ready; then
      echo "git-guard: .claude/hooks/evasion-patterns.txt is missing or empty, so this commit wasn't checked for bot-protection evasion." >&2
      exit 1
    fi
    hits=$(cd "$dir" && evasion_added_lines | evasion_hits)
    if [ -n "$hits" ]; then
      block "uncommitted changes to the suite add bot-protection evasion ($hits). CLAUDE.md's hard rule: never bypass or evade IMDb's bot protection; a person solves CAPTCHAs. Remove it before committing."
    fi
  fi

  # --- On master only ---
  [ "$branch" = "master" ] || continue
  [ "$new_branch_first" = 1 ] && continue
  if is_sub "$part" 'commit|revert|cherry-pick|am|rebase'; then
    block "you are on master. Create a branch first (git checkout -b <name>); master only changes through PRs."
  fi
  if is_sub "$part" 'push' && ! has "$part" '(--delete|[[:space:]]-d([[:space:]]|$)|[[:space:]]:[^[:space:]]+)'; then
    block "you are on master. Push a feature branch and open a PR instead of pushing master."
  fi
  if is_sub "$part" 'merge|pull' && ! has "$part" '--ff-only'; then
    block "you are on master. Merge through a PR (gh pr merge --merge); locally only '--ff-only' updates are allowed."
  fi
  if is_sub "$part" 'reset' && has "$part" '--hard'; then
    block "you are on master. 'git reset --hard' would throw away work; ask the user first."
  fi
  if is_sub "$part" 'clean' && has "$part" '[[:space:]](-[[:alpha:]]*f[[:alpha:]]*|--force)([[:space:]]|$)'; then
    block "you are on master. 'git clean -f' deletes untracked files; ask the user first."
  fi
done <<< "$parts"

exit 0
