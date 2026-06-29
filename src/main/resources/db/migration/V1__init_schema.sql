-- ERD 확정 후 3단계(prod) 전환 시 적용. 배포 후 immutable — 수정 금지, V2부터 append.
-- 1~2단계(dev/local)에서는 flyway.enabled=false 이므로 실행되지 않음.

CREATE TABLE `user` (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    nickname        VARCHAR(255) NOT NULL,
    profile_image_url TEXT       NULL,
    provider        ENUM ('APPLE', 'GOOGLE', 'KAKAO') NOT NULL,
    social_id       VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_provider_social_id (provider, social_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_condition (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at                 DATETIME(6)  NOT NULL,
    updated_at                 DATETIME(6)  NOT NULL,
    is_half_vacation_available BIT(1)       NOT NULL,
    is_holiday_rest            BIT(1)       NOT NULL,
    max_vacation_days          INT          NULL,
    vacation_apply_period      VARCHAR(255) NULL,
    work_days                  VARCHAR(255) NULL,
    work_end_time              TIME         NULL,
    work_start_time            TIME         NULL,
    user_id                    BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_condition_user_id (user_id),
    CONSTRAINT fk_user_condition_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE trip (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    deleted_at           DATETIME(6)  NULL,
    confirmed_end_date   DATE         NULL,
    confirmed_start_date DATE         NULL,
    duration_days        INT          NOT NULL,
    end_range            DATE         NOT NULL,
    invite_code          VARCHAR(255) NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    start_range          DATE         NOT NULL,
    status               ENUM ('CANCELED', 'CONFIRMED', 'ONGOING') NOT NULL,
    target_member_count  INT          NOT NULL,
    owner_id             BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_invite_code (invite_code),
    CONSTRAINT fk_trip_owner FOREIGN KEY (owner_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE trip_member (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    joined_at  DATETIME(6) NOT NULL,
    role       ENUM ('MEMBER', 'OWNER') NOT NULL,
    status     ENUM ('JOINED', 'RESPONDED') NOT NULL,
    trip_id    BIGINT      NOT NULL,
    user_id    BIGINT      NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_member_trip_user (trip_id, user_id),
    CONSTRAINT fk_trip_member_trip FOREIGN KEY (trip_id) REFERENCES trip (id),
    CONSTRAINT fk_trip_member_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE member_schedule (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    note           VARCHAR(255) NULL,
    schedule_date  DATE         NOT NULL,
    status         ENUM ('IMPOSSIBLE', 'POSSIBLE', 'TBD') NOT NULL,
    time_slot      ENUM ('AFTERNOON', 'EVENING', 'MORNING') NOT NULL,
    trip_member_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_schedule_slot (trip_member_id, schedule_date, time_slot),
    CONSTRAINT fk_member_schedule_trip_member FOREIGN KEY (trip_member_id) REFERENCES trip_member (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE recommendation (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    created_at          DATETIME(6)  NOT NULL,
    end_date            DATE         NOT NULL,
    recommendation_rank INT          NOT NULL,
    reason              TEXT         NULL,
    risk_note           TEXT         NULL,
    score               DOUBLE       NULL,
    start_date          DATE         NOT NULL,
    trip_id             BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recommendation_trip FOREIGN KEY (trip_id) REFERENCES trip (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci;
