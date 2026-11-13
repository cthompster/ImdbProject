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

# The comment syntax of a file: c (// and /* */: Java, JSON), js (like c, plus `...` template literals),
# xml (<!-- -->), hash (# after whitespace: Python, shell), properties (# or ! at the start of a line),
# plain (no comments).
evasion_file_type() {
  case "$1" in
    *.java|*.json) echo c ;;
    *.js) echo js ;;
    *.xml) echo xml ;;
    *.py|*.sh) echo hash ;;
    *.properties) echo properties ;;
    *) echo plain ;;
  esac
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

# Removes the comments of file type $1 from stdin and keeps all code, including code on the same line as
# a comment. Knows string literals ("..." and '...', with backslash escapes, plus multi-line Java text
# blocks """...""" and JavaScript `...` template literals), so comment markers inside a string
# ("http://...", "**/*.java", '//', a text block containing /*) are not comments. Nothing inside a string
# is ever removed, so misreading where a string ends can only leave extra text to check.
# Known gap: a JavaScript regex literal containing /* (e.g. /[/*]/) is read as opening a comment.
# Block comments (/* */, <!-- -->) are followed across lines the way the compiler reads them: a line is
# inside one only if an earlier opening marker is still open, so Javadoc is skipped while a code line
# that starts with "* " (a line broken before a multiplication) is checked. A line equal to
# $EVASION_RESET resets that state; diff scans emit it at every hunk, so an unclosed marker in one part
# of a file can't hide lines from another. Text that starts in the middle of an existing block comment
# (an Edit of a Javadoc line without its /** line) is checked as code: a harmless extra block, never a
# missed one.
EVASION_RESET='@@EVASION_RESET@@'
evasion_strip_comments() {
  local type="${1:-plain}"
  awk -v type="$type" -v reset="$EVASION_RESET" '
    BEGIN { blk = 0; xml = 0; tb = 0; tl = 0; cl = (type == "c" || type == "js") }
    $0 == reset { blk = 0; xml = 0; tb = 0; tl = 0; print ""; next }
    type == "properties" && /^[[:space:]]*[#!]/ { print ""; next }
    {
      line = $0; n = length(line); out = ""; q = ""
      for (i = 1; i <= n; i++) {
        c = substr(line, i, 1); nx = substr(line, i + 1, 1)
        prev = (i == 1) ? " " : substr(line, i - 1, 1)
        if (tb) {                                   # inside a Java text block """..."""
          out = out c
          if (c == "\\") { out = out nx; i++ }
          else if (substr(line, i, 3) == "\"\"\"") { out = out "\"\""; i += 2; tb = 0 }
          continue
        }
        if (tl) {                                   # inside a JavaScript template literal
          out = out c
          if (c == "\\") { out = out nx; i++ }
          else if (c == "`") tl = 0
          continue
        }
        if (q != "") {                              # inside a string literal
          out = out c
          if (c == "\\") { out = out nx; i++ }
          else if (c == q) q = ""
          continue
        }
        if (blk) { if (c == "*" && nx == "/") { blk = 0; i++ } ; continue }
        if (xml) { if (substr(line, i, 3) == "-->") { xml = 0; i += 2 } ; continue }
        if (cl && substr(line, i, 3) == "\"\"\"") { tb = 1; out = out "\"\"\""; i += 2; continue }
        if (type == "js" && c == "`") { tl = 1; out = out c; continue }
        if ((c == "\"" || c == "\x27") && type != "plain") { q = c; out = out c; continue }
        if (cl && c == "/" && nx == "*") { blk = 1; i++; continue }
        if (cl && c == "/" && nx == "/" && prev ~ /[[:space:]]/) break
        if (type == "xml" && substr(line, i, 4) == "<!--") { xml = 1; i += 3; continue }
        if (type == "hash" && c == "#" && prev ~ /[[:space:]]/) break
        out = out c
      }
      print out
    }'
}

# Prints the evasion patterns found in stdin (comments of file type $1 ignored), comma-separated.
evasion_hits() {
  evasion_strip_comments "${1:-plain}" | grep -oiE "$(evasion_pattern)" | sort -u | paste -sd, -
}

# Prints the evasion patterns that uncommitted changes add to the suite files of the repository in the
# current directory, comma-separated. Looks at the lines added in the working tree and in the index (staged
# content can differ from the file on disk) and at the whole content of new untracked files, each file with
# its own comment syntax.
evasion_changed_hits() {
  local opts=(-U0 --inter-hunk-context=0 --no-color --no-ext-diff --no-textconv --src-prefix=a/ --dst-prefix=b/)
  local has_head=0 untracked f
  git rev-parse -q --verify HEAD >/dev/null 2>&1 && has_head=1
  # -z so names come out unquoted, the same as in the list below.
  untracked=$(git ls-files -z --others --exclude-standard -- "${EVASION_PATHSPECS[@]}" 2>/dev/null | tr '\0' '\n')
  {
    [ "$has_head" = 1 ] && git diff -z --name-only HEAD -- "${EVASION_PATHSPECS[@]}"
    git diff -z --name-only --cached -- "${EVASION_PATHSPECS[@]}"
    git ls-files -z --others --exclude-standard -- "${EVASION_PATHSPECS[@]}"
  } 2>/dev/null | tr '\0' '\n' | sed '/^$/d' | sort -u | while IFS= read -r f; do
    if printf '%s\n' "$untracked" | grep -qxF -- "$f"; then
      [ -f "$f" ] && cat "$f"
    else
      {
        [ "$has_head" = 1 ] && git diff HEAD "${opts[@]}" -- "$f"
        git diff --cached "${opts[@]}" -- "$f"
      } 2>/dev/null | awk -v reset="$EVASION_RESET" '
          /^@@/ { print reset; next }                   # a new hunk: block-comment state starts over
          /^\+\+\+ (b\/|\/dev\/null)/ { next }          # file header, not content
          /^\+/ { print substr($0, 2) }'
    fi | evasion_hits "$(evasion_file_type "$f")"
  done | tr ',' '\n' | sed '/^$/d' | sort -u | paste -sd, -
}
