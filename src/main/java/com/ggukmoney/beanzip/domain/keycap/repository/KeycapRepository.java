package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KeycapRepository extends JpaRepository<Keycap, Long> {

    Optional<Keycap> findByPublicId(UUID publicId);

    Optional<Keycap> findByCode(String code);

    boolean existsByCode(String code);

    List<Keycap> findByActiveTrueOrderBySortOrderAscCodeAsc();

    /**
     * 온보딩 보너스 추첨 후보. 이벤트 키캡이 온보딩에서 나가지 않도록 획득 경로를 함께 받는다(BEA-285).
     */
    List<Keycap> findByGradeAndAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(
            Keycap.Grade grade,
            Keycap.AcquisitionType acquisitionType
    );

    long countByAcquisitionTypeAndActiveTrue(Keycap.AcquisitionType acquisitionType);

    /**
     * 상자 추첨 후보. 이벤트 키캡은 빠진다(BEA-285) — 추첨기가 등급 가중치를 같은 등급 후보 수로 나누므로,
     * 이벤트 키캡이 섞이면 같은 등급 상시 키캡의 확률이 낮아진다.
     */
    @Query("""
            select keycap
            from Keycap keycap
            left join UserKeycap userKeycap
              on userKeycap.keycap = keycap
             and userKeycap.user.id = :userId
            where keycap.active = true
              and keycap.acquisitionType = com.ggukmoney.beanzip.domain.keycap.entity.Keycap.AcquisitionType.BOX
              and (userKeycap.id is null or userKeycap.status = com.ggukmoney.beanzip.domain.keycap.entity.UserKeycap.Status.IN_PROGRESS)
            order by keycap.sortOrder asc, keycap.code asc
            """)
    List<Keycap> findIncompleteActiveRewardCandidates(@Param("userId") UUID userId);
}
