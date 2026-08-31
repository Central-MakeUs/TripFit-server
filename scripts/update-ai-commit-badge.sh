#!/usr/bin/env bash
set -euo pipefail

# shields.io endpoint 배지용 JSON 생성 (스키마: https://shields.io/badges/endpoint-badge)
# "AI co-authored commit" = 커밋 메시지에 Claude 또는 Cursor의 Co-Authored-By 트레일러가 있는 커밋.
# 두 트레일러가 같은 커밋에 동시에 있는 경우는 --grep을 두 번 쓰는 OR(합집합) 매칭이라 중복 집계되지 않는다.
total=$(git rev-list --count HEAD)
ai=$(git log --grep="co-authored-by:.*claude" --grep="co-authored-by:.*cursor" -i -E --oneline | wc -l | tr -d ' ')

mkdir -p .github/badges
cat > .github/badges/ai-commits.json <<EOF
{
  "schemaVersion": 1,
  "label": "AI co-authored commits",
  "message": "${ai}/${total}",
  "color": "6366F1"
}
EOF

echo "AI co-authored commits: ${ai}/${total}"
