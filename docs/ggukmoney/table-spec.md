# 꾹머니 PostgreSQL 테이블 명세

> 현재 구현 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`). 이 문서는 전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스의 Source of Truth다.

> 구현 기준: Java 26, Spring Boot 4.1.0, Jackson 3(`tools.jackson.*`). 계약/테이블 명세는 이 기준으로 해석한다.

이 문서는 전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스의 Source of Truth다. A 담당자 민재의 테이블은 CONFIRMED, B 담당자 은창의 미확정 테이블은 DRAFT로 구분한다.

## 표기 규칙

- 공통 컬럼: 별도 언급이 없으면 `id BIGINT PK`, `public_id UUID UNIQUE NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`를 둔다.
- FK 표기: `table(id)`는 내부 BIGINT FK다. 외부 API에는 `public_id`만 노출한다.
- 삭제 정책: 기본은 `ON DELETE RESTRICT`다. 민감정보 삭제/익명화는 회원 탈퇴 정책 확정 후 구현한다.
- 상태값 표기는 `CANCELED`로 통일한다.
- Refresh Token 원문과 Access Token 원문은 PostgreSQL에 저장하지 않는다.

## A 테이블 요약

| 테이블 | 소유자 | 상태 | Aggregate |
|---|---|---|---|
| `app_user` | A | CONFIRMED | User/Auth |
| `auth_identity` | A | CONFIRMED | User/Auth |
| `device` | A | CONFIRMED | User/Auth |
| `user_device` | A | CONFIRMED | User/Auth |
| `user_merge_history` | A | CONFIRMED | User/Auth |
| `auth_session_log` | A | CONFIRMED | User/Auth |
| `legal_document` | A | CONFIRMED | Config/Legal |
| `user_consent` | A | CONFIRMED | Config/Legal |
| `app_config` | A | CONFIRMED | Config/Legal |
| `keycap` | A | CONFIRMED | Keycap/Box |
| `user_keycap` | A | CONFIRMED | Keycap/Box |
| `keycap_drop_table` | A | CONFIRMED | Keycap/Box |
| `keycap_drop_item` | A | CONFIRMED | Keycap/Box |
| `keycap_box_account` | A | CONFIRMED | Keycap/Box |
| `keycap_box_ledger` | A | CONFIRMED | Keycap/Box |
| `keycap_box_open` | A | CONFIRMED | Keycap/Box |
| `keycap_box_open_result` | A | CONFIRMED | Keycap/Box |
| `region` | A | CONFIRMED | Region |
| `user_region` | A | CONFIRMED | Region |
| `user_region_change` | A | CONFIRMED | Region |
| `ranking_season` | A | CONFIRMED | Ranking |
| `ranking_participation` | A | CONFIRMED | Ranking |
| `ranking_score` | A | CONFIRMED | Ranking |
| `ranking_score_event` | A | CONFIRMED | Ranking |
| `ranking_snapshot` | A | CONFIRMED | Ranking |
| `ranking_reward` | A | CONFIRMED | Ranking |
| `push_device` | A | CONFIRMED | Notification |
| `notification_preference` | A | CONFIRMED | Notification |
| `notification_log` | A | CONFIRMED | Notification |
| `user_record_daily` | A | CONFIRMED | Record |
| `user_record_summary` | A | CONFIRMED | Record |
| `user_record_reward` | A | CONFIRMED | Record |
| `event_outbox` | A | CONFIRMED | Reliability |
| `event_inbox` | A | CONFIRMED | Reliability |

A 상세 테이블 공통 정책:

- 소유자: A
- 상태: CONFIRMED
- FK ON DELETE: 별도 표기가 없으면 `RESTRICT`
- Lock/version 정책: 금액성/수량성/상태 전환 테이블은 서비스 트랜잭션에서 row lock 또는 version check를 사용한다.
- 민감정보와 암호화: 원문 토큰, 원문 deviceKey, 원문 Push Token, Toss authorizationCode는 저장하지 않는다.
- 보관/익명화/삭제 정책: 회원 탈퇴 시 법무 기준에 따라 개인정보성 컬럼은 익명화하고, 원장/정산/감사 로그는 회계·보안 목적 기간 동안 보관한다.
- 관련 API/Event/Port/멱등성 기준은 각 테이블 섹션의 명시값을 우선하고, 명시가 없으면 해당 Aggregate의 API/Event 정책을 따른다.

## User/Auth

### app_user

- 역할: 게스트와 회원을 하나의 계정으로 관리한다.
- 관련 API: `POST /api/v1/guests`, `POST /api/v1/auth/toss/login`, `GET /api/v1/members/me`, `DELETE /api/v1/members/me`
- 관련 Event/Port: `RecordEventIngestUseCase`, `UserWithdrawalGuardPort`
- 멱등성 기준: `deviceKeyHash + GUEST_OWNER`, Toss provider identity

| 컬럼명 | PostgreSQL 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 식별자 |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 사용자 id |
| `account_type` | VARCHAR(20) | N | | | CHECK `GUEST`, `MEMBER` | 계정 유형 |
| `status` | VARCHAR(20) | N | `ACTIVE` | | CHECK `ACTIVE`, `SUSPENDED`, `WITHDRAWN`, `MERGED` | 계정 상태 |
| `nickname` | VARCHAR(50) | Y | | | | 표시 이름 |
| `nickname_normalized` | VARCHAR(50) | Y | | | | 중복 검사용 정규화 닉네임 |
| `merged_to_user_id` | BIGINT | Y | | FK `app_user(id)` | | MERGED 시 target |
| `last_login_at` | TIMESTAMPTZ | Y | | | | 마지막 로그인 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 시각 |

- 인덱스: `ix_app_user_status(status)`, `ix_app_user_merged_to_user_id(merged_to_user_id)`
- Partial Unique Index: `ux_app_user_active_nickname_normalized ON app_user(nickname_normalized) WHERE nickname_normalized IS NOT NULL AND status = 'ACTIVE'`
- FK ON DELETE: `merged_to_user_id`는 `SET NULL`
- Lock/version 정책: 회원 병합과 탈퇴 시 row lock 권장
- 민감정보와 암호화: 직접 민감정보 없음
- 보관/익명화/삭제: 탈퇴 시 nickname 익명화, 법무 기준에 따라 user id 보관
- 닉네임 정규화: 앞뒤 공백 제거, 연속 공백 단일화, 대소문자 fold, Unicode NFKC 기준 정규화를 적용한다.

### auth_identity

- 역할: Toss 등 외부 로그인 식별자를 사용자와 연결한다.
- 관련 API: `POST /api/v1/auth/toss/login`
- 멱등성 기준: `provider + provider_user_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 연결 사용자 |
| `provider` | VARCHAR(30) | N | | | CHECK `TOSS` | 로그인 Provider |
| `provider_user_id` | VARCHAR(255) | N | | | | Provider 사용자 id |
| `linked_at` | TIMESTAMPTZ | N | now() | | | 연결 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_auth_identity_user_id(user_id)`
- UNIQUE: `ux_auth_identity_provider_user(provider, provider_user_id)`, `ux_auth_identity_user_provider(user_id, provider)`
- FK ON DELETE: `RESTRICT`
- 민감정보와 암호화: provider id는 식별정보로 접근 제한

### device

- 역할: 앱 설치/기기를 식별한다.
- 관련 API: `POST /api/v1/guests`, `PUT /api/v1/push-devices/current`
- 멱등성 기준: `device_key_hash`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 device id |
| `device_key_hash` | VARCHAR(255) | N | | | UNIQUE | 클라이언트 deviceKey hash |
| `platform` | VARCHAR(20) | N | | | CHECK `IOS`, `ANDROID` | 플랫폼 |
| `app_version` | VARCHAR(30) | Y | | | | 마지막 앱 버전 |
| `first_seen_at` | TIMESTAMPTZ | N | now() | | | 최초 확인 |
| `last_seen_at` | TIMESTAMPTZ | N | now() | | | 마지막 확인 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_device_last_seen_at(last_seen_at)`
- 민감정보와 암호화: 원문 deviceKey 저장 금지
- 보관/삭제: 탈퇴 후에도 부정 사용 방지 목적 hash 보관 가능

### user_device

- 역할: 사용자와 기기의 관계, 게스트 소유 기기와 회원 기기를 표현한다.
- 관련 API: `POST /api/v1/guests`, `POST /api/v1/auth/toss/login`
- 멱등성 기준: 활성 `GUEST_OWNER` partial unique

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `device_id` | BIGINT | N | | FK `device(id)` | | 기기 |
| `account_role` | VARCHAR(30) | N | | | CHECK `GUEST_OWNER`, `MEMBER_DEVICE` | 계정-기기 역할 |
| `active` | BOOLEAN | N | true | | | 활성 여부 |
| `last_login_at` | TIMESTAMPTZ | Y | | | | 마지막 로그인 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_user_device_user_active(user_id, active)`, `ix_user_device_device_active(device_id, active)`
- Partial Unique Index: `ux_user_device_active_guest ON user_device(device_id) WHERE active = true AND account_role = 'GUEST_OWNER'`
- FK ON DELETE: `RESTRICT`
- Lock/version 정책: 게스트 복구와 Toss 승격/병합 시 row lock 권장

### user_merge_history

- 역할: 게스트 데이터를 기존 회원으로 병합하는 절차를 기록한다.
- 관련 API: `POST /api/v1/auth/toss/login`, `GET /api/v1/members/me/merge-status`, `POST /api/v1/members/me/merge-retry`
- 멱등성 기준: `source_user_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `source_user_id` | BIGINT | N | | FK `app_user(id)` | UNIQUE | 게스트 source |
| `target_user_id` | BIGINT | N | | FK `app_user(id)` | | 회원 target |
| `status` | VARCHAR(20) | N | `PENDING` | | CHECK `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` | 병합 상태 |
| `progress` | VARCHAR(50) | N | `STARTED` | | | 재시도 기준 단계 |
| `reason` | VARCHAR(50) | N | | | | 병합 사유 |
| `failure_code` | VARCHAR(50) | Y | | | | 실패 코드 |
| `completed_at` | TIMESTAMPTZ | Y | | | | 완료 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_user_merge_target_status(target_user_id, status)`
- FK ON DELETE: `RESTRICT`
- Lock/version 정책: source user 기준 단일 병합 lock

### auth_session_log

- 소유자: A(민재)
- 상태: CONFIRMED
- 역할: 인증 상태 변경과 거부 이벤트를 영구 감사 로그로 남긴다.
- Aggregate: User/Auth
- 관련 API: `POST /api/v1/guests`, `POST /api/v1/auth/toss/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`, `POST /api/v1/auth/logout-all`
- 관련 Event/Port: 회원 정지/탈퇴 세션 폐기, `AuthAuditLogPort`
- 멱등성 기준: `trace_id + event_type + session_id_hash` 권장
- 현재 구현 상태: `IMPLEMENTED`.
- `V1000__create_auth_session_log.sql`, AuthSessionLog Entity, Repository, BIGINT identity, `user_public_id`/`device_public_id` UUID scalar 저장, JSONB `metadata`, Java enum 저장과 `result` DB CHECK는 실제 PostgreSQL 통합 테스트를 통과했다.
- 검증 기준: `FlywayMigrationIntegrationTest`, `AuthAuditServiceIntegrationTest`.
- 감사 로그 저장 실패 Outbox/재처리와 운영 장애 복구는 `IN_PROGRESS`다.
- 향후 `app_user`/`device` 도입 시 내부 FK 연결 여부는 Decision Required다.

| 컬럼명 | PostgreSQL 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | generated by default as identity | PK | | 내부 식별자 |
| `public_id` | UUID | N | Java 생성 | | UNIQUE | 외부 감사 로그 id. DB DEFAULT 없음 |
| `user_public_id` | UUID | Y | | | | 사용자 public id. 최종 FK 도입 전 UUID 값으로 저장 |
| `device_public_id` | UUID | Y | | | | 기기 public id. 최종 FK 도입 전 UUID 값으로 저장 |
| `session_id_hash` | VARCHAR(128) | Y | | | | sessionId hash 또는 마스킹값 |
| `token_family_id_hash` | VARCHAR(128) | Y | | | | token family hash |
| `event_type` | VARCHAR(40) | N | | | Java Enum 저장 | 인증 이벤트. 현재 V1000 DB CHECK 없음 |
| `result` | VARCHAR(20) | N | | | CHECK `SUCCESS`, `FAILURE`, `DENIED` | 결과 |
| `failure_code` | VARCHAR(80) | Y | | | | 실패/거부 코드 |
| `trace_id` | VARCHAR(80) | Y | | | | 요청 trace id |
| `ip_address_masked` | VARCHAR(80) | Y | | | | 마스킹 IP 또는 IP hash |
| `user_agent` | VARCHAR(512) | Y | | | | User-Agent |
| `metadata` | JSONB | Y | | | | 민감정보 제외 JSONB |
| `occurred_at` | TIMESTAMPTZ | N | Java 생성 | | | 발생 시각. DB DEFAULT 없음 |
| `created_at` | TIMESTAMPTZ | N | Java `@PrePersist` | | | 생성 시각. DB DEFAULT 없음 |
| `updated_at` | TIMESTAMPTZ | N | Java `@PrePersist/@PreUpdate` | | | 수정 시각. DB DEFAULT 없음 |

- 인덱스: `ix_auth_session_log_user_time(user_public_id, occurred_at)`, `ix_auth_session_log_session_time(session_id_hash, occurred_at)`, `ix_auth_session_log_event_time(event_type, occurred_at)`, `ix_auth_session_log_trace(trace_id)`
- Partial Unique Index: 없음
- FK ON DELETE: 현재 구현 없음. 최종 내부 FK 도입 시 `SET NULL` 후보.
- Lock/version 정책: append-only. Redis 인증 상태 변경 성공 후 감사 로그 저장 실패가 발생해도 인증 상태를 원복하지 않는다.
- 민감정보와 암호화: Access Token, Refresh Token, Toss authorizationCode, Push Token 원문 저장 금지
- UUID scalar 입력은 null/blank만 nullable로 허용한다. non-blank invalid UUID는 저장을 실패시키고 감사 로그 서비스가 실패 로그만 남기며 인증 상태 변경은 rollback하지 않는다.
- 보관/익명화/삭제: 보안 감사 목적 기간 동안 보관하고 탈퇴 시 사용자 식별자는 정책에 따라 익명화 후보

## Config/Legal

### legal_document

- 역할: 약관/개인정보/사업자 정보 본문을 버전별 저장한다.
- 관련 API: `GET /api/v1/legal-documents/current`
- 멱등성 기준: `document_type + version`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `document_type` | VARCHAR(30) | N | | | CHECK `TERMS`, `PRIVACY`, `BUSINESS_INFO` | 문서 유형 |
| `version` | VARCHAR(30) | N | | | | 버전 |
| `title` | VARCHAR(100) | N | | | | 제목 |
| `content` | TEXT | N | | | | 본문 |
| `effective_at` | TIMESTAMPTZ | N | | | | 시행일 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_legal_document_type_version(document_type, version)`
- 인덱스: `ix_legal_document_current(document_type, active, effective_at)`
- 민감정보: 없음

### user_consent

- 역할: 사용자 법적 문서 동의 이력을 저장한다.
- 관련 API: 회원 가입/승격, 약관 동의
- 멱등성 기준: `user_id + document_type + version`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `document_type` | VARCHAR(30) | N | | | | 동의 문서 유형 |
| `version` | VARCHAR(30) | N | | | | 동의 버전 |
| `consented_at` | TIMESTAMPTZ | N | now() | | | 동의 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_consent_doc(user_id, document_type, version)`
- FK ON DELETE: `RESTRICT`

### app_config

- 역할: A 소유 앱 정책을 append-only로 저장한다.
- 관련 API: `GET /api/v1/app-config`
- 멱등성 기준: `config_key + effective_at`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `config_key` | VARCHAR(80) | N | | | | 설정 키 |
| `config_value` | JSONB | N | | | | 설정 값 |
| `effective_at` | TIMESTAMPTZ | N | | | | 적용 시각 |
| `created_by` | VARCHAR(80) | Y | | | | 운영자 식별자 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_app_config_key_effective(config_key, effective_at)`
- 인덱스: `ix_app_config_active(config_key, effective_at)`
- Lock/version 정책: update 금지, 새 row append

## Keycap/Box

### keycap

- 역할: 키캡 카탈로그.
- 관련 API: `GET /api/v1/keycaps`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `code` | VARCHAR(60) | N | | | UNIQUE | API 노출용 안정 코드 |
| `name` | VARCHAR(80) | N | | | UNIQUE | 이름 |
| `grade` | VARCHAR(20) | N | | | CHECK `COMMON`, `RARE`, `EPIC`, `LEGENDARY`, `LIMITED` | 등급 |
| `required_shard_count` | INTEGER | N | | | CHECK `> 0` | 완성 필요 조각 |
| `image_url` | TEXT | Y | | | | 이미지 |
| `limited` | BOOLEAN | N | false | | | 한정 여부 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_active_grade(active, grade)`
- `code`는 이름 변경과 무관한 seed/master data 식별자로 사용한다.

### user_keycap

- 역할: 사용자 키캡 조각, 완성, 장착 상태.
- 관련 API: `GET /api/v1/keycaps/me`, `PUT /api/v1/keycaps/{keycapId}/equip`
- 멱등성 기준: `user_id + keycap_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `keycap_id` | BIGINT | N | | FK `keycap(id)` | | 키캡 |
| `shard_count` | INTEGER | N | 0 | | CHECK `>= 0` | 보유 조각 |
| `status` | VARCHAR(20) | N | `IN_PROGRESS` | | CHECK `IN_PROGRESS`, `COMPLETED` | 완성 상태 |
| `equipped` | BOOLEAN | N | false | | | 장착 여부 |
| `completed_at` | TIMESTAMPTZ | Y | | | | 완성 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_keycap_user_keycap(user_id, keycap_id)`
- Partial Unique Index: `ux_user_keycap_equipped ON user_keycap(user_id) WHERE equipped = true`
- Lock/version 정책: 조각 증가와 장착 변경 시 row lock

### keycap_drop_table

- 역할: 기간/우선순위별 드롭 테이블.
- 관련 API: 상자 개봉 내부

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `name` | VARCHAR(100) | N | | | | 테이블명 |
| `priority` | INTEGER | N | 0 | | | 우선순위 |
| `purpose` | VARCHAR(20) | N | `STANDARD` | | CHECK `STANDARD`, `ONBOARDING` | 드롭 목적 |
| `active_from` | TIMESTAMPTZ | N | | | | 시작 |
| `active_until` | TIMESTAMPTZ | Y | | | CHECK null or `> active_from` | 종료 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_drop_table_active(active, active_from, active_until, priority)`
- 온보딩 키캡 후보군은 `purpose=ONBOARDING`으로 일반 상자 후보군과 분리한다. SQL 구현 전까지 상태는 명세 제안이며, 일반 상자 정책을 온보딩 정책으로 바꾸지 않는다.

### keycap_drop_item

- 역할: 드롭 테이블의 후보와 가중치.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `drop_table_id` | BIGINT | N | | FK `keycap_drop_table(id)` | | 드롭 테이블 |
| `keycap_id` | BIGINT | N | | FK `keycap(id)` | | 키캡 |
| `grant_mode` | VARCHAR(20) | N | `SHARD` | | CHECK `SHARD`, `COMPLETE_KEYCAP` | 지급 방식 |
| `shard_count` | INTEGER | Y | | | 조건부 CHECK | 지급 조각 |
| `weight` | INTEGER | N | | | CHECK `> 0` | 가중치 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_drop_item_table(drop_table_id)`
- 지급 방식 조건: `(grant_mode = 'SHARD' AND shard_count IS NOT NULL AND shard_count > 0) OR (grant_mode = 'COMPLETE_KEYCAP' AND shard_count IS NULL)`.
- `purpose=STANDARD` 드롭 테이블은 기본적으로 `grant_mode=SHARD`를 사용한다.
- `purpose=ONBOARDING` 드롭 테이블은 `grant_mode=COMPLETE_KEYCAP`을 사용한다.
- 온보딩 지급은 필요한 조각 수를 채우는 방식으로 위장하지 않고 `user_keycap.status=COMPLETED` 결과를 만든다.
- 기존 일반 상자 조각 지급 정책은 유지한다.

### keycap_box_account

- 역할: 사용자 상자 보유량과 개봉 조건.
- 관련 API: `GET /api/v1/keycap-boxes/status`, `POST /api/v1/keycap-boxes/open`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | UNIQUE | 사용자 |
| `balance` | INTEGER | N | 0 | | CHECK `>= 0` | 보유 상자 |
| `next_free_open_at` | TIMESTAMPTZ | Y | | | | 다음 무료 개봉 |
| `ad_open_date` | DATE | Y | | | | 광고 개봉 카운트 날짜 |
| `ad_open_count` | INTEGER | N | 0 | | CHECK `>= 0` | 일 광고 개봉 횟수 |
| `version` | BIGINT | N | 0 | | | 낙관적 lock 후보 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- Lock/version 정책: 상자 개봉 시 row lock 또는 version check

### keycap_box_ledger

- 역할: 상자 증감 원장.
- 관련 Event/Port: `KeycapBoxGrantUseCase`
- 멱등성 기준: `reference_type + reference_id + reason`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `change_amount` | INTEGER | N | | | CHECK `<> 0` | 증감 |
| `reason` | VARCHAR(40) | N | | | | 사유 |
| `reference_type` | VARCHAR(50) | N | | | | 참조 유형 |
| `reference_id` | VARCHAR(100) | N | | | | 참조 id |
| `balance_after` | INTEGER | N | | | CHECK `>= 0` | 반영 후 잔액 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_keycap_box_ledger_ref(reference_type, reference_id, reason)`
- 인덱스: `ix_keycap_box_ledger_user_created(user_id, created_at)`

### keycap_box_open

- 역할: 상자 개봉 요청과 멱등성.
- 관련 API: `POST /api/v1/keycap-boxes/open`
- 멱등성 기준: `user_id + idempotency_key`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `open_method` | VARCHAR(20) | N | | | CHECK `FREE`, `ADVERTISEMENT` | 개봉 방식 |
| `ad_view_id` | UUID | Y | | | | 광고 완료 id |
| `open_count` | INTEGER | N | 1 | | CHECK `= 1` | MVP 요청당 1개 |
| `status` | VARCHAR(20) | N | `COMPLETED` | | CHECK `COMPLETED`, `FAILED` | 처리 상태 |
| `idempotency_key` | VARCHAR(100) | N | | | | 멱등키 |
| `opened_at` | TIMESTAMPTZ | N | now() | | | 개봉 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_keycap_box_open_idem(user_id, idempotency_key)`
- Partial Unique Index: `ux_keycap_box_open_ad_view ON keycap_box_open(ad_view_id) WHERE open_method = 'ADVERTISEMENT'`

### keycap_box_open_result

- 역할: 개봉 결과.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `open_id` | BIGINT | N | | FK `keycap_box_open(id)` | | 개봉 |
| `keycap_id` | BIGINT | N | | FK `keycap(id)` | | 결과 키캡 |
| `shard_count` | INTEGER | N | | | CHECK `> 0` | 지급 조각 |
| `completed` | BOOLEAN | N | false | | | 완성 여부 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_keycap_box_open_result_open(open_id)`

## Region

### region

- 역할: 행정구역 마스터.
- 관련 API: `GET /api/v1/regions`, `POST /api/v1/regions/detect`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `sido` | VARCHAR(50) | N | | | | 시도 |
| `sigungu` | VARCHAR(80) | N | | | | 시군구 |
| `code` | VARCHAR(30) | N | | | UNIQUE | 행정구역 코드 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_region_active_sido(active, sido, sigungu)`

### user_region

- 역할: 사용자 현재 적용 지역.
- 관련 API: `GET /api/v1/members/me/region`, `PUT /api/v1/members/me/region`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | UNIQUE | 사용자 |
| `region_id` | BIGINT | N | | FK `region(id)` | | 현재 지역 |
| `effective_from` | TIMESTAMPTZ | N | now() | | | 적용 시작 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- Lock/version 정책: 지역 변경 적용 시 row lock

### user_region_change

- 역할: 월 1회 지역 변경 예약.
- 멱등성 기준: `user_id + change_month`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `from_region_id` | BIGINT | Y | | FK `region(id)` | | 이전 지역 |
| `to_region_id` | BIGINT | N | | FK `region(id)` | | 변경 지역 |
| `change_month` | CHAR(7) | N | | | | `YYYY-MM` |
| `effective_at` | TIMESTAMPTZ | N | | | | 다음 주 월요일 |
| `status` | VARCHAR(20) | N | `SCHEDULED` | | CHECK `SCHEDULED`, `APPLIED`, `CANCELED` | 상태 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_region_change_month(user_id, change_month)`
- Partial Unique Index: `ux_user_region_change_scheduled ON user_region_change(user_id) WHERE status = 'SCHEDULED'`

## Ranking

### ranking_season

- 역할: 7일 단위 전체 랭킹 시즌. 지역을 직접 갖지 않는다.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `round_number` | INTEGER | N | | | UNIQUE | 화면 노출 회차 번호 |
| `starts_at` | TIMESTAMPTZ | N | | | | 시작 |
| `ends_at` | TIMESTAMPTZ | N | | | CHECK `ends_at > starts_at` | 종료 |
| `status` | VARCHAR(20) | N | `ACTIVE` | | CHECK `ACTIVE`, `SETTLING`, `SETTLED` | 상태 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_season_period(starts_at, ends_at)`
- 정책: 한 시점의 ACTIVE 시즌은 최대 1개이며, 운영 정책상 정확히 7일 단위다.
- 시즌 종료 시 자동 정산과 `ranking_snapshot` 생성을 수행한다.

### ranking_participation

- 역할: 시즌별 사용자 자동 포함 상태.
- 멱등성 기준: `season_id + user_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `joined_at` | TIMESTAMPTZ | N | now() | | | 참여 시각 |
| `status` | VARCHAR(20) | N | `JOINED` | | CHECK `JOINED`, `CANCELED` | 상태 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_participation_user(season_id, user_id)`
- 프론트 수동 참여 API와 연결하지 않는다. 신규 게스트 생성, 신규 시즌 시작, 첫 유효 탭 또는 최초 랭킹 조회 중 어느 시점에 row를 만들지는 Decision Required다.

### ranking_score

- 역할: 현재 랭킹 점수.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `participation_id` | BIGINT | N | | FK `ranking_participation(id)` | UNIQUE | 참여 |
| `score` | BIGINT | N | 0 | | CHECK `>= 0` | 점수 |
| `reached_at` | TIMESTAMPTZ | Y | | | | 현재 점수 최초 도달 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_ranking_score_score(score DESC, reached_at ASC)`
- Lock/version 정책: 점수 반영 시 participation 기준 row lock
- `previous_rank`는 순위 등락 비교 기준이 확정되지 않았으므로 추가하지 않는다. 일일 또는 주기 스냅샷 테이블 필요 여부는 Decision Required다.

### ranking_score_event

- 역할: 점수 반영 멱등성 이벤트.
- 관련 Event/Port: `ValidatedTapApplyUseCase`
- 멱등성 기준: `source_type + source_event_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `source_type` | VARCHAR(40) | N | | | | 원천 유형 |
| `source_event_id` | VARCHAR(100) | N | | | | 원천 이벤트 |
| `score_delta` | BIGINT | N | | | CHECK `> 0` | 증가 점수 |
| `occurred_at` | TIMESTAMPTZ | N | | | | 발생 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_score_event_source(source_type, source_event_id)`

### ranking_snapshot

- 역할: 사용자·시즌별 전체 랭킹 정산 완료 결과. 이전 회차 기록 API의 Source of Truth다.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `final_rank` | INTEGER | N | | | CHECK `> 0` | 최종 순위 |
| `final_score` | BIGINT | N | | | CHECK `>= 0` | 최종 점수 |
| `reached_at` | TIMESTAMPTZ | Y | | | | 동점 보정 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_snapshot_user(season_id, user_id)`
- 인덱스: `ix_ranking_snapshot_rank(season_id, final_rank)`
- `firstPlace`는 `final_rank = 1`에서 파생하므로 별도 Boolean 컬럼을 만들지 않는다.

### ranking_reward

- 역할: 주간 랭킹 보상 자동 지급 결과.
- 멱등성 기준: `season_id + user_id + reward_type`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `reward_type` | VARCHAR(40) | N | | | | 보상 유형 |
| `status` | VARCHAR(20) | N | `GRANTED` | | CHECK `GRANTED`, `FAILED` | 지급 상태 |
| `keycap_id` | BIGINT | Y | | FK `keycap(id)` | | 한정 키캡 |
| `granted_at` | TIMESTAMPTZ | Y | | | | 지급 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_reward_once(season_id, user_id, reward_type)`
- 지역 관련 설명은 갖지 않는다. 최신 MVP에서 보상 모달 노출을 유지할지는 Decision Required이며, 화면 두 개만 보고 `ranking_reward`를 삭제하지 않는다.

## Notification

### push_device

- 역할: 푸시 발송 대상 기기.
- 관련 API: `PUT /api/v1/push-devices/current`, `DELETE /api/v1/push-devices/current`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `device_id` | BIGINT | N | | FK `device(id)` | | 기기 |
| `push_token_ciphertext` | TEXT | N | | | | 암호화된 발송용 토큰 |
| `push_token_hash` | VARCHAR(255) | N | | | UNIQUE | 중복 조회 hash |
| `platform` | VARCHAR(20) | N | | | CHECK `IOS`, `ANDROID` | 플랫폼 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `last_seen_at` | TIMESTAMPTZ | N | now() | | | 마지막 등록 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_push_device_user_active(user_id, active)`
- 민감정보와 암호화: 발송용 토큰은 암호화, 조회는 hash 사용

### notification_preference

- 역할: 사용자 알림 설정.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `type` | VARCHAR(40) | N | | | | 알림 유형 |
| `enabled` | BOOLEAN | N | true | | | 수신 여부 |
| `reminder_time` | TIME | Y | | | | 리마인드 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_notification_preference_user_type(user_id, type)`

### notification_log

- 역할: 푸시 발송/오픈 로그.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `push_device_id` | BIGINT | N | | FK `push_device(id)` | | 발송 기기 |
| `type` | VARCHAR(40) | N | | | | 알림 유형 |
| `title` | VARCHAR(100) | N | | | | 제목 |
| `body` | VARCHAR(500) | Y | | | | 본문 |
| `dedupe_key` | VARCHAR(120) | N | | | | 중복 방지 |
| `status` | VARCHAR(20) | N | `READY` | | CHECK `READY`, `SENT`, `FAILED`, `OPENED` | 상태 |
| `sent_at` | TIMESTAMPTZ | Y | | | | 발송 |
| `opened_at` | TIMESTAMPTZ | Y | | | | 열람 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_notification_log_dedupe_device(dedupe_key, push_device_id)`
- 인덱스: `ix_notification_log_user_created(user_id, created_at)`

## Record

### user_record_daily

- 역할: 일별 기록 projection.
- 관련 Event/Port: `RecordEventIngestUseCase`
- 멱등성 기준: `user_id + record_date`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `record_date` | DATE | N | | | | 기준일 |
| `tap_count` | BIGINT | N | 0 | | CHECK `>= 0` | 탭 |
| `point_amount` | BIGINT | N | 0 | | CHECK `>= 0` | 포인트 |
| `box_count` | INTEGER | N | 0 | | CHECK `>= 0` | 상자 |
| `completed_keycap_count` | INTEGER | N | 0 | | CHECK `>= 0` | 완성 키캡 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_record_daily_user_date(user_id, record_date)`

### user_record_summary

- 역할: 누적 기록 projection.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | UNIQUE | 사용자 |
| `total_tap_count` | BIGINT | N | 0 | | CHECK `>= 0` | 누적 탭 |
| `total_point_amount` | BIGINT | N | 0 | | CHECK `>= 0` | 누적 포인트 |
| `total_box_count` | INTEGER | N | 0 | | CHECK `>= 0` | 누적 상자 |
| `completed_keycap_count` | INTEGER | N | 0 | | CHECK `>= 0` | 완성 키캡 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

### user_record_reward

- 역할: 보상 기록 projection.
- 멱등성 기준: `user_id + reference_type + reference_id + reward_type`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `reference_type` | VARCHAR(50) | N | | | | 참조 유형 |
| `reference_id` | VARCHAR(100) | N | | | | 참조 id |
| `reward_type` | VARCHAR(50) | N | | | | 보상 유형 |
| `amount` | BIGINT | Y | | | | 수량 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_record_reward_ref(user_id, reference_type, reference_id, reward_type)`

## Reliability

### event_outbox

- 역할: A 이벤트 발행 신뢰성.
- 멱등성 기준: `event_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `event_id` | UUID | N | | | UNIQUE | 이벤트 id |
| `aggregate_type` | VARCHAR(60) | N | | | | Aggregate |
| `aggregate_id` | VARCHAR(100) | N | | | | Aggregate public id |
| `event_type` | VARCHAR(80) | N | | | | 이벤트 유형 |
| `payload` | JSONB | N | | | | payload |
| `status` | VARCHAR(20) | N | `READY` | | CHECK `READY`, `PUBLISHED`, `FAILED` | 상태 |
| `retry_count` | INTEGER | N | 0 | | CHECK `>= 0` | 재시도 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_event_outbox_status_created(status, created_at)`
- FK ON DELETE: 실제 FK 없음

### event_inbox

- 역할: B/외부 이벤트 수신 멱등성.
- 멱등성 기준: `event_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `event_id` | UUID | N | | | UNIQUE | 이벤트 id |
| `source` | VARCHAR(40) | N | | | | 이벤트 출처 |
| `event_type` | VARCHAR(80) | N | | | | 이벤트 유형 |
| `payload` | JSONB | N | | | | payload |
| `processed_at` | TIMESTAMPTZ | Y | | | | 처리 시각 |
| `failure_code` | VARCHAR(80) | Y | | | | 실패 코드 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_event_inbox_processed(processed_at, created_at)`
- FK ON DELETE: 실제 FK 없음

## B 담당 DRAFT 테이블

아래 테이블은 전체 API 계약을 구현하기 위한 선작성 제안안이다. 소유자는 B이며, 은창 최종 확정 전 상태는 `DRAFT`다. A 도메인 테이블과 직접 FK를 만들지 않고 `user_public_id`, `device_public_id` 같은 scalar 식별자로 연결한다.

### B 테이블 요약

| 테이블 | 소유자 | 상태 | Aggregate |
|---|---|---|---|
| `tap_batch` | B | DRAFT | Tap |
| `tap_event` | B | DRAFT | Tap |
| `user_tap_daily` | B | DRAFT | Tap |
| `abuse_signal` | B | DRAFT | Tap/Risk |
| `point_account` | B | DRAFT | Point |
| `point_ledger` | B | DRAFT | Point |
| `cashout_request` | B | DRAFT | Cashout |
| `toss_point_transfer` | B | DRAFT | Cashout |
| `ad_placement` | B | DRAFT | Advertisement |
| `ad_view` | B | DRAFT | Advertisement |
| `booster_grant` | B | DRAFT | Booster |
| `user_onboarding_progress` | B | DRAFT | Onboarding |
| `invite_code` | B | DRAFT | Invitation |
| `invite_relation` | B | DRAFT | Invitation |
| `analytics_event` | B | DRAFT | Analytics |

B 공통 정책:

- 외부 API에는 `public_id`를 노출한다.
- A 소유 `app_user`, `device`와 DB FK를 만들지 않는다.
- 금액성/수량성 테이블은 `version BIGINT NOT NULL DEFAULT 0` 또는 row lock을 사용한다.
- 원장 테이블은 수정·삭제보다 반대 분개를 사용한다.
- 정책 수치는 코드 상수로 고정하지 않고 B 설정 소유 방식을 구현 전 합의한다.

## Tap

### tap_batch

- 역할: 프론트 탭 배치 수신, 검증 결과와 멱등성 저장.
- 관련 API: `POST /api/v1/taps/batches`, `GET /api/v1/taps/today`
- 멱등성 기준: `public_id(tapBatchId)`, `user_public_id + tap_session_id + sequence`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | tapBatchId |
| `user_public_id` | UUID | N | | | INDEX | 사용자 public id |
| `device_public_id` | UUID | Y | | | INDEX | 기기 public id |
| `tap_session_id` | UUID | N | | | | 앱 탭 세션 |
| `sequence` | BIGINT | N | | | CHECK `>= 0` | 세션 내 순서 |
| `submitted_count` | INTEGER | N | | | CHECK `> 0 AND <= 500` | 제출 탭 수 |
| `accepted_count` | INTEGER | N | 0 | | CHECK `>= 0` | 인정 탭 수 |
| `rejected_count` | INTEGER | N | 0 | | CHECK `>= 0` | 거절 탭 수 |
| `started_at` | TIMESTAMPTZ | N | | | | 클라이언트 시작 |
| `ended_at` | TIMESTAMPTZ | N | | | | 클라이언트 종료 |
| `elapsed_ms` | INTEGER | N | | | CHECK `> 0` | 경과 ms |
| `interval_stats` | JSONB | Y | | | | min/avg/stddev |
| `status` | VARCHAR(20) | N | `RECEIVED` | | CHECK `RECEIVED`, `ACCEPTED`, `PARTIAL`, `REJECTED` | 처리 상태 |
| `risk_level` | VARCHAR(20) | N | `NORMAL` | | CHECK `NORMAL`, `SUSPICIOUS`, `BLOCKED` | 위험도 |
| `request_hash` | VARCHAR(255) | N | | | | 동일 id 다른 body 방지 |
| `processed_at` | TIMESTAMPTZ | Y | | | | 처리 완료 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_tap_batch_session_sequence(user_public_id, tap_session_id, sequence)`.
- 인덱스: `ix_tap_batch_user_created(user_public_id, created_at DESC)`, `ix_tap_batch_status_created(status, created_at)`.
- Lock/version: 동일 public_id insert unique로 멱등 처리.

### tap_event

- 역할: 탭 배치에서 확정된 포인트/상자/랭킹 delta 이벤트 원장.
- 멱등성 기준: `source_event_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 이벤트 id |
| `tap_batch_id` | BIGINT | N | | FK `tap_batch(id)` | | 원본 배치 |
| `source_event_id` | UUID | N | | | UNIQUE | A/B 연동 멱등 id |
| `user_public_id` | UUID | N | | | INDEX | 사용자 |
| `event_type` | VARCHAR(40) | N | | | CHECK `VALIDATED`, `POINT_GRANTED`, `BOX_PROGRESS_APPLIED`, `RANKING_APPLIED` | 이벤트 |
| `valid_tap_delta` | INTEGER | N | 0 | | CHECK `>= 0` | 유효 탭 delta |
| `point_progress_delta` | INTEGER | N | 0 | | CHECK `>= 0` | 포인트 진행도 |
| `box_progress_delta` | INTEGER | N | 0 | | CHECK `>= 0` | 상자 진행도 |
| `ranking_tap_delta` | INTEGER | N | 0 | | CHECK `>= 0` | 랭킹 delta |
| `payload` | JSONB | Y | | | | 부가 정보 |
| `occurred_at` | TIMESTAMPTZ | N | | | | 발생 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- FK ON DELETE: `tap_batch_id RESTRICT`.
- 인덱스: `ix_tap_event_user_time(user_public_id, occurred_at DESC)`.

### user_tap_daily

- 역할: 사용자 일별 탭 카운터와 포인트 진행도 원본.
- 멱등성 기준: `user_public_id + tap_date`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_public_id` | UUID | N | | | | 사용자 |
| `tap_date` | DATE | N | | | | KST 집계일 |
| `valid_tap_count` | INTEGER | N | 0 | | CHECK `>= 0` | 전체 유효 탭 |
| `point_eligible_tap_count` | INTEGER | N | 0 | | CHECK `BETWEEN 0 AND 5000` | 포인트 대상 탭 |
| `point_progress_remainder` | INTEGER | N | 0 | | CHECK `BETWEEN 0 AND 499` | 500 환산 나머지 |
| `last_sequence` | BIGINT | Y | | | | 마지막 sequence |
| `version` | BIGINT | N | 0 | | | 낙관적 잠금 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_user_tap_daily(user_public_id, tap_date)`.
- Lock/version: 포인트 계산 시 row lock 또는 version update.

### user_onboarding_progress

- 역할: 신규 사용자 온보딩 진행 상태와 15/30/45 milestone 지급 멱등성.
- 소유자/상태: B / DRAFT.
- A 소유 `app_user`와 DB FK를 만들지 않고 `user_public_id` scalar만 저장한다.
- 관련 API: `GET /api/v1/home`, `POST /api/v1/taps/batches`.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_public_id` | UUID | N | | | UNIQUE | 사용자 public id |
| `valid_tap_count` | INTEGER | N | 0 | | CHECK `>= 0` | 온보딩 유효 탭 수 |
| `point_15_granted_at` | TIMESTAMPTZ | Y | | | | 15탭 1P 지급 시각 |
| `point_30_granted_at` | TIMESTAMPTZ | Y | | | | 30탭 추가 1P 지급 시각 |
| `first_keycap_granted_at` | TIMESTAMPTZ | Y | | | | 45탭 온보딩 키캡 지급 시각 |
| `granted_user_keycap_public_id` | UUID | Y | | | | A가 지급한 `user_keycap.public_id` |
| `status` | VARCHAR(20) | N | `IN_PROGRESS` | | CHECK `IN_PROGRESS`, `LOGIN_REQUIRED`, `COMPLETED` | 온보딩 상태 |
| `version` | BIGINT | N | 0 | | | 낙관적 lock |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 사용자당 1행이다.
- 상태 전이: 0~44 유효 탭은 `IN_PROGRESS`, 45탭 milestone과 키캡 지급 성공 후 `LOGIN_REQUIRED`, Toss 회원 승격 성공 후 `COMPLETED`.
- `IN_PROGRESS`와 `LOGIN_REQUIRED`는 API에서 `active=true`, `COMPLETED`는 `active=false`로 계산한다.
- 45탭 보상 지급으로 탭 기반 온보딩 보상 단계는 완료되지만, 회원 로그인/승격 전까지 전체 온보딩 상태는 `LOGIN_REQUIRED`다.
- 로그인 실패 또는 앱 종료 후에도 `LOGIN_REQUIRED`와 기존 포인트/키캡 보상은 유지한다.
- `granted_user_keycap_public_id`는 `OnboardingKeycapGrantUseCase`가 반환한 `userKeycapId`를 FK 없이 UUID scalar로 저장한다. 카탈로그 정보는 A가 `user_keycap -> keycap` 조회로 제공한다.
- 각 milestone timestamp는 한 번만 설정한다.
- optimistic lock 또는 row lock으로 포인트와 키캡 중복 지급을 방지한다.
- `box opening animation started/ended` 같은 UI 상태는 DB에 저장하지 않는다.
- 실제 SQL 파일은 아직 만들지 않았고, B DRAFT 마이그레이션 계획에 포함한다.

### abuse_signal

- 역할: 자동 탭·속도 이상·sequence 이상 등 검출 근거 저장.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `tap_batch_id` | BIGINT | Y | | FK `tap_batch(id)` | | 관련 배치 |
| `user_public_id` | UUID | N | | | INDEX | 사용자 |
| `device_public_id` | UUID | Y | | | INDEX | 기기 |
| `signal_type` | VARCHAR(50) | N | | | | 신호 유형 |
| `severity` | VARCHAR(20) | N | | | CHECK `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` | 심각도 |
| `evidence` | JSONB | N | | | | 민감정보 제외 근거 |
| `status` | VARCHAR(20) | N | `OPEN` | | CHECK `OPEN`, `IGNORED`, `CONFIRMED`, `RESOLVED` | 검토 상태 |
| `detected_at` | TIMESTAMPTZ | N | now() | | | 검출 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_abuse_signal_user_status(user_public_id, status, detected_at DESC)`.

## Point

### point_account

- 역할: 포인트 현재 잔액과 누적 합계.
- 관련 API: `GET /api/v1/points/me`, `POST /api/v1/cashouts`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_public_id` | UUID | N | | | UNIQUE | 사용자 |
| `balance` | BIGINT | N | 0 | | CHECK `>= 0` | 현재 P |
| `lifetime_earned` | BIGINT | N | 0 | | CHECK `>= 0` | 누적 적립 |
| `lifetime_spent` | BIGINT | N | 0 | | CHECK `>= 0` | 누적 사용 |
| `version` | BIGINT | N | 0 | | | 낙관적 잠금 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- Lock/version: 적립·출금 시 row lock 권장.

### point_ledger

- 역할: 포인트 증감 불변 원장.
- 멱등성 기준: `idempotency_key` 또는 `reference_type + reference_id + reason`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `point_account_id` | BIGINT | N | | FK `point_account(id)` | | 계정 |
| `user_public_id` | UUID | N | | | INDEX | 사용자 |
| `entry_type` | VARCHAR(20) | N | | | CHECK `CREDIT`, `DEBIT`, `REVERSAL` | 분개 유형 |
| `amount` | BIGINT | N | | | CHECK `> 0` | 절대 금액 |
| `reason` | VARCHAR(50) | N | | | | 사유 |
| `reference_type` | VARCHAR(40) | N | | | | 참조 유형 |
| `reference_id` | UUID | N | | | | 참조 id |
| `idempotency_key` | UUID | Y | | | UNIQUE | 요청 멱등 키 |
| `balance_after` | BIGINT | N | | | CHECK `>= 0` | 반영 후 잔액 |
| `occurred_at` | TIMESTAMPTZ | N | now() | | | 발생 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_point_ledger_reference(user_public_id, reference_type, reference_id, reason)`.
- 수정/삭제 금지, 오류는 REVERSAL 분개.

## Cashout

### cashout_request

- 역할: 포인트→Toss 포인트 전환 요청과 상태.
- 멱등성 기준: `user_public_id + idempotency_key`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | cashoutId |
| `user_public_id` | UUID | N | | | INDEX | 회원 |
| `point_amount` | BIGINT | N | | | CHECK `>= 10 AND point_amount % 10 = 0` | 출금 P |
| `toss_point_amount` | BIGINT | N | | | CHECK `>= 7` | 지급량 |
| `rate_point_unit` | INTEGER | N | 10 | | CHECK `> 0` | 환산 snapshot |
| `rate_toss_unit` | INTEGER | N | 7 | | CHECK `> 0` | 환산 snapshot |
| `status` | VARCHAR(20) | N | `PENDING` | | CHECK `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `CANCELED` | 상태 |
| `idempotency_key` | UUID | N | | | | 요청 키 |
| `failure_code` | VARCHAR(80) | Y | | | | 실패 코드 |
| `requested_at` | TIMESTAMPTZ | N | now() | | | 요청 |
| `completed_at` | TIMESTAMPTZ | Y | | | | 완료 |
| `version` | BIGINT | N | 0 | | | 상태 전이 lock |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_cashout_user_idempotency(user_public_id, idempotency_key)`.
- 인덱스: `ix_cashout_user_requested(user_public_id, requested_at DESC)`, `ix_cashout_status_requested(status, requested_at)`.

### toss_point_transfer

- 역할: 외부 Toss 지급 호출 시도와 결과.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `cashout_request_id` | BIGINT | N | | FK `cashout_request(id)` | | 출금 요청 |
| `provider_transfer_id` | VARCHAR(255) | Y | | | UNIQUE | Toss transfer id |
| `status` | VARCHAR(20) | N | `READY` | | CHECK `READY`, `REQUESTED`, `SUCCEEDED`, `FAILED` | 상태 |
| `attempt_count` | INTEGER | N | 0 | | CHECK `>= 0` | 시도 횟수 |
| `request_hash` | VARCHAR(255) | Y | | | | 민감정보 없는 요청 hash |
| `provider_code` | VARCHAR(80) | Y | | | | Provider 결과 코드 |
| `last_attempt_at` | TIMESTAMPTZ | Y | | | | 마지막 시도 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_toss_transfer_cashout(cashout_request_id)`.
- FK ON DELETE: `RESTRICT`.

## Advertisement/Booster

### ad_placement

- 역할: 광고 노출 위치와 일 한도 정책.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `code` | VARCHAR(60) | N | | | UNIQUE | placement code |
| `purpose` | VARCHAR(20) | N | | | CHECK `BOX_OPEN`, `BOOSTER`, `BANNER` | 목적 |
| `daily_limit` | INTEGER | Y | | | CHECK `> 0` | 일 한도 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

### ad_view

- 역할: 광고 시청 시작·완료 검증과 재사용 방지.
- 멱등성 기준: `user_public_id + idempotency_key`, provider view id

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | adViewId |
| `placement_id` | BIGINT | N | | FK `ad_placement(id)` | | placement |
| `user_public_id` | UUID | N | | | INDEX | 사용자 |
| `device_public_id` | UUID | Y | | | INDEX | 기기 |
| `purpose` | VARCHAR(20) | N | | | CHECK `BOX_OPEN`, `BOOSTER`, `BANNER` | 목적 |
| `provider_view_id` | VARCHAR(255) | Y | | | UNIQUE | Provider id |
| `status` | VARCHAR(20) | N | `STARTED` | | CHECK `STARTED`, `COMPLETED`, `FAILED`, `EXPIRED` | 상태 |
| `idempotency_key` | UUID | N | | | | 시작 요청 멱등 키 |
| `started_at` | TIMESTAMPTZ | N | now() | | | 시작 |
| `completed_at` | TIMESTAMPTZ | Y | | | | 완료 |
| `expires_at` | TIMESTAMPTZ | N | | | | 완료 가능 만료 |
| `consumed_at` | TIMESTAMPTZ | Y | | | | BOX_OPEN/BOOSTER 사용 시각 |
| `failure_code` | VARCHAR(80) | Y | | | | 실패 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ad_view_user_idempotency(user_public_id, idempotency_key)`.
- Partial Unique 후보: 목적별 재사용은 `consumed_at IS NULL` 조건과 서비스 lock으로 방어.

### booster_grant

- 역할: 광고 완료로 부여된 5분 2배 부스터.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | boosterId |
| `user_public_id` | UUID | N | | | INDEX | 사용자 |
| `ad_view_id` | BIGINT | N | | FK `ad_view(id)` | UNIQUE | 원인 광고 |
| `grant_date` | DATE | N | | | | KST 일자 |
| `daily_sequence` | INTEGER | N | | | CHECK `BETWEEN 1 AND 3` | 당일 순번 |
| `multiplier` | NUMERIC(3,1) | N | 2.0 | | CHECK `= 2.0` | 배수 |
| `status` | VARCHAR(20) | N | `ACTIVE` | | CHECK `ACTIVE`, `EXPIRED`, `CANCELED` | 상태 |
| `starts_at` | TIMESTAMPTZ | N | | | | 시작 |
| `ends_at` | TIMESTAMPTZ | N | | | | 종료 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_booster_user_date_sequence(user_public_id, grant_date, daily_sequence)`.
- Check: `ends_at > starts_at`, MVP duration 5분은 서비스 정책 검증.

## Invitation

### invite_code

- 역할: 사용자별 공유 초대 코드.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_public_id` | UUID | N | | | UNIQUE | 코드 소유자 |
| `code` | VARCHAR(20) | N | | | UNIQUE | 초대 코드 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

### invite_relation

- 역할: inviter/invitee 관계, 첫 유효 탭 자격, 양쪽 보상 상태.
- 멱등성 기준: invitee user, invitee device hash

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | relation id |
| `invite_code_id` | BIGINT | N | | FK `invite_code(id)` | | 사용 코드 |
| `inviter_user_public_id` | UUID | N | | | INDEX | 초대자 |
| `invitee_user_public_id` | UUID | N | | | UNIQUE | 피초대자 |
| `invitee_device_hash` | VARCHAR(255) | Y | | | | 1기기 1보상 판정 |
| `status` | VARCHAR(30) | N | `PENDING_FIRST_TAP` | | CHECK `PENDING_FIRST_TAP`, `QUALIFIED`, `REWARDED`, `CANCELED` | 상태 |
| `accepted_at` | TIMESTAMPTZ | N | now() | | | 수락 |
| `qualified_at` | TIMESTAMPTZ | Y | | | | 첫 유효 탭 |
| `rewarded_at` | TIMESTAMPTZ | Y | | | | 양쪽 보상 완료 |
| `failure_code` | VARCHAR(80) | Y | | | | 실패 |
| `version` | BIGINT | N | 0 | | | 상태 lock |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- Partial Unique: `ux_invite_device_reward(invitee_device_hash) WHERE invitee_device_hash IS NOT NULL AND status IN ('QUALIFIED','REWARDED')`.
- Check: inviter와 invitee가 달라야 함은 서비스 검증.

## Analytics

### analytics_event

- 역할: 화면·행동 분석 이벤트 비동기 수집.
- 멱등성 기준: `event_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | eventId |
| `user_public_id` | UUID | Y | | | INDEX | 사용자 |
| `device_public_id` | UUID | Y | | | INDEX | 기기 |
| `event_name` | VARCHAR(80) | N | | | | 이벤트 이름 |
| `screen_name` | VARCHAR(80) | Y | | | | 화면 |
| `schema_version` | INTEGER | N | 1 | | CHECK `> 0` | 이벤트 스키마 버전 |
| `source` | VARCHAR(20) | N | `CLIENT` | | CHECK `CLIENT`, `SERVER` | 이벤트 생성 주체 |
| `properties` | JSONB | Y | | | | 민감정보 금지 |
| `occurred_at` | TIMESTAMPTZ | N | | | | 클라이언트 발생 |
| `received_at` | TIMESTAMPTZ | N | now() | | | 서버 수신 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_analytics_event_name_time(event_name, occurred_at DESC)`, `ix_analytics_event_user_time(user_public_id, occurred_at DESC)`.
- 보관: 분석 보관 기간과 파티셔닝은 운영 정책에서 확정.

## COMMON 후보 테이블

현재 COMMON 소유로 확정된 별도 테이블은 없다. 공통 응답, trace, 예외 코드는 코드/문서 규칙으로 관리한다.

## 2026-07-03 실제 마이그레이션 검증 메모

- `auth_session_log`는 `V1000__create_auth_session_log.sql`로 실제 PostgreSQL 16 Testcontainer에 생성됨을 검증했다.
- 검증된 주요 타입은 `id BIGINT identity`, `public_id UUID`, `user_public_id UUID`, `device_public_id UUID`, `metadata JSONB`, `occurred_at/created_at/updated_at TIMESTAMPTZ`다.
- V1000은 `public_id`, `occurred_at`, `created_at`, `updated_at`에 DB DEFAULT를 두지 않는다. Java Entity가 값을 생성한다.
- V1000의 DB CHECK는 `result IN ('SUCCESS', 'FAILURE', 'DENIED')`에만 존재한다. `event_type`은 Java Enum 문자열 저장으로 검증하며 DB CHECK는 후속 migration 결정 사항이다.
- Java `AuthSessionLog` Entity는 PostgreSQL `metadata JSONB` 저장/조회와 UUID/enum 문자열 저장을 통합 테스트로 검증했다.
- `app_user`, `device` 등 나머지 A 전체 테이블과 B 담당 DRAFT 테이블의 상세 정책은 기존 표 명세를 Source of Truth로 유지한다.
- `V1010__create_user_auth.sql`은 아직 생성하지 않았고 `PLANNED/NOT_STARTED`다. `app_user`, `device`, `user_device`, `auth_identity`, `user_merge_history` 구현과 `POST /api/v1/guests` API는 `NOT_STARTED`다.
- 후속 순서는 Redis Session save/revoke race 보강, Refresh Rotation revoke marker 확인, V1010 User/Auth 테이블, Entity/Repository, `POST /api/v1/guests`, Toss 승격/병합 순으로 진행한다.
