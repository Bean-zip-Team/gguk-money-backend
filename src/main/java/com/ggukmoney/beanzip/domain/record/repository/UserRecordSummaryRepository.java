package com.ggukmoney.beanzip.domain.record.repository;

import com.ggukmoney.beanzip.domain.record.entity.UserRecordSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRecordSummaryRepository extends JpaRepository<UserRecordSummary, Long> {

    Optional<UserRecordSummary> findByPublicId(UUID publicId);
}
