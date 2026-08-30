package com.ggukmoney.beanzip.domain.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ggukmoney.beanzip.domain.auth.client.TossAuthClient;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossLoginRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossUnlinkWebhookRequest;
import com.ggukmoney.beanzip.domain.auth.dto.response.AuthTokenResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.tap.service.UserTapProgressService;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.config.TapPolicyConfig;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AuthServiceTossLifecycleTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new ObjectMapper(),
            "test-secret-test-secret-test-secret",
            "ggukmoney",
            Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC)
    );
    private RedisService redisService;
    private TossAuthClient tossAuthClient;
    private UserService userService;
    private PointAccountService pointAccountService;
    private KeycapBoxAccountService keycapBoxAccountService;
    private UserTapProgressService userTapProgressService;
    private TapPolicyConfig tapPolicyConfig;
    private AuthLoginTransactionService authLoginTransactionService;
    private AuthWithdrawalTransactionService authWithdrawalTransactionService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        redisService = mock(RedisService.class);
        lenient().when(redisService.executeScript(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(0L);
        tossAuthClient = mock(TossAuthClient.class);
        userService = mock(UserService.class);
        pointAccountService = mock(PointAccountService.class);
        keycapBoxAccountService = mock(KeycapBoxAccountService.class);
        userTapProgressService = mock(UserTapProgressService.class);
        tapPolicyConfig = mock(TapPolicyConfig.class);
        authLoginTransactionService = mock(AuthLoginTransactionService.class);
        authWithdrawalTransactionService = mock(AuthWithdrawalTransactionService.class);
        authService = new AuthService(
                jwtTokenProvider,
                redisService,
                tossAuthClient,
                authLoginTransactionService,
                authWithdrawalTransactionService
        );
        ReflectionTestUtils.setField(authService, "tossWebhookSecret", "webhook-secret");
    }

    private List<String> capturedRevokeReasons(UUID userId, int expectedCallCount) {
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisService, times(expectedCallCount)).executeScript(
                any(RedisScript.class),
                eq(List.of(AuthService.userSessionsKey(userId), "ggukmoney:auth:revoke:user:" + userId)),
                anyString(),
                reasonCaptor.capture(),
                anyString(),
                anyString(),
                anyString()
        );
        return reasonCaptor.getAllValues();
    }

    @Test
    void tossLoginCreatesUuidUserIdentityAccountsAndRedisSession() {
        UUID userId = UUID.randomUUID();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe(
                "user-key-1", "Bean", "https://img", List.of("service_terms_v1", "privacy_v2")
        ));
        when(authLoginTransactionService.loginWithTossUser("user-key-1", "Bean", "https://img", null, List.of("service_terms_v1", "privacy_v2")))
                .thenReturn(new AuthLoginTransactionService.LoginTransactionResult(userId, true, false));

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isTrue();
        assertThat(response.onboardingRewardApplied()).isFalse();
        assertThat(jwtTokenProvider.parseToken(response.accessToken()).userId()).isEqualTo(userId);
        verify(authLoginTransactionService).loginWithTossUser("user-key-1", "Bean", "https://img", null, List.of("service_terms_v1", "privacy_v2"));
        verify(redisService).putAllHash(anyString(), anyMap());
    }

    @Test
    void tossLoginPassesOnboardingAttemptIdAndReturnsAppliedFlag() {
        UUID userId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", "https://img"));
        when(authLoginTransactionService.loginWithTossUser("user-key-1", "Bean", "https://img", attemptId, List.of()))
                .thenReturn(new AuthLoginTransactionService.LoginTransactionResult(userId, true, true));

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT", attemptId));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isTrue();
        assertThat(response.onboardingRewardApplied()).isTrue();
        verify(authLoginTransactionService).loginWithTossUser("user-key-1", "Bean", "https://img", attemptId, List.of());
        verify(redisService).putAllHash(anyString(), anyMap());
    }

    @Test
    void tossLoginReusesExistingActiveUuidUserWithoutCreatingAccountsAgain() {
        UUID userId = UUID.randomUUID();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", "https://img"));
        when(authLoginTransactionService.loginWithTossUser("user-key-1", "Bean", "https://img", null, List.of()))
                .thenReturn(new AuthLoginTransactionService.LoginTransactionResult(userId, false, false));

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isFalse();
        assertThat(response.onboardingRewardApplied()).isFalse();
        verify(userService, never()).createActive(any(), any());
        verify(pointAccountService, never()).createFor(any());
        verify(keycapBoxAccountService, never()).createFor(any());
        verify(userTapProgressService, never()).createFor(any(), any());
        verify(authLoginTransactionService).loginWithTossUser("user-key-1", "Bean", "https://img", null, List.of());
        verify(redisService).putAllHash(anyString(), anyMap());
    }

    @Test
    void tossLoginIssuesSessionForReactivatedExistingUser() {
        UUID userId = UUID.randomUUID();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", null));
        when(authLoginTransactionService.loginWithTossUser("user-key-1", "Bean", null, null, List.of()))
                .thenReturn(new AuthLoginTransactionService.LoginTransactionResult(userId, false, false));

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isFalse();
        assertThat(response.onboardingRewardApplied()).isFalse();
        verify(userService, never()).createActive(any(), any());
        verify(userService, never()).recordLogin(any(), any(), any());
        verify(redisService).putAllHash(anyString(), anyMap());
    }

    @Test
    void withdrawalRequiresFreshTossUserKeyToMatchCurrentIdentity() {
        UUID userId = UUID.randomUUID();
        when(authWithdrawalTransactionService.prepare(userId))
                .thenReturn(new AuthWithdrawalTransactionService.WithdrawalPreparation(userId, false, "user-key-1"));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("other-user-key", null, null));

        assertThatThrownBy(() -> authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("TOSS_USER_MISMATCH");

        verify(tossAuthClient, never()).removeByUserKey(any(), any());
        verify(authWithdrawalTransactionService, never()).complete(any(), any());
    }

    @Test
    void withdrawalUnlinksTossThenSoftWithdrawsAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        when(authWithdrawalTransactionService.prepare(userId))
                .thenReturn(new AuthWithdrawalTransactionService.WithdrawalPreparation(userId, false, "user-key-1"));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", null, null));

        authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT"));

        verify(tossAuthClient).removeByUserKey("toss-access", "user-key-1");
        verify(authWithdrawalTransactionService).complete(userId, "DIRECT_WITHDRAWAL");
        assertThat(capturedRevokeReasons(userId, 1)).allMatch(reason -> reason.contains("\"reason\":\"WITHDRAWAL\""));
    }

    @Test
    void withdrawalDoesNotSoftWithdrawWhenTossUnlinkFails() {
        UUID userId = UUID.randomUUID();
        when(authWithdrawalTransactionService.prepare(userId))
                .thenReturn(new AuthWithdrawalTransactionService.WithdrawalPreparation(userId, false, "user-key-1"));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", null, null));
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_UNLINK_FAILED"))
                .when(tossAuthClient).removeByUserKey("toss-access", "user-key-1");

        assertThatThrownBy(() -> authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("TOSS_UNLINK_FAILED");

        verify(authWithdrawalTransactionService, never()).complete(any(), any());
        verifyNoInteractions(redisService);
    }

    @Test
    void webhookValidatesSecretTreatsMissingUserAsProcessedAndDoesNotCallTossUnlink() {
        when(authWithdrawalTransactionService.completeFromWebhook("missing-key", "UNLINK"))
                .thenReturn(Optional.empty());

        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("missing-key", "UNLINK")).processed())
                .isTrue();

        verify(tossAuthClient, never()).removeByUserKey(any(), any());
        verify(authWithdrawalTransactionService).completeFromWebhook("missing-key", "UNLINK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNLINK", "WITHDRAWAL_TERMS", "WITHDRAWAL_TOSS"})
    void webhookSoftWithdrawsRegisteredUserForEachSupportedWithdrawalEvent(String eventType) {
        UUID userId = UUID.randomUUID();
        when(authWithdrawalTransactionService.completeFromWebhook("user-key-1", eventType))
                .thenReturn(Optional.of(userId));

        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("user-key-1", eventType)).processed())
                .isTrue();
        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("user-key-1", eventType)).processed())
                .isTrue();

        verify(authWithdrawalTransactionService, times(2)).completeFromWebhook("user-key-1", eventType);
        assertThat(capturedRevokeReasons(userId, 2)).allMatch(reason -> reason.contains("\"reason\":\"TOSS_UNLINK_WEBHOOK\""));
        verify(tossAuthClient, never()).removeByUserKey(any(), any());
    }

    @Test
    void webhookLogsMatchedWithdrawalWithoutLoggingTossUserKey() {
        UUID userId = UUID.randomUUID();
        when(authWithdrawalTransactionService.completeFromWebhook("toss-user-key", "WITHDRAWAL_TOSS"))
                .thenReturn(Optional.of(userId));
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("toss-user-key", "WITHDRAWAL_TOSS"));

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("TOSS_UNLINK_WEBHOOK_PROCESSED", "eventType=WITHDRAWAL_TOSS", "identityFound=true", "action=USER_WITHDRAWN", "userId=" + userId)
                            .doesNotContain("toss-user-key"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void webhookLogsMissingIdentityWithoutWritingConsentHistory() {
        when(authWithdrawalTransactionService.completeFromWebhook("missing-key", "UNLINK"))
                .thenReturn(Optional.empty());
        Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("missing-key", "UNLINK"));

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("TOSS_UNLINK_WEBHOOK_PROCESSED", "eventType=UNLINK", "identityFound=false", "action=IDENTITY_NOT_FOUND", "userId=-")
                            .doesNotContain("missing-key"));
            verify(authWithdrawalTransactionService).completeFromWebhook("missing-key", "UNLINK");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void webhookRejectsUnsupportedEventType() {
        assertThatThrownBy(() -> authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("key", "OTHER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void webhookRejectsWrongSecret() {
        assertThatThrownBy(() -> authService.handleTossUnlinkWebhook(basic("wrong-secret"), new TossUnlinkWebhookRequest("key", "UNLINK")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static AppUser withId(AppUser user, UUID userId) {
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private static String basic(String secret) {
        return "Basic " + Base64.getEncoder().encodeToString(secret.getBytes());
    }
}
