#!/usr/bin/env bash
# Open·Closed issues → wave labels + milestones (재실행 가능)
# SSOT: docs/product/waves.md · 이슈 본문 wave·선행은 GitHub issue
# wave별 이슈 조회: gh issue list --label wave:N --state all
set -euo pipefail

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo "[github-sync-issues] repo: $REPO"

strip_old_labels() {
  local n="$1"
  gh issue edit "$n" \
    --remove-label "type: feature" 2>/dev/null || true
  gh issue edit "$n" \
    --remove-label "priority: P0" --remove-label "priority: P1" \
    --remove-label "priority: P2" --remove-label "priority: out" \
    --remove-label "priority: nice" 2>/dev/null || true
  gh issue edit "$n" \
    --remove-label "size: S" --remove-label "size: M" --remove-label "size: T" 2>/dev/null || true
}

set_issue() {
  local n="$1" wave="$2" milestone="$3" kind="${4:-feature}" area="${5:-api}"
  if ! gh issue view "$n" --json number -q .number >/dev/null 2>&1; then
    echo "  skip #${n} (not found)"
    return 0
  fi
  strip_old_labels "$n"
  for w in 1 2 3 4; do
    gh issue edit "$n" --remove-label "wave:${w}" 2>/dev/null || true
  done
  gh issue edit "$n" \
    --add-label "wave:${wave}" \
    --add-label "kind: ${kind}" \
    --add-label "area: ${area}" \
    --milestone "$milestone"
  echo "  issue #${n} → wave:${wave}, kind:${kind}, ${milestone}"
}

# wave 1 — 기반
set_issue 1  1 "Wave 1 — 준비"
set_issue 3  1 "Wave 1 — 준비"
set_issue 10 1 "Wave 1 — 준비"
set_issue 22 1 "Wave 1 — 준비"
set_issue 24 1 "Wave 1 — 준비"

# wave 2 — 핵심 (+ D5 defer · Out 후속)
set_issue 11 2 "Wave 2 — 핵심 MVP"
set_issue 12 2 "Wave 2 — 핵심 MVP"
set_issue 13 2 "Wave 2 — 핵심 MVP"
set_issue 17 2 "Wave 2 — 핵심 MVP"
set_issue 19 2 "Wave 2 — 핵심 MVP"
set_issue 20 2 "Wave 2 — 핵심 MVP"
set_issue 26 2 "Wave 2 — 핵심 MVP"
set_issue 27 2 "Wave 2 — 핵심 MVP"

# wave 2 — docs (Closed)
set_issue 14 2 "Wave 2 — 핵심 MVP" docs docs

# wave 3 — 마무리
set_issue 21 3 "Wave 3 — 출시 UX"

# wave 4 — 이후
set_issue 4 4 "Wave 4 — 운영·확장"
set_issue 5 4 "Wave 4 — 운영·확장"
set_issue 6 4 "Wave 4 — 운영·확장"
set_issue 9 4 "Wave 4 — 운영·확장"

# Wave Backlog SSOT (kind: chore · area: docs)
set_issue 29 1 "Wave 1 — 준비" chore docs
set_issue 30 2 "Wave 2 — 핵심 MVP" chore docs
set_issue 31 3 "Wave 3 — 출시 UX" chore docs
set_issue 32 4 "Wave 4 — 운영·확장" chore docs

# stale temp issue
if gh issue view 2 --json state -q .state 2>/dev/null | grep -q OPEN; then
  gh issue close 2 --comment "정리: 중복·임시 이슈" 2>/dev/null || true
  gh issue edit 2 --add-label "meta:duplicate" 2>/dev/null || true
  echo "  issue #2 closed"
fi

echo "[github-sync-issues] done"
echo ""
echo "wave 2 Must vs 후속: docs/product/waves.md § wave 2 — 핵심 Must vs 후속"
