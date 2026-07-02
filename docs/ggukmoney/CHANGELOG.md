# 수정 내역

## 2026-07-02 와이어프레임 교차 검토 보강

- 34페이지 와이어프레임 PDF를 다시 확인하고 출금 단위, 탭 배치/어뷰징 기준, 지역 랭킹 콜드스타트 기준을 `Decision Required` 항목으로 분리했습니다.
- 와이어프레임의 상자 반복/일괄 개봉 UI와 주간 보상 받기 버튼은 MVP 백엔드 정책과 의도적으로 다른 표현임을 명시했습니다.
- `keycap.code`를 API 노출용 안정 코드로 추가하고 `GET /keycaps` 응답과 테이블 명세를 정합화했습니다.
- 키캡 수집 상태를 DB/API 모두 `IN_PROGRESS`, `COMPLETED`로 통일하고 `GET /keycaps` 페이지 응답을 공통 마스터 데이터 규칙과 맞췄습니다.
- 닉네임 중복 방지를 위해 `nickname_normalized`와 ACTIVE 사용자 partial unique 정책을 문서화했습니다.
- `204 No Content` API는 공통 response body를 사용하지 않고 `X-Trace-Id` 헤더로 추적 id를 제공할 수 있도록 예외를 명시했습니다.
- 사용자 전체 revoke 비교 기준을 `issuedAtMillis <= revokedAtMillis`로 바꾸고 같은 초 발급 토큰 경계 테스트를 추가했습니다.
- Toss 로그인 인증 조건을 일반 로그인, 게스트 승격/병합, 회원 재연결 범위로 분리해 명시했습니다.
- 랭킹 결과 API 응답 스키마와 분석 이벤트 카탈로그/스키마 버전/source 정책을 보강했습니다.

## 2026-07-02 최신 후보 문서 반영

- A 전체 API 상세 계약을 보완하고 각 API에 Owner/Status/인증/멱등성/관련 저장소를 명시했습니다.
- B 전체 API를 `PROPOSED` 상세 계약으로 작성했습니다.
- B 14개 테이블을 컬럼·타입·제약·인덱스 수준으로 상세화했습니다.
- 전체 로그아웃 시 사용자 revoke 시각으로 모든 Access Token을 즉시 차단하도록 보완했습니다.
- `auth:user-sessions` Sorted Set 만료 member 정리 규칙을 추가했습니다.
- ERD의 `APP_USER -> DEVICE` 직접 관계를 제거하고 B Aggregate를 추가했습니다.
- Refresh 동시 충돌과 실제 과거 Token 재사용 테스트를 분리했습니다.
- B 탭/포인트/출금/광고/부스터/초대/API 계약 테스트를 추가했습니다.
