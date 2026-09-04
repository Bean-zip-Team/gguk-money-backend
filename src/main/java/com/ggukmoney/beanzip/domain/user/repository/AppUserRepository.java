package com.ggukmoney.beanzip.domain.user.repository;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    boolean existsByNicknameNormalizedAndStatusAndIdNot(
            String nicknameNormalized,
            AppUser.Status status,
            UUID id
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from AppUser user where user.id = :userId")
    Optional<AppUser> findByIdForUpdate(@Param("userId") UUID userId);
}
