package com.ggukmoney.beanzip.domain.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.NotificationPreferenceService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationPreferenceControllerTest {

    private final NotificationPreferenceService notificationPreferenceService = mock(NotificationPreferenceService.class);
    private final NotificationPreferenceController controller =
            new NotificationPreferenceController(notificationPreferenceService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preferencesRequireAccessJwtUser() throws Exception {
        mockMvc.perform(get("/api/notifications/preferences"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_REQUIRED"));
    }

    @Test
    void v1NotificationsPathIsNotRegistered() throws Exception {
        RequestMapping mapping = NotificationPreferenceController.class.getAnnotation(RequestMapping.class);

        org.assertj.core.api.Assertions.assertThat(mapping.value())
                .containsExactly("/api/notifications/preferences")
                .doesNotContain("/api/v1/notifications/preferences");
    }

    @Test
    void getPreferencesReturnsEnvelopeItems() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationPreferenceService.list(userId)).thenReturn(new NotificationPreferenceListResponse(List.of(
                new NotificationPreferenceResponse(
                        NotificationType.RANK_CHANGE,
                        true,
                        NotificationAgreementStatus.AGREED,
                        "TPL_RANK",
                        true
                )
        )));

        mockMvc.perform(get("/api/notifications/preferences").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].type").value("RANK_CHANGE"))
                .andExpect(jsonPath("$.data.items[0].templateCode").value("TPL_RANK"))
                .andExpect(jsonPath("$.data.items[0].promptEligible").value(true));

        verify(notificationPreferenceService).list(userId);
    }

    @Test
    void patchPreferenceStoresAgreementResult() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateNotificationPreferenceRequest request =
                new UpdateNotificationPreferenceRequest(NotificationType.RANK_CHANGE, true, "newAgreement");
        when(notificationPreferenceService.update(userId, request)).thenReturn(new NotificationPreferenceResponse(
                NotificationType.RANK_CHANGE,
                true,
                NotificationAgreementStatus.AGREED,
                "TPL_RANK",
                true
        ));

        mockMvc.perform(patch("/api/notifications/preferences")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("RANK_CHANGE"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.agreementStatus").value("AGREED"));

        verify(notificationPreferenceService).update(userId, request);
    }
}
