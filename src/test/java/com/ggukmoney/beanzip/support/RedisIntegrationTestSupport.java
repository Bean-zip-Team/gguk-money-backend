package com.ggukmoney.beanzip.support;

import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
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
    protected RedisAuthSessionRepository repository;

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
        repository = new RedisAuthSessionRepository(redisTemplate);
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

    protected AuthSession activeSession(
            UUID sessionId,
            String userPublicId,
            String devicePublicId,
            String currentRefreshJtiHash,
            String refreshTokenHash,
            String tokenFamilyIdHash,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return new AuthSession(
                sessionId,
                userPublicId,
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