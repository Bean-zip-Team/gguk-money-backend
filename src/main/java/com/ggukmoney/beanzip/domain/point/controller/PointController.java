package com.ggukmoney.beanzip.domain.point.controller;

import com.ggukmoney.beanzip.domain.point.dto.response.PointLedgerPageResponse;
import com.ggukmoney.beanzip.domain.point.dto.response.PointMeResponse;
import com.ggukmoney.beanzip.domain.point.entity.PointLedger;
import com.ggukmoney.beanzip.domain.point.service.PointStatusService;
import com.ggukmoney.beanzip.global.common.ApiPaths;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping(ApiPaths.POINTS)
public class PointController {

    private final PointStatusService pointStatusService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PointMeResponse>> me(HttpServletRequest httpServletRequest) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(pointStatusService.getMyPoints(userId)));
    }

    @GetMapping("/ledger")
    public ResponseEntity<ApiResponse<PointLedgerPageResponse>> ledger(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) PointLedger.EntryType entryType,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            HttpServletRequest httpServletRequest
    ) {
        var userId = AuthRequestAttributes.getRequiredUserId(httpServletRequest);
        return ResponseEntity.ok(ApiResponse.success(
                pointStatusService.getLedger(userId, cursor, size, entryType, reason, from, to)
        ));
    }
}
