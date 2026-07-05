package com.ggukmoney.beanzip.domain.record.repository;

import com.ggukmoney.beanzip.domain.record.entity.UserRecordReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRecordRewardRepository extends JpaRepository<UserRecordReward, Long> {

    Optional<UserRecordReward> findByPublicId(UUID publicId);
}
