package com.ggukmoney.beanzip.domain.tap.controller;

import com.ggukmoney.beanzip.domain.tap.dto.request.TapBatchSubmitRequest;
import com.ggukmoney.beanzip.domain.tap.dto.response.TapBatchSubmitResponse;
import com.ggukmoney.beanzip.domain.tap.dto.mapper.TapMapper;
import com.ggukmoney.beanzip.domain.tap.service.TapComplexService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.TAP)
public class TapController {

    private final TapComplexService tapComplexService;
    private final TapMapper tapMapper;

    @PostMapping("/batches")
    public ResponseEntity<ApiResponse<TapBatchSubmitResponse>> submitBatch(
            @Valid @RequestBody TapBatchSubmitRequest request,
            HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        var outcome = tapComplexService.submitBatch(tapMapper.toCommand(userId, request));
        return ResponseEntity.ok(ApiResponse.success(tapMapper.toResponse(outcome)));
    }
}
