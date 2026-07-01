# 꾹머니 PostgreSQL 테이블 명세

이 문서는 전체 PostgreSQL 테이블, 컬럼, 제약, 인덱스의 Source of Truth다. A 담당 테이블은 CONFIRMED, B 담당 미확정 테이블은 DRAFT로 구분한다.

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
- 관련 API: `POST /guests`, `POST /auth/toss/login`, `GET /members/me`, `DELETE /members/me`
- 관련 Event/Port: `RecordEventIngestUseCase`, `UserWithdrawalGuardPort`
- 멱등성 기준: `deviceKeyHash + GUEST_OWNER`, Toss provider identity

| 컬럼명 | PostgreSQL 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 식별자 |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 사용자 id |
| `account_type` | VARCHAR(20) | N | | | CHECK `GUEST`, `MEMBER` | 계정 유형 |
| `status` | VARCHAR(20) | N | `ACTIVE` | | CHECK `ACTIVE`, `SUSPENDED`, `WITHDRAWN`, `MERGED` | 계정 상태 |
| `nickname` | VARCHAR(50) | Y | | | | 표시 이름 |
| `merged_to_user_id` | BIGINT | Y | | FK `app_user(id)` | | MERGED 시 target |
| `last_login_at` | TIMESTAMPTZ | Y | | | | 마지막 로그인 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 시각 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 시각 |

- 인덱스: `ix_app_user_status(status)`, `ix_app_user_merged_to_user_id(merged_to_user_id)`
- FK ON DELETE: `merged_to_user_id`는 `SET NULL`
- Lock/version 정책: 회원 병합과 탈퇴 시 row lock 권장
- 민감정보와 암호화: 직접 민감정보 없음
- 보관/익명화/삭제: 탈퇴 시 nickname 익명화, 법무 기준에 따라 user id 보관

### auth_identity

- 역할: Toss 등 외부 로그인 식별자를 사용자와 연결한다.
- 관련 API: `POST /auth/toss/login`
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
- 관련 API: `POST /guests`, `PUT /push-devices/current`
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
- 관련 API: `POST /guests`, `POST /auth/toss/login`
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
- 관련 API: `POST /auth/toss/login`, `GET /members/me/merge-status`, `POST /members/me/merge-retry`
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

- 역할: 인증 상태 변경과 거부 이벤트를 영구 감사 로그로 남긴다.
- 관련 API: `POST /guests`, `POST /auth/toss/login`, `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all`
- 관련 Event/Port: 회원 정지/탈퇴 세션 폐기
- 멱등성 기준: `trace_id + event_type + session_id_hash` 권장

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `user_id` | BIGINT | Y | | FK `app_user(id)` | | 실패 전 user 미확정 가능 |
| `device_id` | BIGINT | Y | | FK `device(id)` | | 기기 |
| `session_id_hash` | VARCHAR(255) | Y | | | | sessionId hash 또는 마스킹값 |
| `token_family_id_hash` | VARCHAR(255) | Y | | | | token family hash |
| `event_type` | VARCHAR(40) | N | | | CHECK event enum | 인증 이벤트 |
| `result` | VARCHAR(20) | N | | | CHECK `SUCCESS`, `FAILURE`, `DENIED` | 결과 |
| `failure_code` | VARCHAR(60) | Y | | | | 실패/거부 코드 |
| `trace_id` | VARCHAR(80) | N | | | | 요청 trace id |
| `ip_address_masked` | VARCHAR(80) | Y | | | | 마스킹 IP |
| `ip_hash` | VARCHAR(255) | Y | | | | IP hash |
| `user_agent` | VARCHAR(500) | Y | | | | User-Agent |
| `metadata` | JSONB | Y | | | | 민감정보 제외 부가 정보 |
| `occurred_at` | TIMESTAMPTZ | N | now() | | | 발생 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_auth_session_log_user_time(user_id, occurred_at)`, `ix_auth_session_log_session_time(session_id_hash, occurred_at)`, `ix_auth_session_log_event_time(event_type, occurred_at)`, `ix_auth_session_log_trace(trace_id)`
- FK ON DELETE: `user_id SET NULL`, `device_id SET NULL`
- 민감정보와 암호화: Access/Refresh Token 원문 저장 금지, authorizationCode 저장 금지
- 보관/익명화/삭제: IP/User-Agent 보관 기간은 운영 정책으로 제한

## Config/Legal

### legal_document

- 역할: 약관/개인정보/사업자 정보 본문을 버전별 저장한다.
- 관련 API: `GET /legal-documents/current`
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
- 관련 API: `GET /app-config`
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
- 관련 API: `GET /keycaps`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `name` | VARCHAR(80) | N | | | UNIQUE | 이름 |
| `grade` | VARCHAR(20) | N | | | CHECK `COMMON`, `RARE`, `EPIC`, `LEGENDARY`, `LIMITED` | 등급 |
| `required_shard_count` | INTEGER | N | | | CHECK `> 0` | 완성 필요 조각 |
| `image_url` | TEXT | Y | | | | 이미지 |
| `limited` | BOOLEAN | N | false | | | 한정 여부 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_active_grade(active, grade)`

### user_keycap

- 역할: 사용자 키캡 조각, 완성, 장착 상태.
- 관련 API: `GET /keycaps/me`, `PUT /keycaps/{keycapId}/equip`
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
| `active_from` | TIMESTAMPTZ | N | | | | 시작 |
| `active_until` | TIMESTAMPTZ | Y | | | CHECK null or `> active_from` | 종료 |
| `active` | BOOLEAN | N | true | | | 활성 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_drop_table_active(active, active_from, active_until, priority)`

### keycap_drop_item

- 역할: 드롭 테이블의 후보와 가중치.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `drop_table_id` | BIGINT | N | | FK `keycap_drop_table(id)` | | 드롭 테이블 |
| `keycap_id` | BIGINT | N | | FK `keycap(id)` | | 키캡 |
| `shard_count` | INTEGER | N | | | CHECK `> 0` | 지급 조각 |
| `weight` | INTEGER | N | | | CHECK `> 0` | 가중치 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- 인덱스: `ix_keycap_drop_item_table(drop_table_id)`

### keycap_box_account

- 역할: 사용자 상자 보유량과 개봉 조건.
- 관련 API: `GET /keycap-boxes/status`, `POST /keycap-boxes/open`

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
- 관련 API: `POST /keycap-boxes/open`
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
- 관련 API: `GET /regions`, `POST /regions/detect`

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
- 관련 API: `GET /members/me/region`, `PUT /members/me/region`

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

- 역할: 주간 랭킹 시즌. 지역을 직접 갖지 않는다.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `starts_at` | TIMESTAMPTZ | N | | | | 시작 |
| `ends_at` | TIMESTAMPTZ | N | | | CHECK `ends_at > starts_at` | 종료 |
| `status` | VARCHAR(20) | N | `ACTIVE` | | CHECK `ACTIVE`, `SETTLING`, `SETTLED` | 상태 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_season_period(starts_at, ends_at)`

### ranking_participation

- 역할: 시즌별 사용자 참여 지역.
- 멱등성 기준: `season_id + user_id`

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `region_id` | BIGINT | N | | FK `region(id)` | | 참여 지역 |
| `joined_at` | TIMESTAMPTZ | N | now() | | | 참여 시각 |
| `status` | VARCHAR(20) | N | `JOINED` | | CHECK `JOINED`, `CANCELED` | 상태 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- UNIQUE: `ux_ranking_participation_user(season_id, user_id)`
- 인덱스: `ix_ranking_participation_region(season_id, region_id)`

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

- 역할: 정산 완료 결과.

| 컬럼명 | 타입 | NULL | 기본값 | PK/FK | UNIQUE/CHECK | 설명 |
|---|---|---:|---|---|---|---|
| `id` | BIGINT | N | identity | PK | | 내부 id |
| `public_id` | UUID | N | generated | | UNIQUE | 외부 id |
| `season_id` | BIGINT | N | | FK `ranking_season(id)` | | 시즌 |
| `region_id` | BIGINT | Y | | FK `region(id)` | | null이면 전국 |
| `user_id` | BIGINT | N | | FK `app_user(id)` | | 사용자 |
| `final_rank` | INTEGER | N | | | CHECK `> 0` | 최종 순위 |
| `final_score` | BIGINT | N | | | CHECK `>= 0` | 최종 점수 |
| `reached_at` | TIMESTAMPTZ | Y | | | | 동점 보정 시각 |
| `created_at` | TIMESTAMPTZ | N | now() | | | 생성 |
| `updated_at` | TIMESTAMPTZ | N | now() | | | 수정 |

- Partial Unique Index: `ux_ranking_snapshot_region(season_id, region_id, user_id) WHERE region_id IS NOT NULL`
- Partial Unique Index: `ux_ranking_snapshot_national(season_id, user_id) WHERE region_id IS NULL`
- 인덱스: `ix_ranking_snapshot_rank(season_id, region_id, final_rank)`

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

## Notification

### push_device

- 역할: 푸시 발송 대상 기기.
- 관련 API: `PUT /push-devices/current`, `DELETE /push-devices/current`

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

아래 테이블은 전체 서비스 ERD 관점에서 필요한 후보지만 B 담당자 최종 확정 전까지 컬럼을 확정하지 않는다.

| 테이블 | owner | status | note |
|---|---|---|---|
| `tap_batch` | B | DRAFT | B 담당자 최종 확정 필요 |
| `tap_event` | B | DRAFT | B 담당자 최종 확정 필요 |
| `user_tap_daily` | B | DRAFT | B 담당자 최종 확정 필요 |
| `abuse_signal` | B | DRAFT | B 담당자 최종 확정 필요 |
| `point_account` | B | DRAFT | B 담당자 최종 확정 필요 |
| `point_ledger` | B | DRAFT | B 담당자 최종 확정 필요 |
| `cashout_request` | B | DRAFT | B 담당자 최종 확정 필요 |
| `toss_point_transfer` | B | DRAFT | B 담당자 최종 확정 필요 |
| `ad_placement` | B | DRAFT | B 담당자 최종 확정 필요 |
| `ad_view` | B | DRAFT | B 담당자 최종 확정 필요 |
| `booster_grant` | B | DRAFT | B 담당자 최종 확정 필요 |
| `invite_code` | B | DRAFT | B 담당자 최종 확정 필요 |
| `invite_relation` | B | DRAFT | B 담당자 최종 확정 필요 |
| `analytics_event` | B | DRAFT | B 담당자 최종 확정 필요 |

## COMMON 후보 테이블

현재 COMMON 소유로 확정된 별도 테이블은 없다. 공통 응답, trace, 예외 코드는 코드/문서 규칙으로 관리한다.
