package com.ggukmoney.beanzip.domain.auth.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionLogRepository extends JpaRepository<AuthSessionLog, Long> {
}
