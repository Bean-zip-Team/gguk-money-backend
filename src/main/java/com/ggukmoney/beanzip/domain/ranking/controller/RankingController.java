package com.ggukmoney.beanzip.domain.ranking.controller;

import com.ggukmoney.beanzip.domain.ranking.dto.response.CurrentRankingResponse;
import com.ggukmoney.beanzip.domain.ranking.service.RankingQueryService;
import com.ggukmoney.beanzip.global.common.ApiResponse;
import com.ggukmoney.beanzip.global.interceptor.AuthRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingQueryService rankingQueryService;

    @GetMapping("/current")
    public ApiResponse<CurrentRankingResponse> getCurrentRanking(
            HttpServletRequest request,
            @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(rankingQueryService.getCurrentRanking(
                AuthRequestAttributes.getRequiredUserId(request),
                limit
        ));
    }
}
