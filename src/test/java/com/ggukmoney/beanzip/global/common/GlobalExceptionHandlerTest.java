package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends FullStackIntegrationTestSupport {

    @Test
    void validationFailureUsesRequestIdHeaderAndDoesNotExposeBodyRequestIdOrDetails() throws Exception {
        String requestId = "01JTESTREQUEST";

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .header(RequestLogContext.REQUEST_ID_HEADER, requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestLogContext.REQUEST_ID_HEADER, requestId))
                .andExpect(header().doesNotExist("X-" + "Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value("리프레시 토큰이 필요합니다."))
                .andExpect(jsonPath("$." + "trace" + "Id").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ");
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal-error");

        ResponseEntity<ApiErrorResponse> response = handler.handleException(
                new IllegalStateException("database password leaked"),
                request
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("COMMON_INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().error().message()).doesNotContain("database password leaked");
    }
}
