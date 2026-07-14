package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxStatusService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KEYCAP_BOXES)
public class KeycapBoxController {

    private final KeycapBoxStatusService keycapBoxStatusService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KeycapBoxStatusResponse>> getStatus(HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxStatusService.getStatus(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }
}
