package com.ggukmoney.beanzip.domain.auth.audit;

import com.ggukmoney.beanzip.domain.auth.util.TokenHash;
import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final AuthSessionLogRepository repository;

    public void record(
            String userPublicId,
            String devicePublicId,
            UUID sessionId,
            String tokenFamilyIdHash,
            AuthAuditEventType eventType,
            AuthAuditResult result,
            String failureCode,
            String metadata
    ) {
        try {
            repository.saveAndFlush(AuthSessionLog.create(
                    userPublicId,
                    devicePublicId,
                    sessionId == null ? null : TokenHash.sha256Base64Url(sessionId.toString()),
                    tokenFamilyIdHash,
                    eventType,
                    result,
                    failureCode,
                    RequestLogContext.currentTraceIdOrDefault(),
                    null,
                    null,
                    metadata
            ));
        } catch (RuntimeException exception) {
            log.error(
                    "AUTH_AUDIT_WRITE_FAILED traceId={} eventType={} result={} failureCode={}",
                    RequestLogContext.currentTraceIdOrDefault(),
                    eventType,
                    result,
                    failureCode,
                    exception
            );
        }
    }
}

