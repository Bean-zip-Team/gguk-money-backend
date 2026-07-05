package com.ggukmoney.beanzip.domain.notification.repository;

import com.ggukmoney.beanzip.domain.notification.entity.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByPublicId(UUID publicId);
}
