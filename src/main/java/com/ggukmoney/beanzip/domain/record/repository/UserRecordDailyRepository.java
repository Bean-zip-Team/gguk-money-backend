package com.ggukmoney.beanzip.domain.record.repository;

import com.ggukmoney.beanzip.domain.record.entity.UserRecordDaily;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRecordDailyRepository extends JpaRepository<UserRecordDaily, Long> {

    Optional<UserRecordDaily> findByPublicId(UUID publicId);
}
