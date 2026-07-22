#!/bin/bash
# Blocks destructive shell commands for TripFit project hooks.
# Hook input: JSON with "tool_input.command" field on stdin (Claude Code PreToolUse schema).

input=$(cat)

command=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('command', ''))
except Exception:
    print('')
" <<< "$input")

# Patterns: force push, recursive delete, hard reset, prod DB wipe
if echo "$command" | grep -qE \
  'git push (--force|-f)|rm -rf|git reset --hard|docker compose down -v|docker-compose down -v'; then
  echo "위험한 명령이 차단되었습니다 (force push, rm -rf, hard reset, docker compose down -v). Blocked by .claude/hooks/block-dangerous.sh — use safer alternatives or ask the user explicitly." >&2
  exit 2
fi

exit 0
