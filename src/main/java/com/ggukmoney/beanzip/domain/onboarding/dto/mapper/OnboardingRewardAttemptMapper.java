package com.ggukmoney.beanzip.domain.onboarding.dto.mapper;

import com.ggukmoney.beanzip.domain.onboarding.dto.response.OnboardingKeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.onboarding.entity.OnboardingRewardAttempt;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OnboardingRewardAttemptMapper {

    @Mapping(target = "onboardingAttemptId", source = "publicId")
    @Mapping(target = "keycapId", source = "bonusRewardKeycap.publicId")
    @Mapping(target = "code", source = "bonusRewardKeycap.code")
    @Mapping(target = "name", source = "bonusRewardKeycap.name")
    @Mapping(target = "grade", expression = "java(attempt.getBonusRewardKeycap().getGrade().name())")
    @Mapping(target = "imageUrl", source = "bonusRewardKeycap.imageUrl")
    @Mapping(target = "soundUrl", source = "bonusRewardKeycap.soundUrl")
    @Mapping(target = "completed", constant = "true")
    @Mapping(target = "rewardPoint", source = "rewardPointAmount")
    OnboardingKeycapBoxOpenResponse mapToResponse(OnboardingRewardAttempt attempt);
}
