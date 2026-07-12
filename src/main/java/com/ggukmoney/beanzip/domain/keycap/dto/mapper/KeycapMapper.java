package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KeycapMapper {

    @Mapping(target = "keycapId", source = "keycap.publicId")
    @Mapping(target = "code", source = "keycap.code")
    @Mapping(target = "name", source = "keycap.name")
    @Mapping(target = "imageUrl", expression = "java(null)")
    EquippedKeycapResponse mapToEquippedKeycapResponse(UserKeycap userKeycap);
}
