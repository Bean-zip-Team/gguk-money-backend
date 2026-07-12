package com.ggukmoney.beanzip.domain.user.service;

import com.ggukmoney.beanzip.domain.keycap.dto.response.EquippedKeycapResponse;
import com.ggukmoney.beanzip.domain.keycap.service.KeycapService;
import com.ggukmoney.beanzip.domain.point.service.PointAccountService;
import com.ggukmoney.beanzip.domain.user.dto.mapper.MemberMapper;
import com.ggukmoney.beanzip.domain.user.dto.request.UpdateMemberRequest;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberMeResponse;
import com.ggukmoney.beanzip.domain.user.dto.response.MemberUpdateResponse;
import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import com.ggukmoney.beanzip.domain.user.repository.AppUserRepository;
import com.ggukmoney.beanzip.global.common.ValidationFailureException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PointAccountService pointAccountService;
    private final KeycapService keycapService;
    private final MemberMapper memberMapper;

    @Transactional
    public AppUser createActive(String nickname, String profileImageUrl) {
        return appUserRepository.save(AppUser.createActive(nickname, profileImageUrl));
    }

    public AppUser getById(UUID userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_USER_NOT_FOUND"));
    }

    public MemberMeResponse getCurrentMember(UUID userId) {
        AppUser user = getActiveMember(userId);
        EquippedKeycapResponse equippedKeycap = keycapService.getEquippedKeycap(userId);
        long pointBalance = pointAccountService.getBalance(userId);
        return memberMapper.mapToMeResponse(user, equippedKeycap, pointBalance);
    }

    @Transactional
    public MemberUpdateResponse updateCurrentMember(UUID userId, UpdateMemberRequest request) {
        AppUser user = getActiveMember(userId);
        validateUpdateRequest(request);
        validateNicknameUnique(userId, request.nickname());
        user.updateProfile(request.nickname(), request.profileImageUrl());
        return memberMapper.mapToUpdateResponse(appUserRepository.save(user));
    }

    @Transactional
    public AppUser recordLogin(AppUser user, String nickname, String profileImageUrl) {
        user.recordLogin(nickname, profileImageUrl);
        return appUserRepository.save(user);
    }

    @Transactional
    public AppUser withdraw(AppUser user) {
        user.withdraw();
        return appUserRepository.save(user);
    }

    private AppUser getActiveMember(UUID userId) {
        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND"));
        if (user.isWithdrawn()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_WITHDRAWN");
        }
        return user;
    }

    private void validateUpdateRequest(UpdateMemberRequest request) {
        if (request.nickname() == null && request.profileImageUrl() == null) {
            throw new ValidationFailureException("수정할 프로필 정보가 필요합니다.");
        }
        if (request.nickname() != null && !StringUtils.hasText(request.nickname())) {
            throw new ValidationFailureException("닉네임은 공백일 수 없습니다.");
        }
    }

    private void validateNicknameUnique(UUID userId, String nickname) {
        String normalized = AppUser.normalizeNicknameForLookup(nickname);
        if (normalized == null) {
            return;
        }
        if (appUserRepository.existsByNicknameNormalizedAndStatusAndIdNot(normalized, AppUser.Status.ACTIVE, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "NICKNAME_ALREADY_EXISTS");
        }
    }
}
