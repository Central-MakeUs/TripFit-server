#!/usr/bin/env bash
# ============================================================================
# TripFit 실제 푸시 알림(FCM) 테스트 트리거 스크립트
#
# 목적: NotificationEventListener의 6개 이벤트 핸들러 중, 실제 API 호출로
#       트리거 가능한 5개(가입완료·정원마감·정보수정·일정확정·확정취소)를
#       지정한 실 계정(TARGET_USER_ID)에게 실제로 발송한다.
#
# 왜 SQL만으로는 안 되는가: FCM 발송은 NotificationEventListener가
# @TransactionalEventListener로 실제 애플리케이션 이벤트를 받아
# FcmService(Firebase Admin SDK)를 호출해야 나간다. scripts/test-seeding.sql처럼
# DB에 직접 INSERT하면 데이터는 생기지만 이벤트가 발행되지 않아 푸시가 안 나간다.
# 그래서 이 스크립트는 실제 API(POST /trips/join, PATCH /trips/{id},
# POST /trips/{id}/recommendations, /confirm, /unconfirm)를 dev-login 토큰으로
# 직접 호출한다.
#
# 6번째(BR-NOTI-005 정기 알림)는 특정 유저 타겟팅이 안 되는 배치
# (ScheduleReminderBatch, 매월 1·15일 09시 KST)라 여기 포함하지 않는다 —
# 실행하면 알림 켜둔 전체 실 유저에게 발송되므로 제외.
#
# TARGET_USER_ID는 실제 배포 DB에 이미 가입돼 있는 유저여야 한다
# (notification_enabled=true여야 실제로 수신됨). dev-login은 testUserId 20자
# 제한 + 별도 socialId 체계라 실 계정으로 로그인할 수 없으므로, 이 스크립트는
# "notifyowner"/"notifyjoiner"라는 고정 dev 테스트 계정 2개를 만들어 그
# 계정들이 실제 API를 호출하게 하고, TARGET_USER_ID는 여행방의
# 방장/참여자로 DB에 직접 앉힌다(scripts/test-seeding.sql과 동일한 방식).
#
# 사용법:
#   ./scripts/notify-test-trigger.sh seed <target_user_id>
#     -> dev-login으로 더미 계정 2개 준비, DB 시딩용 SQL과 state 파일 생성.
#        생성된 .sql은 이 저장소의 기존 관례(deploy 문서)대로 배포 DB에서
#        직접 실행해야 한다(이 스크립트는 DB 접속 정보를 갖지 않음).
#   ./scripts/notify-test-trigger.sh fire <target_user_id>
#     -> seed SQL을 DB에서 실행 완료한 뒤 호출. 실제 API 5개를 순서대로 호출해
#        TARGET_USER_ID에게 5종류 알림을 발송한다.
#   ./scripts/notify-test-trigger.sh cleanup <target_user_id>
#     -> 정리용 DELETE SQL을 출력한다(자동 실행 안 함 — 직접 검토 후 DB에서 실행).
#
# 생성 파일(.gitignore 처리됨, 커밋 금지): scripts/.notify-test-trigger.<target>.sql
#                                          scripts/.notify-test-trigger.<target>.state
# ============================================================================
set -euo pipefail

API_BASE="${API_BASE:-https://api.tripfit.online}"
OWNER_KEY="notifyowner"
JOINER_KEY="notifyjoiner"

MODE="${1:-}"
TARGET_USER_ID="${2:-}"

if [[ -z "$MODE" || -z "$TARGET_USER_ID" ]]; then
  echo "사용법: $0 {seed|fire|cleanup} <target_user_id>" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="$SCRIPT_DIR/.notify-test-trigger.${TARGET_USER_ID}.sql"
STATE_FILE="$SCRIPT_DIR/.notify-test-trigger.${TARGET_USER_ID}.state"

dev_login() {
  local key="$1"
  curl -sS -X POST "$API_BASE/api/v1/auth/dev-login" \
    -H 'Content-Type: application/json' \
    -d "{\"testUserId\": \"$key\"}"
}

require_jq() {
  command -v jq >/dev/null || { echo "jq가 필요합니다." >&2; exit 1; }
}

case "$MODE" in
  seed)
    require_jq
    echo "==> 더미 계정 로그인: $OWNER_KEY, $JOINER_KEY"
    OWNER_LOGIN="$(dev_login "$OWNER_KEY")"
    JOINER_LOGIN="$(dev_login "$JOINER_KEY")"
    OWNER_ID="$(echo "$OWNER_LOGIN" | jq -r '.data.user.id')"
    JOINER_ID="$(echo "$JOINER_LOGIN" | jq -r '.data.user.id')"

    if [[ -z "$OWNER_ID" || "$OWNER_ID" == "null" ]]; then
      echo "더미 owner 계정 로그인 실패: $OWNER_LOGIN" >&2
      exit 1
    fi

    RUN_SUFFIX="$(date +%s | tail -c 9)"
    INVITE_CODE="NOTIFYJ${RUN_SUFFIX}"

    cat > "$SQL_FILE" <<EOF
-- 자동 생성 — notify-test-trigger.sh seed $TARGET_USER_ID
-- 배포 DB(tripfit)에서 직접 실행할 것. 실행 후 fire 단계를 진행한다.
SET NAMES utf8mb4;
START TRANSACTION;

SET @target_user_id = '${TARGET_USER_ID}';
SET @owner_dummy_id = '${OWNER_ID}';

-- Trip J: 대상 유저(@target_user_id)가 방장. 정원 2명 — 더미 joiner 1명이
-- 실제 POST /trips/join으로 들어오면 TripJoinCompletedEvent + AllMembersSubmittedEvent 발행
SET @tripJ = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@tripJ, @target_user_id, '[알림테스트] 가입완료-정원마감', NULL, '2026-09-01', '2026-09-10', NULL, NULL,
   2, '${INVITE_CODE}', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES (UUID(), @tripJ, @target_user_id, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- Trip I: 더미 owner(@owner_dummy_id)가 방장, 대상 유저는 참여자.
-- 방장이 실제 PATCH/추천생성/confirm/unconfirm API를 호출하면
-- TripInfoChangedEvent·TripConfirmedEvent·TripConfirmCanceledEvent가 대상 유저에게 발행됨
SET @tripI = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@tripI, @owner_dummy_id, '[알림테스트] 정보수정-확정-취소', '서울', '2026-09-01', '2026-09-20', 3, 2,
   2, CONCAT('NOTIFYI', '${RUN_SUFFIX}'), 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @tripI, @owner_dummy_id, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @tripI, @target_user_id, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

COMMIT;

SELECT '@tripJ' AS label, @tripJ AS trip_id, '${INVITE_CODE}' AS invite_code
UNION ALL
SELECT '@tripI', @tripI, NULL;
EOF

    cat > "$STATE_FILE" <<EOF
OWNER_ID=$OWNER_ID
JOINER_ID=$JOINER_ID
INVITE_CODE=$INVITE_CODE
RUN_SUFFIX=$RUN_SUFFIX
EOF
    echo "==> 생성 완료: $SQL_FILE"
    echo "==> 이 SQL을 배포 MySQL(tripfit DB)에서 직접 실행하세요 (deploy/README.md 접속 절차 참고)."
    echo "==> 실행 결과로 나온 @tripJ, @tripI id를 아래 두 줄로 $STATE_FILE 에 추가한 뒤 fire 단계를 실행하세요:"
    echo "    TRIP_J_ID=<실행 결과 @tripJ id>"
    echo "    TRIP_I_ID=<실행 결과 @tripI id>"
    echo "    (참고용 조회: SELECT id FROM trip WHERE invite_code='${INVITE_CODE}'; / 'NOTIFYI${RUN_SUFFIX}')"
    ;;

  fire)
    require_jq
    [[ -f "$STATE_FILE" ]] || { echo "state 파일이 없습니다. 먼저 seed를 실행하세요: $STATE_FILE" >&2; exit 1; }
    # shellcheck disable=SC1090
    source "$STATE_FILE"
    : "${TRIP_J_ID:?TRIP_J_ID를 $STATE_FILE 에 추가하세요 (DB 시딩 후 trip.id 조회 결과)}"
    : "${TRIP_I_ID:?TRIP_I_ID를 $STATE_FILE 에 추가하세요 (DB 시딩 후 trip.id 조회 결과)}"

    echo "==> 더미 계정 재로그인"
    OWNER_TOKEN="$(dev_login "$OWNER_KEY" | jq -r '.data.accessToken')"
    JOINER_TOKEN="$(dev_login "$JOINER_KEY" | jq -r '.data.accessToken')"

    echo "==> joiner 프로필 성·이름 채우기 (join API는 PROFILE_NAME_REQUIRED를 검사함 — notifyowner/giyeon 등 고정 3인 계정이 아니라 프리필이 안 됨)"
    curl -sS -X PATCH "$API_BASE/api/v1/users/profile" \
      -H "Authorization: Bearer $JOINER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"firstName": "알림", "lastName": "테스트"}' >/dev/null

    echo "==> [1/5+2/5] POST /trips/join (joiner) -> TripJoinCompletedEvent + AllMembersSubmittedEvent"
    curl -sS -X POST "$API_BASE/api/v1/trips/join" \
      -H "Authorization: Bearer $JOINER_TOKEN" -H 'Content-Type: application/json' \
      -d "{\"inviteCode\": \"$INVITE_CODE\"}" | jq .

    echo "==> [3/5] PATCH /trips/{tripI} (owner, destination 변경) -> TripInfoChangedEvent"
    curl -sS -X PATCH "$API_BASE/api/v1/trips/$TRIP_I_ID" \
      -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"name": "알림테스트방", "durationNights": 2, "durationDays": 3, "memberCount": 2, "destination": "부산"}' | jq .

    echo "==> 추천 생성 (confirm 전제조건) POST /trips/{tripI}/recommendations"
    curl -sS -X POST "$API_BASE/api/v1/trips/$TRIP_I_ID/recommendations" \
      -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"mode": "BASIC"}' | jq .

    echo "==> [4/5] POST /trips/{tripI}/confirm (owner, 직접 날짜) -> TripConfirmedEvent"
    curl -sS -X POST "$API_BASE/api/v1/trips/$TRIP_I_ID/confirm" \
      -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"startDate": "2026-09-05", "endDate": "2026-09-07"}' | jq .

    echo "==> [5/5] POST /trips/{tripI}/unconfirm (owner) -> TripConfirmCanceledEvent"
    curl -sS -X POST "$API_BASE/api/v1/trips/$TRIP_I_ID/unconfirm" \
      -H "Authorization: Bearer $OWNER_TOKEN" -H 'Content-Type: application/json' \
      -d '{"reason": "OTHER", "reasonDetail": "알림 테스트"}' | jq .

    echo "==> 완료. 5종류 알림이 대상 계정으로 발송되었어야 합니다."
    ;;

  cleanup)
    [[ -f "$STATE_FILE" ]] || { echo "state 파일이 없습니다: $STATE_FILE" >&2; exit 1; }
    # shellcheck disable=SC1090
    source "$STATE_FILE"
    cat <<EOF
-- 정리용 SQL — 검토 후 배포 DB에서 직접 실행하세요 (자동 실행 안 함)
START TRANSACTION;
DELETE tm FROM trip_member tm JOIN trip t ON t.id = tm.trip_id
  WHERE t.invite_code IN ('${INVITE_CODE}', 'NOTIFYI${RUN_SUFFIX}');
DELETE FROM trip WHERE invite_code IN ('${INVITE_CODE}', 'NOTIFYI${RUN_SUFFIX}');
-- 더미 owner/joiner 계정(notifyowner/notifyjoiner)은 재사용되므로 기본적으로 지우지 않음.
-- 필요 시: DELETE FROM users WHERE id IN ('${OWNER_ID}', '${JOINER_ID}');
COMMIT;
EOF
    ;;

  *)
    echo "알 수 없는 모드: $MODE (seed|fire|cleanup 중 하나)" >&2
    exit 1
    ;;
esac
