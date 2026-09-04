package com.ggukmoney.beanzip.domain.tap.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TapBatchSubmitRequestValidationTest {

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = validatorFactory.getValidator();

    @Test
    void acceptsBatchSizeWithinClientFlushBound() {
        // 앱은 100탭 또는 30초마다 flush하므로 정상 배치는 상한에 한참 못 미친다.
        TapBatchSubmitRequest request = new TapBatchSubmitRequest(UUID.randomUUID(), 0L, 100);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsBatchSizeAboveUpperBound() {
        // 상한이 없으면 상자 지급 루프 반복 횟수가 클라이언트 입력에 그대로 노출된다.
        TapBatchSubmitRequest request = new TapBatchSubmitRequest(UUID.randomUUID(), 0L, 1001);

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath()).hasToString("submittedCount");
                    assertThat(violation.getMessage()).isEqualTo("submittedCount는 1000 이하여야 합니다.");
                });
    }

    @Test
    void rejectsNonPositiveBatchSize() {
        TapBatchSubmitRequest request = new TapBatchSubmitRequest(UUID.randomUUID(), 0L, 0);

        assertThat(validator.validate(request))
                .singleElement()
                .satisfies(violation ->
                        assertThat(violation.getMessage()).isEqualTo("submittedCount는 1 이상이어야 합니다."));
    }
}
