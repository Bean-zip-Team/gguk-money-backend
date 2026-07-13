package com.ggukmoney.beanzip.domain.keycap.repository;

import com.ggukmoney.beanzip.domain.keycap.entity.Keycap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KeycapRepository extends JpaRepository<Keycap, Long> {

    Optional<Keycap> findByPublicId(UUID publicId);

    Optional<Keycap> findByCode(String code);

    List<Keycap> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
