// S2 카카오 지연 — WireMock이 "slow-" 토큰의 user/me를 4초 늦게 응답한다.
// 카카오 로그인이 몰리는 동안 여행방 목록 조회가 요청 처리 스레드를 얻지 못해 느려지는지 본다.
import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL,
  listTrips,
  logFailure,
  probeDbConnections,
  readScenario,
  reportOnly,
  summaryTrendStats,
} from './common.js';

export { listTrips, probeDbConnections };

export const options = {
  summaryTrendStats,
  scenarios: {
    login: { executor: 'constant-vus', vus: 300, duration: '60s', exec: 'slowKakaoLogin' },
    read: readScenario,
    probe: { executor: 'constant-vus', vus: 1, duration: '60s', exec: 'probeDbConnections' },
  },
  thresholds: reportOnly(['login', 'read']),
};

export function slowKakaoLogin() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ provider: 'KAKAO', token: `slow-${__VU}-${__ITER}` }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '60s', tags: { name: 'POST /auth/login' } },
  );
  check(res, {
    'login 200': (r) => r.status === 200,
    'login 503': (r) => r.status === 503,
  });
  if (res.status !== 503) {
    logFailure('POST /auth/login', res);
  }
  // 실패한 클라이언트가 곧바로 재시도해 부하를 부풀리지 않도록 잠깐 쉰다. 적용 전후 같은 값이다.
  sleep(0.5);
}
