package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KeycapBoxMapper {

    @Mapping(target = "boxOpenId", source = "publicId")
    @Mapping(target = "openMethod", expression = "java(boxOpen.getOpenMethod().name())")
    @Mapping(target = "keycapId", source = "keycap.publicId")
    KeycapBoxHistoryItemResponse mapToHistoryItemResponse(KeycapBoxOpen boxOpen);
}
