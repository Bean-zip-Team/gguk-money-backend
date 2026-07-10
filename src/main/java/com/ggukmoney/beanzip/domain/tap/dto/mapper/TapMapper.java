package com.ggukmoney.beanzip.domain.tap.dto.mapper;

import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.service.TapSubmitCommand;
import com.ggukmoney.beanzip.domain.tap.service.TapSubmitOutcome;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface TapMapper {

    default TapSubmitCommand toCommand(UUID userId, TapBatchSubmitRequest request) {
        return new TapSubmitCommand(userId, request.tapSessionId(), request.sequence(), request.submittedCount());
    }

    TapBatchSubmitResponse toResponse(TapSubmitOutcome outcome);
}
