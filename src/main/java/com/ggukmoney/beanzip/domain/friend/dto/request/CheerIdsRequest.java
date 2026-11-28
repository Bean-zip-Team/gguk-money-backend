package com.ggukmoney.beanzip.domain.friend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "받은 응원 ID 목록을 보내는 요청이에요. 확인(ack)과 답 응원이 같이 써요.")
public record CheerIdsRequest(
        @Schema(description = "받은 응원 ID 목록이에요. 한 번에 100개까지 보낼 수 있어요. UUID 형식이 아닌 값이 하나라도 있으면 요청 전체가 400이에요.")
        @NotNull @Size(max = 100) List<@NotNull UUID> cheerIds
) {
}
