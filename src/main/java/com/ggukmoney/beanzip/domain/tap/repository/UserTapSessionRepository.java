package com.ggukmoney.beanzip.domain.tap.repository;

import com.ggukmoney.beanzip.domain.tap.entity.UserTapSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserTapSessionRepository extends JpaRepository<UserTapSession, Long> {

    Optional<UserTapSession> findByUserId(UUID userId);
}
