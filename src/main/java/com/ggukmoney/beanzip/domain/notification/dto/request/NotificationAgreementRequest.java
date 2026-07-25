package com.ggukmoney.beanzip.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record NotificationAgreementRequest(@NotBlank String agreementResult) {
}
