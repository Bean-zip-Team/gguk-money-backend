package com.ggukmoney.beanzip.domain.auth.dto.response;

public record LogoutAllResponse(
        boolean loggedOutAll,
        long revokedSessionCount
) {
}
