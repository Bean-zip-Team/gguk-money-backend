package com.ggukmoney.beanzip.domain.user.dto.mapper;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberMapperTest {

    private final MemberMapper memberMapper = Mappers.getMapper(MemberMapper.class);

    @Test
    void mapsUserToUpdateResponse() {
        UUID userId = UUID.randomUUID();
        AppUser user = user(userId, " Bean ", "https://img");

        MemberUpdateResponse response = memberMapper.mapToUpdateResponse(user);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.nickname()).isEqualTo("Bean");
        assertThat(response.profileImageUrl()).isEqualTo("https://img");
    }

    @Test
    void combinesUserEquippedKeycapAndPointBalance() {
        UUID userId = UUID.randomUUID();
        EquippedKeycapResponse equippedKeycap = new EquippedKeycapResponse(
                UUID.randomUUID(),
                "BASIC_001",
                "기본 키캡",
                null
        );

        MemberMeResponse response = memberMapper.mapToMeResponse(
                user(userId, "Bean", null),
                equippedKeycap,
                42L
        );

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.nickname()).isEqualTo("Bean");
        assertThat(response.profileImageUrl()).isNull();
        assertThat(response.equippedKeycap()).isEqualTo(equippedKeycap);
        assertThat(response.pointBalance()).isEqualTo(42L);
    }

    @Test
    void mapsNullEquippedKeycap() {
        MemberMeResponse response = memberMapper.mapToMeResponse(
                user(UUID.randomUUID(), "Bean", "https://img"),
                null,
                0L
        );

        assertThat(response.equippedKeycap()).isNull();
        assertThat(response.pointBalance()).isZero();
    }

    private static AppUser user(UUID userId, String nickname, String profileImageUrl) {
        AppUser user = AppUser.createActive(nickname, profileImageUrl);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
