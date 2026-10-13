package com.ggukmoney.beanzip.global.config.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 설정 파일(application.properties) 읽기 전용 목록. 서버마다 다른 값이라 화면에서 바꾸지 않는다.
 *
 * <p>보여줄 키를 여기 명시한다(전부 덤프하지 않는다). 비밀 키는 값 대신 설정 여부만 보여준다.
 */
@Component
@RequiredArgsConstructor
public class OpsSettings {

    private static final String SECRET = "(비밀)";

    private final Environment environment;

    public record Entry(String group, String key, String value, String note) {
    }

    private record Spec(String group, String key, boolean secret, String defaultValue, String note) {
    }

    private static final List<Spec> SPECS = List.of(
            plain("인프라", "spring.datasource.url", null, "DB"),
            plain("인프라", "spring.datasource.username", null, null),
            secret("인프라", "spring.datasource.password"),
            plain("인프라", "spring.datasource.hikari.maximum-pool-size", "10", "DB 커넥션 수"),
            plain("인프라", "spring.data.redis.host", null, null),
            plain("인프라", "spring.data.redis.port", "6379", null),
            plain("인프라", "spring.data.redis.username", null, null),
            secret("인프라", "spring.data.redis.password"),
            plain("인프라", "spring.data.redis.database", "0", "운영 0, 알파 5"),
            plain("인프라", "spring.jpa.hibernate.ddl-auto", null, "validate 면 컬럼이 없을 때 앱이 뜨지 않는다"),
            plain("인프라", "logging.file.name", null, null),
            plain("인프라", "server.forward-headers-strategy", null, null),
            plain("인프라", "springdoc.swagger-ui.enabled", "true", "Swagger"),
            plain("인프라", "app.cors.allowed-origins", null, "API 를 부를 수 있는 다른 출처"),

            secret("보안", "app.auth.jwt.secret"),
            plain("보안", "app.auth.jwt.issuer", "ggukmoney", null),
            plain("보안", "app.auth.jwt.access-token-ttl", "15m", "access 토큰 유효 시간"),
            plain("보안", "app.auth.jwt.refresh-token-ttl", "30d", "refresh 토큰 유효 시간"),
            secret("보안", "app.ops.token"),
            secret("보안", "app.ops.config-token"),
            plain("보안", "app.policy.revert-overrides", "false", "true 면 배포 때 정책 값을 코드 기본값으로 되돌린다"),

            plain("토스 연동", "app.auth.toss.base-url", null, null),
            plain("토스 연동", "app.cashout.toss.base-url", null, null),
            secret("토스 연동", "app.auth.toss.webhook-secret"),
            secret("토스 연동", "app.auth.toss.decryption-key"),
            secret("토스 연동", "app.auth.toss.aad"),
            plain("토스 연동", "spring.ssl.bundle.pem.toss-auth.keystore.certificate", null, "mTLS 인증서 경로"),
            plain("토스 연동", "spring.ssl.bundle.pem.toss-promotion.keystore.certificate", null, "mTLS 인증서 경로"),

            plain("프로모션 코드", "app.cashout.toss.promotion-code", null, "출금"),
            plain("프로모션 코드", "app.promotion.toss.keycap-five-code", null, "키캡 5종 모으기"),
            plain("프로모션 코드", "app.promotion.toss.tap-thousand-code", null, "1,000번 누르기"),

            plain("알림 캠페인 코드", "app.smart-message.templates.rank-change.campaign-code", null, "비어 있으면 발송 안 함"),
            plain("알림 캠페인 코드", "app.smart-message.templates.booster-recharged.campaign-code", null, "비어 있으면 발송 안 함"),
            plain("알림 캠페인 코드", "app.smart-message.templates.booster-unused.campaign-code", null, "비어 있으면 발송 안 함"),
            plain("알림 캠페인 코드", "app.smart-message.templates.daily-mission.campaign-code", null, "비어 있으면 발송 안 함"),
            plain("알림 캠페인 코드", "app.smart-message.templates.weekly-reward-available.campaign-code", null, "비어 있으면 발송 안 함"),
            plain("알림 캠페인 코드", "app.smart-message.templates.daily-reminder.campaign-code", null, "비어 있으면 발송 안 함"),

            plain("스케줄", "app.smart-message.schedule.morning-cron", "0 30 8 * * *", "아침 알림"),
            plain("스케줄", "app.smart-message.schedule.evening-cron", "0 0 19 * * *", "저녁 알림"),
            plain("스케줄", "app.smart-message.schedule.daily-mission-cron", "0 0 21 * * *", "데일리 미션 알림"),
            plain("스케줄", "app.smart-message.schedule.weekly-reset-cron", "0 * * * * *", "주간 리셋 알림"),
            plain("스케줄", "app.mission.rank-snapshot-cron", "0 55 23 * * *", "일일 순위 스냅샷"),
            plain("스케줄", "app.mission.reward-expiry-cron", "0 5 0 * * *", "미션 보상 만료"),
            plain("스케줄", "app.cashout.polling.fixed-delay", "30s", "출금 상태 확인 주기"),
            plain("스케줄", "app.business-time-zone", "Asia/Seoul", null)
    );

    public List<Entry> entries() {
        return SPECS.stream().map(this::entry).toList();
    }

    private Entry entry(Spec spec) {
        String raw = environment.getProperty(spec.key());
        String value;
        if (spec.secret()) {
            value = StringUtils.hasText(raw) ? SECRET : "(없음)";
        } else if (raw != null) {
            value = raw.isBlank() ? "(비어 있음)" : raw;
        } else {
            value = spec.defaultValue() == null ? "(없음)" : spec.defaultValue() + " (기본값)";
        }
        return new Entry(spec.group(), spec.key(), value, spec.note());
    }

    private static Spec plain(String group, String key, String defaultValue, String note) {
        return new Spec(group, key, false, defaultValue, note);
    }

    private static Spec secret(String group, String key) {
        return new Spec(group, key, true, null, null);
    }
}
