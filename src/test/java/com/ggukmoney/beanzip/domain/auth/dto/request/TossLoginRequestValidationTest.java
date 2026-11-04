package com.ggukmoney.beanzip.domain.auth.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TossLoginRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsAnOverlongSignupReferrerButAllowsItToBeOmitted() {
        TossLoginRequest request = new TossLoginRequest("code", "DEFAULT", null, "x".repeat(101));

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath()).hasToString("signupReferrer");
                    assertThat(violation.getMessage()).isEqualTo("signupReferrer는 100자 이하여야 합니다.");
                });
        assertThat(validator.validate(new TossLoginRequest("code", "DEFAULT"))).isEmpty();
        assertThat(validator.validate(new TossLoginRequest("code", "DEFAULT", null, "search"))).isEmpty();
    }
}
