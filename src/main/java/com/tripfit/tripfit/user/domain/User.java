package com.tripfit.tripfit.user.domain;

import com.tripfit.tripfit.common.domain.SoftDeleteEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "social_id"}))
@Schema(description = "TripFit 서비스 사용자. 식별 키는 (provider, social_id). 테이블명 users (MySQL 예약어 회피)")
public class User extends SoftDeleteEntity {

  public static final int DEFAULT_MAX_VACATION_DAYS = 2;

  public static final int MAX_VACATION_DAYS_LIMIT = 10;

  @Schema(
      description = "사용자 고유 ID (TripFit 내부 PK, UUID v4)",
      example = "550e8400-e29b-41d4-a716-446655440000")
  @Id
  @GeneratedValue
  @UuidGenerator
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36, nullable = false, updatable = false)
  @Setter
  private UUID id;

  @Schema(description = "소셜 제공자 고유 사용자 ID (Google/Apple `sub`, Kakao `id`)", example = "1234567890")
  @Column(nullable = false)
  private String socialId;

  @Schema(description = "소셜 로그인 제공자")
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SocialProvider provider;

  @Schema(
      description = "소셜 계정 이메일. Apple relay·미제공 시 null. UNIQUE·식별 키 아님",
      nullable = true,
      example = "user@example.com")
  @Column
  private String email;

  @Schema(description = "유저 입력 이름 (필수, PATCH profile). 미입력 시 null", nullable = true, example = "길동")
  @Column(name = "first_name")
  private String firstName;

  @Schema(description = "유저 입력 성 (필수, PATCH profile). 미입력 시 null", nullable = true, example = "홍")
  @Column(name = "last_name")
  private String lastName;

  @Schema(
      description = "소셜 provider 표시명 (prefill·참고용). 미제공 시 null — fallback 없음",
      nullable = true,
      example = "홍길동")
  @Column
  private String nickname;

  @Schema(
      description = "프로필 이미지 URL. wave 1(A안): provider CDN URL 그대로. wave 4(B안): TripFit S3 URL 예정",
      nullable = true,
      example = "https://lh3.googleusercontent.com/a/example")
  @Column(name = "profile_image_url")
  private String profileImageUrl;

  @Schema(description = "Google Calendar OAuth 연동 여부", example = "false")
  @Column(name = "is_google_calendar_connected", nullable = false)
  private boolean isGoogleCalendarConnected;

  @Schema(description = "알림 수신 여부(BR-USER-005). default true — false면 NOTI-001~005·009 전부 미발송",
      example = "true")
  @Column(name = "notification_enabled", nullable = false)
  private boolean notificationEnabled;

  @Schema(description = "여행당 사용 가능 최대 연차 일수. default 2, 최대 10", example = "2")
  @Column(name = "max_vacation_days", nullable = false)
  private int maxVacationDays = DEFAULT_MAX_VACATION_DAYS;

  @Schema(
      description = "연차 신청 가능 시점(사전 신청일). null = 사전 일정 입력 미완료 — 최초/갱신 입력 판정 마커",
      nullable = true)
  @Enumerated(EnumType.STRING)
  @Column(name = "vacation_apply_period")
  private VacationApplyPeriod vacationApplyPeriod;

  @Schema(description = "반차 사용 가능 여부. default false(N)", example = "false")
  @Column(name = "is_half_vacation_available", nullable = false)
  private boolean halfVacationAvailable;

  @Schema(description = "공휴일 휴무 여부. default true(Y)", example = "true")
  @Column(name = "is_holiday_rest", nullable = false)
  private boolean holidayRest = true;

  public User(
      String socialId,
      SocialProvider provider,
      String email,
      String nickname,
      String profileImageUrl) {
    this.socialId = socialId;
    this.provider = provider;
    this.email = email;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
    this.isGoogleCalendarConnected = false;
    this.notificationEnabled = true;
  }

  // 성·이름이 모두 입력됐는지 확인함 (온보딩 필수 프로필 완료)
  public boolean hasProfileNameComplete() {
    return firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank();
  }

  // 사용자 표시명 결정 — 성+이름 → nickname → "사용자" 기본값
  public String displayName() {
    if (hasProfileNameComplete()) {
      return lastName + firstName;
    }
    if (nickname != null && !nickname.isBlank()) {
      return nickname;
    }
    return "사용자";
  }

  // 탈퇴(soft-deleted) 계정이 같은 소셜 계정으로 재로그인하면 기존 row를 그대로 부활시킴 — (provider, social_id)
  // UNIQUE 제약 때문에 신규 row를 새로 만들 수 없어 기존 row를 재사용. firstName/lastName·구글 캘린더 연동은
  // 탈퇴 시 초기화된 채로 남아 재온보딩이 필요함(신규 가입과 동일한 경험)
  public void reviveIfWithdrawn() {
    if (getDeletedAt() != null) {
      clearDeleted();
    }
  }

  // 재로그인 시 소셜에서 온 값만 갱신 — 공백·null은 무시(기존 값 유지)
  public void applySocialProfile(String email, String nickname, String profileImageUrl) {
    if (email != null && !email.isBlank()) {
      this.email = email;
    }
    if (nickname != null && !nickname.isBlank()) {
      this.nickname = nickname;
    }
    if (profileImageUrl != null && !profileImageUrl.isBlank()) {
      this.profileImageUrl = profileImageUrl;
    }
  }

  // 프로필 부분 수정 — null인 파라미터는 미변경(onboarding은 firstName·lastName만, PATCH profile은 D8 부분 업데이트)
  public void applyProfilePatch(String firstName, String lastName, Boolean notificationEnabled) {
    if (firstName != null) {
      this.firstName = firstName;
    }
    if (lastName != null) {
      this.lastName = lastName;
    }
    if (notificationEnabled != null) {
      this.notificationEnabled = notificationEnabled;
    }
  }

  public void connectGoogleCalendar() {
    this.isGoogleCalendarConnected = true;
  }

  public void disconnectGoogleCalendar() {
    this.isGoogleCalendarConnected = false;
  }

  // 사전 일정 정보를 한 번이라도 입력 완료했는지 — 최초/갱신 입력 판정. 정기·개별 일정 row 수는 보지 않는다.
  // 연차·휴일 정보 4개 중 사전 신청일만 nullable이라(나머지는 기본값이 늘 차 있어 "저장한 적 있음"을 구분 못 함)
  // 이 값 하나가 입력 완료 마커 역할을 한다
  public boolean hasCompletedPreSchedule() {
    return vacationApplyPeriod != null;
  }

  // 연차·휴일 정보 전체 교체(부분 patch 아님) — 4개 값 모두 필수라 호출부에서 null이 들어오지 않는다
  public void applyVacationPolicy(
      int maxVacationDays,
      VacationApplyPeriod vacationApplyPeriod,
      boolean halfVacationAvailable,
      boolean holidayRest) {
    this.maxVacationDays = maxVacationDays;
    this.vacationApplyPeriod = vacationApplyPeriod;
    this.halfVacationAvailable = halfVacationAvailable;
    this.holidayRest = holidayRest;
  }

  // 연차·휴일 정보를 가입 직후 상태로 되돌린다 — 사전 신청일이 null이 되어 다시 "최초 입력"으로 판정된다
  public void resetVacationPolicy() {
    this.maxVacationDays = DEFAULT_MAX_VACATION_DAYS;
    this.vacationApplyPeriod = null;
    this.halfVacationAvailable = false;
    this.holidayRest = true;
  }

  // 탈퇴 확정 — soft delete + PII 스크럽. socialId·provider·id는 FK 무결성·재로그인 차단 판별을 위해 유지.
  // 연차·휴일 정보도 되돌린다 — 사전 신청일이 남으면 재가입 사용자가 곧바로 "갱신 입력"으로 판정돼
  // 입력 플로우를 건너뛴다("신규 가입과 동일한 경험" 원칙 위반)
  public void scrubPiiForWithdrawal() {
    markDeleted();
    this.email = null;
    this.firstName = null;
    this.lastName = null;
    this.nickname = null;
    this.profileImageUrl = null;
    disconnectGoogleCalendar();
    resetVacationPolicy();
  }
}
