# Shared helpers for detecting bot-protection evasion. Sourced (not run) by bot-protection-guard.sh,
# git-guard.sh and stop-checks.sh, so all three use the same patterns from evasion-patterns.txt.

EVASION_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# The suite files that are checked, as git pathspecs (here * also matches /).
EVASION_PATHSPECS=('hellocucumber/*.java' 'hellocucumber/pom.xml' 'hellocucumber/*.properties')

# The patterns joined into one extended regular expression.
evasion_pattern() {
  grep -vE '^[[:space:]]*(#|$)' "$EVASION_DIR/evasion-patterns.txt" | paste -sd'|' -
}

# Removes comments from stdin, so code and docs can explain the rule: // line comments, lines inside
# /* */ or Javadoc that start with *, # or ! property comments, and <!-- --> XML comments.
evasion_strip_comments() {
  sed -E -e 's#(^|[[:space:]])//.*$#\1#' -e 's#^[[:space:]]*(/\*|\*).*$##' -e 's#^[[:space:]]*[#!].*$##' -e 's#<!--.*-->##g'
}

# Prints the evasion patterns found in stdin (comments ignored), comma-separated; nothing if none.
evasion_hits() {
  evasion_strip_comments | grep -oiE "$(evasion_pattern)" | sort -u | paste -sd, -
}

# Prints the lines that uncommitted changes add to the suite files of the repository in the current
# directory: added lines of tracked files (staged or not) plus the whole content of new untracked files.
evasion_added_lines() {
  local base=HEAD
  git rev-parse -q --verify HEAD >/dev/null 2>&1 || base=--cached
  git diff "$base" -U0 --no-color -- "${EVASION_PATHSPECS[@]}" 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+' | cut -c2-
  git ls-files -z --others --exclude-standard -- "${EVASION_PATHSPECS[@]}" 2>/dev/null \
    | while IFS= read -r -d '' f; do [ -f "$f" ] && cat "$f"; done
}
