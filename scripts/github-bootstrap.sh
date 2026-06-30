#!/usr/bin/env bash
# GitHub labels + milestones 일괄 생성 (1회 실행)
# Requires: gh auth login
set -euo pipefail

if ! command -v gh >/dev/null 2>&1; then
  echo "FAIL: gh CLI required — https://cli.github.com/"
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "FAIL: run 'gh auth login' first"
  exit 1
fi

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)"
echo "[github-bootstrap] repo: $REPO"

create_label() {
  local name="$1"
  local color="$2"
  local description="${3:-}"
  if gh label create "$name" --color "$color" --description "$description" --force 2>/dev/null; then
    echo "  label: $name"
  fi
}

echo "[github-bootstrap] labels..."
# type
create_label "type: feature" "0E8A16" "새 기능·API"
create_label "type: bug" "D73A4A" "버그"
create_label "type: chore" "FBCA04" "CI, docs, 리팩터"
create_label "type: docs" "0075CA" "문서만"
# area
create_label "area: api" "1D76DB" "REST API / controller"
create_label "area: domain" "5319E7" "엔티티·도메인 규칙"
create_label "area: deploy" "B60205" "Docker, EC2, CI"
create_label "area: docs" "0E8A16" "docs/ 기획·아키텍처"
create_label "area: infra" "666666" "인프라·설정"
# priority (mvp.md)
create_label "priority: P0" "B60205" "MVP 필수"
create_label "priority: P1" "D93F0B" "MVP 중요"
create_label "priority: P2" "FEF2C0" "MVP 이후 가능"
# size (harness-workflow S/M/T)
create_label "size: S" "C2E0C6" "스펙 필요 — 큰 변경"
create_label "size: M" "BFDADC" "중간"
create_label "size: T" "EDEDED" "작은 수정"
create_label "blocked" "000000" "선행 작업 대기"

create_milestone() {
  local title="$1"
  local description="$2"
  local existing
  existing="$(gh api "repos/${REPO}/milestones?state=all" --jq ".[] | select(.title==\"${title}\") | .number" 2>/dev/null | head -1)"
  if [[ -n "$existing" ]]; then
    echo "  milestone: ${title} (already #${existing})"
    return 0
  fi
  gh api "repos/${REPO}/milestones" \
    -f title="${title}" \
    -f description="${description}" \
    -f state=open >/dev/null
  echo "  milestone: ${title} (created)"
}

echo "[github-bootstrap] milestones..."
create_milestone "MVP-1 — 여행방·일정·추천 (P0)" \
  "docs/product/mvp.md P0: 여행방 생성/참여, 근무·연차 조건, 일정 추천, 확정 플로우"

create_milestone "MVP-2 — 알림·시각화 (P1)" \
  "docs/product/mvp.md P1: 리마인드 알림, 메시지 공유, 그룹 달력 시각화"

create_milestone "MVP-3 — 편의·고도화 (P2)" \
  "docs/product/mvp.md P2: 소셜 연동 상세, 알림 설정 등"

echo "[github-bootstrap] done"
echo "Create issues: GitHub UI templates or ask Agent — gh issue create ..."
