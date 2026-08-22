#!/bin/bash
# PostToolUse: auto-format Java files after Edit/Write (format-on-save).
# Converts the previously prose-only "핵심 로직 변경 시 ./gradlew test" formatting convention
# into a deterministic guarantee instead of relying on the agent to remember. Never blocks —
# always exits 0, and skips entirely (zero cost) for non-Java files.
# Hook input: JSON with "tool_input.file_path" (Write) / PostToolUse "tool_response.filePath" on stdin.

input=$(cat)

file_path=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    ti = data.get('tool_input', {}) or {}
    tr = data.get('tool_response', {}) or {}
    print(ti.get('file_path') or tr.get('filePath') or '')
except Exception:
    print('')
" <<< "$input")

case "$file_path" in
  *.java) ;;
  *) exit 0 ;;
esac

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$repo_root" || exit 0

./gradlew spotlessApply -q >/dev/null 2>&1 || true

exit 0
