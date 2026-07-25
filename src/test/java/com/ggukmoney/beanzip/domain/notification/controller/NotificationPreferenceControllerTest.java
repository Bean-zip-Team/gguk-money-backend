package com.ggukmoney.beanzip.domain.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceAgreementRequest;
import com.ggukmoney.beanzip.domain.notification.dto.request.UpdateNotificationPreferenceEnabledRequest;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceListResponse;
import com.ggukmoney.beanzip.domain.notification.dto.response.NotificationPreferenceResponse;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationAgreementStatus;
import com.ggukmoney.beanzip.domain.notification.entity.NotificationType;
import com.ggukmoney.beanzip.domain.notification.service.NotificationPreferenceService;
import com.ggukmoney.beanzip.global.common.GlobalExceptionHandler;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    void v1NotificationsPathIsNotRegistered() {
        RequestMapping mapping = NotificationPreferenceController.class.getAnnotation(RequestMapping.class);

        org.assertj.core.api.Assertions.assertThat(mapping.value())
                .containsExactly("/api/notifications")
                .doesNotContain("/api/v1/notifications");
    }

    @Test
    void notificationEndpointsExposeSwaggerTag() {
        Tag tag = NotificationPreferenceController.class.getAnnotation(Tag.class);

        org.assertj.core.api.Assertions.assertThat(tag).isNotNull();
        org.assertj.core.api.Assertions.assertThat(tag.name()).isEqualTo("Notifications");
    }

    @Test
    void getPreferencesReturnsConfiguredCampaignItemsWithoutGlobalFields() throws Exception {
        UUID userId = UUID.randomUUID();
        when(notificationPreferenceService.list(userId)).thenReturn(new NotificationPreferenceListResponse(List.of(
                response(NotificationType.RANK_CHANGE, true, NotificationAgreementStatus.AGREED, "clickmoney-asfasf", true)
        )));

        mockMvc.perform(get("/api/notifications/preferences").requestAttr(AuthRequestAttributes.USER_ID, userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].type").value("RANK_CHANGE"))
                .andExpect(jsonPath("$.data.items[0].templateCode").value("clickmoney-asfasf"))
                .andExpect(jsonPath("$.data.agreementTemplateCode").doesNotExist())
                .andExpect(jsonPath("$.data.enabled").doesNotExist());

        verify(notificationPreferenceService).list(userId);
    }

    @Test
    void patchPreferenceStoresOnlyTheRequestedTypeAgreement() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateNotificationPreferenceAgreementRequest request =
                new UpdateNotificationPreferenceAgreementRequest(NotificationType.RANK_CHANGE, "newAgreement");
        when(notificationPreferenceService.agree(userId, request)).thenReturn(
                response(NotificationType.RANK_CHANGE, true, NotificationAgreementStatus.AGREED, "clickmoney-asfasf", true)
        );

        mockMvc.perform(patch("/api/notifications/preferences")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("RANK_CHANGE"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        verify(notificationPreferenceService).agree(userId, request);
    }

    @Test
    void patchPreferenceEnabledUpdatesOnlyTheRequestedType() throws Exception {
        UUID userId = UUID.randomUUID();
        UpdateNotificationPreferenceEnabledRequest request =
                new UpdateNotificationPreferenceEnabledRequest(NotificationType.BOOSTER_RECHARGED, false);
        when(notificationPreferenceService.updateEnabled(userId, request.type(), request.enabled())).thenReturn(
                response(NotificationType.BOOSTER_RECHARGED, false, NotificationAgreementStatus.AGREED, "clickmoney-box", false)
        );

        mockMvc.perform(patch("/api/notifications/preferences/enabled")
                        .requestAttr(AuthRequestAttributes.USER_ID, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("BOOSTER_RECHARGED"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        verify(notificationPreferenceService).updateEnabled(userId, request.type(), request.enabled());
    }

    @Test
    void legacyGlobalAgreementPathIsNotRegistered() {
        assertThat(Arrays.stream(NotificationPreferenceController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PatchMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .doesNotContain("/agreement");
    }

    private NotificationPreferenceResponse response(
            NotificationType type,
            boolean enabled,
            NotificationAgreementStatus agreementStatus,
            String templateCode,
            boolean promptEligible
    ) {
        return new NotificationPreferenceResponse(type, enabled, agreementStatus, templateCode, promptEligible);
    }
}
