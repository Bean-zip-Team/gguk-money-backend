package com.ggukmoney.beanzip.domain.user.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    @Mapping(target = "userId", source = "id")
    MemberUpdateResponse mapToUpdateResponse(AppUser user);

    default MemberMeResponse mapToMeResponse(
            AppUser user,
            EquippedKeycapResponse equippedKeycap,
            long pointBalance
    ) {
        return new MemberMeResponse(
                user.getId(),
                user.getStatus().name(),
                user.getNickname(),
                user.getProfileImageUrl(),
                equippedKeycap,
                pointBalance
        );
    }
}
