# gguk-money-backend
꾹머니 서버 레포입니다.

## 설계 문서

- [꾹머니 백엔드 설계 문서](docs/ggukmoney/README.md)

## 현재 구현 상태

- 실제 저장소: `C:\Users\lucy\Documents\ggukmoney`
- 기술 기준: Java 21, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`)
- 테스트 환경: 기본 Gradle `build/` 디렉터리 사용, 한글 경로 우회용 temp build 설정 제거

- 브랜치: `main`
- Java 구현: 인증/로그 기반 `IN_PROGRESS`
- 구현됨: 공통 응답 `traceId`, Access Log Filter, JWT Provider, Redis Refresh Session Lua CAS, refresh/logout/logout-all API 뼈대, logout-all Redis 전체 세션 삭제, Auth Audit Log Entity/Repository/Service
- 미구현: 게스트 생성/복구, Toss 승격/병합, 키캡/랭킹/알림/기록/설정 도메인
- Blocking Issue: Toss Access Token 없는 일반 로그인에서 필요한 `deviceKey/platform/appVersion` 요청 계약 미확정
