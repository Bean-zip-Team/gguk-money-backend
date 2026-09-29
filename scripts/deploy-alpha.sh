#!/usr/bin/env bash
#
# 알파 배포 스크립트 — deploy-alpha 워크플로가 jar 를 scp 로 올린 뒤 원격에서 호출한다.
#
#   deploy-alpha.sh <sha>
#
# 메모리가 1 GiB 라 blue-green 없이 재시작한다(수십 초 중단). 새 릴리스가 뜨지 않으면
# 이전 릴리스로 current 를 되돌리고 다시 띄운다.
#
set -euo pipefail

BASE="${BASE:-/opt/ttalkkak}"
INCOMING="$BASE/incoming.jar"
UNIT=clickmoney-alpha
KEEP_RELEASES=3
READY_TIMEOUT=120

log() { printf '[deploy-alpha] %s\n' "$*"; }
fail() {
  printf '[deploy-alpha][ERROR] %s\n' "$*" >&2
  exit 1
}

SHA="${1:-}"
[ -n "$SHA" ] || fail "사용법: deploy-alpha.sh <sha>"
[ -f "$INCOMING" ] || fail "업로드된 jar 가 없습니다: $INCOMING"

unzip -t "$INCOMING" >/dev/null 2>&1 || fail "손상된 jar (zip 검사 실패)"

# switch.sh 와 같은 기준이다. 인증 없이 부르면 401 이지만 응답이 오면 뜬 것이다.
ready() {
  local c
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 http://127.0.0.1:8080/api/tap/today 2>/dev/null)
  [ -n "$c" ] && [ "$c" != "000" ]
}

restart_and_wait() {
  sudo systemctl restart "$UNIT"
  for i in $(seq 1 "$READY_TIMEOUT"); do
    ready && {
      log "기동 완료 (${i}초)"
      return 0
    }
    sleep 1
  done
  return 1
}

prev=$(readlink "$BASE/current" 2>/dev/null || true)
dest="$BASE/releases/$SHA"
mkdir -p "$dest"
mv "$INCOMING" "$dest/app.jar"
ln -sfn "$dest" "$BASE/current"
log "배치 완료: current -> releases/$SHA"

if ! restart_and_wait; then
  sudo journalctl -u "$UNIT" -n 30 --no-pager | tail -20
  if [ -n "$prev" ] && [ "$prev" != "$dest" ]; then
    log "이전 릴리스로 되돌립니다: $prev"
    ln -sfn "$prev" "$BASE/current"
    restart_and_wait || log "이전 릴리스도 뜨지 않았습니다."
  fi
  fail "${READY_TIMEOUT}초 내 응답 없음"
fi

ls -1dt "$BASE"/releases/*/ 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf
free -m | sed -n '2,3p' | sed 's/^/[deploy-alpha]   /'
