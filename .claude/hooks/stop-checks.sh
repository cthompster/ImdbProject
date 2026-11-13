#!/usr/bin/env bash
# Stop: checks before Claude ends its turn.
# 1. Compile check: if Java under hellocucumber/ changed, run `mvn -q test-compile` (-Xlint:all -Werror).
#    Never runs `mvn test`: that opens a browser against live IMDb and can trigger CAPTCHAs.
# 2. Keep-in-step reminder: a changed .feature file needs the READMEs/WIKI updated, and
#    junit-platform.properties and cucumber.properties must change together.
# Each check reports a given set of changes only once, so it doesn't repeat on every turn.
# Exit 2 makes Claude continue and deal with the message.
set -u

input=$(cat)
[ "$(printf '%s' "$input" | jq -r '.stop_hook_active // false')" = "true" ] && exit 0
session=$(printf '%s' "$input" | jq -r '.session_id // "default"')

dir="${CLAUDE_PROJECT_DIR:-.}"
cd "$dir" || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

changed=$(git status --porcelain --untracked-files=all | sed -E 's/^...//; s/.* -> //')
[ -z "$changed" ] && exit 0

state="${TMPDIR:-/tmp}/claude-imdb-stop-$session"
messages=()

# 1. Compile check, only when the Java code differs from the last version that compiled.
java_changed=$(printf '%s\n' "$changed" | grep -E '^hellocucumber/src/.*\.java$' || true)
if [ -n "$java_changed" ]; then
  java_hash=$( { printf '%s\n' "$java_changed"; git diff HEAD -- hellocucumber/src 2>/dev/null; \
    printf '%s\n' "$java_changed" | while IFS= read -r f; do [ -f "$f" ] && cat "$f"; done; } | shasum | cut -c1-40)
  if [ "$(cat "$state.compiled" 2>/dev/null)" != "$java_hash" ]; then
    # Personal toolchain paths (JAVA_HOME, PATH) live in a git-ignored file; see CLAUDE.md "Hooks".
    [ -f "$dir/.claude/hooks/env.local.sh" ] && . "$dir/.claude/hooks/env.local.sh"
    if command -v mvn >/dev/null 2>&1; then
      if out=$(cd hellocucumber && mvn -q test-compile 2>&1); then
        printf '%s' "$java_hash" > "$state.compiled"
      else
        messages+=("Java changed and 'mvn -q test-compile' fails (warnings are errors here). Fix it before finishing:"$'\n'"$(printf '%s\n' "$out" | grep -E 'ERROR|WARNING|error:|warning:' | head -20)")
      fi
    else
      messages+=("Java changed but mvn isn't on PATH, so the compile check was skipped. Run 'mvn -q test-compile' in hellocucumber/ before committing.")
      printf '%s' "$java_hash" > "$state.compiled"
    fi
  fi
fi

# 2. Keep-in-step reminder, once per distinct set of changed files.
reminders=()
if printf '%s\n' "$changed" | grep -Eq '^hellocucumber/src/test/resources/.*\.feature$' \
   && ! printf '%s\n' "$changed" | grep -Eq '^(README\.md|hellocucumber/README\.md|hellocucumber/WIKI\.md)$'; then
  reminders+=("A .feature file changed but README.md, hellocucumber/README.md and hellocucumber/WIKI.md didn't. Update the scenario tables and the status line, or tell the user why no update is needed.")
fi
jp=$(printf '%s\n' "$changed" | grep -c 'hellocucumber/src/test/resources/junit-platform.properties$' || true)
cp=$(printf '%s\n' "$changed" | grep -c 'hellocucumber/src/test/resources/cucumber.properties$' || true)
if [ "$jp" != "$cp" ]; then
  reminders+=("Only one of junit-platform.properties and cucumber.properties changed. Keep them in step (Maven reads the first, IntelliJ's right-click Run the second).")
fi
if [ ${#reminders[@]} -gt 0 ]; then
  changed_hash=$(printf '%s\n' "$changed" | sort | shasum | cut -c1-40)
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
