#!/usr/bin/env python3
"""격리된 로컬 DB/Redis의 fixture로 HTTP 기능과 재기동·재배포를 검증한다."""
import base64
import concurrent.futures
import datetime
import hashlib
import hmac
import http.cookies
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent.parent
ENV_FILE = ROOT / '.local-state/single/.env'
ENV = dict(line.split('=', 1) for line in ENV_FILE.read_text().splitlines() if '=' in line)
COMPOSE = ['docker', 'compose', '-p', 'tripfit-single-local', '--env-file', str(ENV_FILE),
           '-f', str(ROOT / 'deploy/single/docker-compose.yml')]
DOCKER_ENV = {key: value for key, value in os.environ.items()
              if key in {'PATH', 'HOME', 'DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_CONFIG',
                         'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH', 'DOCKER_API_VERSION'}}
BASE = 'http://127.0.0.1:' + ENV['NGINX_HTTP_PORT']
# 포트·프로젝트가 고정된 격리 후보에만 fixture를 쓰도록 한다.
assert ENV['MYSQL_DATABASE'] == 'tripfit'
assert ENV['GHCR_IMAGE'] == 'tripfit-single-local:verify'


def run(args, data=None):
    return subprocess.run(args, input=data, text=True, check=True, capture_output=True, env=DOCKER_ENV).stdout.strip()


def compose(*args, data=None):
    return run(COMPOSE + list(args), data)


def sql(statement):
    return compose('exec', '-T', 'mysql', 'sh', '-ec',
                   'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h 127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" -N',
                   data=statement)


def redis(*args):
    # 비밀번호는 컨테이너 환경에서 읽고 호스트 명령행으로 전달하지 않는다.
    return compose('exec', '-T', 'redis', 'sh', '-ec',
                   'REDISCLI_AUTH="$REDIS_PASSWORD" exec redis-cli "$@"', 'redis-cli', *args)


def request(path, expected=200, method='GET', payload=None, token=None, cookie=None):
    headers = {'Content-Type': 'application/json', 'User-Agent': 'TripFit-local-verification'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if cookie:
        headers['Cookie'] = 'refreshToken=' + cookie
    data = json.dumps(payload).encode() if payload is not None else (b'' if method == 'POST' else None)
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    body = response.read().decode()
    if response.status != expected:
        raise AssertionError(f'{method} {path}: expected {expected}, got {response.status}: {body[:350]}')
    return (json.loads(body) if body else None), response.headers


def jwt(user_id):
    def encode(value):
        return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=')
    now = int(time.time())
    value = encode({'alg': 'HS256'}) + b'.' + encode({'sub': user_id, 'iat': now, 'exp': now + 900})
    signature = hmac.new(ENV['JWT_SECRET'].encode(), value, hashlib.sha256).digest()
    return (value + b'.' + base64.urlsafe_b64encode(signature).rstrip(b'=')).decode()


def memory_snapshot():
    result = {}
    for service in ('app', 'mysql', 'redis', 'gateway'):
        container = compose('ps', '-q', service)
        state = json.loads(run(['docker', 'inspect', container]))[0]
        peak = int(compose('exec', '-T', service, 'cat', '/sys/fs/cgroup/memory.peak'))
        assert not state['State']['OOMKilled'], service + ' OOM killed'
        assert state['RestartCount'] == 0, service + ' unexpectedly restarted'
        result[service] = {'limit_bytes': state['HostConfig']['Memory'], 'peak_cgroup_bytes': peak}
    return result


owner, member = str(uuid.uuid4()), str(uuid.uuid4())
for user_id in (owner, member):
    sql(f"""INSERT INTO users
    (id, created_at, updated_at, social_id, provider, email, first_name, last_name,
     is_google_calendar_connected, notification_enabled, max_vacation_days,
     is_half_vacation_available, is_holiday_rest, vacation_apply_period)
    VALUES ('{user_id}', NOW(6), NOW(6), 'single-{user_id}', 'GOOGLE',
     'single@example.invalid', '로컬', '검증', 0, 0, 2, 0, 1, 'ANY');""")
owner_token, member_token = jwt(owner), jwt(member)
request('/api/v1/auth/me', token=owner_token)
request('/api/v1/auth/me', expected=401)
start = datetime.date.today() + datetime.timedelta(days=30)
trip, _ = request('/api/v1/trips', expected=201, method='POST', token=owner_token, payload={
    'name': '단일 서버 로컬 검증', 'startRange': start.isoformat(),
    'endRange': (start + datetime.timedelta(days=6)).isoformat(),
    'durationNights': 2, 'durationDays': 3, 'memberCount': 2})
trip_id = trip['data']['tripId']
activated, _ = request(f'/api/v1/trips/{trip_id}/activate', method='POST', token=owner_token)
invite_code = activated['data']['inviteCode']
request('/api/v1/trips/join', expected=200, method='POST', token=member_token,
        payload={'inviteCode': invite_code})
request(f'/api/v1/trips/{trip_id}', token=owner_token)

# 외부 OAuth 대신 Redis 세션 fixture로 실제 갱신 API의 로테이션을 확인한다.
initial_refresh, family_id = str(uuid.uuid4()), str(uuid.uuid4())
redis('SET', 'auth:refresh:active:' + initial_refresh, owner + '|' + family_id, 'EX', '3600')
redis('SET', 'auth:refresh:family:' + family_id, initial_refresh, 'EX', '3600')
redis('SADD', 'auth:refresh:user:' + owner, family_id)
redis('EXPIRE', 'auth:refresh:user:' + owner, '3600')
refresh, headers = request('/api/v1/auth/refresh', method='POST', cookie=initial_refresh)
parsed_cookie = http.cookies.SimpleCookie()
parsed_cookie.load(headers['Set-Cookie'])
rotated_refresh = parsed_cookie['refreshToken'].value
assert parsed_cookie['refreshToken']['httponly']
assert parsed_cookie['refreshToken']['secure']
assert parsed_cookie['refreshToken']['samesite'] == 'Lax'
request('/api/v1/auth/me', token=refresh['data']['accessToken'])

# 프록시 요청 제한 안에서 소량의 동시 HTTP 조회를 확인하며 부하 시험을 대체하지 않는다.
for _ in range(10):
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        futures = [executor.submit(request, '/api/v1/trips', token=owner_token) for _ in range(4)]
        for future in futures:
            future.result()
    time.sleep(0.6)
before = memory_snapshot()
compose('restart')
compose('up', '-d', '--wait', '--wait-timeout', '300')
request(f'/api/v1/trips/{trip_id}', token=owner_token)
assert sql(f"SELECT COUNT(*) FROM users WHERE id='{owner}';") == '1'
# Redis는 현행과 같은 비영속 방식이므로 재시작 후 재로그인이 필요하다.
request('/api/v1/auth/refresh', expected=401, method='POST', cookie=rotated_refresh)
request('/api/v1/auth/logout', expected=204, method='POST', cookie=rotated_refresh)
compose('up', '-d', '--no-deps', '--force-recreate', '--wait', '--wait-timeout', '300', 'app')
request(f'/api/v1/trips/{trip_id}', token=owner_token)
after = memory_snapshot()
docker_memory, docker_arch = run(['docker', 'info', '--format', '{{.MemTotal}} {{.Architecture}}']).split()
report = {
    'docker_host': {'memory_bytes': int(docker_memory), 'architecture': docker_arch},
    'verified_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'checks': ['auth-boundary', 'trip-create-activate-join-read', 'redis-refresh-cookie',
               '40-concurrent-read-requests', 'mysql-persistence-after-restart',
               'redis-session-loss-after-restart', 'app-recreate-persistence'],
    'before_restart': before, 'after_recreate': after,
    'limitations': ['container budgets alone do not validate a 2GB Lightsail host',
                    'real OAuth/FCM/Calendar credentials, public DNS and TLS not tested',
                    'scheduled jobs and sustained production traffic not covered'],
}
(ROOT / 'build/single-local-functional.json').write_text(json.dumps(report, indent=2))
print(json.dumps(report, indent=2))
