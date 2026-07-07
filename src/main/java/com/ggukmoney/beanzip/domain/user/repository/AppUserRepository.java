package com.ggukmoney.beanzip.domain.user.repository;

import com.ggukmoney.beanzip.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
