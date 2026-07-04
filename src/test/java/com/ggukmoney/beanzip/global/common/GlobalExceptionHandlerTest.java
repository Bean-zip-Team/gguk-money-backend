package com.ggukmoney.beanzip.global.common;

import com.ggukmoney.beanzip.global.logging.RequestLogContext;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest extends FullStackIntegrationTestSupport {

    @Test
    void validationFailureUsesTraceIdAndDoesNotExposeDataOrDetails() throws Exception {
        String traceId = "01JTESTTRACE";

        MvcResult result = mockMvc.perform(post(ApiPaths.AUTH + "/refresh")
                        .header(RequestLogContext.TRACE_ID_HEADER, traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestLogContext.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.message").value("리프레시 토큰이 필요합니다."))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.error.details").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("Authorization")
                .doesNotContain("Bearer ");
    }
}
