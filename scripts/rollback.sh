#!/usr/bin/env bash
#
# 직전(또는 지정한) 릴리스로 되돌린다.
#
#   rollback.sh              직전 릴리스로
#   rollback.sh <sha>        특정 릴리스로
#   rollback.sh --list       보관 중인 릴리스 목록
#
# current 심볼릭 링크만 바꾸고 재시작하므로 재빌드가 없다.
# 서버에서 gradle 로 다시 빌드하던 기존 방식(5분 이상)을 재시작 시간으로 줄인다.
#
set -euo pipefail

BASE="${BASE:-$HOME/clickmoney}"
RELEASES="$BASE/releases"
READY_TIMEOUT=90

log() { printf '[rollback] %s\n' "$*"; }
fail() {
  printf '[rollback][ERROR] %s\n' "$*" >&2
  exit 1
}

[ -d "$RELEASES" ] || fail "릴리스 디렉토리가 없습니다: $RELEASES"

# 최신순 릴리스 목록
mapfile -t ALL < <(ls -1dt "$RELEASES"/*/ 2>/dev/null | xargs -r -n1 basename)
[ ${#ALL[@]} -gt 0 ] || fail "보관된 릴리스가 없습니다."

CURRENT=$(basename "$(readlink -f "$BASE/current" 2>/dev/null || echo none)")

if [ "${1:-}" = "--list" ]; then
  log "보관 중인 릴리스 (최신순)"
  for r in "${ALL[@]}"; do
    mark="  "
    [ "$r" = "$CURRENT" ] && mark="→ "
    printf '  %s%s  %s\n' "$mark" "$r" "$(date -r "$RELEASES/$r" '+%Y-%m-%d %H:%M:%S')"
  done
  exit 0
fi

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  # current 바로 다음 것을 고른다.
  for i in "${!ALL[@]}"; do
    if [ "${ALL[$i]}" = "$CURRENT" ]; then
      TARGET="${ALL[$((i + 1))]:-}"
      break
    fi
  done
  [ -n "$TARGET" ] || fail "되돌릴 이전 릴리스가 없습니다. --list 로 확인하세요."
fi

[ -f "$RELEASES/$TARGET/app.jar" ] || fail "릴리스를 찾을 수 없습니다: $TARGET"
[ "$TARGET" = "$CURRENT" ] && fail "이미 $TARGET 을 실행 중입니다."

log "현재: $CURRENT"
log "대상: $TARGET"
ln -sfn "$RELEASES/$TARGET" "$BASE/current"
log "current 링크 교체 완료"

# 롤백도 blue-green 으로 무중단 전환한다.
if [ -x "$BASE/switch.sh" ]; then
  bash "$BASE/switch.sh" || fail "전환 실패 — 기존 인스턴스가 계속 서비스 중입니다. current 는 $TARGET 을 가리킵니다."
  log "롤백 완료 — $TARGET 무중단 적용"
else
  log "[SKIP] switch.sh 가 없습니다. 링크만 바꿨으니 수동으로 재시작하세요."
fi
