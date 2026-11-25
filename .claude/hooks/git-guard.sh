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
#   `push --delete <branch>` / `push origin :<branch>` (deleting a merged branch), and an `&&` chain
#   that starts by creating a new branch and doesn't switch back (`git checkout -b x && git commit`).
# Which repository: a command in one strict, simple form (see "Fast path" below) is checked against the
# repository each git command acts on. Every other command is checked as if it ran in this project.
# Limitation: outside the simple form, the whole command text is checked, so text that only mentions a
# blocked command (for example in a heredoc) can be blocked too. Put such text in a file instead.
set -u

if ! command -v jq >/dev/null 2>&1; then
  echo "git-guard hook is disabled: jq was not found." >&2
  exit 1
fi
. "$(dirname "$0")/lib-evasion.sh"
input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null) || { echo "git-guard hook: could not read its input." >&2; exit 1; }
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

# ----------------------------------------------------------------------------------------------------
# Fast path: a command in one strict, simple form is checked against the repository each git command
# really acts on, so work in another repository (on a feature branch) isn't blocked because this project
# is on master. A command that doesn't fit the form exactly falls through to the original checks below,
# which judge every git command as if it ran in this project.
#
# The form: segments joined by `&&` only, each one of
#   cd <folder>
#   git [-C <folder>]... [--no-pager] <subcommand> [<argument>]...   (no -c: core.worktree could move it)
#   gh pr|issue|repo|run|api|release <argument>...                    (not `gh pr checkout`)
# then either an optional ` 2>&1` and/or ` | tail [-n] N` / ` | head [-n] N` at the end of the line, or a
# final ` <<'WORD'` whose following lines are the message of a final `git commit … -F -`, closed by a last
# line that is exactly WORD. Nothing else: no ;, ||, |, &, newlines, $, backticks, brackets, redirections,
# globs, escapes, comments or variable assignments; words are separated by spaces and tabs only.
# A <folder> is an existing folder, written plainly or in quotes (`~` or `~/…` unquoted), with no `..`
# part; a `cd` folder must also be absolute or start with `~` or `./` (so CDPATH can't redirect it).
# An <argument> is plain text or quoted text (double quotes without $, backticks, backslashes or !), not
# starting with an unquoted ~.
# The git subcommand must be one of FAST_SUBS: none of them is meant to run other commands or to switch to
# an existing branch (repo hooks and merge drivers still run, as with any git command). In addition:
# checkout/switch only as `-b|-B|-c|-C|--create|--force-create <name>` (once, a separate word, name not
# master) with at most one start point and -t/--track/--no-track/-q/--quiet; branch without a move
# option (-m/-M/--m…); push, fetch and pull without --r…/--u…/--e…/--a…/--mi… options (--receive-pack,
# --upload-pack, --exec, --recurse-submodules, --all, --mirror) or wildcard refspecs (`*:*`). The rules see
# the arguments without their quotes. On master, a push counts as a deletion only when it is nothing but
# `push [-d|--delete] <remote> <name>...` or `push <remote> :<name>...`, and a merge/pull as --ff-only only
# when its options are just --ff-only, -q/--quiet or -v/--verbose.
# No git command may follow a gh command (gh pr merge can switch the local branch).
P_SP=$'[ \t]'
P_NSP=$'[^ \t]'
P_UNQ="[A-Za-z0-9_./=:@%+,^~-]"
P_SQ="'[^']*'"
P_DQ='"[^"$`\\!]*"'
P_WORD="^((${P_UNQ}|${P_SQ}|${P_DQ})+)(${P_SP}+|\$)"
P_AND="^&&(${P_SP}+|\$)"
FAST_SUBS=' status diff log show add rm mv restore commit push pull fetch merge stash tag remote rev-parse ls-files reset branch checkout switch '
fast_unq() { local t="$1"; t=${t//\'/}; t=${t//\"/}; printf '%s' "$t"; }   # a word without its quotes

# fast_folder BASE TOKEN [cd]: the existing absolute folder TOKEN names (relative to BASE), or fail.
fast_folder() {
  local base="$1" t="$2"
  case "/$(fast_unq "$t")/" in */../*) return 1 ;; esac
  if [ "${3:-}" = cd ]; then case "$t" in /*|"~"|"~/"*|./*|"'/"*|'"/'*|"'./"*|'"./'*) ;; *) return 1 ;; esac; fi
  case "$t" in
    \'*\') t=${t#\'}; t=${t%\'} ;;
    \"*\") t=${t#\"}; t=${t%\"} ;;
    "~") t=$HOME ;;
    "~/"*) t="$HOME/${t#\~/}" ;;
  esac
  case "$t" in ''|*\'*|*\"*|"~"*|-*) return 1 ;; esac
  case "$t" in /*) ;; *) t="$base/$t" ;; esac
  (cd "$t" 2>/dev/null && pwd -P)
}
fast_repo() {   # the repository of a folder: its common git directory (shared by all its worktrees)
  (cd "$1" 2>/dev/null || exit 1
   g=$(git rev-parse --git-common-dir 2>/dev/null) && [ -n "$g" ] && cd "$g" 2>/dev/null && pwd -P)
}

# fast_rules SEGMENT DIR: the same rules as the original checks below, for one git command acting on DIR.
fast_rules() {
  local seg="$1" d="$2" kind="${3:-}" top br hits
  top=$(git -C "$d" rev-parse --show-toplevel 2>/dev/null)
  if is_sub "$seg" 'push' && has "$seg" '[[:space:]]\+?([^[:space:]]*:)?(refs/heads/|heads/)?master([[:space:]]|$)'; then
    block "this pushes to master. Push a feature branch and merge it through a PR."
  fi
  if is_sub "$seg" 'add' && has "$seg" '[[:space:]](-f|--force|-[[:alpha:]]*f[[:alpha:]]*)([[:space:]]|$)' \
     && has "$seg" '(chrome-profile|[[:space:]](-A|--all|-[[:alpha:]]*A[[:alpha:]]*|\.|\./|\*|hellocucumber/?|hellocucumber/\*)([[:space:]]|$))'; then
    block "force-adding this path would stage hellocucumber/chrome-profile/, which is git-ignored on purpose (it holds cookies and IMDb's aws-waf-token). Add files by name without -f."
  fi
  # This project's own rules, when the command acts on this project's repository or work tree.
  if [ -z "$fast_project_repo" ] || [ "$(fast_repo "$d")" = "$fast_project_repo" ] \
     || { [ -n "$top" ] && [ "$top" = "$fast_project_top" ]; }; then
    if is_sub "$seg" 'commit' && git -C "$d" diff --cached --name-only 2>/dev/null | grep -q '^hellocucumber/chrome-profile/'; then
      block "files from hellocucumber/chrome-profile/ are staged. Unstage them (git restore --staged hellocucumber/chrome-profile) before committing."
    fi
    if is_sub "$seg" 'commit'; then
      if ! evasion_ready; then
        echo "git-guard: .claude/hooks/evasion-patterns.txt is missing or empty, so this commit wasn't checked for bot-protection evasion." >&2
        exit 1
      fi
      hits=$(cd "${top:-$d}" && evasion_changed_hits)
      if [ -n "$hits" ]; then
        block "uncommitted changes to the suite add bot-protection evasion ($hits). CLAUDE.md's hard rule: never bypass or evade IMDb's bot protection; a person solves CAPTCHAs. Remove it before committing."
      fi
    fi
  fi
  br=$(git -C "$d" symbolic-ref --short -q HEAD 2>/dev/null)
  [ "$br" = "master" ] || return 0
  # Exemption: the command started by creating a branch in this worktree.
  [ -n "$fast_exempt_top" ] && [ "$top" = "$fast_exempt_top" ] && return 0
  if is_sub "$seg" 'commit|revert|cherry-pick|am|rebase'; then
    block "you are on master. Create a branch first (git checkout -b <name>); master only changes through PRs."
  fi
  if is_sub "$seg" 'push' && [ "$kind" != del ]; then
    block "you are on master. Push a feature branch and open a PR instead of pushing master."
  fi
  if is_sub "$seg" 'merge|pull' && [ "$kind" != ff ]; then
    block "you are on master. Merge through a PR (gh pr merge --merge); locally only '--ff-only' updates are allowed."
  fi
  if is_sub "$seg" 'reset' && has "$seg" '--hard'; then
    block "you are on master. 'git reset --hard' would throw away work; ask the user first."
  fi
  if is_sub "$seg" 'clean' && has "$seg" '[[:space:]](-[[:alpha:]]*f[[:alpha:]]*|--force)([[:space:]]|$)'; then
    block "you are on master. 'git clean -f' deletes untracked files; ask the user first."
  fi
}

# fast_path: returns 1 (fall through to the original checks) unless the whole command fits the form;
# if it fits, applies the rules to each git command where it runs (blocking if one fails).
fast_path() {
  local text="$cmd" heredoc=0 line1 rest last delim re t cur i n k sub tok msg_ok
  local -a toks kinds texts dirs subs flags
  local seen_gh=0 a nm sp
  # A final heredoc: the message of the last command, which must be `git commit … -F -`.
  case "$text" in
    *$'\n'*)
      line1=${text%%$'\n'*}; rest=${text#*$'\n'}
      re="^(.*${P_NSP})${P_SP}+<<'([A-Za-z_][A-Za-z0-9_]*)'\$"
      [[ $line1 =~ $re ]] || return 1
      text=${BASH_REMATCH[1]}; delim=${BASH_REMATCH[2]}
      last=${rest##*$'\n'}
      [ "$last" = "$delim" ] || return 1
      # The first line equal to the delimiter must be the last line (nothing runs after the message).
      [ "$(printf '%s\n' "$rest" | grep -nxF -- "$delim" | sed -n '1s/:.*//p')" = "$(printf '%s\n' "$rest" | wc -l | tr -d ' ')" ] || return 1
      heredoc=1 ;;
    *)
      # Optional ` | tail -N` / ` | head -N`, then an optional ` 2>&1` before it.
      re="^(.*${P_NSP})${P_SP}+\\|${P_SP}+(tail|head)(${P_SP}+-n)?${P_SP}+-?[0-9]+\$"
      [[ $text =~ $re ]] && text=${BASH_REMATCH[1]}
      re="^(.*${P_NSP})${P_SP}+2>&1\$"
      [[ $text =~ $re ]] && text=${BASH_REMATCH[1]} ;;
  esac
  # Words and `&&`, nothing else.
  re="^${P_SP}*(.*)\$"; [[ $text =~ $re ]] && text=${BASH_REMATCH[1]}
  n=0
  while [ -n "$text" ]; do
    if [[ $text =~ $P_AND ]]; then toks[n]='&&'
    elif [[ $text =~ $P_WORD ]]; then toks[n]=${BASH_REMATCH[1]}
    else return 1
    fi
    text=${text:${#BASH_REMATCH[0]}}; n=$((n + 1))
  done
  [ "$n" -gt 0 ] || return 1

  # Segments: check each one's form and work out the folder of each git command.
  cur=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
  [ -n "$cur" ] && cur=$(cd "$cur" 2>/dev/null && pwd -P) || cur=$(cd "${CLAUDE_PROJECT_DIR:-.}" && pwd -P) || return 1
  i=0; k=0
  while [ "$i" -lt "$n" ]; do
    # One segment: tokens i .. before the next `&&`.
    local -a seg=(); local s=0
    while [ "$i" -lt "$n" ] && [ "${toks[i]}" != '&&' ]; do seg[s]=${toks[i]}; s=$((s + 1)); i=$((i + 1)); done
    [ "$s" -gt 0 ] || return 1
    if [ "$i" -lt "$n" ]; then i=$((i + 1)); [ "$i" -lt "$n" ] || return 1; fi   # skip `&&`, not last
    case "${seg[0]}" in
      cd)
        [ "$s" = 2 ] || return 1
        cur=$(fast_folder "$cur" "${seg[1]}" cd) || return 1
        kinds[k]=cd ;;
      git)
        local d="$cur" j=1
        while [ "$j" -lt "$s" ]; do
          case "${seg[j]}" in
            -C) j=$((j + 1)); [ "$j" -lt "$s" ] || return 1; d=$(fast_folder "$d" "${seg[j]}") || return 1 ;;
            --no-pager) ;;
            *) break ;;
          esac
          j=$((j + 1))
        done
        [ "$j" -lt "$s" ] || return 1
        sub=${seg[j]}
        [ "$seen_gh" = 0 ] || return 1
        case "$FAST_SUBS" in *" $sub "*) ;; *) return 1 ;; esac
        for tok in "${seg[@]:j}"; do case "$tok" in "~"*) return 1 ;; esac; done
        case "$sub" in
          checkout|switch)   # only creating a branch, and not one called master
            # Exactly one create flag with a separate name, at most one start point, and only -t/--track/
            # --no-track/-q/--quiet otherwise (git also takes -Bname, --force-create=name, --force-c name,
            # and the last of repeated flags wins, so anything else falls back).
            nm=""; sp=""; a=$((j + 1))
            while [ "$a" -lt "$s" ]; do
              case "$(fast_unq "${seg[a]}")" in
                -b|-B|-c|-C|--create|--force-create)
                  [ -z "$nm" ] || return 1
                  a=$((a + 1)); [ "$a" -lt "$s" ] || return 1; nm=$(fast_unq "${seg[a]}")
                  case "$nm" in -*|'') return 1 ;; esac ;;
                -t|--track|--no-track|-q|--quiet) ;;
                -*) return 1 ;;
                *) [ -z "$sp" ] || return 1; sp=1 ;;
              esac
              a=$((a + 1))
            done
            case "$nm" in ''|master|refs/heads/master|heads/master) return 1 ;; esac ;;
          branch)
            for tok in "${seg[@]:j+1}"; do
              case "$(fast_unq "$tok")" in --m*|-[mM]*|-[!-]*[mM]*) return 1 ;; esac
            done ;;
          push|fetch|pull)
            for tok in "${seg[@]:j+1}"; do
              case "$(fast_unq "$tok")" in --r*|--u*|--e*|--a*|--mi*|*ext::*|*\**) return 1 ;; esac
            done ;;
        esac
        # The rules see the subcommand and its arguments without quotes (so "master" is still master);
        # the -C folders are left out.
        texts[k]="git $sub"
        for tok in "${seg[@]:j+1}"; do texts[k]="${texts[k]} $(fast_unq "$tok")"; done
        # How the master rules may read it: a pure deletion push, or a pure --ff-only merge/pull.
        flags[k]=""
        case "$sub" in
          push)
            local dflag=0 colon=0 plain=0 other=0 w
            for tok in "${seg[@]:j+1}"; do
              w=$(fast_unq "$tok")
              case "$w" in
                -d|--delete) dflag=$((dflag + 1)) ;;
                -*) other=1 ;;
                :?*) colon=$((colon + 1)) ;;
                *:*) other=1 ;;
                *) plain=$((plain + 1)) ;;
              esac
            done
            # plain words: the remote, then branch names (deleted with -d); `:name` refspecs need no -d
            if [ "$other" = 0 ] && { { [ "$dflag" = 1 ] && [ "$colon" = 0 ] && [ "$plain" -ge 2 ]; } \
                 || { [ "$dflag" = 0 ] && [ "$colon" -ge 1 ] && [ "$plain" = 1 ]; }; }; then
              flags[k]=del
            fi ;;
          merge|pull)
            local ff=0 bad=0 w
            for tok in "${seg[@]:j+1}"; do
              w=$(fast_unq "$tok")
              case "$w" in
                --ff-only) ff=1 ;;
                -q|--quiet|-v|--verbose) ;;
                -*) bad=1 ;;
              esac
            done
            [ "$ff" = 1 ] && [ "$bad" = 0 ] && flags[k]=ff ;;
        esac
        kinds[k]=git; dirs[k]=$d; subs[k]=$sub ;;
      gh)
        [ "$s" -ge 2 ] || return 1
        case "${seg[1]}" in pr|issue|repo|run|api|release) ;; *) return 1 ;; esac
        [ "${seg[1]}" = pr ] && [ "$s" -ge 3 ] && [ "$(fast_unq "${seg[2]}")" = checkout ] && return 1
        for tok in "${seg[@]}"; do case "$tok" in "~"*) return 1 ;; esac; done
        seen_gh=1; kinds[k]=gh ;;
      *) return 1 ;;
    esac
    k=$((k + 1))
  done
  # A heredoc may only be the message of a final `git commit -F -`.
  if [ "$heredoc" = 1 ]; then
    [ "${kinds[k-1]}" = git ] || return 1
    msg_ok=0
    [ "${subs[k-1]}" = commit ] || return 1
    has " ${texts[k-1]} " '[[:space:]](-F[[:space:]]+-|-F-|--file=-|--file[[:space:]]+-)[[:space:]]' && msg_ok=1
    [ "$msg_ok" = 1 ] || return 1
  fi

  # The form fits: apply the rules where each git command runs.
  fast_project_repo=$(fast_repo "${CLAUDE_PROJECT_DIR:-.}")
  fast_project_top=$(git -C "${CLAUDE_PROJECT_DIR:-.}" rev-parse --show-toplevel 2>/dev/null)
  fast_exempt_top=""
  i=0
  while [ "$i" -lt "$k" ]; do
    if [ "${kinds[i]}" = git ]; then
      if [ -z "${fast_seen_git:-}" ]; then
        fast_seen_git=1
        # Exemption: the first git command creates a branch (checked above: not master).
        if [ "${subs[i]}" = checkout ] || [ "${subs[i]}" = switch ]; then
          fast_exempt_top=$(git -C "${dirs[i]}" rev-parse --show-toplevel 2>/dev/null)
        fi
      fi
      fast_rules "${texts[i]}" "${dirs[i]}" "${flags[i]}"
    fi
    i=$((i + 1))
  done
  return 0
}
fast_path && exit 0
# ----------------------------------------------------------------------------------------------------
# Original checks: every git command is judged as if it ran in this project.

dir="${CLAUDE_PROJECT_DIR:-.}"
branch=$(git -C "$dir" symbolic-ref --short -q HEAD 2>/dev/null)

# Check each part of a compound command separately. The lines with line continuations joined, as the
# shell reads them, are checked in addition to the lines as written (extra parts can only add blocks).
joined=""
case "$cmd" in *\\$'\n'*) joined=$(printf '%s\n' "$cmd" | awk '{ if (sub(/\\$/, "")) printf "%s", $0; else print }') ;; esac
parts=$(printf '%s\n%s\n' "$cmd" "$joined" | awk '{ gsub(/&&|\|\||;/, "\n"); print }')
# The first part that runs git (an earlier `cd`, even into ~/git/..., doesn't count).
first=$(printf '%s\n' "$parts" | grep -E "$G([[:space:]]|\$)" | sed -n '1p')

# Exemption on master: a command that starts by creating a new branch isn't acting on master. Only for
# pure `&&` chains (after `;`, `||`, a newline or a background `&` the next part runs even if creating the
# branch failed, and after a pipe `&&` tests the last command of the pipe; `2>&1`-style redirections are
# fine), not if the new branch could be master itself (`checkout -B master`, `-Bmaster`: that part
# mentions master anywhere), and not if a later part switches to another branch, such as back to master.
CREATE="(checkout|switch)[^;&|]*[[:space:]](-b|-B|-c|-C|--create|--force-create)([[:space:]]|\$)"
new_branch_first=0
if printf '%s' "$first" | grep -Eq "^[[:space:]]*git$OPTS[[:space:]]+$CREATE" \
   && ! printf '%s' "$first" | grep -q 'master' \
   && ! printf '%s' "$cmd" | grep -Eq '[;|]|(^|[^&>])&([^&>0-9]|$)' \
   && case "$cmd" in *$'\n'*) false ;; *) true ;; esac; then
  new_branch_first=1
  while IFS= read -r later; do
    if is_sub "$later" 'checkout|switch' && ! printf '%s' "$later" | grep -Eq "$CREATE"; then
      new_branch_first=0
    fi
  done <<< "$(printf '%s\n' "$parts" | f="$first" awk 'seen { print } $0 == ENVIRON["f"] { seen = 1 }')"
fi

while IFS= read -r part; do
  # --- Any branch ---
  if is_sub "$part" 'push' && has "$part" '[[:space:]]\+?([^[:space:]]*:)?(refs/heads/|heads/)?master([[:space:]]|$)'; then
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
    hits=$(cd "$dir" && evasion_changed_hits)
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
