// S1 FCM 정지 — 앱을 LOADTEST_FCM_DELAY_MS=120000으로 띄운 뒤 실행한다.
// 방장들이 여행방 정보를 계속 수정해 알림을 발생시키는 동안, 멤버들의 여행방 목록 조회가 느려지는지 본다.
import http from 'k6/http';
import { check } from 'k6';
import {
  BASE_URL,
  authHeaders,
  listTrips,
  logFailure,
  pickOwner,
  probeDbConnections,
  readScenario,
  reportOnly,
  summaryTrendStats,
} from './common.js';

export { listTrips, probeDbConnections };

export const options = {
  summaryTrendStats,
  scenarios: {
    notify: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      exec: 'patchTrip',
    },
    read: readScenario,
    probe: { executor: 'constant-vus', vus: 1, duration: '60s', exec: 'probeDbConnections' },
  },
  thresholds: reportOnly(['notify', 'read']),
};

export function patchTrip() {
  const owner = pickOwner();
  const body = JSON.stringify({
    name: `부하${Math.floor(Math.random() * 1e6)}`,
    destination: null,
    durationNights: null,
    durationDays: null,
    memberCount: 10,
  });
  const res = http.patch(`${BASE_URL}/api/v1/trips/${owner.tripId}`, body, {
    headers: authHeaders(owner.token),
    timeout: '60s',
    tags: { name: 'PATCH /trips/{id}' },
  });
  check(res, { 'patch 200': (r) => r.status === 200 });
  logFailure('PATCH /trips/{id}', res);
}
