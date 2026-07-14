package com.ggukmoney.beanzip.domain.keycap.controller;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.KEYCAPS)
public class KeycapController {

    private final KeycapService keycapService;

    @GetMapping
    public ResponseEntity<ApiResponse<KeycapListResponse>> getKeycaps() {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getKeycaps()));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyKeycapListResponse>> getMyKeycaps(HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.getMyKeycaps(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest)
        )));
    }

    @PutMapping("/{keycapId}/equip")
    public ResponseEntity<ApiResponse<KeycapEquipResponse>> equipKeycap(
            HttpServletRequest httpServletRequest,
            @PathVariable UUID keycapId
    ) {
        return ResponseEntity.ok(ApiResponse.success(keycapService.equipKeycap(
                AuthRequestAttributes.getRequiredUserId(httpServletRequest),
                keycapId
        )));
    }
}
