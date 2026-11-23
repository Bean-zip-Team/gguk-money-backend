package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.global.config.FriendPolicyConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** 친구·초대·응원 API 가 공통으로 쓰는 입구 확인 (BEA-390). */
@Component
@RequiredArgsConstructor
public class FriendAccess {

    private final FriendPolicyConfig policy;

    /** 친구 기능이 꺼져 있으면 409 FRIENDS_DISABLED. 컨트롤러가 인증 다음에 가장 먼저 부른다. */
    public void requireEnabled() {
        if (!policy.friendsEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "FRIENDS_DISABLED");
        }
    }

    /** 정지된 사용자는 응원을 보낼 수 없다. 403 ACCOUNT_SUSPENDED (출금과 같은 코드). */
    public void requireNotSuspended(AppUser user) {
        if (user.getStatus() == AppUser.Status.SUSPENDED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED");
        }
    }

    /**
     * 친구 초대 미션을 볼 수 있고 그 보상을 받을 수 있는지.
     *
     * <p>친구 기능이 꺼져 있거나 정지된 사용자는 미션이 보이지 않는다. 보이지 않는 동안에는 전에 달성해
     * 둔 보상도 받을 수 없다. 받을 수 있게 두면 화면에 없는 보상이 일괄 수령으로 들어온다.
     */
    public boolean canEarnFriendMission(AppUser user) {
        return policy.friendsEnabled() && user.getStatus() != AppUser.Status.SUSPENDED;
    }
}
