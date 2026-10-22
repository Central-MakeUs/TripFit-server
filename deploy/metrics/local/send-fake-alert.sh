#!/usr/bin/env bash
# 배포 전 로컬 점검 — 리포터에 Grafana 형식(Alertmanager)의 가짜 알림을 보낸다.
# 사용: ./local/send-fake-alert.sh firing|resolved|no-token
#   firing    가짜 "DB 커넥션 풀 대기" 발생 — 설명에 이메일·UUID·IP를 넣어 개인정보 가리기도 함께 본다
#   resolved  같은 알림의 해제
#   no-token  토큰 없이 보내 401이 나오는지 본다
set -euo pipefail

cd "$(dirname "$0")"
REPORTER="${REPORTER:-http://127.0.0.1:19095/alerts}"
TOKEN="$(grep -E '^REPORTER_TOKEN=' .env | cut -d= -f2-)"
STARTS_AT="${STARTS_AT:-2026-10-19T09:00:00Z}"

case "${1:-firing}" in
  firing) STATUS=firing; ENDS_AT="0001-01-01T00:00:00Z" ;;
  resolved) STATUS=resolved; ENDS_AT="2026-10-19T09:12:00Z" ;;
  no-token)
    curl -s -o /dev/null -w '%{http_code}\n' -X POST "$REPORTER" -d '{"alerts":[]}'
    exit 0 ;;
  *) echo "사용: $0 firing|resolved|no-token" >&2; exit 1 ;;
esac

curl -s -w ' (HTTP %{http_code})\n' -X POST "$REPORTER" \
  -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d @- <<JSON
{
  "receiver": "incident-reporter",
  "status": "${STATUS}",
  "alerts": [{
    "status": "${STATUS}",
    "labels": {"alertname": "DB 커넥션 풀 대기", "env": "local", "resource": "db_pool", "target": "db"},
    "annotations": {"summary": "가짜 점검 알림 — 담당 codus@example.com, 사용자 3f2b8c1e-9a4d-4e2f-8b1a-7c6d5e4f3a2b, 접속 203.0.113.7"},
    "startsAt": "${STARTS_AT}",
    "endsAt": "${ENDS_AT}",
    "fingerprint": "local-smoke-db-pool",
    "values": {"A": 3, "B": 0, "C": 1}
  }]
}
JSON
