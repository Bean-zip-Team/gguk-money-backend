package com.ggukmoney.beanzip.global.common;

import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ErrorCode {

    COMMON_INVALID_REQUEST("COMMON_INVALID_REQUEST", "요청 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    COMMON_VALIDATION_ERROR("COMMON_VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    COMMON_METHOD_NOT_ALLOWED("COMMON_METHOD_NOT_ALLOWED", "허용되지 않은 메서드입니다.", HttpStatus.METHOD_NOT_ALLOWED),
    COMMON_UNSUPPORTED_MEDIA_TYPE("COMMON_UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 요청 형식입니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    COMMON_INTERNAL_SERVER_ERROR("COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    AUTH_REQUIRED("AUTH_REQUIRED", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_TOKEN("AUTH_INVALID_TOKEN", "유효하지 않은 인증 정보입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_EXPIRED_TOKEN("AUTH_EXPIRED_TOKEN", "만료된 인증 정보입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_DENIED("AUTH_ACCESS_DENIED", "유효하지 않은 인증 정보입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_USER_REVOKED("AUTH_USER_REVOKED", "만료된 인증 정보입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_SESSION_NOT_FOUND("AUTH_SESSION_NOT_FOUND", "인증 세션을 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_SESSION_USER_MISMATCH("AUTH_SESSION_USER_MISMATCH", "인증 세션이 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_REUSED("AUTH_REFRESH_REUSED", "이미 사용된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_CONFLICT("AUTH_REFRESH_CONFLICT", "리프레시 토큰 갱신 충돌이 발생했습니다.", HttpStatus.CONFLICT),
    AUTH_USER_NOT_FOUND("AUTH_USER_NOT_FOUND", "인증 사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_REQUIRED("AUTH_REFRESH_REQUIRED", "리프레시 토큰이 필요합니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REFRESH_EXPIRED("AUTH_REFRESH_EXPIRED", "만료된 리프레시 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_LOGOUT_SESSION_MISMATCH("AUTH_LOGOUT_SESSION_MISMATCH", "로그아웃 세션이 일치하지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_REQUIRED("AUTH_ACCESS_REQUIRED", "액세스 토큰이 필요합니다.", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_EXPIRED("AUTH_ACCESS_EXPIRED", "만료된 액세스 토큰입니다.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EMPTY("AUTH_TOKEN_EMPTY", "인증 토큰이 비어 있습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_MALFORMED("AUTH_TOKEN_MALFORMED", "인증 토큰 형식이 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_PARSE_FAILED("AUTH_TOKEN_PARSE_FAILED", "인증 토큰을 해석할 수 없습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_ISSUER("AUTH_INVALID_ISSUER", "인증 토큰 발급자가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_INVALID_CLAIM("AUTH_INVALID_CLAIM", "인증 토큰 정보가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED),
    AUTH_REDIS_UNAVAILABLE("AUTH_REDIS_UNAVAILABLE", "인증 세션 저장소를 사용할 수 없습니다.", HttpStatus.SERVICE_UNAVAILABLE),

    JWT_CREATE_FAILED("JWT_CREATE_FAILED", "인증 토큰 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    JWT_SIGN_FAILED("JWT_SIGN_FAILED", "인증 토큰 서명에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),

    ACCOUNT_WITHDRAWN("ACCOUNT_WITHDRAWN", "탈퇴한 계정입니다.", HttpStatus.FORBIDDEN),
    MEMBER_NOT_FOUND("MEMBER_NOT_FOUND", "회원을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    NICKNAME_ALREADY_EXISTS("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),

    TOSS_AUTHORIZATION_CODE_REQUIRED("TOSS_AUTHORIZATION_CODE_REQUIRED", "Toss authorizationCode가 필요합니다.", HttpStatus.BAD_REQUEST),
    TOSS_ACCESS_TOKEN_MISSING("TOSS_ACCESS_TOKEN_MISSING", "Toss accessToken이 없습니다.", HttpStatus.BAD_REQUEST),
    TOSS_USER_KEY_MISSING("TOSS_USER_KEY_MISSING", "Toss userKey가 없습니다.", HttpStatus.BAD_GATEWAY),
    TOSS_IDENTITY_NOT_FOUND("TOSS_IDENTITY_NOT_FOUND", "Toss 연결 정보를 찾을 수 없습니다.", HttpStatus.CONFLICT),
    TOSS_USER_MISMATCH("TOSS_USER_MISMATCH", "현재 사용자와 Toss 사용자가 일치하지 않습니다.", HttpStatus.FORBIDDEN),
    TOSS_WEBHOOK_EVENT_REQUIRED("TOSS_WEBHOOK_EVENT_REQUIRED", "Toss webhook 이벤트가 필요합니다.", HttpStatus.BAD_REQUEST),
    TOSS_WEBHOOK_SECRET_REQUIRED("TOSS_WEBHOOK_SECRET_REQUIRED", "Toss webhook secret이 설정되지 않았습니다.", HttpStatus.BAD_REQUEST),
    TOSS_WEBHOOK_UNSUPPORTED_EVENT("TOSS_WEBHOOK_UNSUPPORTED_EVENT", "지원하지 않는 Toss webhook 이벤트입니다.", HttpStatus.BAD_REQUEST),
    TOSS_WEBHOOK_UNAUTHORIZED("TOSS_WEBHOOK_UNAUTHORIZED", "Toss webhook 인증에 실패했습니다.", HttpStatus.UNAUTHORIZED),
    TOSS_SERVER_ERROR("TOSS_SERVER_ERROR", "Toss 서버 오류가 발생했습니다.", HttpStatus.BAD_GATEWAY),
    TOSS_UNLINK_FAILED("TOSS_UNLINK_FAILED", "Toss 연결 해제에 실패했습니다.", HttpStatus.BAD_GATEWAY),
    TOSS_CLIENT_NOT_CONFIGURED("TOSS_CLIENT_NOT_CONFIGURED", "Toss 클라이언트 설정이 올바르지 않습니다.", HttpStatus.BAD_GATEWAY),
    TOSS_REQUEST_INVALID("TOSS_REQUEST_INVALID", "Toss 요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    TOSS_INVALID_GRANT("TOSS_INVALID_GRANT", "Toss 인증 코드가 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),

    TAP_RATE_LIMITED("TAP_RATE_LIMITED", "잠시 후 다시 시도해주세요.", HttpStatus.TOO_MANY_REQUESTS),
    TAP_REDIS_UNAVAILABLE("TAP_REDIS_UNAVAILABLE", "탭 처리 서버가 일시적으로 불안정합니다.", HttpStatus.SERVICE_UNAVAILABLE),
    TAP_PROGRESS_NOT_FOUND("TAP_PROGRESS_NOT_FOUND", "탭 진행도를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    POINT_ACCOUNT_NOT_FOUND("POINT_ACCOUNT_NOT_FOUND", "포인트 계정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    KEYCAP_NOT_FOUND("KEYCAP_NOT_FOUND", "키캡을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    USER_KEYCAP_NOT_FOUND("USER_KEYCAP_NOT_FOUND", "보유한 키캡을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    KEYCAP_NOT_COMPLETED("KEYCAP_NOT_COMPLETED", "완성한 키캡만 장착할 수 있습니다.", HttpStatus.BAD_REQUEST),
    KEYCAP_BOX_ACCOUNT_NOT_FOUND("KEYCAP_BOX_ACCOUNT_NOT_FOUND", "키캡 상자 계정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    AD_VIEW_ID_REQUIRED("AD_VIEW_ID_REQUIRED", "adViewId가 필요합니다.", HttpStatus.BAD_REQUEST),
    BOOSTER_ALREADY_ACTIVE("BOOSTER_ALREADY_ACTIVE", "이미 부스터가 활성화되어 있습니다.", HttpStatus.CONFLICT),
    BOOSTER_DAILY_LIMIT_EXCEEDED("BOOSTER_DAILY_LIMIT_EXCEEDED", "오늘 부스터 사용 횟수를 모두 사용했습니다.", HttpStatus.TOO_MANY_REQUESTS),

    CASHOUT_MINIMUM_NOT_MET("CASHOUT_MINIMUM_NOT_MET", "최소 출금 포인트 미달", HttpStatus.BAD_REQUEST),
    CASHOUT_ALREADY_PROCESSING("CASHOUT_ALREADY_PROCESSING", "처리 중인 출금 존재", HttpStatus.CONFLICT),
    CASHOUT_NOT_FOUND("CASHOUT_NOT_FOUND", "출금 요청을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public HttpStatus status() {
        return status;
    }

    public static ErrorCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(errorCode -> errorCode.code.equals(code))
                .findFirst()
                .orElse(null);
    }

    public static ErrorCode fromStatus(int status) {
        if (status == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return COMMON_METHOD_NOT_ALLOWED;
        }
        if (status == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return COMMON_UNSUPPORTED_MEDIA_TYPE;
        }
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            return AUTH_REQUIRED;
        }
        if (status == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            return COMMON_INTERNAL_SERVER_ERROR;
        }
        return COMMON_INVALID_REQUEST;
    }
}
