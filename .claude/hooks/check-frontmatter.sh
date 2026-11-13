#!/usr/bin/env bash
# PostToolUse (Edit|Write|MultiEdit): check the frontmatter of agent and skill files after every edit.
# A bad frontmatter makes the agent or skill silently disappear (this happened in PR #13, when a list
# was put between the --- lines). Only settings belong there, one "key: value" per line.
# Exit 2 reports the problem to Claude so it fixes the file.
set -u

if ! command -v jq >/dev/null 2>&1; then
  echo "check-frontmatter hook is disabled: jq was not found." >&2
  exit 1
fi
file=$(jq -r '.tool_input.file_path // empty' 2>/dev/null) || exit 0
case "$file" in
  */.claude/agents/*.md) kind=agent
    allowed='name description tools disallowedTools model color permissionMode skills mcpServers hooks memory effort isolation background maxTurns initialPrompt' ;;
  */.claude/skills/*/SKILL.md) kind=skill
    allowed='name description allowed-tools disallowed-tools argument-hint model effort disable-model-invocation user-invocable context agent hooks version license metadata when_to_use paths shell' ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

# Ignore Windows line endings when reading.
content=$(tr -d '\r' < "$file")
errors=()
if ! printf '%s\n' "$content" | head -n 1 | grep -qE '^---[[:space:]]*$'; then
  errors+=("line 1 must be '---'")
else
  end=$(printf '%s\n' "$content" | awk 'NR>1 && /^---[[:space:]]*$/ {print NR; exit}')
  if [ -z "$end" ]; then
    errors+=("no closing '---' line")
  elif [ "$end" -eq 2 ]; then
    errors+=("the frontmatter is empty; it needs at least 'name' and 'description'")
  else
    seen=" "
    lineno=1
    while IFS= read -r line; do
      lineno=$((lineno + 1))
      [ -z "${line//[[:space:]]/}" ] && continue
      case "$line" in [[:space:]]*|\#*) continue ;; esac   # indented continuation, or a YAML comment
      if [[ ! "$line" =~ ^([A-Za-z][A-Za-z0-9_-]*):([[:space:]](.*))?$ ]]; then
        errors+=("line $lineno is not 'key: value': $line")
        continue
      fi
      key="${BASH_REMATCH[1]}"; value="${BASH_REMATCH[3]:-}"
      case " $allowed " in *" $key "*) ;; *) errors+=("line $lineno: '$key' is not a known $kind setting (content belongs below the closing ---)") ;; esac
      case "$value" in
        \"*|\'*) ;;
        *": "*|*" #"*) errors+=("line $lineno: the value of '$key' contains ': ' or ' #', which breaks YAML; quote the whole value") ;;
        \`*|@*|%*|\&*|\**|\!*) errors+=("line $lineno: the value of '$key' starts with a YAML indicator character (\` @ % & * !); quote the whole value") ;;
      esac
      # A list or map where text is expected ("[WIP] ...", "{...}", "- ..."). Flow lists are fine elsewhere,
      # e.g. "tools: [Read, Grep]".
      case "$key" in name|description)
        case "$value" in \[*|\{*|"- "*) errors+=("line $lineno: the value of '$key' starts with '[', '{' or '- ', which YAML reads as a list or map; quote the whole value") ;; esac ;;
      esac
      seen="$seen$key "
    done < <(printf '%s\n' "$content" | sed -n "2,$((end - 1))p")
    for required in name description; do
      case "$seen" in *" $required "*) ;; *) errors+=("missing '$required'") ;; esac
    done
  fi
fi

if [ ${#errors[@]} -gt 0 ]; then
  echo "Frontmatter problem in ${file#"${CLAUDE_PROJECT_DIR:-}"/} (the $kind will not load until it's fixed):" >&2
  printf '  - %s\n' "${errors[@]}" >&2
  exit 2
fi
exit 0
