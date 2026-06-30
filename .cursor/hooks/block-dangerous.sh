#!/bin/bash
# Blocks destructive shell commands for TripFit project hooks.
# Hook input: JSON with "command" field on stdin.

input=$(cat)

command=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('command', ''))
except Exception:
    print('')
" <<< "$input")

# Patterns: force push, recursive delete, hard reset, prod DB wipe
if echo "$command" | grep -qE \
  'git push (--force|-f)|rm -rf|git reset --hard|docker compose down -v|docker-compose down -v'; then
  echo '{
    "permission": "deny",
    "user_message": "위험한 명령이 차단되었습니다 (force push, rm -rf, hard reset, docker compose down -v).",
    "agent_message": "Blocked by .cursor/hooks/block-dangerous.sh. Use safer alternatives or ask the user explicitly."
  }'
  exit 2
fi

echo '{ "permission": "allow" }'
exit 0
