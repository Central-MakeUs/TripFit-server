-- ============================================================================
-- TripFit 프론트 테스트용 데이터 시딩 스크립트
--
-- 대상 유저: 아래 @target_user_id 값을 원하는 유저의 UUID로 바꿔서 실행 (배포 DB에
--            이미 가입돼 있는 유저여야 함)
-- 목적: 이 유저가 "방장(OWNER)"인 경우 6개 + "참여자(MEMBER)"인 경우 6개,
--       여행방 상태(ONGOING/CONFIRMED/EXPIRED)·정원마감·Pin·확정취소이력·
--       방장 일정확인 대기 등 서로 다른 상태를 최대한 다양하게 커버
--
-- 실행 대상: 배포 환경 MySQL (이 프로젝트는 dev 프로필이 유일한 실제 배포 환경).
-- Flyway/마이그레이션이 아니라 QA 테스트용 1회성 데이터 삽입 스크립트임.
-- 트랜잭션으로 감싸서 중간에 하나라도 실패하면 전부 롤백됨.
--
-- ⚠️ 재실행 가능: @run_suffix를 매 실행마다 새로 뽑아 더미 유저의 social_id·email과
-- trip의 invite_code에 섞어 넣으므로, 같은 유저든 다른 유저든 이 스크립트를 여러 번
-- 실행해도 UNIQUE 제약(users.provider+social_id, trip.invite_code) 충돌이 나지 않음.
-- 검증·정리 쿼리도 이번 실행분(@run_suffix)만 대상으로 함 — 과거 실행분과 안 섞임.
--
-- 식별 규칙:
--   - 더미 유저: email이 '%@tripfit.test'로 끝남, nickname에 '(테스트)' 표기
--   - 이번에 만든 trip: invite_code가 'SEED'로 시작 (SEEDO1~SEEDO6=방장쪽, SEEDM1~SEEDM6=참여자쪽),
--     끝에 이번 실행의 @run_suffix가 붙음 (예: SEEDO1-a1b2c3d4)
-- ============================================================================

SET NAMES utf8mb4;

START TRANSACTION;

SET @target_user_id = 'c4b3c8c7-dcd2-4631-8237-73e107a0a394';
-- 실행마다 바뀌는 8자리 suffix — 더미 유저 social_id/email, trip invite_code에 붙여 재실행 충돌 방지
SET @run_suffix = SUBSTRING(REPLACE(UUID(), '-', ''), 1, 8);

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
  (@u1, CONCAT('tripfit-seed-', @run_suffix, '-u1'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u1@tripfit.test'), '민준', '김', '김민준(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u2, CONCAT('tripfit-seed-', @run_suffix, '-u2'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u2@tripfit.test'), '서연', '이', '이서연(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u3, CONCAT('tripfit-seed-', @run_suffix, '-u3'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u3@tripfit.test'), '도윤', '박', '박도윤(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u4, CONCAT('tripfit-seed-', @run_suffix, '-u4'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u4@tripfit.test'), '지우', '최', '최지우(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u5, CONCAT('tripfit-seed-', @run_suffix, '-u5'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u5@tripfit.test'), '하은', '정', '정하은(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u6, CONCAT('tripfit-seed-', @run_suffix, '-u6'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u6@tripfit.test'), '시우', '강', '강시우(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u7, CONCAT('tripfit-seed-', @run_suffix, '-u7'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u7@tripfit.test'), '수아', '조', '조수아(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u8, CONCAT('tripfit-seed-', @run_suffix, '-u8'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u8@tripfit.test'), '예준', '윤', '윤예준(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL),
  (@u9, CONCAT('tripfit-seed-', @run_suffix, '-u9'), 'GOOGLE', CONCAT('seed.', @run_suffix, '.u9@tripfit.test'), '지호', '장', '장지호(테스트)', NULL, 0, 0, 1, NOW(), NOW(), NULL);

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
   4, CONCAT('SEEDO1-', @run_suffix), 'ONGOING', NULL, NULL,
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
   4, CONCAT('SEEDO2-', @run_suffix), 'ONGOING', NULL, NULL,
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
   3, CONCAT('SEEDO3-', @run_suffix), 'ONGOING', NULL, NULL,
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
   4, CONCAT('SEEDO4-', @run_suffix), 'ONGOING', NULL, NULL,
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
   3, CONCAT('SEEDO5-', @run_suffix), 'CONFIRMED', '2026-08-12', '2026-08-14',
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
   2, CONCAT('SEEDO6-', @run_suffix), 'EXPIRED', '2026-06-03', '2026-06-05',
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
   5, CONCAT('SEEDM1-', @run_suffix), 'ONGOING', NULL, NULL,
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
   3, CONCAT('SEEDM2-', @run_suffix), 'ONGOING', NULL, NULL,
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
   4, CONCAT('SEEDM3-', @run_suffix), 'ONGOING', NULL, NULL,
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
   2, CONCAT('SEEDM4-', @run_suffix), 'CONFIRMED', '2026-08-06', '2026-08-08',
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
   3, CONCAT('SEEDM5-', @run_suffix), 'EXPIRED', NULL, NULL,
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
   2, CONCAT('SEEDM6-', @run_suffix), 'EXPIRED', '2026-05-03', '2026-05-05',
   NULL, NULL, NULL, 'SAVE_VACATION',
   '2026-05-07 15:00:00', '2026-04-10 09:00:00', '2026-05-07 15:00:00', NULL);

INSERT INTO trip_member (id, trip_id, user_id, role, joined_at, activated_at, is_pinned, pinned_at, created_at, updated_at, deleted_at)
VALUES
  (UUID(), @m6, @u8, 'OWNER', '2026-04-10 09:00:00', '2026-04-10 09:00:00', 0, NULL, '2026-04-10 09:00:00', '2026-04-10 09:00:00', NULL),
  (UUID(), @m6, @target_user_id, 'MEMBER', '2026-04-11 10:00:00', '2026-04-11 10:00:00', 0, NULL, '2026-04-11 10:00:00', '2026-04-11 10:00:00', NULL);

UPDATE trip SET confirmed_attend_count = 2, confirmed_vacation_member_count = 1, confirmed_uncertain_count = 0 WHERE id = @m6;

COMMIT;

-- ============================================================================
-- 검증 — 대상 유저 기준으로 이번 실행(@run_suffix)이 만든 12개 방과 역할·상태를 한눈에 확인
-- ============================================================================
SELECT
  t.name, t.destination, t.status, t.invite_code,
  tm.role, tm.activated_at, tm.is_pinned,
  t.unconfirm_reason, t.confirmed_start_date, t.confirmed_end_date,
  t.member_count, (SELECT COUNT(*) FROM trip_member tm2 WHERE tm2.trip_id = t.id AND tm2.deleted_at IS NULL) AS joined_count
FROM trip t
JOIN trip_member tm ON tm.trip_id = t.id AND tm.user_id = @target_user_id
WHERE t.invite_code LIKE CONCAT('SEED%-', @run_suffix)
ORDER BY t.invite_code;

-- ============================================================================
-- 정리(rollback) — 필요할 때만 아래를 별도로 선택해서 실행
-- 세션이 끊기면 @run_suffix 값을 잃으므로, 정리하려는 실행분의 SELECT 결과에서
-- invite_code 뒤의 suffix(예: SEEDO1-a1b2c3d4 → a1b2c3d4)를 확인해 SET @run_suffix로
-- 다시 지정한 뒤 아래를 실행할 것.
-- ============================================================================
-- SET @run_suffix = '이번에-정리할-suffix';
-- DELETE tm FROM trip_member tm JOIN trip t ON t.id = tm.trip_id WHERE t.invite_code LIKE CONCAT('SEED%-', @run_suffix);
-- DELETE FROM trip WHERE invite_code LIKE CONCAT('SEED%-', @run_suffix);
-- DELETE FROM users WHERE email LIKE CONCAT('seed.', @run_suffix, '.%@tripfit.test');

-- 과거 실행분을 전부 한 번에 정리하고 싶을 때(모든 run_suffix 대상 — 신중히 사용):
-- DELETE tm FROM trip_member tm JOIN trip t ON t.id = tm.trip_id WHERE t.invite_code LIKE 'SEED%';
-- DELETE FROM trip WHERE invite_code LIKE 'SEED%';
-- DELETE FROM users WHERE email LIKE '%@tripfit.test';
