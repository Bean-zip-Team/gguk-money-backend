package com.ggukmoney.beanzip.domain.reliability.repository;

import com.ggukmoney.beanzip.domain.reliability.entity.EventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventOutboxRepository extends JpaRepository<EventOutbox, Long> {

    Optional<EventOutbox> findByEventId(UUID eventId);
}
