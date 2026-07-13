package com.ggukmoney.beanzip.support;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.tap.service.TapBatchService;
import com.ggukmoney.beanzip.global.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

@Testcontainers
public abstract class RedisIntegrationTestSupport {

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    protected StringRedisTemplate redisTemplate;
    protected AuthService authService;
    protected TapBatchService tapBatchService;

    private LettuceConnectionFactory connectionFactory;

    @BeforeEach
    void setUpRedisTemplate() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        RedisService redisService = new RedisService(redisTemplate);
        authService = new AuthService(null, redisService, null, null, null, null, null, null, null);
        tapBatchService = new TapBatchService(null, null, null, null, null, null, null, redisService, null, null);
        flushRedis();
    }

    @AfterEach
    void tearDownRedisTemplate() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    protected void flushRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    protected AuthService.AuthSession activeSession(
            UUID sessionId,
            UUID userId,
            String devicePublicId,
            String currentRefreshJtiHash,
            String refreshTokenHash,
            String tokenFamilyIdHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new AuthService.AuthSession(
                sessionId,
                userId,
                devicePublicId,
                currentRefreshJtiHash,
                refreshTokenHash,
                tokenFamilyIdHash,
                null,
                null,
                issuedAt,
                expiresAt,
                "ACTIVE"
        );
    }
}
