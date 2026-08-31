package com.tripfit.tripfit.trip.port.out;

import com.tripfit.tripfit.user.domain.User;
import java.util.List;
import java.util.UUID;

// trip이 필요로 하는 "사용자 조회·프로필 검증·활동 상태 갱신"을 인터페이스로 감싼 포트(의존 역전).
//
// 이 인터페이스는 trip 패키지에 있지만, 구현체(UserDirectoryAdapter)는 user 패키지에 있다 — trip은
// UserLookupService·UserRepository·UserProfileService·UserSummaryService를 더 이상 직접 import하지 않고
// 이 인터페이스 하나만 안다. 메서드 5개는 각각 다른 user 서비스에 위임되는데(아래 각 메서드 주석 참고),
// 전부 "trip 유스케이스가 진행되려면 사용자 쪽에 확인·반영해야 하는 것들"이라 하나의 포트로 묶었다.
//
// `User` 타입 자체는 포트 시그니처에 그대로 노출된다 — Trip·TripMember 엔티티가 이미 User를 JPA
// 연관관계(@ManyToOne)로 참조하고 있어서, 엔티티 타입 수준의 결합은 원래부터 있었다(포트로 감싸는 대상은
// "서비스 호출"이지 "엔티티 타입 참조"가 아님).
public interface UserDirectoryPort {

  // userId로 User를 조회한다. 없으면 어댑터 내부(UserLookupService)가 AUTH_FORBIDDEN을 던진다 — 이 메서드를
  // 쓰는 쪽은 결과가 항상 존재한다고 가정하고 쓰면 된다(Optional 처리를 따로 하지 않음).
  User requireUser(UUID userId);

  // 여러 userId를 한 번에 조회한다(N+1 방지) — 여행방 멤버 프리뷰처럼 여러 명을 한꺼번에 표시할 때 쓴다.
  List<User> findAllById(List<UUID> userIds);

  // 성·이름이 모두 등록됐는지 검증한다 — 미완료면 어댑터 내부(UserProfileService)가 예외를 던진다.
  // 여행방 생성·참여 진입 조건(BR) 검증용.
  void requireProfileNameComplete(User user);

  // 이 사용자의 정기·개별 일정이 0건이면 전부 "free"로 표시한다(부수 효과 — DB에 반영됨). 일정이 1건이라도
  // 있으면 아무 것도 하지 않는다(no-op). 여행방 activate·join 시 "일정 ≥1 또는 전부 free"라는 입장 조건을
  // 항상 충족시키기 위해 호출한다.
  void markAllFreeIfNoSchedules(User user);

  // 여행방 전역 입장 조건(일정 ≥1건 또는 전부 free)을 검증한다 — 미충족이면 어댑터 내부(UserSummaryService)가
  // SCHEDULE_ENTRY_REQUIRED류 예외를 던진다. TripAuthorizationInterceptor가 @TripMemberOnly 검사에서 호출.
  void requireCanEnterRoom(UUID userId);
}
