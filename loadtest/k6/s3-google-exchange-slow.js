// S3 토큰 서버 지연 — WireMock이 Google 토큰 엔드포인트를 4초 늦게 응답한다.
// 로그인 응답이 인가 코드 교환을 기다리는지 본다.
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, reportOnly, summaryTrendStats } from './common.js';

// 기본은 VU 10개가 쉬지 않고 로그인한다(응답 시간 비교용). LOGIN_RATE를 주면 초당 그 수만큼만 로그인해,
// 토큰 서버가 느릴 때 교환 작업자가 따라가는지(credential이 저장되는지)를 현실적인 빈도로 본다.
const loginRate = __ENV.LOGIN_RATE ? Number(__ENV.LOGIN_RATE) : null;

export const options = {
  summaryTrendStats,
  scenarios: {
    login: loginRate
      ? {
          executor: 'constant-arrival-rate',
          rate: loginRate,
          timeUnit: '1s',
          duration: '30s',
          preAllocatedVUs: 10,
          exec: 'googleLogin',
        }
      : { executor: 'constant-vus', vus: 10, duration: '30s', exec: 'googleLogin' },
  },
  thresholds: reportOnly(['login']),
};

export function googleLogin() {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      provider: 'GOOGLE',
      token: `g-${__VU}-${__ITER}`,
      authorizationCode: 'loadtest-code',
      redirectUri: 'http://localhost',
    }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '60s', tags: { name: 'POST /auth/login' } },
  );
  check(res, { 'login 200': (r) => r.status === 200 });
}
