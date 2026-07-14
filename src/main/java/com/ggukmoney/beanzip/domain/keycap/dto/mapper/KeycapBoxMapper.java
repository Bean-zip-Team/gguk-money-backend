package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxOpenResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxHistoryItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapBoxStatusResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxAccount;
import com.ggukmoney.beanzip.domain.keycap.entity.KeycapBoxOpen;
import com.ggukmoney.beanzip.domain.tap.dto.BoxProgressSnapshot;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KeycapBoxMapper {

    @Mapping(target = "boxBalance", source = "account.boxBalance")
    @Mapping(target = "freeOpenTicketCount", source = "account.freeOpenTicketCount")
    @Mapping(target = "boxProgressTapCount", source = "progress.cumulativeValidTapCount")
    @Mapping(target = "nextBoxRequiredTapCount", source = "progress.nextBoxTarget")
    KeycapBoxStatusResponse mapToStatusResponse(KeycapBoxAccount account, BoxProgressSnapshot progress);

    @Mapping(target = "boxOpenId", source = "publicId")
    @Mapping(target = "keycapId", source = "keycap.publicId")
    KeycapBoxOpenResponse mapToOpenResponse(KeycapBoxOpen boxOpen);

    @Mapping(target = "boxOpenId", source = "publicId")
    @Mapping(target = "openMethod", expression = "java(boxOpen.getOpenMethod().name())")
    @Mapping(target = "keycapId", source = "keycap.publicId")
    KeycapBoxHistoryItemResponse mapToHistoryItemResponse(KeycapBoxOpen boxOpen);
}
