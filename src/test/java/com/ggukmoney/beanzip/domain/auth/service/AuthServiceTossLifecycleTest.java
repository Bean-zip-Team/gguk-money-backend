package com.ggukmoney.beanzip.domain.auth.service;

import com.ggukmoney.beanzip.domain.auth.client.TossAuthClient;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossLoginRequest;
import com.ggukmoney.beanzip.domain.auth.dto.request.TossUnlinkWebhookRequest;
import com.ggukmoney.beanzip.domain.auth.dto.response.AuthTokenResponse;
import com.ggukmoney.beanzip.domain.auth.entity.AuthIdentity;
import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.repository.AuthIdentityRepository;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxAccountService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.dto.request.UserWithdrawalRequest;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AuthServiceTossLifecycleTest {

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
            new ObjectMapper(),
            "test-secret-test-secret-test-secret",
            "ggukmoney",
            Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC)
    );
    private RedisAuthSessionRepository authSessionRepository;
    private TossAuthClient tossAuthClient;
    private AuthIdentityRepository authIdentityRepository;
    private UserService userService;
    private PointAccountService pointAccountService;
    private KeycapBoxAccountService keycapBoxAccountService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authSessionRepository = mock(RedisAuthSessionRepository.class);
        tossAuthClient = mock(TossAuthClient.class);
        authIdentityRepository = mock(AuthIdentityRepository.class);
        userService = mock(UserService.class);
        pointAccountService = mock(PointAccountService.class);
        keycapBoxAccountService = mock(KeycapBoxAccountService.class);
        authService = new AuthService(
                jwtTokenProvider,
                authSessionRepository,
                tossAuthClient,
                authIdentityRepository,
                userService,
                pointAccountService,
                keycapBoxAccountService
        );
        ReflectionTestUtils.setField(authService, "tossWebhookSecret", "webhook-secret");
    }

    @Test
    void tossLoginCreatesUuidUserIdentityAccountsAndRedisSession() {
        UUID userId = UUID.randomUUID();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", "https://img"));
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "user-key-1")).thenReturn(Optional.empty());
        when(userService.createActive("Bean", "https://img")).thenReturn(withId(AppUser.createActive("Bean", "https://img"), userId));

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isTrue();
        assertThat(jwtTokenProvider.parseToken(response.accessToken()).userId()).isEqualTo(userId);
        verify(authIdentityRepository).save(any(AuthIdentity.class));
        verify(pointAccountService).createFor(any(AppUser.class));
        verify(keycapBoxAccountService).createFor(any(AppUser.class));
        verify(authSessionRepository).save(any());
    }

    @Test
    void tossLoginReusesExistingActiveUuidUserWithoutCreatingAccountsAgain() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Old", null), userId);
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", "https://img"));
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "user-key-1"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));
        when(userService.recordLogin(user, "Bean", "https://img")).thenAnswer(invocation -> {
            user.recordLogin("Bean", "https://img");
            return user;
        });

        AuthTokenResponse response = authService.loginWithToss(new TossLoginRequest("code", "DEFAULT"));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.newUser()).isFalse();
        assertThat(user.getNickname()).isEqualTo("Bean");
        verify(userService, never()).createActive(any(), any());
        verify(pointAccountService, never()).createFor(any());
        verify(keycapBoxAccountService, never()).createFor(any());
        verify(authSessionRepository).save(any());
    }

    @Test
    void tossLoginRejectsWithdrawnIdentityWithoutRecreatingUser() {
        AppUser user = withId(AppUser.createActive("Bean", null), UUID.randomUUID());
        user.withdraw();
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", "Bean", null));
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "user-key-1"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));

        assertThatThrownBy(() -> authService.loginWithToss(new TossLoginRequest("code", "DEFAULT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(userService, never()).createActive(any(), any());
        verify(userService, never()).recordLogin(any(), any(), any());
    }

    @Test
    void withdrawalRequiresFreshTossUserKeyToMatchCurrentIdentity() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", null), userId);
        when(userService.getById(userId)).thenReturn(user);
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("other-user-key", null, null));

        assertThatThrownBy(() -> authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("TOSS_USER_MISMATCH");

        assertThat(user.isWithdrawn()).isFalse();
        verify(tossAuthClient, never()).removeByUserKey(any(), any());
        verify(userService, never()).withdraw(any());
    }

    @Test
    void withdrawalUnlinksTossThenSoftWithdrawsAndRevokesSessions() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", "https://img"), userId);
        when(userService.getById(userId)).thenReturn(user);
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", null, null));
        when(userService.withdraw(user)).thenAnswer(invocation -> {
            user.withdraw();
            return user;
        });

        authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT"));

        verify(tossAuthClient).removeByUserKey("toss-access", "user-key-1");
        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getProfileImageUrl()).isNull();
        verify(authSessionRepository).revokeAllUserSessions(eq(userId), eq("jti"), eq(Instant.parse("2026-07-02T00:15:00Z")), any(Instant.class), eq("WITHDRAWAL"));
    }

    @Test
    void withdrawalDoesNotSoftWithdrawWhenTossUnlinkFails() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", "https://img"), userId);
        when(userService.getById(userId)).thenReturn(user);
        when(authIdentityRepository.findByUserIdAndProvider(userId, AuthIdentity.Provider.TOSS))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));
        when(tossAuthClient.generateToken("code", "DEFAULT")).thenReturn(new TossAuthClient.TossToken("toss-access"));
        when(tossAuthClient.loginMe("toss-access")).thenReturn(new TossAuthClient.TossLoginMe("user-key-1", null, null));
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TOSS_UNLINK_FAILED"))
                .when(tossAuthClient).removeByUserKey("toss-access", "user-key-1");

        assertThatThrownBy(() -> authService.withdrawCurrentUser(userId, "jti", Instant.parse("2026-07-02T00:15:00Z"), new UserWithdrawalRequest("code", "DEFAULT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getReason())
                .isEqualTo("TOSS_UNLINK_FAILED");

        assertThat(user.isWithdrawn()).isFalse();
        verify(userService, never()).withdraw(any());
        verify(authSessionRepository, never()).revokeAllUserSessions(any(), any(), any(), any(), any());
    }

    @Test
    void webhookValidatesSecretTreatsMissingUserAsProcessedAndDoesNotCallTossUnlink() {
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "missing-key")).thenReturn(Optional.empty());

        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("missing-key", "UNLINK")).processed())
                .isTrue();

        verify(tossAuthClient, never()).removeByUserKey(any(), any());
        verify(userService, never()).withdraw(any());
    }

    @Test
    void webhookSoftWithdrawsRegisteredUserAndIsIdempotentOnReplay() {
        UUID userId = UUID.randomUUID();
        AppUser user = withId(AppUser.createActive("Bean", "https://img"), userId);
        when(authIdentityRepository.findByProviderAndProviderUserId(AuthIdentity.Provider.TOSS, "user-key-1"))
                .thenReturn(Optional.of(AuthIdentity.toss(user, "user-key-1")));
        when(userService.withdraw(user)).thenAnswer(invocation -> {
            user.withdraw();
            return user;
        });

        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("user-key-1", "UNLINK")).processed())
                .isTrue();
        assertThat(authService.handleTossUnlinkWebhook(basic("webhook-secret"), new TossUnlinkWebhookRequest("user-key-1", "UNLINK")).processed())
                .isTrue();

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getProfileImageUrl()).isNull();
        verify(userService, times(1)).withdraw(any());
        verify(authSessionRepository, times(2)).revokeAllUserSessions(eq(userId), eq(null), eq(null), any(Instant.class), eq("TOSS_UNLINK_WEBHOOK"));
        verify(tossAuthClient, never()).removeByUserKey(any(), any());
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
