package com.ggukmoney.beanzip.domain.friend.service;

import com.ggukmoney.beanzip.domain.friend.entity.InviteCode;
import com.ggukmoney.beanzip.domain.friend.repository.InviteCodeRepository;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** 초대 코드를 발급하고, 코드로 초대한 사람을 찾는다 (BEA-390). */
@Service
@RequiredArgsConstructor
public class InviteCodeService {

    /** 영문 대문자와 숫자. 서로 헷갈리는 0·O, 1·I·L 은 뺐다. */
    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int MAX_TRIES = 5;

    private final InviteCodeRepository codes;
    private final AppUserRepository users;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 내 초대 코드. 없으면 발급한다. */
    @Transactional
    public String getOrCreate(UUID userId) {
        for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
            Optional<InviteCode> existing = codes.findById(userId);
            if (existing.isPresent()) {
                return existing.get().getCode();
            }
            // 0 이 돌아오면 그사이 이 사용자의 코드가 생겼거나 코드가 겹친 것이다. 다시 읽고, 없으면 새 코드로 넣는다.
            codes.insertIfAbsent(userId, newCode(), clock.instant());
        }
        return codes.findById(userId)
                .map(InviteCode::getCode)
                .orElseThrow(() -> new IllegalStateException("invite code could not be issued; userId=" + userId));
    }

    /**
     * 코드의 주인. 없는 코드이거나 주인이 탈퇴했으면 비어 있다.
     *
     * <p>정지된 사용자의 코드는 유효하다. 가입한 사람은 보상을 받고, 정지된 초대자만 받지 못한다.
     */
    @Transactional(readOnly = true)
    public Optional<AppUser> findInviter(String rawCode) {
        if (rawCode == null) {
            return Optional.empty();
        }
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() != InviteCode.LENGTH) {
            return Optional.empty();
        }
        return codes.findByCode(code)
                .flatMap(inviteCode -> users.findById(inviteCode.getUserId()))
                .filter(user -> user.getStatus() != AppUser.Status.WITHDRAWN);
    }

    private String newCode() {
        StringBuilder code = new StringBuilder(InviteCode.LENGTH);
        for (int i = 0; i < InviteCode.LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
