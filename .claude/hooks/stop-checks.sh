#!/usr/bin/env bash
# Stop: checks before Claude ends its turn.
# 1. Evasion check: uncommitted changes to the suite must not add bot-protection evasion
#    (evasion-patterns.txt), including changes made through Bash that bot-protection-guard.sh never saw.
#    Runs first and reports at once (skipping the compile), so a compile cut off by the hook timeout
#    can't hide it.
# 2. Compile check: if uncommitted changes touch Java under hellocucumber/src or hellocucumber/pom.xml,
#    run `mvn -q test-compile` (-Xlint:all -Werror). Never runs `mvn test`: that opens a browser against
#    live IMDb and can trigger CAPTCHAs.
# 3. Keep-in-step reminder: a changed .feature file needs the READMEs/WIKI updated, and
#    junit-platform.properties and cucumber.properties must change together.
# The compile result and the reminders are reported once per state: a compile failure is not re-run or
# re-reported until the code changes again, and a reminder is not repeated for the same set of changed
# files. Evasion is reported on every turn until it is removed.
# Exit 2 makes Claude continue and deal with the message.
set -u

# Without jq, stop_hook_active can't be read, so blocking could loop. Warn instead.
if ! command -v jq >/dev/null 2>&1; then
  echo "stop-checks hook is disabled: jq was not found." >&2
  exit 1
fi
HOOKS_DIR=$(cd "$(dirname "$0")" && pwd)
input=$(cat)
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null || echo true)" = "false" ] || exit 0
session=$(printf '%s' "$input" | jq -r '.session_id // "default"' 2>/dev/null)
session=${session//[^A-Za-z0-9-]/}
[ -n "$session" ] || session=default

dir="${CLAUDE_PROJECT_DIR:-.}"
cd "$dir" || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

# Uncommitted changes (working tree, index and untracked files), one unquoted path per line.
changed=$( { git diff -z --name-only HEAD 2>/dev/null; git diff -z --name-only --cached 2>/dev/null
             git ls-files -z --others --exclude-standard; } | tr '\0' '\n' | sed '/^$/d' | sort -u)
[ -z "$changed" ] && exit 0

state="${TMPDIR:-/tmp}/claude-imdb-stop-$session"
messages=()

# 1. Bot-protection evasion in uncommitted changes, however they were made (Edit, Bash, IDE). Unlike the
#    other checks this is reported on every turn until it is removed: it breaks a hard rule.
. "$HOOKS_DIR/lib-evasion.sh"
if ! evasion_ready; then
  messages+=(".claude/hooks/evasion-patterns.txt is missing or empty, so uncommitted changes weren't checked for bot-protection evasion.")
else
  evasion=$(evasion_changed_hits)
  if [ -n "$evasion" ]; then
    # Reported at once, before the compile: if mvn ran into the hook timeout, nothing after it would be
    # shown. The compile can wait; the code has to change anyway.
    echo "Checks from .claude/hooks/stop-checks.sh:" >&2
    echo "- Uncommitted changes to the suite add bot-protection evasion ($evasion). CLAUDE.md's hard rule: never bypass or evade IMDb's bot protection; a person solves CAPTCHAs. Remove it (also from the staged copy), and don't run the suite with it." >&2
    exit 2
  fi
fi

# 2. Compile check.
build_changed=$(printf '%s\n' "$changed" | grep -E '^hellocucumber/(src/.*\.java|pom\.xml)$' || true)
if [ -n "$build_changed" ]; then
  code_hash=$( { printf '%s\n' "$build_changed"; printf '%s\n' "$build_changed" | while IFS= read -r f; do [ -f "$f" ] && cat "$f"; done; } | shasum | cut -c1-40)
  if [ "$(cat "$state.checked" 2>/dev/null)" != "$code_hash" ]; then
    # Personal toolchain paths (JAVA_HOME, PATH) live in a git-ignored file; see CLAUDE.md "Hooks".
    [ -f "$dir/.claude/hooks/env.local.sh" ] && . "$dir/.claude/hooks/env.local.sh"
    if command -v mvn >/dev/null 2>&1; then
      out=$(cd hellocucumber && mvn -q test-compile 2>&1); rc=$?
      # Recorded only once mvn has finished, so a run killed by the hook timeout is retried next time.
      printf '%s' "$code_hash" > "$state.checked"   # this state is reported once, pass or fail
      if [ "$rc" -ne 0 ]; then
        details=$(printf '%s\n' "$out" | grep -E 'ERROR|WARNING|error:|warning:' | head -20)
        [ -n "$details" ] || details=$(printf '%s\n' "$out" | tail -20)
        messages+=("The code changed and 'mvn -q test-compile' fails (warnings are errors here). Fix it before finishing; this is reported once until the code changes again:"$'\n'"$details")
      fi
    else
      printf '%s' "$code_hash" > "$state.checked"
      messages+=("The code changed but mvn isn't on PATH, so the compile check was skipped. Run 'mvn -q test-compile' in hellocucumber/ before committing.")
    fi
  fi
fi

# 3. Keep-in-step reminder, once per distinct set of changed files.
reminders=()
if printf '%s\n' "$changed" | grep -Eq '^hellocucumber/src/test/resources/.*\.feature$' \
   && ! printf '%s\n' "$changed" | grep -Eq '^(README\.md|hellocucumber/README\.md|hellocucumber/WIKI\.md)$'; then
  reminders+=("A .feature file changed but README.md, hellocucumber/README.md and hellocucumber/WIKI.md didn't. Update the scenario tables and the status line, or tell the user why no update is needed.")
fi
jp=$(printf '%s\n' "$changed" | grep -c '^hellocucumber/src/test/resources/junit-platform.properties$' || true)
cp=$(printf '%s\n' "$changed" | grep -c '^hellocucumber/src/test/resources/cucumber.properties$' || true)
if [ "$jp" != "$cp" ]; then
  reminders+=("Only one of junit-platform.properties and cucumber.properties changed. Keep them in step (Maven reads the first, IntelliJ's right-click Run the second).")
fi
if [ ${#reminders[@]} -gt 0 ]; then
  changed_hash=$(printf '%s\n' "$changed" | shasum | cut -c1-40)
  if [ "$(cat "$state.reminded" 2>/dev/null)" != "$changed_hash" ]; then
    printf '%s' "$changed_hash" > "$state.reminded"
    messages+=("${reminders[@]}")
  fi
fi

if [ ${#messages[@]} -gt 0 ]; then
  echo "Checks from .claude/hooks/stop-checks.sh:" >&2
  printf -- '- %s\n' "${messages[@]}" >&2
  exit 2
fi
exit 0
