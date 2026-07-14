package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.request.KeycapBoxOpenRequest;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxHistoryService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxOpenService;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapBoxStatusService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KEYCAP_BOXES)
public class KeycapBoxController {

    private final KeycapBoxStatusService keycapBoxStatusService;
    private final KeycapBoxOpenService keycapBoxOpenService;
    private final KeycapBoxHistoryService keycapBoxHistoryService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KeycapBoxStatusResponse>> getStatus(HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxStatusService.getStatus(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<KeycapBoxOpenResponse>> open(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody KeycapBoxOpenRequest request,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxOpenService.open(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                idempotencyKey,
                request
        )));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<KeycapBoxHistoryResponse>> getHistory(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            HttpServletRequest httpServletRequest
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapBoxHistoryService.getHistory(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                cursor,
                size
        )));
    }
}
