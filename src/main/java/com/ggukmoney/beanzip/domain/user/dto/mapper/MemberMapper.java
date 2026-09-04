package com.ggukmoney.beanzip.domain.user.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.util.NameMasker;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MemberMapper {

    default MemberUpdateResponse mapToUpdateResponse(AppUser user) {
        return new MemberUpdateResponse(
                user.getId(),
                NameMasker.mask(user.getNickname()),
                user.getProfileImageUrl()
        );
    }

    default MemberMeResponse mapToMeResponse(
            AppUser user,
            EquippedKeycapResponse equippedKeycap,
            long pointBalance
    ) {
        return new MemberMeResponse(
                user.getId(),
                user.getStatus().name(),
                NameMasker.mask(user.getNickname()),
                user.getProfileImageUrl(),
                equippedKeycap,
                pointBalance
        );
    }
}
