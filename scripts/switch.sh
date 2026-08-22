#!/usr/bin/env bash
#
# blue-green 전환. 유휴 포트에 새 인스턴스를 띄우고 준비되면 nginx 를 그쪽으로 돌린다.
#
#   switch.sh            현재 활성 포트의 반대편으로 전환
#   switch.sh --status   현재 상태만 출력
#
# 전환 실패 시 nginx 를 건드리지 않고 새 인스턴스만 정리하므로, 기존 인스턴스가 계속 서비스한다.
# 즉 "배포 실패"가 "장애"가 되지 않는다.
#
set -uo pipefail

BASE="${BASE:-$HOME/clickmoney}"
# nginx 는 root(master) 가 설정을 읽으므로 이 경로에 둬도 된다.
# /etc/nginx 아래가 아니라 여기 두면 전환할 때마다 root 쓰기가 필요 없다.
UPSTREAM_CONF="$BASE/nginx/upstream.conf"
READY_TIMEOUT=90
DRAIN_SECONDS=5 # nginx reload 후 기존 커넥션이 빠질 시간

log() { printf '[switch] %s\n' "$*"; }
fail() {
  printf '[switch][ERROR] %s\n' "$*" >&2
  exit 1
}

active_port() { grep -oE '127\.0\.0\.1:[0-9]+' "$UPSTREAM_CONF" 2>/dev/null | head -1 | cut -d: -f2; }
probe() {
  local p=$1 c
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 "http://127.0.0.1:$p/api/tap/today" 2>/dev/null)
  [ -n "$c" ] && [ "$c" != "000" ] && echo "$c"
}
running_on() { ss -lnt 2>/dev/null | grep -qE ":$1\b"; }

ACTIVE=$(active_port)
[ -n "$ACTIVE" ] || fail "활성 포트를 알 수 없습니다. $UPSTREAM_CONF 를 확인하세요."
IDLE=$([ "$ACTIVE" = "8080" ] && echo 8081 || echo 8080)

if [ "${1:-}" = "--status" ]; then
  log "활성 포트: $ACTIVE ($(probe "$ACTIVE" || echo 무응답))"
  log "유휴 포트: $IDLE ($(running_on "$IDLE" && echo '점유 중 - 이전 전환 잔재일 수 있음' || echo 비어있음))"
  log "current  : $(readlink "$BASE/current")"
  systemctl is-active "clickmoney@$ACTIVE" >/dev/null 2>&1 &&
    log "유닛     : clickmoney@$ACTIVE" ||
    log "유닛     : clickmoney (템플릿 이전 형태)"
  exit 0
fi

# zram 이 없으면 겹침 구간에서 EBS 로 밀려 이후 GC 가 20초 멈춘다.
grep -q zram0 /proc/swaps || fail "zram swap 이 비활성입니다. 전환을 중단합니다."

log "활성 $ACTIVE → 유휴 $IDLE 로 전환"
log "current = $(readlink "$BASE/current")"

running_on "$IDLE" && {
  log "유휴 포트 $IDLE 가 점유 중입니다. 정리합니다."
  sudo systemctl stop "clickmoney@$IDLE" 2>/dev/null
  sleep 3
  running_on "$IDLE" && fail "$IDLE 를 비우지 못했습니다."
}

log "새 인스턴스 기동: clickmoney@$IDLE"
sudo systemctl start "clickmoney@$IDLE" || fail "유닛 기동 실패"

# green 을 OOM 1순위로 지정한다. 메모리가 모자라면 기존 인스턴스가 아니라 새 인스턴스가 죽는다.
# 유닛이 User=minjae 로 돌기 때문에 값을 올리는 데 root 가 필요 없다.
NEWPID=$(systemctl show "clickmoney@$IDLE" -p MainPID --value 2>/dev/null)
[ -n "$NEWPID" ] && [ "$NEWPID" != "0" ] &&
  echo 1000 >"/proc/$NEWPID/oom_score_adj" 2>/dev/null &&
  log "oom_score_adj=1000 적용 (pid=$NEWPID)"

log "기동 대기 (최대 ${READY_TIMEOUT}초)"
READY=""
for i in $(seq 1 "$READY_TIMEOUT"); do
  if ! systemctl is-active --quiet "clickmoney@$IDLE"; then
    log "유닛이 죽었습니다."
    sudo journalctl -u "clickmoney@$IDLE" -n 20 --no-pager | tail -15
    sudo systemctl stop "clickmoney@$IDLE" 2>/dev/null
    fail "새 인스턴스 기동 실패 — nginx 는 건드리지 않았습니다. $ACTIVE 가 계속 서비스합니다."
  fi
  c=$(probe "$IDLE") && {
    READY=$i
    log "기동 완료 (${i}초, http=$c)"
    break
  }
  sleep 1
done
[ -n "$READY" ] || {
  sudo systemctl stop "clickmoney@$IDLE" 2>/dev/null
  fail "${READY_TIMEOUT}초 내 응답 없음 — nginx 미변경. $ACTIVE 가 계속 서비스합니다."
}

log "nginx 전환: $ACTIVE → $IDLE"
printf 'upstream clickmoney {\n    server 127.0.0.1:%s max_fails=0;\n}\n' "$IDLE" >"$UPSTREAM_CONF"

if ! sudo nginx -t 2>/dev/null; then
  log "nginx 설정 오류 — 되돌립니다."
  printf 'upstream clickmoney {\n    server 127.0.0.1:%s max_fails=0;\n}\n' "$ACTIVE" >"$UPSTREAM_CONF"
  sudo systemctl stop "clickmoney@$IDLE" 2>/dev/null
  fail "nginx -t 실패 — 원복했습니다."
fi

# reload 는 재시작이 아니다. master 가 리스닝 소켓을 계속 들고 있고 worker 만 교체되므로
# 진행 중인 커넥션이 끊기지 않는다. 이것이 전환이 무중단인 이유다.
sudo nginx -s reload || fail "nginx reload 실패"
log "nginx reload 완료 — 트래픽이 $IDLE 로 넘어갔습니다"

log "기존 커넥션 배출 대기 ${DRAIN_SECONDS}초"
sleep "$DRAIN_SECONDS"

log "구 인스턴스 종료 (포트 $ACTIVE)"
if systemctl is-active --quiet "clickmoney@$ACTIVE" 2>/dev/null; then
  sudo systemctl stop "clickmoney@$ACTIVE"
fi

sudo systemctl enable "clickmoney@$IDLE" >/dev/null 2>&1
sudo systemctl disable "clickmoney@$ACTIVE" >/dev/null 2>&1

echo
log "전환 완료: $ACTIVE → $IDLE"
log "외부 확인: $(curl -s -o /dev/null -w '%{http_code}' --max-time 8 https://api-clickmoney.compounding.co.kr/api/tap/today)"
free -m | sed -n '2,3p' | sed 's/^/[switch]   /'
