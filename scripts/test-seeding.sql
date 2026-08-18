-- ============================================================================
-- TripFit 프론트 테스트용 데이터 시딩 스크립트
--
-- 대상 유저: 37a317f6-6f9d-4e6d-a548-8dd14e2c8a54 (배포 DB에 이미 가입돼 있음, 확인 완료)
-- 목적: 이 유저가 "방장(OWNER)"인 경우 6개 + "참여자(MEMBER)"인 경우 6개,
--       여행방 상태(ONGOING/CONFIRMED/EXPIRED)·정원마감·Pin·확정취소이력·
--       방장 일정확인 대기 등 서로 다른 상태를 최대한 다양하게 커버
--
-- 실행 대상: 배포 환경 MySQL (이 프로젝트는 dev 프로필이 유일한 실제 배포 환경).
-- Flyway/마이그레이션이 아니라 QA 테스트용 1회성 데이터 삽입 스크립트임.
-- 트랜잭션으로 감싸서 중간에 하나라도 실패하면 전부 롤백됨.
--
-- ⚠️ 참고: 배포 DB에는 이전 세션이 남긴 시드 데이터(id가 '00000000-0000-4000-8000-'로
-- 시작하는 유저 3명 + trip 6개)가 이미 존재함. 이번 스크립트는 그것과 겹치지 않도록
-- 전부 새 UUID(세션 변수 + UUID())로 생성함 — 정리하려면 아래 "정리(rollback)" 절 참고.
--
-- 식별 규칙:
--   - 더미 유저: email이 '%@tripfit.test'로 끝남, nickname에 '(테스트)' 표기
--   - 이번에 만든 trip: invite_code가 'SEED'로 시작 (SEEDO1~SEEDO6=방장쪽, SEEDM1~SEEDM6=참여자쪽)
-- ============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

SET @target_user_id = '37a317f6-6f9d-4e6d-a548-8dd14e2c8a54';

-- ----------------------------------------------------------------------------
-- 0. 더미 유저 9명
--    u1,u2: 방장쪽 시나리오(O3/O5)에서 대상 유저와 같은 방에 들어가는 동료 멤버
--    u3~u8: 참여자쪽 시나리오(M1~M6)에서 대상 유저가 들어가는 방의 방장
--    u9   : M2(정원마감) 방을 꽉 채우기 위한 추가 멤버
-- ----------------------------------------------------------------------------
SET @u1 = UUID();
SET @u2 = UUID();
SET @u3 = UUID();
SET @u4 = UUID();
SET @u5 = UUID();
SET @u6 = UUID();
SET @u7 = UUID();
SET @u8 = UUID();
SET @u9 = UUID();

USE tripfit;

INSERT INTO users
  (id, social_id, provider, email, first_name, last_name, nickname,
   profile_image_url, is_google_calendar_connected, is_all_free, notification_enabled,
   created_at, updated_at, deleted_at)
VALUES
  (@u1, 'tripfit-seed-37a317f6-u1', 'GOOGLE', 'seed.37a317f6.u1@tripfit.test', '민준', '김', '김민준(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u2, 'tripfit-seed-37a317f6-u2', 'GOOGLE', 'seed.37a317f6.u2@tripfit.test', '서연', '이', '이서연(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u3, 'tripfit-seed-37a317f6-u3', 'GOOGLE', 'seed.37a317f6.u3@tripfit.test', '도윤', '박', '박도윤(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u4, 'tripfit-seed-37a317f6-u4', 'GOOGLE', 'seed.37a317f6.u4@tripfit.test', '지우', '최', '최지우(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u5, 'tripfit-seed-37a317f6-u5', 'GOOGLE', 'seed.37a317f6.u5@tripfit.test', '하은', '정', '정하은(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u6, 'tripfit-seed-37a317f6-u6', 'GOOGLE', 'seed.37a317f6.u6@tripfit.test', '시우', '강', '강시우(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u7, 'tripfit-seed-37a317f6-u7', 'GOOGLE', 'seed.37a317f6.u7@tripfit.test', '수아', '조', '조수아(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u8, 'tripfit-seed-37a317f6-u8', 'GOOGLE', 'seed.37a317f6.u8@tripfit.test', '예준', '윤', '윤예준(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u9, 'tripfit-seed-37a317f6-u9', 'GOOGLE', 'seed.37a317f6.u9@tripfit.test', '지호', '장', '장지호(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL);

-- ============================================================================
-- 1. 방장(OWNER) 여행방 6개 — 대상 유저가 owner
-- ============================================================================

-- 1-1. O1: ONGOING, 방장 본인이 아직 일정 확인 전(SCHEDULE_PENDING) — 방 생성 직후 상태.
--      목적지·기간 전부 미정, 혼자만 있음(정원 4명 중 1명)
SET @o1 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o1, @target_user_id, '제주도 힐링 여행', NULL, '2026-09-01', '2026-09-10', NULL, NULL,
   4, 'SEEDO1', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES (UUID(), @o1, @target_user_id, 'OWNER', NOW(), NULL, 0, NULL, NOW(), NOW(), NULL);

-- 1-2. O2: ONGOING, 방장 일정 확인 완료(ACTIVE) — 초대코드 공유하며 멤버 기다리는 중.
--      목적지·기간 확정, 아직 본인만 참여(정원 4명 중 1명)
SET @o2 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o2, @target_user_id, '부산 미식 여행', '부산', '2026-09-05', '2026-09-12', 3, 2,
   4, 'SEEDO2', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES (UUID(), @o2, @target_user_id, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- 1-3. O3: ONGOING, 정원 마감(3/3) + 홈 화면 Pin ON — 방장 본인이 Pin 켜둔 상태
SET @o3 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o3, @target_user_id, '강릉 바다 여행', '강릉', '2026-08-15', '2026-08-20', 3, 2,
   3, 'SEEDO3', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @o3, @target_user_id, 'OWNER', NOW(), NOW(), 1, NOW(), NOW(), NOW(), NULL),
  (UUID(), @o3, @u1, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @o3, @u2, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- 1-4. O4: ONGOING인데 확정 취소(unconfirm) 이력이 있는 상태 — 한 번 CONFIRMED였다가
--      방장이 되돌려서 다시 ONGOING. confirmed_* 필드는 unconfirm 시 정책대로 NULL.
SET @o4 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o4, @target_user_id, '경주 역사 탐방', '경주', '2026-08-25', '2026-09-02', 4, 3,
   4, 'SEEDO4', 'ONGOING', NULL, NULL,
   NULL, 'NEW_SCHEDULE_ADDED', NULL, 'BASIC',
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @o4, @target_user_id, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @o4, @u1, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- 1-5. O5: CONFIRMED — 일정 확정, 확정 시점 인원 스냅샷 값 채움
SET @o5 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o5, @target_user_id, '여수 밤바다 여행', '여수', '2026-08-10', '2026-08-16', 3, 2,
   3, 'SEEDO5', 'CONFIRMED', '2026-08-12', '2026-08-14',
   NULL, NULL, NULL, 'BASIC',
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @o5, @target_user_id, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @o5, @u1, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @o5, @u2, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

UPDATE trip SET confirmed_attend_count = 3, confirmed_vacation_member_count = 1, confirmed_uncertain_count = 0 WHERE id = @o5;

-- 1-6. O6: EXPIRED — 확정까지 됐다가 희망 기간(end_range)이 지나 종료된 방
SET @o6 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@o6, @target_user_id, '전주 미식 여행', '전주', '2026-06-01', '2026-06-07', 3, 2,
   2, 'SEEDO6', 'EXPIRED', '2026-06-03', '2026-06-05',
   NULL, NULL, NULL, 'ALL_ATTEND',
   '2026-06-07 20:00:00', '2026-05-15 10:00:00', '2026-06-07 20:00:00', NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @o6, @target_user_id, 'OWNER', '2026-05-15 10:00:00', '2026-05-15 10:00:00', 0, NULL, '2026-05-15 10:00:00', '2026-05-15 10:00:00', NULL),
  (UUID(), @o6, @u1, 'MEMBER', '2026-05-16 09:00:00', '2026-05-16 09:00:00', 0, NULL, '2026-05-16 09:00:00', '2026-05-16 09:00:00', NULL);

UPDATE trip SET confirmed_attend_count = 2, confirmed_vacation_member_count = 0, confirmed_uncertain_count = 0 WHERE id = @o6;

-- ============================================================================
-- 2. 참여자(MEMBER) 여행방 6개 — 더미 유저가 owner, 대상 유저는 member
-- ============================================================================

-- 2-1. M1: ONGOING, 정원 미달(2/5) — 평범하게 초대 참여한 상태
SET @m1 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m1, @u3, '서울 벚꽃 나들이', '서울', '2026-09-10', '2026-09-15', 2, 1,
   5, 'SEEDM1', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m1, @u3, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @m1, @target_user_id, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- 2-2. M2: ONGOING, 정원 마감(3/3) — 대상 유저 참여 이후 방이 꽉 찬 상태
SET @m2 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m2, @u4, '속초 여행', '속초', '2026-08-20', '2026-08-25', 3, 2,
   3, 'SEEDM2', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m2, @u4, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @m2, @target_user_id, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @m2, @u9, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

-- 2-3. M3: ONGOING — 대상 유저가 이 방을 홈 화면에 Pin ON 해둔 상태
SET @m3 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m3, @u5, '통영 여행', '통영', '2026-09-01', '2026-09-08', 4, 3,
   4, 'SEEDM3', 'ONGOING', NULL, NULL,
   NULL, NULL, NULL, NULL,
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m3, @u5, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @m3, @target_user_id, 'MEMBER', NOW(), NOW(), 1, NOW(), NOW(), NOW(), NULL);

-- 2-4. M4: CONFIRMED — 대상 유저는 참여자로서 확정된 여행에 속해 있음
SET @m4 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m4, @u6, '거제 바다 여행', '거제', '2026-08-05', '2026-08-10', 3, 2,
   2, 'SEEDM4', 'CONFIRMED', '2026-08-06', '2026-08-08',
   NULL, NULL, NULL, 'CERTAIN',
   NOW(), NOW(), NOW(), NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m4, @u6, 'OWNER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL),
  (UUID(), @m4, @target_user_id, 'MEMBER', NOW(), NOW(), 0, NULL, NOW(), NOW(), NULL);

UPDATE trip SET confirmed_attend_count = 2, confirmed_vacation_member_count = 0, confirmed_uncertain_count = 0 WHERE id = @m4;

-- 2-5. M5: EXPIRED — 확정까지 가지 못하고(한 번도 CONFIRMED 안 됨) 그냥 종료된 방
SET @m5 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m5, @u7, '춘천 닭갈비 여행', '춘천', '2026-06-10', '2026-06-15', 3, 2,
   3, 'SEEDM5', 'EXPIRED', NULL, NULL,
   NULL, NULL, NULL, NULL,
   '2026-06-15 12:00:00', '2026-05-25 11:00:00', '2026-06-15 12:00:00', NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m5, @u7, 'OWNER', '2026-05-25 11:00:00', '2026-05-25 11:00:00', 0, NULL, '2026-05-25 11:00:00', '2026-05-25 11:00:00', NULL),
  (UUID(), @m5, @target_user_id, 'MEMBER', '2026-05-26 14:00:00', '2026-05-26 14:00:00', 0, NULL, '2026-05-26 14:00:00', '2026-05-26 14:00:00', NULL);

-- 2-6. M6: EXPIRED — CONFIRMED까지 됐다가 희망 기간이 지나 종료된 방 (참여자 시점)
SET @m6 = UUID();
INSERT INTO trip
  (id, owner_id, name, destination, start_range, end_range, duration_days, duration_nights,
   member_count, invite_code, status, confirmed_start_date, confirmed_end_date,
   cancel_reason, unconfirm_reason, unconfirm_reason_detail, last_recommendation_mode,
   last_activity_at, created_at, updated_at, deleted_at)
VALUES
  (@m6, @u8, '포항 여행', '포항', '2026-05-01', '2026-05-07', 3, 2,
   2, 'SEEDM6', 'EXPIRED', '2026-05-03', '2026-05-05',
   NULL, NULL, NULL, 'SAVE_VACATION',
   '2026-05-07 15:00:00', '2026-04-10 09:00:00', '2026-05-07 15:00:00', NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m6, @u8, 'OWNER', '2026-04-10 09:00:00', '2026-04-10 09:00:00', 0, NULL, '2026-04-10 09:00:00', '2026-04-10 09:00:00', NULL),
  (UUID(), @m6, @target_user_id, 'MEMBER', '2026-04-11 10:00:00', '2026-04-11 10:00:00', 0, NULL, '2026-04-11 10:00:00', '2026-04-11 10:00:00', NULL);

UPDATE trip SET confirmed_attend_count = 2, confirmed_vacation_member_count = 1, confirmed_uncertain_count = 0 WHERE id = @m6;

COMMIT;

-- ============================================================================
-- 검증 — 대상 유저 기준으로 이번에 만든 12개 방과 역할·상태를 한눈에 확인
-- ============================================================================
SELECT
  t.name, t.destination, t.status, t.invite_code,
  tm.role, tm.activated_at, tm.is_pinned,
  t.unconfirm_reason, t.confirmed_start_date, t.confirmed_end_date,
  t.member_count, (SELECT COUNT(*) FROM trip_member tm2 WHERE tm2.trip_id = t.id AND tm2.deleted_at IS NULL) AS joined_count
FROM trip t
JOIN trip_member tm ON tm.trip_id = t.id AND tm.user_id = '37a317f6-6f9d-4e6d-a548-8dd14e2c8a54'
WHERE t.invite_code LIKE 'SEED%'
ORDER BY t.invite_code;

-- ============================================================================
-- 정리(rollback) — 필요할 때만 아래를 별도로 선택해서 실행
-- (이번 스크립트가 만든 것만 정리함. 이전 세션이 남긴 '00000000-0000-4000-8000-%'
--  데이터는 별개이며 이 정리 절로 지워지지 않음 — 그건 지울지 여부를 먼저 확인할 것)
-- ============================================================================
-- DELETE tm FROM trip_member tm JOIN trip t ON t.id = tm.trip_id WHERE t.invite_code LIKE 'SEED%';
-- DELETE FROM trip WHERE invite_code LIKE 'SEED%';
-- DELETE FROM users WHERE email LIKE '%@tripfit.test';
