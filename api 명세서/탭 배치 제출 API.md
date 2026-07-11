# **👉 Description**

클라이언트가 누적한 탭(연타) 배치를 서버에 제출하는 API입니다. **30초 또는 100탭 중 먼저 도달하는 시점**마다 호출하는 것을 전제로 설계되었습니다.

서버는 클라이언트가 보낸 `submittedCount`를 그대로 신뢰하지 않고, 최소 클릭 간격·분당 상한·일일 상한·배치 도착 간격 기반 봇 탐지를 거쳐 실제로 인정할 탭 수(`acceptedCount`)를 다시 계산합니다. 유효 탭이 누적되어 내부 목표치에 도달하면 포인트(P)가 자동으로 지급됩니다.

같은 `tapSessionId` + `sequence` 조합으로 재전송하면 **멱등 처리**되어 중복 지급되지 않고, 그 시점의 잔액 스냅샷만 반환합니다.

> ⚠️ 다음 포인트 지급까지 남은 탭 수(목표치)는 **의도적으로 응답에 포함하지 않습니다** — 적립 시점을 예측하지 못하게 하기 위한 정책입니다.

---

# 👉Request Header

| name | type | description |
| --- | --- | --- |
| Content-Type | String | application/json |
| Authorization | String | accessToken (type: Bearer) |

# 👉Request **Body**

| name | type | requiered | Description |
| --- | --- | --- | --- |
| tapSessionId | UUID(String) | true | 클라이언트 탭 세션 식별자. 같은 세션 내 배치 순서 구분 및 멱등키의 일부로 사용 |
| sequence | Long | true | 세션 내 배치 순번(0 이상). `tapSessionId`와 조합해 멱등키로 사용됨 |
| submittedCount | Integer | true | 이번 배치에서 클라이언트가 집계한 탭 개수(1 이상). 서버가 재검증하므로 실제 인정 개수와 다를 수 있음 |

```json
{
    "tapSessionId": "b3f1c2a0-1234-4a5b-9c3d-abcdef123456",
    "sequence": 5,
    "submittedCount": 87
}
```

---

# **👉 Response**

### ✅ **성공 응답 예시**

### **Response Code**

`200`

### **Response Body**

| name | type | Description |
| --- | --- | --- |
| acceptedCount | Integer | 서버가 검증 후 유효하다고 인정한 탭 개수 (봇 의심 시 0으로 반영될 수 있음) |
| pointsAwarded | Integer | 이번 요청 처리로 새로 지급된 포인트 개수 |
| balance | Long | 처리 후 현재 포인트 잔액 |

```json
{
  "success": true,
  "data": {
    "acceptedCount": 87,
    "pointsAwarded": 1,
    "balance": 42
  }
}
```

**멱등 재전송 시 응답 예시** (같은 `tapSessionId` + `sequence` 재요청 — 재지급 없이 잔액 스냅샷만 반환)

```json
{
  "success": true,
  "data": {
    "acceptedCount": 87,
    "pointsAwarded": 0,
    "balance": 42
  }
}
```

### ❌ **실패 응답 예시**

### **Response Code**

`400 Bad Request`

### **Response Body (요청 값이 올바르지 않은 경우)**

```json
{
  "success": false,
  "error": {
    "code": "COMMON_VALIDATION_ERROR",
    "message": "요청 값이 올바르지 않습니다."
  }
}
```

---

### **Response Code**

`401 Unauthorized`

### **Response Body (Access Token 누락/유효하지 않은 경우)**

```json
{
  "success": false,
  "error": {
    "code": "AUTH_REQUIRED",
    "message": "인증이 필요합니다."
  }
}
```

---

### **Response Code**

`429 Too Many Requests`

### **Response Body (전송 빈도 제한 초과 — 토큰버킷 소진)**

```json
{
  "success": false,
  "error": {
    "code": "TAP_RATE_LIMITED",
    "message": "잠시 후 다시 시도해주세요."
  }
}
```

---

### **Response Code**

`503 Service Unavailable`

### **Response Body (Redis 등 탭 처리 인프라 장애)**

```json
{
  "success": false,
  "error": {
    "code": "TAP_REDIS_UNAVAILABLE",
    "message": "탭 처리 서버가 일시적으로 불안정합니다."
  }
}
```

---

### **Response Code**

`500 Internal Server Error`

### **Response Body (서버 오류 발생 시)**

```json
{
  "success": false,
  "error": {
    "code": "COMMON_INTERNAL_SERVER_ERROR",
    "message": "서버 내부 오류가 발생했습니다."
  }
}
```

---
