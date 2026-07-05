package com.ggukmoney.beanzip.domain.ranking.repository;

import com.ggukmoney.beanzip.domain.ranking.entity.RankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RankingSnapshotRepository extends JpaRepository<RankingSnapshot, Long> {

    Optional<RankingSnapshot> findByPublicId(UUID publicId);
}
