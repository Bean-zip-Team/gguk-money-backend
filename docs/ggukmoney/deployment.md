# 배포 가이드

작성 2026-08-22. 실측 근거는 모두 운영 서버(52.79.67.165)에서 직접 측정했다.

## 서버 개요

| 항목 | 값 |
|---|---|
| 호스트 | 52.79.67.165 (`api-clickmoney.compounding.co.kr`) |
| 사양 | **RAM 913MB**, 2 vCPU, swap 4GB |
| 앱 | 8080, Spring Boot / Java 26 (Amazon Corretto) |
| 프록시 | nginx 1.30.4 → `127.0.0.1:8080` |
| DB / Redis | 1.234.82.82 (외부 공용 서버) |
| 앱 RSS | 약 375MB (힙 126MB + 메타스페이스 134MB + 코드캐시 44MB + 기타) |
| 기동 시간 | **약 20초** |

## 배포 절차

빌드는 GitHub Actions에서 하고 서버는 jar를 받아 실행만 한다.
서버에서 gradle을 돌리면 Gradle 데몬이 **RSS 312MB + swap 351MB**를 상주로 먹어 913MB 박스를 고갈시킨다.

```
.github/workflows/deploy-develop.yml   수동 실행(workflow_dispatch)
  ├─ verify   jar 무결성만 검사하고 폐기 (운영 무영향)
  └─ release  releases/<sha>/ 배치 + current 링크 교체 + 재시작
```

```
~/clickmoney/
├── current -> releases/<sha>/     실행 중인 릴리스
├── releases/<sha>/app.jar         최근 5개 보관
├── config/application.properties  설정 (gitignored, 이 서버에만 존재)
└── deploy.sh, rollback.sh         CI 가 매 배포마다 갱신
```

### 배포 창 — 02:00~04:00 KST

**배포는 이 시간대에만 한다.** 기동 20초 동안 서비스가 끊기는데, 시간대별 트래픽 실측이 이렇다.

| 시간대 (KST) | 요청량 | 20초 중단 시 영향 |
|---|---|---|
| 22~23시 (피크) | 30 req/min | 약 10건 실패 |
| 13~16시 | 10~25 req/min | 3~8건 실패 |
| **02~04시** | **2~3 req/hour** | **0.02건** |

03시 배포는 영향받는 요청이 사실상 0이다. 무중단 배포 없이도 같은 결과를 얻는다.

### 롤백

```bash
./rollback.sh --list      # 보관 중인 릴리스 확인
./rollback.sh             # 직전 릴리스로
./rollback.sh <sha>       # 특정 릴리스로
```

심볼릭 링크 교체 + 재시작이라 재빌드가 없다. 서버에서 gradle로 다시 빌드하던 기존 방식은 5분 이상 걸렸다.

## 무중단(blue-green)을 도입하지 않은 이유

nginx upstream + 8080/8081 두 프로세스 방식을 **3회 실측한 뒤 기각했다.** 원리상으로는 맞지만 이 서버에서는 손해다.

### 실측 결과

| 시도 | green RSS@READY | 겹침 중 최저 available |
|---|---|---|
| 기본 (`-Xms128m`) | 335MB | **8MB** |
| `-Xms64m -Xss512k` | 343MB | 15MB |
| + `springdoc` 비활성화 | 356MB | 24MB |

**JVM 튜닝이 전부 실패했다.** 측정 간 편차(±20MB)가 튜닝 효과보다 크다.

- `-Xms64m`이 안 먹힌 이유: 기동 과정에서 실제로 그만큼의 힙이 필요해 READY 시점엔 같은 크기가 된다
- 메타스페이스 110MB는 로드되는 클래스 수가 결정한다 (Spring + Hibernate + QueryDSL + MapStruct)
- Swagger를 꺼도 메타스페이스는 2MB만 줄었다
- 서버의 다른 프로세스에서 회수할 메모리도 없다 (로컬 Redis는 이미 정지, 나머지 합쳐 75MB인데 전부 필수)

**앱 발자국 약 340MB는 이 앱의 하한선이다.**

### 기각의 결정적 근거 — swap이 유발하는 20초 GC 정지

blue-green은 필연적으로 상대 인스턴스의 페이지를 swap으로 밀어낸다. 실측에서 blue의 RSS가 415MB → 160MB까지 눌리고 swap에 276MB가 쌓였다.

이 상태에서 full GC를 걸어봤더니:

```
GC.run 소요: 19,669ms   ← swap 240MB 를 되읽느라 20초 stop-the-world
```

없애려던 20초 다운타임과 같은 크기인데, **예정된 시각이 아니라 아무 때나(피크 포함) 터진다.**
배포 창을 지키면 20초 중단의 영향이 0.02건인 반면, 이쪽은 언제 30 req/min 구간에 걸릴지 알 수 없다.

### 재검토 조건

RAM이 2GB 이상이 되면 blue-green이 성립한다. 그때 필요한 것:

1. nginx `upstream` 블록 + 8081 포트
2. `/actuator/health/readiness` (현재 actuator 미포함, 기동 완료 판단 수단이 로그 grep뿐)
3. **스케줄러 중복 가드** — 겹치는 20초 동안 `@Scheduled`가 양쪽에서 돈다
   - `CashoutProcessingScheduler`(30초 주기): 같은 출금 건 이중 폴링 → FAILED 시 환불 이중 시도
   - `NotificationScheduler`: `dedupeKey` unique 제약이 막아줌
   - 랭킹 reconcile/rollover: Redis 락 있음
   - 정책 리프레시 3종: 읽기 전용

## 스키마 관리

`spring.jpa.hibernate.ddl-auto=validate` (2026-08-22 `update`에서 변경).

`update`는 위험하다. 2026-08-22에 실제로 장애를 냈다.

```
WARN GenerationTarget encountered exception accepting command : Error executing DDL "
    alter table if exists user_tap_daily
       add column total_valid_tap_count integer not null" via JDBC
[ERROR: column "total_valid_tap_count" of relation "user_tap_daily" contains null values]
```

Hibernate가 `DEFAULT` 없이 `NOT NULL` 컬럼을 기존 행이 있는 테이블에 추가하려다 실패했는데, **이 실패를 WARN으로 삼키고 정상 기동했다.** 이후 해당 엔티티를 건드리는 모든 요청이 500을 냈고 26분간 지속됐다.

`validate`는 스키마가 엔티티와 다르면 **기동 자체가 실패**한다. 500을 뿌리는 대신 배포가 실패로 끝나고, 이전 릴리스가 계속 서비스한다.

**따라서 엔티티에 컬럼을 추가할 때는 마이그레이션 SQL을 먼저 적용해야 한다.**

```sql
-- 기존 행이 있는 테이블에는 반드시 DEFAULT 를 준다
ALTER TABLE user_tap_daily ADD COLUMN total_valid_tap_count INTEGER NOT NULL DEFAULT 0;
UPDATE user_tap_daily SET total_valid_tap_count = valid_tap_count;  -- 백필이 필요하면
```

Flyway 도입이 다음 단계다. 현재 마이그레이션 도구가 없어 수동 SQL에 의존한다.

## 남은 작업

| 항목 | 상태 | 비고 |
|---|---|---|
| `ddl-auto=validate` | ✅ 완료 | 2026-08-22 적용 |
| CI 빌드 파이프라인 | ✅ 완료 | `verify` 모드 검증 완료 |
| 릴리스 디렉토리 + 롤백 | ✅ 스크립트 완료 | systemd 이관 후 동작 |
| **systemd 이관** | ⬜ 미완 | 재시작 1회 필요. 아래 참고 |
| Flyway 도입 | ⬜ 미완 | |
| actuator readiness | ⬜ 미완 | blue-green 재검토 시 필요 |
| Swagger 프로덕션 비활성화 | ⬜ 미완 | `springdoc.api-docs.enabled=true` — API 문서가 공개돼 있다 |

### systemd 이관이 필요한 이유

현재 앱은 SSH 셸에서 띄운 **수동 nohup 프로세스**다. 그런데 `clickmoney.service`가 **enabled 상태로 남아 있다**(2026-08-18 이후 inactive).

- 서버를 재부팅하면 systemd가 앱을 띄우는데, 수동 실행과 설정 소스가 다르다
- `systemctl start clickmoney`를 실행하면 8080 바인딩 실패 → `Restart=on-failure`로 10초마다 무한 재시도
- 수동 프로세스라 크래시 시 되살아나지 않는다

`scripts/clickmoney.service`가 교정된 유닛이다. 이관은 배포 창에서 재시작 1회로 끝난다.
