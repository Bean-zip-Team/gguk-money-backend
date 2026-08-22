# 배포 가이드

작성 2026-08-22. 실측 근거는 모두 운영 서버(52.79.67.165)에서 직접 측정했다.

## 서버 개요

| 항목 | 값 |
|---|---|
| 호스트 | 52.79.67.165 (`api-clickmoney.compounding.co.kr`) |
| 사양 | **RAM 913MB**, 2 vCPU, swap 4GB |
| 앱 | 8080, Spring Boot / Java 26 (Amazon Corretto) |
| 프록시 | nginx 1.30.4 → upstream `clickmoney` (8080 ↔ 8081 교대) |
| DB / Redis | 1.234.82.82 (외부 공용 서버) |
| 앱 RSS | 약 375MB (힙 126MB + 메타스페이스 134MB + 코드캐시 44MB + 기타) |
| 기동 시간 | 약 20초 (전환 시 readiness 확인까지 약 24초) |

## 배포 절차

빌드는 GitHub Actions에서 하고 서버는 jar를 받아 실행만 한다.
서버에서 gradle을 돌리면 Gradle 데몬이 **RSS 312MB + swap 351MB**를 상주로 먹어 913MB 박스를 고갈시킨다.

```
.github/workflows/deploy-develop.yml   수동 실행(workflow_dispatch)
  ├─ verify   jar 무결성만 검사하고 폐기 (운영 무영향)
  └─ release  releases/<sha>/ 배치 + current 링크 교체 + blue-green 전환
```

```
~/clickmoney/
├── current -> releases/<sha>/     실행 중인 릴리스
├── releases/<sha>/app.jar         최근 5개 보관
├── config/application.properties  설정 (gitignored, 이 서버에만 존재)
├── nginx/upstream.conf            활성 포트 (nginx 가 include, minjae 소유)
└── deploy.sh, switch.sh, rollback.sh   CI 가 매 배포마다 갱신
```

### 무중단 전환 (blue-green)

`release` 모드는 `switch.sh`로 blue-green 전환한다. **중단이 없으므로 배포 시간대 제약이 없다.**

```
유휴 포트(8080↔8081)에 새 릴리스 기동
  → readiness 확인 (약 24초)
  → nginx upstream 을 새 포트로 교체 + reload
  → 5초 배출 대기
  → 구 인스턴스 graceful stop
```

`nginx -s reload`가 무중단인 이유: master 프로세스가 리스닝 소켓을 계속 들고 있고 worker만 교체된다. 기존 worker는 처리 중이던 요청을 끝까지 완료하고 스스로 종료한다.

**실측 (0.2초 간격으로 계속 요청하며 전환)**

| 방향 | 요청 | 실패 | 최대 연속 실패 | 전환 중 응답시간 |
|---|---|---|---|---|
| 8080 → 8081 | 161건 | **0건** | **0.0초** | 중앙값 39ms / 최대 133ms |
| 8081 → 8080 | 154건 | **0건** | **0.0초** | 중앙값 44ms / 최대 1.06s |

전환에 실패하면 nginx를 건드리지 않고 새 인스턴스만 정리한다. 즉 **배포 실패가 장애가 되지 않는다.**

### 롤백

```bash
./rollback.sh --list      # 보관 중인 릴리스 확인
./rollback.sh             # 직전 릴리스로
./rollback.sh <sha>       # 특정 릴리스로
```

심볼릭 링크 교체 + 재시작이라 재빌드가 없다. 서버에서 gradle로 다시 빌드하던 기존 방식은 5분 이상 걸렸다.

## zram — 무중단을 가능하게 한 전제

**이 서버에서 blue-green은 zram 없이는 성립하지 않는다.** `/etc/systemd/zram-generator.conf`로 영구 설정돼 있고, `switch.sh`는 zram이 비활성이면 전환을 거부한다.

겹치는 약 24초 동안 두 JVM(각 340~375MB)이 913MB 박스에 함께 올라간다. 부족분은 swap으로 밀리는데, 그 swap이 어디냐가 전부를 가른다.

| swap 위치 | 밀린 페이지를 되읽을 때 full GC 정지 |
|---|---|
| `/swapfile` (EBS) | **19,669ms** |
| `/dev/zram0` (압축 RAM) | **1,021ms** |

EBS로 밀리면 살아남은 인스턴스가 다음 full GC에서 20초 멈춘다. 없애려던 20초 다운타임과 크기가 같은데 **발생 시점을 고를 수 없어** 피크에 터질 수 있다. zram은 압축된 페이지가 RAM에 남아 되읽기가 사실상 공짜다.

```ini
[zram0]
zram-size = 512
compression-algorithm = lzo-rle
swap-priority = 100    # /swapfile(-2) 보다 먼저 사용된다
```

`swapon --show`에 `/dev/zram0`이 PRIO 100으로 보여야 정상이다.

### 시도했다가 버린 것들 — JVM 튜닝

zram 도입 전에 발자국을 줄여보려 했으나 **전부 실패했다.** 기록해 둔다.

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

### 재검토 조건 (해소됨)

RAM 2GB가 필요하다고 판단했으나, zram으로 해결했다. 인스턴스 업그레이드 없이 blue-green이 동작한다.
남은 개선 여지는 `/actuator/health/readiness`다. 현재 readiness 판단은 `/api/tap/today`가 401을 돌려주는지로 대신하고 있다.

### 겹침 구간의 스케줄러 중복

전환 중 약 24초 동안 두 인스턴스가 `@Scheduled`를 동시에 돌린다. 확인한 결과 **코드 변경 없이 안전하다.**

| 스케줄러 | 겹침 시 |
|---|---|
| `CashoutProcessingScheduler` (30초) | `finalizeProcessingCashout`이 `@Transactional`이고 `uq_point_ledger_user_idempotency` unique 제약이 있어 이중 환불이 롤백된다. Toss 폴링 자체는 읽기 전용 |
| `NotificationScheduler` | `dedupeKey` unique 제약 |
| 랭킹 reconcile / rollover | Redis 락 |
| 정책 리프레시 3종 (60초) | 읽기 전용 |

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

## 서버 재구축 절차

앱 코드와 달리 아래 인프라 설정은 CI 가 배포하지 않는다. 서버를 새로 만들면 사람이 한 번 넣어야 한다.
`scripts/infra/` 에 실제 파일이 그대로 들어 있다.

| 파일 | 설치 위치 | 없으면 |
|---|---|---|
| `zram-generator.conf` | `/etc/systemd/zram-generator.conf` | `switch.sh` 가 전환을 거부한다 |
| `clickmoney@.service` | `/etc/systemd/system/clickmoney@.service` | 인스턴스를 띄울 수 없다 |
| `nginx-clickmoney.conf` | `/etc/nginx/conf.d/clickmoney.conf` | upstream 이 없어 전환 대상이 없다 |
| `clickmoney-deploy.sudoers` | `/etc/sudoers.d/clickmoney-deploy` (0440) | CI 가 비밀번호를 못 넣어 배포가 멈춘다 |

```bash
# 1) zram — blue-green 의 전제 조건
sudo cp scripts/infra/zram-generator.conf /etc/systemd/zram-generator.conf
sudo systemctl daemon-reload
swapon --show          # /dev/zram0 이 PRIO 100 으로 보여야 한다

# 2) systemd 템플릿 유닛
sudo cp 'scripts/infra/clickmoney@.service' /etc/systemd/system/
sudo systemctl daemon-reload

# 3) sudoers — 문법 검사를 반드시 먼저 통과시킬 것
sudo visudo -c -f scripts/infra/clickmoney-deploy.sudoers
sudo install -m 0440 -o root -g root scripts/infra/clickmoney-deploy.sudoers /etc/sudoers.d/clickmoney-deploy

# 4) nginx — ssl_* 경로는 이 서버 기준이므로 certbot 발급 후에 반영한다
sudo cp scripts/infra/nginx-clickmoney.conf /etc/nginx/conf.d/clickmoney.conf
sudo nginx -t && sudo nginx -s reload

# 5) 앱 디렉토리 뼈대
mkdir -p ~/clickmoney/{config,releases,nginx,logs}
printf 'upstream clickmoney {\n    server 127.0.0.1:8080 max_fails=0;\n}\n' > ~/clickmoney/nginx/upstream.conf
# config/application.properties 는 시크릿이라 repo 에 없다. 별도로 확보해야 한다.

# 6) 첫 릴리스는 Actions 의 Deploy develop 워크플로로 배포한다
sudo systemctl enable clickmoney@8080
```

`clickmoney.service`(포트가 박힌 비템플릿 유닛)는 `clickmoney@.service` 로 대체됐다.
서버에 남아 있다면 지우는 편이 안전하다 — `systemctl start clickmoney` 를 실행하면 8080 바인딩에 실패하고
`Restart=on-failure` 로 10초마다 무한 재시도한다.

## 남은 작업

| 항목 | 상태 | 비고 |
|---|---|---|
| `ddl-auto=validate` | ✅ 완료 | 2026-08-22 적용 |
| CI 빌드 파이프라인 | ✅ 완료 | `verify` 모드 검증 완료 |
| 릴리스 디렉토리 + 롤백 | ✅ 완료 | `current` → `releases/<sha>` |
| systemd 이관 | ✅ 완료 | 2026-08-22 08:46Z, 중단 21.2초, 유실 요청 0건 |
| 설정 외부화 | ✅ 완료 | 설정 미포함 jar 로 기동 검증 |
| Flyway 도입 | ⬜ 미완 | 현재 마이그레이션은 수동 SQL |
| actuator readiness | ⬜ 미완 | 현재는 `/api/tap/today` 401 응답으로 대체 |
| Swagger 프로덕션 비활성화 | ⬜ 미완 | `springdoc.api-docs.enabled=true` — API 문서가 공개돼 있다 |
| 배포 직후 낙관적 락 충돌 | ⬜ 미완 | 아래 참고 |

### 배포 직후 낙관적 락 충돌

2026-08-22 07:16 재배포 직후 실제 500이 1건 났다.

```
07:16:49  ObjectOptimisticLockingFailureException
          Unexpected row count (expected 1 but was 0) [update user_tap_session ...]
```

기동 완료 2초 뒤, **1초 안에 8건이 몰린 구간**에서 발생했다. 중단 동안 대기하던 클라이언트가 한꺼번에 재시도하는 thundering herd다.
커밋 `04e7906`에서 유사한 충돌을 한 번 수정했으나 남은 경로가 있다.
blue-green 도입으로 중단이 사라져 thundering herd 자체가 없어졌지만, 동시 요청이 몰리면 여전히 재현될 수 있는 경로다.

## 이관 기록 (2026-08-22)

수동 nohup → systemd 전환을 08:46Z(17:46 KST)에 수행했다.

| 항목 | 값 |
|---|---|
| 중단 시간 | **21.2초** (08:46:02.3 kill → 08:46:23.5 Tomcat 기동) |
| 유실된 실사용자 요청 | **0건** (nginx 502 3건은 전부 확인용 curl) |
| 기동 시간 | 18.5초 |
| 전환 후 | `MainPID=849789`, `parent=1`, RSS 361MB, 응답 20~28ms |

전환과 함께 정리한 것:

- 7일간 정지 상태로 방치돼 있던 root `vim /etc/systemd/system/clickmoney.service` 종료
- `app.pid` 제거 (PID 관리 주체가 systemd 로 이동)
- `/etc/sudoers.d/clickmoney-deploy` 추가 — CI 가 비밀번호 없이 `systemctl start/stop clickmoney@*`, `nginx -t`, `nginx -s reload` 를 실행할 수 있어야 무인 배포가 된다

이후 09:10Z 에 blue-green 으로 재전환했다. 이때는 **중단 0초**였고, 같은 절차로 `clickmoney.service` → `clickmoney@8080` 템플릿 이관까지 무중단으로 끝냈다.
