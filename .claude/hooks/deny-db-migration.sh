#!/bin/bash
# Blocks creating Flyway/Liquibase migration files for TripFit project hooks.
# core-guardrails.md STOP §3 — no prod-preserved data; schema SSOT is JPA entities + ddl-auto, not migration files.
# Hook input: JSON with "tool_input.file_path" field on stdin (Claude Code PreToolUse schema, Write/Edit).

input=$(cat)

file_path=$(python3 -c "
import json, sys
try:
    data = json.load(sys.stdin)
    print(data.get('tool_input', {}).get('file_path', ''))
except Exception:
    print('')
" <<< "$input")

# Patterns: Flyway/Liquibase migration dir, or Flyway naming convention (V1__x.sql, R__x.sql) anywhere
if echo "$file_path" | grep -qE '/db/migration/|/(V[0-9]+(\.[0-9]+)*|R)__[^/]*\.sql$'; then
  echo "Flyway/Liquibase 마이그레이션 파일 생성이 차단되었습니다 (core-guardrails.md STOP §3 — 상용 보존 데이터 없음, 스키마 SSOT는 JPA 엔티티 + ddl-auto). Blocked by .claude/hooks/deny-db-migration.sh — 정말 필요하면 사용자에게 먼저 확인하세요." >&2
  exit 2
fi

exit 0
