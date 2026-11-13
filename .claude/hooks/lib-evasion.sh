# Shared helpers for detecting bot-protection evasion. Sourced (not run) by bot-protection-guard.sh,
# git-guard.sh and stop-checks.sh, so all three use the same patterns (evasion-patterns.txt), the same
# list of suite files and the same comment handling.

EVASION_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
EVASION_PATTERNS_FILE="$EVASION_DIR/evasion-patterns.txt"

# The suite files that are checked: code, build and config files, and resources or helper scripts that
# could launch or configure the browser. Generated javaDoc/ and target/ are skipped.
# The two lists below say the same thing in two notations; keep them in step.
EVASION_PATHSPECS=('hellocucumber/*.java' 'hellocucumber/pom.xml' 'hellocucumber/*.properties'
  'hellocucumber/*.js' 'hellocucumber/*.json' 'hellocucumber/*.sh' 'hellocucumber/*.py' 'hellocucumber/.mvn/*'
  ':(exclude)hellocucumber/javaDoc/*' ':(exclude)hellocucumber/target/*')
evasion_is_suite_file() {   # absolute or relative path
  case "/$1" in
    */hellocucumber/javaDoc/*|*/hellocucumber/target/*) return 1 ;;
    */hellocucumber/*.java|*/hellocucumber/pom.xml|*/hellocucumber/*.properties|*/hellocucumber/*.js|\
    */hellocucumber/*.json|*/hellocucumber/*.sh|*/hellocucumber/*.py|*/hellocucumber/.mvn/*) return 0 ;;
  esac
  return 1
}

# True when the patterns file exists and has at least one pattern. Without it the checks would match
# nothing and silently allow everything, so callers warn instead.
evasion_ready() {
  [ -f "$EVASION_PATTERNS_FILE" ] && grep -qvE '^[[:space:]]*(#|$)' "$EVASION_PATTERNS_FILE"
}

# The patterns joined into one extended regular expression.
evasion_pattern() {
  grep -vE '^[[:space:]]*(#|$)' "$EVASION_PATTERNS_FILE" | paste -sd'|' -
}

# Removes comments from stdin, so code and docs can explain the rule, while keeping any code on the same
# line: closed /* ... */ and <!-- ... --> comments are cut out; a line that opens a comment it doesn't
# close, a Javadoc line ("* text" or a bare "*") and a "# ..." line are dropped; a // comment is cut from
# the line unless the // is inside a string literal ("http://..." is never treated as a comment).
evasion_strip_comments() {
  sed -E -e 's#/\*([^*]|\*+[^*/])*\*+/##g' -e 's#<!--([^-]|-[^-])*-->##g' \
         -e 's#^[[:space:]]*/\*.*$##' -e 's#^[[:space:]]*\*([[:space:]].*)?$##' -e 's#^[[:space:]]*\#.*$##' \
  | awk '{
      line = $0; n = length(line); inq = 0
      for (i = 1; i <= n; i++) {
        c = substr(line, i, 1)
        if (c == "\\") { i++; continue }
        if (c == "\"") { inq = !inq; continue }
        if (!inq && c == "/" && substr(line, i + 1, 1) == "/" && (i == 1 || substr(line, i - 1, 1) ~ /[[:space:]]/)) {
          line = substr(line, 1, i - 1); break
        }
      }
      print line
    }'
}

# Prints the evasion patterns found in stdin (comments ignored), comma-separated; nothing if none.
evasion_hits() {
  evasion_strip_comments | grep -oiE "$(evasion_pattern)" | sort -u | paste -sd, -
}

# Prints the lines that uncommitted changes add to the suite files of the repository in the current
# directory: added lines in the working tree and in the index (staged content can differ from the file
# on disk), plus the whole content of new untracked files.
evasion_added_lines() {
  local opts=(-U0 --no-color --src-prefix=a/ --dst-prefix=b/)
  {
    if git rev-parse -q --verify HEAD >/dev/null 2>&1; then
      git diff HEAD "${opts[@]}" -- "${EVASION_PATHSPECS[@]}"
    fi
    git diff --cached "${opts[@]}" -- "${EVASION_PATHSPECS[@]}"
  } 2>/dev/null | grep -E '^\+' | grep -vE '^\+\+\+ (b/|/dev/null)' | cut -c2-
  git ls-files -z --others --exclude-standard -- "${EVASION_PATHSPECS[@]}" 2>/dev/null \
    | while IFS= read -r -d '' f; do [ -f "$f" ] && cat "$f"; done
}
