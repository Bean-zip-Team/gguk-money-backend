package com.ggukmoney.beanzip.domain.keycap.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapEquipResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.KeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapItemResponse;
import com.ggukmoney.beanzip.domain.keycap.dto.response.MyKeycapListResponse;
import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface KeycapMapper {

    @Mapping(target = "keycapId", source = "publicId")
    @Mapping(target = "grade", expression = "java(keycap.getGrade().name())")
    KeycapItemResponse mapToKeycapItemResponse(Keycap keycap);

    default KeycapListResponse mapToKeycapListResponse(List<Keycap> keycaps) {
        return new KeycapListResponse(keycaps.stream()
                .map(this::mapToKeycapItemResponse)
                .toList());
    }

    @Mapping(target = "keycapId", source = "keycap.publicId")
    @Mapping(target = "code", source = "keycap.code")
    @Mapping(target = "name", source = "keycap.name")
    @Mapping(target = "status", expression = "java(userKeycap.getStatus().name())")
    MyKeycapItemResponse mapToMyKeycapItemResponse(UserKeycap userKeycap);

    default MyKeycapListResponse mapToMyKeycapListResponse(List<UserKeycap> userKeycaps) {
        return new MyKeycapListResponse(userKeycaps.stream()
                .map(this::mapToMyKeycapItemResponse)
                .toList());
    }

    @Mapping(target = "keycapId", source = "keycap.publicId")
    @Mapping(target = "code", source = "keycap.code")
    @Mapping(target = "name", source = "keycap.name")
    @Mapping(target = "imageUrl", source = "keycap.imageUrl")
    EquippedKeycapResponse mapToEquippedKeycapResponse(UserKeycap userKeycap);

    @Mapping(target = "keycapId", source = "keycap.publicId")
    KeycapEquipResponse mapToKeycapEquipResponse(UserKeycap userKeycap);
}
