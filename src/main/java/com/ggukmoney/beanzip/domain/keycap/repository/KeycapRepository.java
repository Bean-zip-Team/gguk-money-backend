package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.data.jpa.repository.JpaRepository;

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
     * 뽑기 후보이자 공개 도감 목록 (BEA-329). {@code BOX} 만 넘긴다.
     *
     * <p>뽑기: 이벤트 키캡이 섞이면 같은 등급 상시 키캡의 확률이 낮아진다(BEA-285). 이미 보유한 키캡도
     * 후보에 남는다 — 중복은 레벨로 쌓인다.
     * 도감: 미보유 카드에 이름을 보여 주므로 시즌 키캡을 내려주면 아직 시작도 안 한 이벤트가 새어 나간다.
     */
    List<Keycap> findByAcquisitionTypeAndActiveTrueOrderBySortOrderAscCodeAsc(Keycap.AcquisitionType acquisitionType);
}
