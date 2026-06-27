#!/bin/bash
# Blocks destructive shell commands (force push, rm -rf).
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

if echo "$command" | grep -qE 'git push (--force|-f)|rm -rf'; then
  echo '{
    "permission": "deny",
    "user_message": "위험한 명령이 차단되었습니다 (force push, rm -rf).",
    "agent_message": "This command was blocked by project hook block-dangerous.sh."
  }'
  exit 2
fi

echo '{ "permission": "allow" }'
exit 0
