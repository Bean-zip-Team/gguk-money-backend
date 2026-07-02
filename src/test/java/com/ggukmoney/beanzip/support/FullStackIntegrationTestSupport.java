package com.ggukmoney.beanzip.support;

import com.ggukmoney.beanzip.domain.auth.infra.RedisAuthSessionRepository;
import com.ggukmoney.beanzip.domain.auth.model.AuthSession;
import com.ggukmoney.beanzip.domain.auth.service.JwtTokenProvider;
import com.ggukmoney.beanzip.domain.auth.util.TokenHash;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class FullStackIntegrationTestSupport {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected RedisAuthSessionRepository authSessionRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void registerInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.test.database.replace", () -> "none");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.repositories.enabled", () -> "false");
        registry.add("springdoc.api-docs.enabled", () -> "false");
        registry.add("springdoc.swagger-ui.enabled", () -> "false");
    }

    @BeforeEach
    void cleanState() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbcTemplate.update("delete from auth_session_log");
    }

    protected TestTokens saveTokenBackedSession(String userPublicId, String devicePublicId) {
        UUID sessionId = UUID.randomUUID();
        String refreshJti = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.createRefreshToken(userPublicId, sessionId, refreshJti);
        String accessToken = jwtTokenProvider.createAccessToken(userPublicId, sessionId, accessJti);
        JwtTokenProvider.JwtTokenClaims refreshClaims = jwtTokenProvider.parseToken(refreshToken);
        JwtTokenProvider.JwtTokenClaims accessClaims = jwtTokenProvider.parseToken(accessToken);
        AuthSession session = new AuthSession(
                sessionId,
                userPublicId,
                devicePublicId,
                TokenHash.sha256Base64Url(refreshJti),
                TokenHash.sha256Base64Url(refreshToken),
                TokenHash.sha256Base64Url("family-" + sessionId),
                null,
                null,
                Instant.now(),
                refreshClaims.expiresAt(),
                "ACTIVE"
        );
        authSessionRepository.save(session);
        return new TestTokens(session, accessToken, refreshToken, accessJti, refreshJti, accessClaims.expiresAt());
    }

    protected record TestTokens(
            AuthSession session,
            String accessToken,
            String refreshToken,
            String accessJti,
            String refreshJti,
            Instant accessExpiresAt
    ) {
    }
}