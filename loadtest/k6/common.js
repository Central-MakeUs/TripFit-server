// 시나리오 공통 — 시드 토큰 읽기, 여행방 목록 조회(영향을 재는 일반 API), DB 커넥션 점유 수 표본 수집
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';

// 앱(bootTestRun)이 기동하며 만든 시드. 앱을 다시 띄우면 DB가 새로 만들어지므로 매번 새로 읽는다.
export const seed = JSON.parse(open('../data/seed.json'));

const dbConnectionsActive = new Trend('db_connections_active');
const dbConnectionsPending = new Trend('db_connections_pending');

export function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

function pick(list) {
  return list[Math.floor(Math.random() * list.length)];
}

export function pickOwner() {
  return pick(seed.owners);
}

// 외부 API와 무관한 일반 API. 이 응답 시간이 외부 장애에 끌려가는지가 격리 효과의 지표다.
export function listTrips() {
  const res = http.get(`${BASE_URL}/api/v1/trips`, {
    headers: authHeaders(pick(seed.readers)),
    timeout: '60s',
    tags: { name: 'GET /trips' },
  });
  check(res, { 'list 200': (r) => r.status === 200 });
  logFailure('GET /trips', res);
}

// 실패 원인을 추측하지 않도록, 200이 아닌 응답의 상태와 오류 코드를 남긴다(status 0은 연결 단계 실패).
export function logFailure(name, res) {
  if (res.status !== 200) {
    console.warn(`${name} status=${res.status} error=${res.error_code} ${res.error}`);
  }
}

// 1초마다 Hikari 지표를 읽는다. active는 사용 중인 커넥션 수, pending은 커넥션을 기다리는 요청 수다.
export function probeDbConnections() {
  sampleMetric('hikaricp.connections.active', dbConnectionsActive);
  sampleMetric('hikaricp.connections.pending', dbConnectionsPending);
  sleep(1);
}

function sampleMetric(name, trend) {
  const res = http.get(`${BASE_URL}/actuator/metrics/${name}`, { timeout: '10s', tags: { name: 'probe' } });
  if (res.status === 200) {
    trend.add(res.json('measurements.0.value'));
  }
}

// 시나리오별 하위 지표를 요약에 보이게 하려고 거는 항상 통과하는 기준.
export function reportOnly(scenarios) {
  const thresholds = {};
  for (const scenario of scenarios) {
    thresholds[`http_req_duration{scenario:${scenario}}`] = ['p(99)>=0'];
    thresholds[`http_req_failed{scenario:${scenario}}`] = ['rate>=0'];
  }
  return thresholds;
}

export const summaryTrendStats = ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'];

// 일반 API 부하는 초당 요청 수를 고정한다. 응답이 느려져도 요청량이 줄지 않으므로 적용 전후를 같은 트래픽으로 비교할 수 있다.
// 요청을 보낼 VU가 모자라면 k6가 dropped_iterations로 따로 센다.
export const readScenario = {
  executor: 'constant-arrival-rate',
  rate: 300,
  timeUnit: '1s',
  duration: '60s',
  preAllocatedVUs: 100,
  maxVUs: 400,
  exec: 'listTrips',
};
