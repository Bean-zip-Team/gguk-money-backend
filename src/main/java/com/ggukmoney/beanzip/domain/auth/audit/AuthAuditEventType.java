package com.ggukmoney.beanzip.domain.auth.audit;

public enum AuthAuditEventType {
    GUEST_CREATED,
    GUEST_RECOVERED,
    MEMBER_PROMOTED,
    MEMBER_MERGED,
    REFRESHED,
    REFRESH_CONFLICT,
    REFRESH_REUSE_DETECTED,
    LOGOUT,
    LOGOUT_ALL,
    ACCESS_DENIED
}
