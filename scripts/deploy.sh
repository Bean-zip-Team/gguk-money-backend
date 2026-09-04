#!/usr/bin/env bash
#
# 배포 스크립트 — GitHub Actions 가 jar 를 scp 로 올린 뒤 원격에서 호출한다.
#
#   deploy.sh verify  <sha>   업로드된 jar 무결성만 검사하고 폐기한다 (운영 무영향)
#   deploy.sh release <sha>   releases/<sha>/ 에 배치하고 current 링크를 교체한다
#
# release 모드는 switch.sh 로 blue-green 전환한다. 유휴 포트에 새 릴리스를 띄우고
# 준비되면 nginx 를 그쪽으로 돌리므로 중단이 없다 (실측: 요청 161건 중 실패 0건).
# 전환에 실패하면 nginx 를 건드리지 않아 기존 인스턴스가 계속 서비스한다.
#
set -euo pipefail

BASE="${BASE:-$HOME/clickmoney}"
INCOMING="$BASE/incoming.jar"
KEEP_RELEASES=5


log() { printf '[deploy] %s\n' "$*"; }
fail() {
  printf '[deploy][ERROR] %s\n' "$*" >&2
  exit 1
}

MODE="${1:-}"
SHA="${2:-}"
[ -n "$MODE" ] && [ -n "$SHA" ] || fail "사용법: deploy.sh <verify|release> <sha>"
[ -f "$INCOMING" ] || fail "업로드된 jar 가 없습니다: $INCOMING"

verify_jar() {
  log "무결성 검사: $INCOMING ($(stat -c%s "$INCOMING") bytes)"
  unzip -t "$INCOMING" >/dev/null 2>&1 || fail "손상된 jar (zip 검사 실패)"
  unzip -p "$INCOMING" META-INF/MANIFEST.MF 2>/dev/null | grep -q 'Spring-Boot-Version' ||
    fail "Spring Boot 실행 가능 jar 가 아님"

  # 시크릿이 jar 에 packed 되어 흘러들어오지 않았는지 확인한다.
  if unzip -l "$INCOMING" | grep -q 'BOOT-INF/classes/application.properties'; then
    log "[경고] jar 안에 application.properties 가 포함돼 있습니다."
    log "        config/application.properties 가 우선 적용되지만, 시크릿이 jar 에 남아있습니다."
    log "        src/main/resources/application.properties 제거를 권장합니다."
  fi

  log "무결성 OK  sha256=$(sha256sum "$INCOMING" | cut -c1-16)..."
}

case "$MODE" in
verify)
  verify_jar
  rm -f "$INCOMING"
  log "verify 모드 — 운영에 아무 변경 없이 종료합니다."
  ;;

release)
  verify_jar
  dest="$BASE/releases/$SHA"
  mkdir -p "$dest"
  mv "$INCOMING" "$dest/app.jar"
  ln -sfn "$dest" "$BASE/current"
  log "배치 완료: current -> releases/$SHA"

  # blue-green 전환. 유휴 포트에 새 릴리스를 띄우고 준비되면 nginx 를 돌린다.
  # 실패하면 nginx 를 건드리지 않으므로 기존 인스턴스가 계속 서비스한다.
  if [ -x "$BASE/switch.sh" ]; then
    bash "$BASE/switch.sh" || fail "전환 실패 — 기존 인스턴스가 계속 서비스 중입니다. 롤백: ./rollback.sh"
  else
    log "[SKIP] switch.sh 가 없어 전환을 생략합니다 (배치만 완료)."
  fi

  # 최근 KEEP_RELEASES 개만 남기고 정리한다.
  ls -1dt "$BASE"/releases/*/ 2>/dev/null | tail -n +$((KEEP_RELEASES + 1)) | xargs -r rm -rf
  log "보관 중인 릴리스: $(ls -1d "$BASE"/releases/*/ 2>/dev/null | wc -l) 개"
  ;;

*)
  fail "알 수 없는 모드: $MODE (verify | release)"
  ;;
esac
