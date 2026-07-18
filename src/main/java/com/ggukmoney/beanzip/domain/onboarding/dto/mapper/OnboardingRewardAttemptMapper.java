package com.ggukmoney.beanzip.domain.onboarding.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OnboardingRewardAttemptMapper {

    @Mapping(target = "onboardingAttemptId", source = "publicId")
    @Mapping(target = "keycaps", expression = "java(toKeycapSummaries(attempt))")
    @Mapping(target = "completed", constant = "true")
    @Mapping(target = "rewardPoint", source = "rewardPointAmount")
    OnboardingKeycapBoxOpenResponse mapToResponse(OnboardingRewardAttempt attempt);

    default List<OnboardingKeycapBoxOpenResponse.KeycapSummary> toKeycapSummaries(OnboardingRewardAttempt attempt) {
        return List.of(toSummary(attempt.getRewardKeycap()), toSummary(attempt.getBonusRewardKeycap()));
    }

    default OnboardingKeycapBoxOpenResponse.KeycapSummary toSummary(Keycap keycap) {
        return new OnboardingKeycapBoxOpenResponse.KeycapSummary(
                keycap.getPublicId(),
                keycap.getCode(),
                keycap.getName(),
                keycap.getGrade().name(),
                keycap.getImageUrl(),
                keycap.getSoundUrl()
        );
    }
}
