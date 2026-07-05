package com.ggukmoney.beanzip.domain.reliability.repository;

import com.ggukmoney.beanzip.domain.reliability.entity.EventInbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventInboxRepository extends JpaRepository<EventInbox, Long> {

    Optional<EventInbox> findByEventId(UUID eventId);
}
