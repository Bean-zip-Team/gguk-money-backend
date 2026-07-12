package com.ggukmoney.beanzip.domain.user.controller;

import com.ggukmoney.beanzip.domain.auth.service.AuthService;
import com.ggukmoney.beanzip.domain.user.dto.request.UpdateMemberRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.service.UserService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final UserService userService = mock(UserService.class);
    private final MemberController memberController = new MemberController(authService, userService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(memberController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void getMePassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        MemberMeResponse response = new MemberMeResponse(userId, "ACTIVE", "Bean", null, null, 0L);
        when(userService.getCurrentMember(userId)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = memberController.getMe(request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(userService).getCurrentMember(userId);
    }

    @Test
    void patchMePassesAuthenticatedUserIdToService() {
        UUID userId = UUID.randomUUID();
        UpdateMemberRequest requestBody = new UpdateMemberRequest("Bean", null);
        MemberUpdateResponse response = new MemberUpdateResponse(userId, "Bean", null);
        when(userService.updateCurrentMember(userId, requestBody)).thenReturn(response);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthRequestAttributes.USER_ID, userId);

        var result = memberController.updateMe(requestBody, request);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().data()).isEqualTo(response);
        verify(userService).updateCurrentMember(userId, requestBody);
    }

    @Test
    void getMeReturnsSuccessDataShape() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentMember(userId))
                .thenReturn(new MemberMeResponse(userId, "ACTIVE", "Bean", null, null, 0L));

        mockMvc.perform(get("/api/v1/members/me")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.pointBalance").value(0))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void patchMeReturnsSuccessDataShape() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateCurrentMember(userId, new UpdateMemberRequest("Bean", null)))
                .thenReturn(new MemberUpdateResponse(userId, "Bean", null));

        mockMvc.perform(patch("/api/v1/members/me")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"Bean\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.nickname").value("Bean"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void patchMeRejectsEmptyBodyWithValidationError() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .requestAttr(AuthRequestAttributes.USER_ID, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
