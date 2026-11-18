package com.ggukmoney.beanzip.domain.friend;

import com.ggukmoney.beanzip.domain.friend.entity.Friendship;
import com.ggukmoney.beanzip.domain.friend.service.FriendshipService;
import com.ggukmoney.beanzip.domain.friend.service.InviteCodeService;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.support.FullStackIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** 초대 코드, 친구 관계, 초대 흐름을 실제 PostgreSQL 에서 확인한다 (BEA-390). */
class FriendInviteIntegrationTest extends FullStackIntegrationTestSupport {

    @Autowired
    private InviteCodeService inviteCodeService;
    @Autowired
    private FriendshipService friendshipService;
    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    void aUserKeepsOneInviteCodeEvenWhenAskedTwiceAtOnce() throws Exception {
        UUID userId = newUser();
        Callable<String> ask = () -> inviteCodeService.getOrCreate(userId);

        List<Future<String>> answers;
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            answers = executor.invokeAll(List.of(ask, ask, ask, ask));
        }

        String code = inviteCodeService.getOrCreate(userId);
        assertThat(code).matches("[2-9A-HJKMNP-Z]{8}");
        for (Future<String> answer : answers) {
            assertThat(answer.get()).isEqualTo(code);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invite_code WHERE user_id = ?", Long.class, userId)).isEqualTo(1);
        assertThat(inviteCodeService.findInviter(code.toLowerCase())).map(AppUser::getId).contains(userId);
        assertThat(inviteCodeService.getOrCreate(newUser())).isNotEqualTo(code);
    }

    @Test
    void aPairOfFriendsIsOneRowWhicheverSideConnects() {
        // Chosen so that Java's signed UUID order disagrees with PostgreSQL's byte order.
        UUID small = newUser(UUID.fromString("00000000-0000-4000-8000-" + randomHex12()));
        UUID large = newUser(UUID.fromString("ffffffff-ffff-4fff-bfff-" + randomHex12()));

        assertThat(friendshipService.areFriends(small, large)).isFalse();
        assertThat(friendshipService.connect(large, small, Friendship.Source.INVITE_SIGNUP)).isTrue();
        // The database's own CHECK (user_low < user_high) accepted the order chosen in Java.
        assertThat(friendshipService.connect(small, large, Friendship.Source.INVITE_LINK)).isFalse();

        assertThat(friendshipService.areFriends(small, large)).isTrue();
        assertThat(friendshipService.areFriends(large, small)).isTrue();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT user_low, user_high, source FROM friendship WHERE user_low = ? OR user_high = ?", small, small))
                .containsEntry("user_low", small)
                .containsEntry("user_high", large)
                .containsEntry("source", "INVITE_SIGNUP");
    }

    private UUID newUser() {
        return appUserRepository.save(AppUser.createActive("friend-" + UUID.randomUUID(), null)).getId();
    }

    /** 원하는 ID 로 사용자를 만든다. 엔티티는 ID 를 스스로 정하므로 저장한 뒤 바꾼다. */
    private UUID newUser(UUID id) {
        UUID generated = newUser();
        jdbcTemplate.update("UPDATE app_user SET id = ? WHERE id = ?", id, generated);
        return id;
    }

    private static String randomHex12() {
        return UUID.randomUUID().toString().substring(24);
    }
}
