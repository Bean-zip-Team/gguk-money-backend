package com.ggukmoney.beanzip.domain.mission.service;

import com.ggukmoney.beanzip.domain.notification.config.NotificationTemplateProperties;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 데일리 미션 알림 동의 여부를 알림 설정에서 읽는다 (BEA-299).
 *
 * <p>동의 여부만 보고 <b>토글의 on/off 는 보지 않는다</b>. 미션의 목적이 동의를 받는 것이므로,
 * 한 번 동의한 뒤 알림을 꺼도 수행한 사실은 그대로 남아야 한다.
 *
 * <p>캠페인 코드가 설정되지 않았으면 판정하지 않는다. 알림 설정 목록이 캠페인 코드가 있는 타입만
 * 내려 주기 때문에, 코드가 없으면 유저에게 동의 토글 자체가 보이지 않는다. 그 상태에서 미션만
 * 남겨 두면 달성할 수단이 없는 미션이 된다.
 */
@Component
@RequiredArgsConstructor
public class NotificationPreferenceOptInSignal implements NotificationOptInSignal {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationTemplateProperties templateProperties;

    @Override
    @Transactional(readOnly = true)
    public Optional<Boolean> agreed(UUID userId) {
        if (!templateProperties.isConfigured(NotificationType.DAILY_MISSION)) {
            return Optional.empty();
        }

        return Optional.of(preferenceRepository.findByUserIdAndType(userId, NotificationType.DAILY_MISSION)
                .map(preference -> preference.getAgreementStatus() == NotificationAgreementStatus.AGREED)
                .orElse(false));
    }
}
